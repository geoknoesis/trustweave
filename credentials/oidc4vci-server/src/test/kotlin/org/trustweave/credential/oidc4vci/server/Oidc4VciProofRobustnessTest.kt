package org.trustweave.credential.oidc4vci.server

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A failed build must not strand the wallet; a malformed proof is invalid_proof; one credential per configuration. */
class Oidc4VciProofRobustnessTest {
    private val issuerUrl = "https://issuer.example.com"
    private val holder: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val enc = Base64.getUrlEncoder().withoutPadding()

    private class RecordingBuilder(
        var failures: Int = 0,
    ) : Oidc4VciCredentialBuilder {
        override val supportedFormats = setOf("jwt_vc_json")
        val seen = mutableListOf<List<String>>()

        override suspend fun build(request: Oidc4VciCredentialRequest): String {
            if (failures > 0) {
                failures--
                throw IllegalStateException("signer unavailable")
            }
            seen += request.credentialTypes
            return "cred-${request.credentialTypes.joinToString(",")}"
        }
    }

    private fun service(builder: Oidc4VciCredentialBuilder) =
        Oidc4VciIssuerService(issuerUrl, "did:key:z6MkTestIssuer", TEST_CONFIGURATIONS, credentialBuilder = builder)

    private fun proof(
        nonce: String,
        aud: JsonElement = JsonPrimitive(issuerUrl),
        nonceClaim: JsonElement = JsonPrimitive(nonce),
        alg: JsonElement = JsonPrimitive("EdDSA"),
        typ: JsonElement = JsonPrimitive("openid4vci-proof+jwt"),
        kty: JsonElement = JsonPrimitive("OKP"),
    ): String {
        val raw = holder.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }
        val header =
            buildJsonObject {
                put("alg", alg)
                put("typ", typ)
                put(
                    "jwk",
                    buildJsonObject {
                        put("kty", kty)
                        put("crv", "Ed25519")
                        put("x", enc.encodeToString(raw))
                    },
                )
            }
        val payload =
            buildJsonObject {
                put("aud", aud)
                put("nonce", nonceClaim)
            }
        val h = enc.encodeToString(header.toString().toByteArray())
        val p = enc.encodeToString(payload.toString().toByteArray())
        val sig =
            Signature
                .getInstance("Ed25519")
                .apply {
                    initSign(holder.private)
                    update("$h.$p".toByteArray())
                }.sign()
        return "$h.$p.${enc.encodeToString(sig)}"
    }

    private fun token(
        svc: Oidc4VciIssuerService,
        vararg configs: String,
    ) = svc.exchangePreAuthCode(svc.createOffer(configs.toList()).preAuthCode, null)

    @Test
    fun `a builder failure leaves the wallet able to retry with the nonce it holds`() =
        runBlocking<Unit> {
            val builder = RecordingBuilder(failures = 1)
            val svc = service(builder)
            val token = token(svc, "T")
            assertFailsWith<IllegalStateException> {
                svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), proof(token.cNonce))
            }
            // the wallet never saw a new nonce, so the one it already has must still work
            val issued = svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), proof(token.cNonce))
            assertEquals("cred-T", issued.credential)
        }

    @Test
    fun `malformed proof members are invalid_proof with a fresh nonce, not an unexpected error`() =
        runBlocking<Unit> {
            val svc = service(RecordingBuilder())
            val cases =
                mapOf<String, (String) -> String>(
                    "aud array" to { n -> proof(n, aud = JsonArray(listOf(JsonPrimitive(issuerUrl)))) },
                    "aud object" to { n -> proof(n, aud = JsonObject(emptyMap())) },
                    "alg object" to { n -> proof(n, alg = JsonObject(emptyMap())) },
                    "typ array" to { n -> proof(n, typ = JsonArray(emptyList())) },
                    "nonce object" to { n -> proof(n, nonceClaim = JsonObject(emptyMap())) },
                    "kty object" to { n -> proof(n, kty = JsonObject(emptyMap())) },
                )
            for ((name, make) in cases) {
                val token = token(svc, "T")
                val e =
                    assertFailsWith<InvalidProofException>(name) {
                        svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), make(token.cNonce))
                    }
                assertNotEquals(token.cNonce, e.freshCNonce, name)
            }
        }

    @Test
    fun `an offer with several configurations issues one configuration per credential`() =
        runBlocking<Unit> {
            val builder = RecordingBuilder()
            val svc = service(builder)
            val token = token(svc, "T", "A")
            val first =
                svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), proof(token.cNonce), credentialConfigurationId = "A")
            val second =
                svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), proof(first.cNonce!!), credentialConfigurationId = "T")
            assertEquals(listOf(listOf("A"), listOf("T")), builder.seen)
            assertEquals("cred-A", first.credential)
            assertEquals("cred-T", second.credential)
        }

    @Test
    fun `a configuration already issued cannot be requested again while another remains`() =
        runBlocking<Unit> {
            val svc = service(RecordingBuilder())
            val token = token(svc, "T", "A")
            val first =
                svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), proof(token.cNonce), credentialConfigurationId = "A")
            assertFailsWith<CredentialLimitExceededException> {
                svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), proof(first.cNonce!!), credentialConfigurationId = "A")
            }
        }

    @Test
    fun `without an identified configuration the offer's configurations are issued in order`() =
        runBlocking<Unit> {
            val builder = RecordingBuilder()
            val svc = service(builder)
            val token = token(svc, "T", "A")
            val first = svc.issueCredential(token.accessToken, "jwt_vc_json", emptyList(), proof(token.cNonce))
            svc.issueCredential(token.accessToken, "jwt_vc_json", listOf("VerifiableCredential"), proof(first.cNonce!!))
            assertEquals(listOf(listOf("T"), listOf("A")), builder.seen)
            assertTrue(builder.seen.all { it.size == 1 })
        }
}
