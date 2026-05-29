package org.trustweave.did.orb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Base64

/**
 * Helpers for producing JWS Compact Serialization signatures with ES256 (P-256 + SHA-256),
 * the canonical algorithm used by Sidetree `signedData` envelopes for update and
 * deactivate operations.
 */
internal object JwsCompact {

    private val b64url: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDecoder: Base64.Decoder = Base64.getUrlDecoder()
    private val json = Json { encodeDefaults = true }

    /**
     * Build a JWS Compact Serialization (`<header>.<payload>.<signature>`) over
     * [payload], signed with the EC private key encoded in [privateJwk]
     * (`kty=EC, crv=P-256, d=<base64url(private scalar)>`).
     */
    fun signES256(payload: JsonObject, privateJwk: Map<String, Any?>): String {
        require(privateJwk["kty"] == "EC") { "JWK kty must be EC" }
        require(privateJwk["crv"] == "P-256") { "JWK crv must be P-256" }
        val d = privateJwk["d"] as? String
            ?: error("JWK private key must contain 'd' (private scalar, base64url)")

        val headerJson = """{"alg":"ES256","typ":"application/jose"}"""
        val payloadJson = json.encodeToString(JsonObject.serializer(), payload)
        val headerB64 = b64url.encodeToString(headerJson.toByteArray(StandardCharsets.UTF_8))
        val payloadB64 = b64url.encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$headerB64.$payloadB64".toByteArray(StandardCharsets.UTF_8)

        val privateKey = jwkDToEcPrivateKey(d)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(signingInput)
        val derSignature = signer.sign()
        val joseSignature = derToJoseSignature(derSignature, P256_COORD_BYTES)
        val signatureB64 = b64url.encodeToString(joseSignature)

        return "$headerB64.$payloadB64.$signatureB64"
    }

    private fun jwkDToEcPrivateKey(dBase64Url: String): ECPrivateKey {
        val dBytes = b64urlDecoder.decode(dBase64Url)
        val s = BigInteger(1, dBytes)
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }
        val ecParams = params.getParameterSpec(ECParameterSpec::class.java)
        val keySpec = ECPrivateKeySpec(s, ecParams)
        return KeyFactory.getInstance("EC").generatePrivate(keySpec) as ECPrivateKey
    }

    /**
     * Convert an ASN.1 DER ECDSA signature (the form returned by JCA's
     * `SHA256withECDSA`) into JOSE concatenated `r || s` form with each coordinate
     * zero-padded to [coordLength] bytes (32 for P-256).
     */
    internal fun derToJoseSignature(der: ByteArray, coordLength: Int): ByteArray {
        require(der.size >= 8) { "DER signature too short" }
        require(der[0] == 0x30.toByte()) { "DER signature must start with SEQUENCE (0x30)" }
        var i = 1
        // Skip the outer SEQUENCE length (short or long form).
        if (der[i].toInt() and 0x80 != 0) {
            val n = der[i].toInt() and 0x7f
            i += 1 + n
        } else {
            i += 1
        }
        require(der[i] == 0x02.toByte()) { "Expected INTEGER tag for r" }
        i++
        val rLen = der[i].toInt() and 0xff
        i++
        val rBytes = der.sliceArray(i until i + rLen)
        i += rLen
        require(der[i] == 0x02.toByte()) { "Expected INTEGER tag for s" }
        i++
        val sLen = der[i].toInt() and 0xff
        i++
        val sBytes = der.sliceArray(i until i + sLen)

        val r = unsignedFixedLength(rBytes, coordLength)
        val s = unsignedFixedLength(sBytes, coordLength)
        return r + s
    }

    private fun unsignedFixedLength(value: ByteArray, length: Int): ByteArray {
        val stripped = if (value.isNotEmpty() && value[0] == 0x00.toByte() && value.size > 1) {
            value.sliceArray(1 until value.size)
        } else {
            value
        }
        return when {
            stripped.size == length -> stripped
            stripped.size > length -> stripped.sliceArray(stripped.size - length until stripped.size)
            else -> ByteArray(length - stripped.size) + stripped
        }
    }

    private const val P256_COORD_BYTES: Int = 32
}
