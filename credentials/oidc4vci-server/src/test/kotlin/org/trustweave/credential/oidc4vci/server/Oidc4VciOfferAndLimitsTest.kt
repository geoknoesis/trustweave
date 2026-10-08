package org.trustweave.credential.oidc4vci.server

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.credential.oidc4vci.models.TxCode
import org.trustweave.observability.HostAuthentication
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Offer validation, per-token issuance limits, malformed-request mapping and protocol rate limiting. */
class Oidc4VciOfferAndLimitsTest {
    private val issuerUrl = "https://issuer.example.com"
    private val holder: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun service() =
        Oidc4VciIssuerService(
            issuerUrl,
            "did:key:z6MkTestIssuer",
            TEST_CONFIGURATIONS,
            credentialBuilder = TestCredentialBuilder(),
        )

    private fun host(
        svc: Oidc4VciIssuerService,
        limit: Oidc4VciProtocolRateLimit? = Oidc4VciProtocolRateLimit(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            val server =
                Oidc4VciServer(svc)
                    .withAuthentication(HostAuthentication.frontedByProxy("test"))
                    .withProtocolRateLimit(limit)
            with(server) { configureApplication() }
        }
        block()
    }

    private fun proof(nonce: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val raw = holder.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }
        val header =
            buildJsonObject {
                put("alg", "EdDSA")
                put("typ", "openid4vci-proof+jwt")
                put(
                    "jwk",
                    buildJsonObject {
                        put("kty", "OKP")
                        put("crv", "Ed25519")
                        put("x", enc.encodeToString(raw))
                    },
                )
            }
        val payload =
            buildJsonObject {
                put("aud", issuerUrl)
                put("nonce", nonce)
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

    private suspend fun ApplicationTestBuilder.postCredential(
        token: String,
        body: String,
    ): HttpResponse =
        client.post("/credential") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun credentialBody(nonce: String) =
        buildJsonObject {
            put("format", "jwt_vc_json")
            put(
                "proof",
                buildJsonObject {
                    put("proof_type", "jwt")
                    put("jwt", proof(nonce))
                },
            )
        }.toString()

    private fun field(
        response: String,
        name: String,
    ) = Json
        .parseToJsonElement(response)
        .jsonObject[name]
        ?.jsonPrimitive
        ?.content

    // ---- offer validation (M2)

    @Test
    fun `an offer needs at least one credential type`() {
        assertFailsWith<IllegalArgumentException> { service().createOffer(emptyList()) }
    }

    @Test
    fun `an offer may only name configurations the issuer advertises`() {
        assertFailsWith<IllegalArgumentException> { service().createOffer(listOf("NotAdvertised")) }
    }

    @Test
    fun `txCode and txCodeValue must be given together`() {
        val svc = service()
        assertFailsWith<IllegalArgumentException> { svc.createOffer(listOf("T"), txCode = TxCode(length = 4)) }
        assertFailsWith<IllegalArgumentException> { svc.createOffer(listOf("T"), txCodeValue = "1234") }
        assertFailsWith<IllegalArgumentException> { svc.createOffer(listOf("T"), TxCode(length = 4), "") }
        svc.createOffer(listOf("T"), TxCode(length = 4), "1234")
    }

    @Test
    fun `api offer answers an invalid offer with 400 invalid_request`() =
        host(service()) {
            for (body in listOf(
                """{"credentialTypes":[]}""",
                """{"credentialTypes":["NotAdvertised"]}""",
                """{"credentialTypes":["T"],"txCodeValue":"1234"}""",
                """{"credentialTypes":"T"}""",
            )) {
                val r =
                    client.post("/api/offer") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                assertEquals(HttpStatusCode.BadRequest, r.status, body)
                assertEquals("invalid_request", field(r.bodyAsText(), "error"), body)
            }
        }

    // ---- per-token issuance limit (M1)

    @Test
    fun `one access token obtains only the credentials its offer covers`() {
        val svc = service()
        host(svc) {
            val token = svc.exchangePreAuthCode(svc.createOffer(listOf("T")).preAuthCode, null)
            val first = postCredential(token.accessToken, credentialBody(token.cNonce))
            assertEquals(HttpStatusCode.OK, first.status, first.bodyAsText())

            val nextNonce = field(first.bodyAsText(), "c_nonce")!!
            val second = postCredential(token.accessToken, credentialBody(nextNonce))
            assertEquals(HttpStatusCode.BadRequest, second.status, second.bodyAsText())
            assertEquals("invalid_request", field(second.bodyAsText(), "error"))
        }
    }

    @Test
    fun `an offer for two configurations allows exactly two credentials`() {
        val svc = service()
        host(svc) {
            val token = svc.exchangePreAuthCode(svc.createOffer(listOf("T", "A")).preAuthCode, null)
            var nonce = token.cNonce
            repeat(2) {
                val r = postCredential(token.accessToken, credentialBody(nonce))
                assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
                nonce = field(r.bodyAsText(), "c_nonce")!!
            }
            assertEquals(HttpStatusCode.BadRequest, postCredential(token.accessToken, credentialBody(nonce)).status)
        }
    }

    // ---- malformed fields (L1) and /token messages (L2)

    @Test
    fun `wrong-typed fields on credential are 400 invalid_request, not 500`() {
        val svc = service()
        host(svc) {
            val token = svc.exchangePreAuthCode(svc.createOffer(listOf("T")).preAuthCode, null)
            for (body in listOf(
                """{"format":{"a":1}}""",
                """{"format":["jwt_vc_json"]}""",
                """{"proof":"not-an-object"}""",
                """{"proof":{"jwt":{"x":1}}}""",
                """{"credential_definition":"x"}""",
                """{"credential_definition":{"type":"x"}}""",
                """{"credential_definition":{"type":[{"a":1}]}}""",
                """[1,2]""",
                """not json""",
            )) {
                val r = postCredential(token.accessToken, body)
                assertEquals(HttpStatusCode.BadRequest, r.status, "$body -> ${r.bodyAsText()}")
                assertEquals("invalid_request", field(r.bodyAsText(), "error"), body)
            }
        }
    }

    @Test
    fun `wrong-typed transaction_id on deferred_credential is 400 invalid_request`() {
        val svc = service()
        host(svc) {
            val token = svc.exchangePreAuthCode(svc.createOffer(listOf("T")).preAuthCode, null)
            for (body in listOf("""{"transaction_id":{"a":1}}""", """{"transaction_id":[1]}""", """{"transaction_id":5}""", "oops")) {
                val r =
                    client.post("/deferred_credential") {
                        header(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                assertEquals(HttpStatusCode.BadRequest, r.status, "$body -> ${r.bodyAsText()}")
                assertEquals("invalid_request", field(r.bodyAsText(), "error"), body)
            }
        }
    }

    @Test
    fun `token errors do not distinguish an unknown code from a wrong tx_code`() {
        val svc = service()
        host(svc) {
            val withPin = svc.createOffer(listOf("T"), TxCode(length = 4), "1234")

            suspend fun redeem(
                code: String,
                pin: String?,
            ) = client.post("/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code" +
                        "&pre-authorized_code=$code" + (pin?.let { "&tx_code=$it" } ?: ""),
                )
            }
            val unknown = redeem("no-such-code", null)
            val wrongPin = redeem(withPin.preAuthCode, "0000")
            assertEquals(HttpStatusCode.BadRequest, unknown.status)
            assertEquals(unknown.status, wrongPin.status)
            assertEquals(unknown.bodyAsText(), wrongPin.bodyAsText())
            assertFalse("tx_code" in wrongPin.bodyAsText().lowercase())
        }
    }

    @Test
    fun `an expired and an unknown access token are answered identically`() {
        val svc =
            Oidc4VciIssuerService(
                issuerUrl,
                "did:key:z6MkTestIssuer",
                TEST_CONFIGURATIONS,
                tokenTtlSeconds = 0,
                credentialBuilder = TestCredentialBuilder(),
            )
        host(svc) {
            val expired = svc.exchangePreAuthCode(svc.createOffer(listOf("T")).preAuthCode, null)
            val a = postCredential(expired.accessToken, "{}")
            val b = postCredential("never-issued", "{}")
            assertEquals(HttpStatusCode.Unauthorized, a.status)
            assertEquals(b.bodyAsText(), a.bodyAsText())
        }
    }

    // ---- protocol rate limit (M1)

    @Test
    fun `credential and deferred_credential are rate limited apart from the host gate`() {
        val svc = service()
        host(svc, Oidc4VciProtocolRateLimit(permits = 3)) {
            repeat(3) {
                assertEquals(HttpStatusCode.Unauthorized, postCredential("bogus", "{}").status)
            }
            val limited = postCredential("bogus", "{}")
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertTrue(limited.headers.contains(HttpHeaders.RetryAfter))

            // Budgets are per endpoint: exhausting /credential leaves /deferred_credential available.
            val deferred =
                client.post("/deferred_credential") {
                    header(HttpHeaders.Authorization, "Bearer bogus")
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.Unauthorized, deferred.status)
        }
    }

    @Test
    fun `the rate limit can be replaced by null`() {
        val svc = service()
        host(svc, null) {
            repeat(200) { assertEquals(HttpStatusCode.Unauthorized, postCredential("bogus", "{}").status) }
        }
    }
}
