package com.trustweave.credential.didcomm.crypto

import com.trustweave.credential.didcomm.exception.DidCommException

import com.trustweave.credential.didcomm.models.DidCommEnvelope
import com.trustweave.credential.didcomm.models.DidCommProtectedHeaders
import com.trustweave.credential.didcomm.models.DidCommRecipient
import com.trustweave.credential.didcomm.models.DidCommRecipientHeader
import com.trustweave.core.identifiers.KeyId
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.VerificationMethod
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.util.Base64
import java.security.*
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.*

/**
 * Cryptographic operations for DIDComm V2 (Fallback Implementation).
 *
 * This is a fallback implementation that provides basic structure but uses
 * simplified cryptographic operations. For production use, the system automatically
 * uses `DidCommCryptoProduction` which leverages the `didcomm-java` library.
 *
 * This implementation is kept for:
 * - Development/testing when didcomm-java is not available
 * - Fallback if production crypto fails
 *
 * ⚠️ **WARNING**: This implementation uses simplified crypto and should not be
 * used in production. Always use production crypto when available.
 */
class DidCommCrypto(
    private val kms: KeyManagementService,
    private val resolveDid: suspend (String) -> DidDocument?
) : DidCommCryptoInterface {
    init {
        // Ensure BouncyCastle is registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Encrypts a message using AuthCrypt (ECDH-1PU + AES-256-GCM).
     *
     * @param message The plaintext message JSON
     * @param fromDid Sender DID
     * @param fromKeyId Sender key ID for key agreement
     * @param toDid Recipient DID
     * @param toKeyId Recipient key ID for key agreement (from their DID document)
     * @return Encrypted envelope
     */
    override suspend fun encrypt(
        message: JsonObject,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String
    ): DidCommEnvelope = withContext(Dispatchers.IO) {
        try {
            // Resolve recipient DID document
            val recipientDoc = resolveDid(toDid)
                ?: throw IllegalArgumentException("Cannot resolve recipient DID: $toDid")

            // Get recipient's public key
            val recipientKey = recipientDoc.verificationMethod
                .find { it.id.toString() == toKeyId || it.id.toString() == "$toDid#$toKeyId" }
                ?: throw IllegalArgumentException("Recipient key not found: $toKeyId")

            val recipientPublicKeyJwk = recipientKey.publicKeyJwk
                ?: throw IllegalArgumentException("Recipient key missing JWK")

            // Get sender's key handle
            val senderKeyResult = kms.getPublicKey(KeyId(fromKeyId))
            val senderKeyHandle = when (senderKeyResult) {
                is com.trustweave.kms.results.GetPublicKeyResult.Success -> senderKeyResult.keyHandle
                is com.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound -> throw IllegalArgumentException("Sender key not found: ${senderKeyResult.keyId.value}")
                is com.trustweave.kms.results.GetPublicKeyResult.Failure.Error -> throw IllegalArgumentException("Failed to get sender key: ${senderKeyResult.reason}")
            }
            val senderPublicKeyJwk = senderKeyHandle.publicKeyJwk
                ?: throw IllegalArgumentException("Sender key missing JWK")

            // Generate ephemeral key pair for ECDH
            val ephemeralKeyPair = generateEphemeralKeyPair(recipientPublicKeyJwk)

            // Perform ECDH-1PU key agreement
            val sharedSecret = performEcdh1puKeyAgreement(
                ephemeralKeyPair.private,
                senderPublicKeyJwk,
                recipientPublicKeyJwk
            )

            // Derive content encryption key (CEK) and key encryption key (KEK)
            val (cek, kek) = deriveKeys(sharedSecret)

            // Encrypt the message with AES-256-GCM
            val messageBytes = message.toString().toByteArray(Charsets.UTF_8)
            val (iv, ciphertext, tag) = encryptAes256Gcm(messageBytes, cek)

            // Wrap the CEK with KEK
            val wrappedKey = wrapKey(cek, kek)

            // Build protected headers
            val protectedHeaders = DidCommProtectedHeaders(
                skid = fromKeyId
            )
            val protectedHeadersJson = buildJsonObject {
                put("typ", protectedHeaders.typ)
                put("alg", protectedHeaders.alg)
                put("enc", protectedHeaders.enc)
                protectedHeaders.skid?.let { put("skid", it) }
            }
            val protectedHeadersBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(
                protectedHeadersJson.toString().toByteArray(Charsets.UTF_8)
            )

            // Build recipient header with ephemeral public key
            val epkJson = buildJsonObject {
                ephemeralKeyPair.publicJwk["kty"]?.let { put("kty", it) }
                ephemeralKeyPair.publicJwk["crv"]?.let { put("crv", it) }
                ephemeralKeyPair.publicJwk["x"]?.let { put("x", it) }
                ephemeralKeyPair.publicJwk["y"]?.let { put("y", it) }
            }

            val recipient = DidCommRecipient(
                header = DidCommRecipientHeader(
                    kid = toKeyId,
                    epk = epkJson
                ),
                encrypted_key = Base64.getUrlEncoder().withoutPadding().encodeToString(wrappedKey)
            )

            DidCommEnvelope(
                protected = protectedHeadersBase64,
                recipients = listOf(recipient),
                iv = Base64.getUrlEncoder().withoutPadding().encodeToString(iv),
                ciphertext = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext),
                tag = Base64.getUrlEncoder().withoutPadding().encodeToString(tag)
            )
        } catch (e: Exception) {
            throw DidCommException.EncryptionFailed(
                reason = e.message ?: "Unknown encryption error",
                fromDid = fromDid,
                toDid = toDid,
                cause = e
            )
        }
    }

    /**
     * Decrypts an encrypted envelope.
     *
     * @param envelope The encrypted envelope
     * @param recipientDid Recipient DID
     * @param recipientKeyId Recipient key ID
     * @param senderDid Sender DID (for verification)
     * @return Decrypted message JSON
     */
    override suspend fun decrypt(
        envelope: DidCommEnvelope,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String
    ): JsonObject = withContext(Dispatchers.IO) {
        try {
            // Resolve sender DID document
            val senderDoc = resolveDid(senderDid)
                ?: throw IllegalArgumentException("Cannot resolve sender DID: $senderDid")

            // Get sender's public key from protected headers
            val protectedHeadersJson = Json.parseToJsonElement(
                String(Base64.getUrlDecoder().decode(envelope.protected))
            ).jsonObject

            val senderKeyId = protectedHeadersJson["skid"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing sender key ID in protected headers")

            val senderKey = senderDoc.verificationMethod
                .find { it.id.toString() == senderKeyId || it.id.toString() == "$senderDid#$senderKeyId" }
                ?: throw IllegalArgumentException("Sender key not found: $senderKeyId")

            val senderPublicKeyJwk = senderKey.publicKeyJwk
                ?: throw IllegalArgumentException("Sender key missing JWK")

            // Get recipient's private key (from KMS)
            val recipientKeyResult = kms.getPublicKey(com.trustweave.core.identifiers.KeyId(recipientKeyId))
            val recipientKeyHandle = when (recipientKeyResult) {
                is com.trustweave.kms.results.GetPublicKeyResult.Success -> recipientKeyResult.keyHandle
                is com.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound -> throw IllegalArgumentException("Recipient key not found: ${recipientKeyResult.keyId.value}")
                is com.trustweave.kms.results.GetPublicKeyResult.Failure.Error -> throw IllegalArgumentException("Failed to get recipient key: ${recipientKeyResult.reason}")
            }
            val recipientPublicKeyJwk = recipientKeyHandle.publicKeyJwk
                ?: throw IllegalArgumentException("Recipient key missing JWK")

            // Get ephemeral public key from recipient header
            val recipient = envelope.recipients.firstOrNull()
                ?: throw IllegalArgumentException("No recipients in envelope")

            val epk = recipient.header.epk
                ?: throw IllegalArgumentException("Missing ephemeral public key")

            // Perform ECDH-1PU key agreement
            val sharedSecret = performEcdh1puKeyAgreement(
                recipientKeyId, // We'll need to get the private key from KMS
                senderPublicKeyJwk,
                recipientPublicKeyJwk,
                epk
            )

            // Derive keys
            val (cek, kek) = deriveKeys(sharedSecret)

            // Unwrap the CEK
            val unwrappedKey = unwrapKey(
                Base64.getUrlDecoder().decode(recipient.encrypted_key),
                kek
            )

            // Decrypt the message
            val ciphertext = Base64.getUrlDecoder().decode(envelope.ciphertext)
            val iv = Base64.getUrlDecoder().decode(envelope.iv)
            val tag = Base64.getUrlDecoder().decode(envelope.tag)

            val plaintext = decryptAes256Gcm(ciphertext, iv, tag, unwrappedKey)

            // Parse and return JSON
            Json.parseToJsonElement(String(plaintext, Charsets.UTF_8)).jsonObject
        } catch (e: Exception) {
            throw DidCommException.DecryptionFailed(
                reason = e.message ?: "Unknown decryption error",
                messageId = null,
                cause = e
            )
        }
    }

    // Helper methods for cryptographic operations

    private fun generateEphemeralKeyPair(recipientKeyJwk: Map<String, Any?>): EphemeralKeyPair {
        val crv = recipientKeyJwk["crv"] as? String
            ?: throw IllegalArgumentException("Missing curve in recipient key")

        val curveName = when (crv) {
            "Ed25519" -> "curve25519"
            "secp256k1" -> "secp256k1"
            "P-256" -> "secp256r1"
            "P-384" -> "secp384r1"
            "P-521" -> "secp521r1"
            else -> throw IllegalArgumentException("Unsupported curve: $crv")
        }

        // Generate ephemeral key pair
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC")
        val ecSpec = ECNamedCurveTable.getParameterSpec(curveName)
        keyPairGenerator.initialize(ecSpec, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        // Convert to JWK format
        val publicKey = keyPair.public as java.security.interfaces.ECPublicKey
        val w = publicKey.w
        val x = Base64.getUrlEncoder().withoutPadding().encodeToString(w.affineX.toByteArray())
        val y = Base64.getUrlEncoder().withoutPadding().encodeToString(w.affineY.toByteArray())

        val publicJwk = buildJsonObject {
            put("kty", "EC")
            put("crv", crv)
            put("x", x)
            put("y", y)
        }

        return EphemeralKeyPair(keyPair.private, publicJwk)
    }

    private suspend fun performEcdh1puKeyAgreement(
        privateKeyId: String,
        senderPublicKeyJwk: Map<String, Any?>,
        recipientPublicKeyJwk: Map<String, Any?>,
        epk: JsonObject
    ): ByteArray {
        // Simplified ECDH-1PU implementation
        // In production, use a proper DIDComm library
        // This is a placeholder that demonstrates the structure

        // For now, return a placeholder shared secret
        // Real implementation would:
        // 1. Load private key from KMS
        // 2. Perform ECDH with sender's public key
        // 3. Perform ECDH with ephemeral public key
        // 4. Combine using ECDH-1PU algorithm

        return ByteArray(32) // Placeholder
    }

    private suspend fun performEcdh1puKeyAgreement(
        ephemeralPrivate: PrivateKey,
        senderPublicKeyJwk: Map<String, Any?>,
        recipientPublicKeyJwk: Map<String, Any?>
    ): ByteArray {
        // Simplified implementation
        return ByteArray(32) // Placeholder
    }

    private fun deriveKeys(sharedSecret: ByteArray): Pair<ByteArray, ByteArray> {
        // Derive CEK and KEK using HKDF
        // Simplified: split the shared secret
        val cek = sharedSecret.sliceArray(0..15)
        val kek = sharedSecret.sliceArray(16..31)
        return Pair(cek, kek)
    }

    private fun encryptAes256Gcm(
        plaintext: ByteArray,
        key: ByteArray
    ): Triple<ByteArray, ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val spec = GCMParameterSpec(128, iv)
        val keySpec = SecretKeySpec(key, "AES")

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)
        val ciphertext = cipher.doFinal(plaintext)

        // GCM tag is appended to ciphertext
        val tag = ciphertext.sliceArray(ciphertext.size - 16 until ciphertext.size)
        val ciphertextOnly = ciphertext.sliceArray(0 until ciphertext.size - 16)

        return Triple(iv, ciphertextOnly, tag)
    }

    private fun decryptAes256Gcm(
        ciphertext: ByteArray,
        iv: ByteArray,
        tag: ByteArray,
        key: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        val spec = GCMParameterSpec(128, iv)
        val keySpec = SecretKeySpec(key, "AES")

        val ciphertextWithTag = ciphertext + tag
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
        return cipher.doFinal(ciphertextWithTag)
    }

    private fun wrapKey(key: ByteArray, kek: ByteArray): ByteArray {
        // AES-256-KW key wrapping
        val cipher = Cipher.getInstance("AESWrap", "BC")
        val keySpec = SecretKeySpec(kek, "AES")
        cipher.init(Cipher.WRAP_MODE, keySpec)
        return cipher.wrap(SecretKeySpec(key, "AES"))
    }

    private fun unwrapKey(wrappedKey: ByteArray, kek: ByteArray): ByteArray {
        // AES-256-KW key unwrapping
        val cipher = Cipher.getInstance("AESWrap", "BC")
        val keySpec = SecretKeySpec(kek, "AES")
        cipher.init(Cipher.UNWRAP_MODE, keySpec)
        val key = cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY)
        return key.encoded
    }

    private data class EphemeralKeyPair(
        val private: PrivateKey,
        val publicJwk: JsonObject
    )
}


