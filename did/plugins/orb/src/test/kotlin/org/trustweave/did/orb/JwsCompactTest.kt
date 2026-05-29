package org.trustweave.did.orb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwsCompactTest {

    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDecoder = Base64.getUrlDecoder()

    /**
     * Round-trip: produce a JWS Compact Serialization with [JwsCompact.signES256]
     * and verify the signature using a separately-constructed JCA `Signature`
     * instance. Proves we emit a valid ES256 JWS that any conformant Sidetree
     * implementation can verify.
     */
    @Test
    fun `signES256 produces a JWS that verifies against the corresponding public JWK`() {
        val keyPair = generateP256KeyPair()
        val payload = buildJsonObject {
            put("updateKey", "any string")
            put("deltaHash", "ZGVsdGFIYXNo")
        }

        val jws = JwsCompact.signES256(payload, keyPair.privateJwk)

        val parts = jws.split(".")
        assertEquals(3, parts.size, "JWS Compact Serialization must have 3 parts")

        val headerJson = String(b64urlDecoder.decode(parts[0]), StandardCharsets.UTF_8)
        val header = Json.parseToJsonElement(headerJson) as JsonObject
        assertEquals("ES256", (header["alg"] as kotlinx.serialization.json.JsonPrimitive).content)

        val parsedPayload = Json.parseToJsonElement(
            String(b64urlDecoder.decode(parts[1]), StandardCharsets.UTF_8),
        ) as JsonObject
        assertEquals(payload, parsedPayload)

        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
        val joseSig = b64urlDecoder.decode(parts[2])
        assertEquals(64, joseSig.size, "ES256 JOSE signature must be 64 bytes (r||s, 32 each)")

        val publicKey = jwkToEcPublicKey(keyPair.publicJwk)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(signingInput)
        assertTrue(verifier.verify(joseToDer(joseSig)), "ES256 signature must verify against the public JWK")
    }

    @Test
    fun `signES256 produces a different signature for a different payload (same key)`() {
        val keyPair = generateP256KeyPair()
        val jws1 = JwsCompact.signES256(buildJsonObject { put("a", "1") }, keyPair.privateJwk)
        val jws2 = JwsCompact.signES256(buildJsonObject { put("a", "2") }, keyPair.privateJwk)
        assertFalse(jws1 == jws2, "JWS for different payloads must differ")
    }

    @Test
    fun `signES256 with a different key produces a signature that does not verify against the original public key`() {
        val keyPair1 = generateP256KeyPair()
        val keyPair2 = generateP256KeyPair()
        val payload = buildJsonObject { put("k", "v") }
        val jws = JwsCompact.signES256(payload, keyPair2.privateJwk)
        val parts = jws.split(".")

        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
        val joseSig = b64urlDecoder.decode(parts[2])
        val publicKey = jwkToEcPublicKey(keyPair1.publicJwk)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(signingInput)
        assertFalse(verifier.verify(joseToDer(joseSig)), "Signature must NOT verify against an unrelated public key")
    }

    @Test
    fun `signES256 rejects non-EC JWKs`() {
        val rsaLike = mapOf("kty" to "RSA", "n" to "x", "d" to "y")
        assertThrows<IllegalArgumentException> { JwsCompact.signES256(buildJsonObject {}, rsaLike) }
    }

    @Test
    fun `signES256 rejects JWKs missing private d`() {
        val publicOnly = mapOf("kty" to "EC", "crv" to "P-256", "x" to "x", "y" to "y")
        assertThrows<IllegalStateException> { JwsCompact.signES256(buildJsonObject {}, publicOnly) }
    }

    // ─── Test helpers ────────────────────────────────────────────────────────────

    private data class TestKeyPair(
        val privateJwk: Map<String, Any?>,
        val publicJwk: Map<String, Any?>,
    )

    private fun generateP256KeyPair(): TestKeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val kp = gen.generateKeyPair()
        val pub = kp.public as ECPublicKey
        val priv = kp.private as java.security.interfaces.ECPrivateKey
        val w = pub.w
        val xBytes = w.affineX.toByteArray().padCoord()
        val yBytes = w.affineY.toByteArray().padCoord()
        val dBytes = priv.s.toByteArray().padCoord()
        val pubJwk = mapOf(
            "kty" to "EC",
            "crv" to "P-256",
            "x" to b64url.encodeToString(xBytes),
            "y" to b64url.encodeToString(yBytes),
        )
        val privJwk = pubJwk + ("d" to b64url.encodeToString(dBytes))
        return TestKeyPair(privateJwk = privJwk, publicJwk = pubJwk)
    }

    private fun ByteArray.padCoord(): ByteArray = when {
        size > 32 -> sliceArray(size - 32 until size)
        size < 32 -> ByteArray(32 - size) + this
        else -> this
    }

    private fun jwkToEcPublicKey(jwk: Map<String, Any?>): ECPublicKey {
        val xBytes = b64urlDecoder.decode(jwk["x"] as String)
        val yBytes = b64urlDecoder.decode(jwk["y"] as String)
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }
        val ecParams = params.getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, xBytes), BigInteger(1, yBytes))
        val spec = ECPublicKeySpec(point, ecParams)
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }

    /**
     * Convert a JOSE ECDSA signature (`r || s`, fixed 32-byte halves) into the
     * ASN.1 DER encoding (`SEQUENCE { INTEGER r, INTEGER s }`) that JCA's
     * `SHA256withECDSA.verify` expects.
     */
    private fun joseToDer(jose: ByteArray): ByteArray {
        val half = jose.size / 2
        val r = BigInteger(1, jose.sliceArray(0 until half))
        val s = BigInteger(1, jose.sliceArray(half until jose.size))
        val rBytes = r.toByteArray()
        val sBytes = s.toByteArray()
        val rDer = byteArrayOf(0x02, rBytes.size.toByte()) + rBytes
        val sDer = byteArrayOf(0x02, sBytes.size.toByte()) + sBytes
        val content = rDer + sDer
        return byteArrayOf(0x30, content.size.toByte()) + content
    }
}
