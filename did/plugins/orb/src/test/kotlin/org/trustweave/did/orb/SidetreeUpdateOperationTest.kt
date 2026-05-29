package org.trustweave.did.orb

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.trustweave.did.model.DidDocument
import org.trustweave.did.identifiers.Did
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Cryptographic correctness tests for [SidetreeOrbClient.buildUpdateOperation] and
 * [SidetreeOrbClient.buildDeactivateOperation] per Sidetree v1.0.0 §6.2 / §6.4.
 *
 * These tests verify what the existing mockwebserver-based tests cannot: that the
 * operations would actually be accepted by a conformant Sidetree node.
 */
class SidetreeUpdateOperationTest {

    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDecoder = Base64.getUrlDecoder()

    private val client = SidetreeOrbClient(
        httpClient = OkHttpClient(),
        config = OrbDidConfig(baseUrl = "https://orb.example.com"),
    )

    @Test
    fun `update reveals the PREVIOUS update key (not a fresh one)`() = runBlocking {
        val previous = client.generateP256KeyPair()
        val next = client.generateP256KeyPair()

        val updateOp = client.buildUpdateOperation(
            did = "did:orb:EiSomeSuffix",
            updatedDocument = emptyDocument("did:orb:EiSomeSuffix"),
            previousUpdateKeyPair = previous,
            nextUpdatePublicJwk = next.publicJwk,
        )

        val expectedRevealValue = b64url.encodeToString(sha256(jcs(previous.publicJwk)))
        assertEquals(
            expectedRevealValue,
            updateOp["revealValue"]?.jsonPrimitive?.content,
            "revealValue MUST be base64url(SHA-256(JCS(previousUpdatePublicJwk))).",
        )

        val rebuilt = b64url.encodeToString(sha256(jcs(next.publicJwk)))
        val nextCommitment = updateOp["delta"]?.jsonObject?.get("updateCommitment")?.jsonPrimitive?.content
        assertEquals(rebuilt, nextCommitment, "delta.updateCommitment MUST be the hash of the NEXT update public key.")

        assertNotEquals(
            updateOp["revealValue"]?.jsonPrimitive?.content,
            nextCommitment,
            "revealValue and the new updateCommitment MUST come from different keys.",
        )
    }

    @Test
    fun `update signedData is a JWS Compact Serialization (3 dot-separated parts), not a JSON object`() = runBlocking {
        val previous = client.generateP256KeyPair()
        val next = client.generateP256KeyPair()

        val updateOp = client.buildUpdateOperation(
            did = "did:orb:EiSomeSuffix",
            updatedDocument = emptyDocument("did:orb:EiSomeSuffix"),
            previousUpdateKeyPair = previous,
            nextUpdatePublicJwk = next.publicJwk,
        )

        val signedData = updateOp["signedData"]?.jsonPrimitive?.content
        assertNotNull(signedData)
        val parts = signedData.split(".")
        assertEquals(3, parts.size, "signedData MUST be a JWS Compact Serialization with 3 parts.")
    }

    @Test
    fun `update signedData JWS verifies against the previous update public key`() = runBlocking {
        val previous = client.generateP256KeyPair()
        val next = client.generateP256KeyPair()

        val updateOp = client.buildUpdateOperation(
            did = "did:orb:EiSomeSuffix",
            updatedDocument = emptyDocument("did:orb:EiSomeSuffix"),
            previousUpdateKeyPair = previous,
            nextUpdatePublicJwk = next.publicJwk,
        )

        assertTrue(
            verifyJwsAgainst(updateOp["signedData"]!!.jsonPrimitive.content, previous.publicJwk),
            "signedData JWS MUST verify with the previous update public key.",
        )
    }

    @Test
    fun `update signedData payload contains updateKey=previous and deltaHash`() = runBlocking {
        val previous = client.generateP256KeyPair()
        val next = client.generateP256KeyPair()

        val updateOp = client.buildUpdateOperation(
            did = "did:orb:EiSomeSuffix",
            updatedDocument = emptyDocument("did:orb:EiSomeSuffix"),
            previousUpdateKeyPair = previous,
            nextUpdatePublicJwk = next.publicJwk,
        )

        val signedData = updateOp["signedData"]!!.jsonPrimitive.content
        val parts = signedData.split(".")
        val payload = Json.parseToJsonElement(
            String(b64urlDecoder.decode(parts[1]), StandardCharsets.UTF_8),
        ) as JsonObject

        // updateKey in the payload MUST be the previous update public key.
        val updateKey = payload["updateKey"] as JsonObject
        assertEquals(previous.publicJwk["x"], updateKey["x"]?.jsonPrimitive?.content)
        assertEquals(previous.publicJwk["y"], updateKey["y"]?.jsonPrimitive?.content)
        assertEquals("EC", updateKey["kty"]?.jsonPrimitive?.content)
        assertEquals("P-256", updateKey["crv"]?.jsonPrimitive?.content)

        // deltaHash MUST be base64url(SHA-256(JCS(delta))).
        val delta = updateOp["delta"] as JsonObject
        val expectedDeltaHash = b64url.encodeToString(sha256(jcs(delta)))
        assertEquals(expectedDeltaHash, payload["deltaHash"]?.jsonPrimitive?.content)
    }

    @Test
    fun `deactivate reveals the PREVIOUS recovery key and signs with its private half`() = runBlocking {
        val previousRecovery = client.generateP256KeyPair()

        val deactivateOp = client.buildDeactivateOperation(
            did = "did:orb:EiSomeSuffix",
            previousRecoveryKeyPair = previousRecovery,
        )

        val expectedRevealValue = b64url.encodeToString(sha256(jcs(previousRecovery.publicJwk)))
        assertEquals(
            expectedRevealValue,
            deactivateOp["revealValue"]?.jsonPrimitive?.content,
            "revealValue MUST be base64url(SHA-256(JCS(previousRecoveryPublicJwk))).",
        )

        val signedData = deactivateOp["signedData"]?.jsonPrimitive?.content
        assertNotNull(signedData)
        assertEquals(3, signedData.split(".").size, "signedData MUST be JWS Compact Serialization.")
        assertTrue(
            verifyJwsAgainst(signedData, previousRecovery.publicJwk),
            "signedData JWS MUST verify with the previous recovery public key.",
        )
    }

    // ─── Test helpers ────────────────────────────────────────────────────────────

    private fun emptyDocument(did: String): DidDocument {
        return DidDocument(id = Did(did))
    }

    /**
     * JCS canonicalization for a JSON object: sort object keys lexicographically,
     * no whitespace, matches the helper inside [SidetreeOrbClient].
     */
    private fun jcs(obj: JsonObject): ByteArray {
        return obj.entries
            .sortedBy { it.key }
            .joinToString(",", "{", "}") { (k, v) -> "\"$k\":${jcsValue(v)}" }
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun jcs(map: Map<String, Any?>): ByteArray {
        val tree = map.entries
            .sortedBy { it.key }
            .joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"${v}\"" }
        return tree.toByteArray(StandardCharsets.UTF_8)
    }

    private fun jcsValue(element: kotlinx.serialization.json.JsonElement): String = when (element) {
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .joinToString(",", "{", "}") { (k, v) -> "\"$k\":${jcsValue(v)}" }
        is kotlinx.serialization.json.JsonArray -> element.joinToString(",", "[", "]") { jcsValue(it) }
        is JsonPrimitive -> element.toString()
        else -> element.toString()
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    private fun verifyJwsAgainst(jws: String, publicJwk: Map<String, Any?>): Boolean {
        val parts = jws.split(".")
        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
        val joseSig = b64urlDecoder.decode(parts[2])
        val pub = jwkToEcPublicKey(publicJwk)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pub)
        verifier.update(signingInput)
        return verifier.verify(joseToDer(joseSig))
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
