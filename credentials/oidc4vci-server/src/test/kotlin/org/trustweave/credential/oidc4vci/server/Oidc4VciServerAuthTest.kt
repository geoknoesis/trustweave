package org.trustweave.credential.oidc4vci.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.trustweave.observability.HostAuthentication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The host gate covers the issuer's administrative surface; the protocol surface carries its own.
 *
 * `POST /api/offer` mints credential offers, so it must not be reachable because a port was. The
 * OID4VCI endpoints are a different case: their callers are wallets, which hold no host
 * credential, so a host gate in front of them would refuse the protocol rather than protect it.
 */
class Oidc4VciServerAuthTest {
    private val token = "b".repeat(48)

    private fun server() = Oidc4VciServer(Oidc4VciIssuerService(baseUrl = "https://issuer.example", issuerDid = "did:key:z6MkTestIssuer"))

    private val offer = """{"credentialTypes":["UniversityDegree"]}"""

    @Test
    fun `an unconfigured server refuses to mint an offer and says how to fix it`() =
        testApplication {
            val subject = server()
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/api/offer") {
                    contentType(ContentType.Application.Json)
                    setBody(offer)
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = response.bodyAsText()
            assertTrue("authentication_not_configured" in body, body)
            assertTrue("withAuthentication" in body, "the refusal must say how to fix it: $body")
        }

    @Test
    fun `the protocol endpoints stay reachable while the server is unconfigured`() =
        testApplication {
            val subject = server()
            application { with(subject) { configureApplication() } }
            // The request is rejected on its own merits, by the protocol — not by the host gate.
            val token = client.post("/token") { setBody("grant_type=nonsense") }
            assertNotEquals(
                HttpStatusCode.ServiceUnavailable,
                token.status,
                "the OID4VCI token endpoint must not be gated behind a host credential a wallet cannot hold",
            )
            val credential = client.post("/credential") { setBody("{}") }
            assertNotEquals(HttpStatusCode.ServiceUnavailable, credential.status)
        }

    @Test
    fun `the protocol endpoints stay reachable when the host gate is configured`() =
        testApplication {
            val subject = server().withAuthentication(HostAuthentication.bearerToken(token))
            application { with(subject) { configureApplication() } }
            val credential = client.post("/credential") { setBody("{}") }
            assertEquals(
                HttpStatusCode.Unauthorized,
                credential.status,
                "the refusal must come from the missing access token, not from the host gate",
            )
            assertTrue("invalid_token" in credential.bodyAsText(), credential.bodyAsText())
        }

    @Test
    fun `issuer metadata is readable either way`() =
        testApplication {
            val subject = server()
            application { with(subject) { configureApplication() } }
            assertEquals(HttpStatusCode.OK, client.get("/.well-known/openid-credential-issuer").status)
        }

    @Test
    fun `a caller with the token may mint an offer`() =
        testApplication {
            val subject = server().withAuthentication(HostAuthentication.bearerToken(token))
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/api/offer") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(offer)
                }
            assertNotEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertNotEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
