package org.trustweave.did.sidetree

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
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

class SidetreeJwsCompactTest {

    private val b64urlDecoder = Base64.getUrlDecoder()

    @Test
    fun `signES256 produces a JWS that verifies against the corresponding public JWK`() {
        val keyPair = SidetreeP256KeyPair.generate()
        val payload = buildJsonObject {
            put("updateKey", "any string")
            put("deltaHash", "ZGVsdGFIYXNo")
        }

        val jws = SidetreeJwsCompact.signES256(payload, keyPair.privateJwk)

        val parts = jws.split(".")
        assertEquals(3, parts.size, "JWS Compact Serialization must have 3 parts")

        val headerJson = String(b64urlDecoder.decode(parts[0]), StandardCharsets.UTF_8)
        val header = Json.parseToJsonElement(headerJson) as JsonObject
        assertEquals("ES256", (header["alg"] as JsonPrimitive).content)

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
        val keyPair = SidetreeP256KeyPair.generate()
        val jws1 = SidetreeJwsCompact.signES256(buildJsonObject { put("a", "1") }, keyPair.privateJwk)
        val jws2 = SidetreeJwsCompact.signES256(buildJsonObject { put("a", "2") }, keyPair.privateJwk)
        assertFalse(jws1 == jws2, "JWS for different payloads must differ")
    }

    @Test
    fun `signES256 with a different key produces a signature that does not verify against the original public key`() {
        val keyPair1 = SidetreeP256KeyPair.generate()
        val keyPair2 = SidetreeP256KeyPair.generate()
        val payload = buildJsonObject { put("k", "v") }
        val jws = SidetreeJwsCompact.signES256(payload, keyPair2.privateJwk)
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
        assertThrows<IllegalArgumentException> { SidetreeJwsCompact.signES256(buildJsonObject {}, rsaLike) }
    }

    @Test
    fun `signES256 rejects JWKs missing private d`() {
        val publicOnly = mapOf("kty" to "EC", "crv" to "P-256", "x" to "x", "y" to "y")
        assertThrows<IllegalStateException> { SidetreeJwsCompact.signES256(buildJsonObject {}, publicOnly) }
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
