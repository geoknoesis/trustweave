package org.trustweave.credential.mdl.engine

import com.upokecenter.cbor.CBORObject
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.mdl.MdocException
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import java.security.KeyFactory
import java.security.Security
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * COSE_Sign1 encoding for ISO 18013-5 MSO signing and verification.
 *
 * COSE_Sign1 structure (RFC 8152):
 *   [protected-headers-bstr, unprotected-headers-map, payload-bstr-or-nil, signature-bstr]
 *
 * ISO 18013-5 uses COSE_Sign1 to sign the Mobile Security Object (MSO).
 * The protected header contains the algorithm identifier (alg = -7 for ES256).
 */
internal object MdocCoseSign {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // COSE algorithm identifiers (RFC 8152 + IANA COSE)
    private const val ALG_ES256 = -7
    private const val ALG_ES384 = -35
    private const val ALG_ES512 = -36
    private const val ALG_EDDSA = -8

    // COSE header parameters
    private const val HEADER_ALG = 1

    /**
     * Sign an MSO payload, producing a CBOR-encoded COSE_Sign1 byte array.
     *
     * @param payload     The MSO CBOR bytes to sign (used as the COSE payload).
     * @param keyId       KMS key identifier for the issuer signing key.
     * @param kms         Key management service used for signing.
     * @param algorithm   Signing algorithm (defaults to P-256 / ES256).
     * @return CBOR-encoded COSE_Sign1 byte array.
     */
    suspend fun sign(
        payload: ByteArray,
        keyId: KeyId,
        kms: KeyManagementService,
        algorithm: Algorithm = Algorithm.P256
    ): ByteArray {
        val coseAlg = algorithmToCose(algorithm)

        // Build protected header: { alg: <coseAlg> }
        val protectedHeaderMap = CBORObject.NewOrderedMap()
        protectedHeaderMap[CBORObject.FromObject(HEADER_ALG)] = CBORObject.FromObject(coseAlg)
        val protectedHeaderBytes = protectedHeaderMap.EncodeToBytes()

        // Sig_structure = ["Signature1", protected-bstr, external-aad, payload]
        val sigStructure = CBORObject.NewArray()
        sigStructure.Add(CBORObject.FromObject("Signature1"))
        sigStructure.Add(CBORObject.FromObject(protectedHeaderBytes))
        sigStructure.Add(CBORObject.FromObject(ByteArray(0))) // empty external AAD
        sigStructure.Add(CBORObject.FromObject(payload))
        val toBeSigned = sigStructure.EncodeToBytes()

        val signResult = kms.sign(keyId, toBeSigned, algorithm)
        val signature = when (signResult) {
            is SignResult.Success -> signResult.signature
            is SignResult.Failure.KeyNotFound ->
                throw MdocException("KMS_KEY_NOT_FOUND", "Issuer signing key not found: ${keyId.value}")
            is SignResult.Failure.UnsupportedAlgorithm ->
                throw MdocException("KMS_UNSUPPORTED_ALG", "Algorithm not supported for key: ${keyId.value}")
            is SignResult.Failure.Error ->
                throw MdocException("KMS_SIGN_ERROR", "Signing failed: ${signResult.reason}", signResult.cause)
        }

        // COSE_Sign1 = [protected-bstr, unprotected-map, payload-bstr, signature-bstr]
        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(protectedHeaderBytes))
        coseSign1.Add(CBORObject.NewOrderedMap()) // empty unprotected headers
        coseSign1.Add(CBORObject.FromObject(payload))
        coseSign1.Add(CBORObject.FromObject(signature))

        return coseSign1.EncodeToBytes()
    }

    /**
     * Verify a CBOR-encoded COSE_Sign1 structure.
     *
     * @param coseSign1Bytes CBOR-encoded COSE_Sign1 bytes.
     * @param kms            KMS to retrieve the public key for verification.
     * @param keyId          KMS key identifier for the issuer verification key.
     * @return The verified payload bytes (MSO CBOR).
     * @throws MdocException if verification fails.
     */
    suspend fun verify(
        coseSign1Bytes: ByteArray,
        kms: KeyManagementService,
        keyId: KeyId
    ): ByteArray {
        val coseSign1 = CBORObject.DecodeFromBytes(coseSign1Bytes)
        require(coseSign1.size() == 4) {
            throw MdocException.invalidCoseSign1("Expected 4-element array, got ${coseSign1.size()}")
        }

        val protectedHeaderBytes = coseSign1[0].GetByteString()
        val payloadBytes = coseSign1[2].GetByteString()
        val signature = coseSign1[3].GetByteString()

        // Decode algorithm from protected header
        val protectedHeaderMap = CBORObject.DecodeFromBytes(protectedHeaderBytes)
        val coseAlg = protectedHeaderMap[CBORObject.FromObject(HEADER_ALG)]?.AsInt32()
            ?: throw MdocException.invalidCoseSign1("Missing 'alg' in protected header")

        val algorithm = coseAlgorithmToAlgorithm(coseAlg)

        // Reconstruct Sig_structure
        val sigStructure = CBORObject.NewArray()
        sigStructure.Add(CBORObject.FromObject("Signature1"))
        sigStructure.Add(CBORObject.FromObject(protectedHeaderBytes))
        sigStructure.Add(CBORObject.FromObject(ByteArray(0)))
        sigStructure.Add(CBORObject.FromObject(payloadBytes))
        val toBeSigned = sigStructure.EncodeToBytes()

        // Get public key bytes from KMS
        val publicKeyBytes = when (val result = kms.getPublicKey(keyId)) {
            is GetPublicKeyResult.Success -> extractPublicKeyBytes(result.keyHandle.publicKeyJwk, algorithm)
            is GetPublicKeyResult.Failure.KeyNotFound ->
                throw MdocException("KMS_KEY_NOT_FOUND", "Verifier key not found: ${keyId.value}")
            is GetPublicKeyResult.Failure.Error ->
                throw MdocException("KMS_KEY_ERROR", "Failed to get public key: ${result.reason}")
        }

        val valid = verifySignature(toBeSigned, signature, publicKeyBytes, algorithm)
        if (!valid) throw MdocException.invalidCoseSign1("Signature verification failed")

        return payloadBytes
    }

    /**
     * Verify the device authentication COSE_Sign1 for an ISO 18013-5 presentation.
     *
     * Per ISO 18013-5 §9.1.3.6, the Sig_Structure payload is a CBOR-encoded
     * `DeviceAuthentication` array:
     * ```
     * DeviceAuthentication = [
     *   "DeviceAuthentication",
     *   SessionTranscript,
     *   DocType,
     *   DeviceNameSpacesBytes   -- #6.24(bstr .cbor DeviceNameSpaces)
     * ]
     * ```
     *
     * @param coseSign1Bytes      CBOR-encoded COSE_Sign1 from [DeviceAuth.deviceSignature].
     * @param sessionTranscript   CBOR-encoded SessionTranscript bytes from the reader session.
     * @param docType             Document type string (e.g. "org.iso.18013.5.1.mDL").
     * @param deviceNameSpacesBytes Raw device namespace CBOR bytes from [DeviceSigned.nameSpaces].
     * @param deviceKeyBytes      CBOR-encoded COSE_Key of the device's public key (from the MSO).
     * @return `true` if the device signature is valid; `false` on any failure.
     */
    fun verifyDeviceAuth(
        coseSign1Bytes: ByteArray,
        sessionTranscript: ByteArray,
        docType: String,
        deviceNameSpacesBytes: ByteArray,
        deviceKeyBytes: ByteArray,
    ): Boolean = try {
        val coseSign1 = CBORObject.DecodeFromBytes(coseSign1Bytes)
        if (coseSign1.size() != 4) return false

        val protectedHeaderBytes = coseSign1[0].GetByteString()
        val signature = coseSign1[3].GetByteString()

        // Decode signing algorithm from protected header
        val protectedHeaderMap = CBORObject.DecodeFromBytes(protectedHeaderBytes)
        val coseAlg = protectedHeaderMap[CBORObject.FromObject(HEADER_ALG)]?.AsInt32() ?: return false
        val algorithm = coseAlgorithmToAlgorithm(coseAlg)

        // Decode device public key bytes from the CBOR COSE_Key embedded in the MSO
        val devicePublicKeyBytes = decodeDeviceKey(CBORObject.DecodeFromBytes(deviceKeyBytes)) ?: return false

        // Build DeviceAuthentication payload (per §9.1.3.6)
        val sessionTranscriptCbor = CBORObject.DecodeFromBytes(sessionTranscript)
        val deviceNameSpacesBstr = CBORObject.FromObjectAndTag(deviceNameSpacesBytes, 24) // tag 24 = bstr-wrapped CBOR

        val deviceAuthentication = CBORObject.NewArray()
        deviceAuthentication.Add(CBORObject.FromObject("DeviceAuthentication"))
        deviceAuthentication.Add(sessionTranscriptCbor)
        deviceAuthentication.Add(CBORObject.FromObject(docType))
        deviceAuthentication.Add(deviceNameSpacesBstr)
        val authPayload = deviceAuthentication.EncodeToBytes()

        // Sig_Structure for the device COSE_Sign1 uses the DeviceAuthentication as payload
        val sigStructure = CBORObject.NewArray()
        sigStructure.Add(CBORObject.FromObject("Signature1"))
        sigStructure.Add(CBORObject.FromObject(protectedHeaderBytes))
        sigStructure.Add(CBORObject.FromObject(ByteArray(0))) // empty external AAD
        sigStructure.Add(CBORObject.FromObject(authPayload))
        val toBeSigned = sigStructure.EncodeToBytes()

        verifySignature(toBeSigned, signature, devicePublicKeyBytes, algorithm)
    } catch (_: Exception) {
        false
    }

    /**
     * Decode the uncompressed EC or raw OKP public key bytes from a CBOR COSE_Key map.
     *
     * COSE_Key parameters used:
     * - `1` (kty): 1=OKP, 2=EC2
     * - `-1` (crv): 1=P-256, 2=P-384, 3=P-521, 6=Ed25519
     * - `-2` (x): x-coordinate / Ed25519 public key
     * - `-3` (y): y-coordinate (EC2 only)
     *
     * @return Uncompressed point bytes (`04 || x || y`) for EC2, raw `x` for OKP, or `null`.
     */
    private fun decodeDeviceKey(coseKey: CBORObject): ByteArray? {
        val kty = coseKey[CBORObject.FromObject(1)]?.AsInt32() ?: return null
        return when (kty) {
            1 -> { // OKP (Ed25519 / X25519)
                coseKey[CBORObject.FromObject(-2)]?.GetByteString()
            }
            2 -> { // EC2
                val crv = coseKey[CBORObject.FromObject(-1)]?.AsInt32() ?: 1
                val x = coseKey[CBORObject.FromObject(-2)]?.GetByteString() ?: return null
                val y = coseKey[CBORObject.FromObject(-3)]?.GetByteString() ?: return null
                val keySize = when (crv) { 2 -> 48; 3 -> 66; else -> 32 }
                byteArrayOf(0x04) + x.padStart(keySize) + y.padStart(keySize)
            }
            else -> null
        }
    }

    /**
     * Extract the MSO payload from a COSE_Sign1 without verifying the signature.
     * Used during presentation flow where the issuer cert is verified separately.
     */
    fun extractPayload(coseSign1Bytes: ByteArray): ByteArray {
        val coseSign1 = CBORObject.DecodeFromBytes(coseSign1Bytes)
        if (coseSign1.size() != 4) throw MdocException.invalidCoseSign1("Expected 4-element array")
        return coseSign1[2].GetByteString()
    }

    private fun algorithmToCose(algorithm: Algorithm): Int = when (algorithm) {
        Algorithm.P256 -> ALG_ES256
        Algorithm.P384 -> ALG_ES384
        Algorithm.P521 -> ALG_ES512
        Algorithm.Ed25519 -> ALG_EDDSA
        else -> ALG_ES256
    }

    private fun coseAlgorithmToAlgorithm(coseAlg: Int): Algorithm = when (coseAlg) {
        ALG_ES256 -> Algorithm.P256
        ALG_ES384 -> Algorithm.P384
        ALG_ES512 -> Algorithm.P521
        ALG_EDDSA -> Algorithm.Ed25519
        else -> Algorithm.P256
    }

    private fun extractPublicKeyBytes(jwk: Map<String, Any?>?, algorithm: Algorithm): ByteArray {
        if (jwk == null) throw MdocException("MISSING_PUBLIC_KEY", "No public key JWK available for verification")
        val x = jwk["x"]?.toString()
            ?: throw MdocException("MISSING_PUBLIC_KEY_X", "Missing 'x' coordinate in JWK")
        val y = jwk["y"]?.toString()
        val crv = jwk["crv"]?.toString()

        return when (algorithm) {
            Algorithm.Ed25519 -> {
                java.util.Base64.getUrlDecoder().decode(x)
            }
            else -> {
                // For EC keys, reconstruct uncompressed public key: 04 || x || y
                val xBytes = java.util.Base64.getUrlDecoder().decode(x)
                val yBytes = java.util.Base64.getUrlDecoder().decode(
                    y ?: throw MdocException("MISSING_PUBLIC_KEY_Y", "Missing 'y' coordinate in JWK")
                )
                val keySize = when (crv) {
                    "P-384" -> 48
                    "P-521" -> 66
                    else -> 32 // P-256
                }
                val xPadded = xBytes.padStart(keySize)
                val yPadded = yBytes.padStart(keySize)
                byteArrayOf(0x04) + xPadded + yPadded
            }
        }
    }

    private fun ByteArray.padStart(targetSize: Int): ByteArray {
        if (size >= targetSize) return this
        return ByteArray(targetSize - size) + this
    }

    private fun verifySignature(
        data: ByteArray,
        signature: ByteArray,
        publicKeyBytes: ByteArray,
        algorithm: Algorithm
    ): Boolean {
        return try {
            when (algorithm) {
                Algorithm.Ed25519 -> verifyEd25519(data, signature, publicKeyBytes)
                else -> verifyEcdsa(data, signature, publicKeyBytes, algorithm)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun verifyEcdsa(data: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray, algorithm: Algorithm): Boolean {
        val (jcaAlgorithm, curveName) = when (algorithm) {
            Algorithm.P384 -> "SHA384withECDSA" to "secp384r1"
            Algorithm.P521 -> "SHA512withECDSA" to "secp521r1"
            else -> "SHA256withECDSA" to "secp256r1"
        }
        val keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
        val ecPoint = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec(curveName)
            ?: throw MdocException("INVALID_CURVE", "Unknown EC curve: $curveName")
        val point = ecPoint.curve.decodePoint(publicKeyBytes)
        val pubKeySpec = org.bouncycastle.jce.spec.ECPublicKeySpec(point, ecPoint)
        val pubKey = keyFactory.generatePublic(pubKeySpec)

        val derSig = rawToDerSignature(signature)
        val signer = Signature.getInstance(jcaAlgorithm, BouncyCastleProvider.PROVIDER_NAME)
        signer.initVerify(pubKey)
        signer.update(data)
        return signer.verify(derSig)
    }

    private fun verifyEd25519(data: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
        val keyFactory = KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        // Ed25519 public key: 32 raw bytes — wrap in SubjectPublicKeyInfo (X.509 encoded)
        // OID for Ed25519: 1.3.101.112
        val spkiPrefix = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        )
        val spki = spkiPrefix + publicKeyBytes
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(spki))
        val signer = Signature.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        signer.initVerify(pubKey)
        signer.update(data)
        return signer.verify(signature)
    }

    /**
     * Convert raw (r||s) ECDSA signature (COSE format) to DER encoding (JCA format).
     */
    private fun rawToDerSignature(rawSig: ByteArray): ByteArray {
        val half = rawSig.size / 2
        val r = rawSig.copyOfRange(0, half)
        val s = rawSig.copyOfRange(half, rawSig.size)

        fun encodeMpi(bytes: ByteArray): ByteArray {
            var b = bytes.dropWhile { it == 0.toByte() }.toByteArray()
            if (b.isEmpty()) b = byteArrayOf(0)
            if (b[0] < 0) b = byteArrayOf(0) + b
            return byteArrayOf(0x02, b.size.toByte()) + b
        }

        val rDer = encodeMpi(r)
        val sDer = encodeMpi(s)
        val seq = rDer + sDer
        return byteArrayOf(0x30, seq.size.toByte()) + seq
    }
}
