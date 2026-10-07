package org.trustweave.credential.oidc4vci.server

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.observability.HostAuthentication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Oidc4VciRouteErrorMappingTest {
    private fun service(
        maxPendingOffers: Int = 100,
        maxActiveTokens: Int = 100,
        tokenTtlSeconds: Long = 3600,
        deferredTtlSeconds: Long = 3600,
        maxDeferredCredentials: Int = 100,
    ) = Oidc4VciIssuerService(
        baseUrl = "https://issuer.example",
        issuerDid = "did:key:z6MkTestIssuer",
        tokenTtlSeconds = tokenTtlSeconds,
        maxPendingOffers = maxPendingOffers,
        maxActiveTokens = maxActiveTokens,
        deferredTtlSeconds = deferredTtlSeconds,
        maxDeferredCredentials = maxDeferredCredentials,
        credentialBuilder = TestCredentialBuilder(),
    )

    private fun host(
        service: Oidc4VciIssuerService,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            val server = Oidc4VciServer(service).withAuthentication(HostAuthentication.frontedByProxy("test"))
            with(server) { configureApplication() }
        }
        block()
    }

    private fun liveToken(service: Oidc4VciIssuerService): String {
        val offer = service.createOffer(listOf("T"))
        return service.exchangePreAuthCode(offer.preAuthCode, null).accessToken
    }

    @Test
    fun `notification without an access token is 401 invalid_token`() =
        host(service()) {
            val r =
                client.post("/notification") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"notification_id":"n","event":"credential_accepted"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
            assertTrue("invalid_token" in r.bodyAsText())
            assertNotNull(r.headers[HttpHeaders.WWWAuthenticate])
        }

    @Test
    fun `notification with an unknown token is 401 and with a live token is 204`() {
        val svc = service()
        host(svc) {
            val bad =
                client.post("/notification") {
                    header(HttpHeaders.Authorization, "Bearer nope")
                    contentType(ContentType.Application.Json)
                    setBody("""{"notification_id":"n","event":"credential_accepted"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, bad.status)
            val ok =
                client.post("/notification") {
                    header(HttpHeaders.Authorization, "Bearer ${liveToken(svc)}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"notification_id":"n","event":"credential_accepted"}""")
                }
            assertEquals(HttpStatusCode.NoContent, ok.status)
        }
    }

    @Test
    fun `deferred endpoint distinguishes a bad token (401) from an unknown transaction (400)`() {
        val svc = service()
        host(svc) {
            val bad =
                client.post("/deferred_credential") {
                    header(HttpHeaders.Authorization, "Bearer nope")
                    contentType(ContentType.Application.Json)
                    setBody("""{"transaction_id":"t1"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, bad.status)
            assertTrue("invalid_token" in bad.bodyAsText())
            val unknownTx =
                client.post("/deferred_credential") {
                    header(HttpHeaders.Authorization, "Bearer ${liveToken(svc)}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"transaction_id":"t1"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, unknownTx.status)
            assertTrue("invalid_transaction_id" in unknownTx.bodyAsText())
        }
    }

    @Test
    fun `a registered deferred credential is returned once`() {
        val svc = service()
        host(svc) {
            val token = liveToken(svc)
            svc.registerDeferredCredential("t1", "{\"x\":1}")

            suspend fun call() =
                client.post("/deferred_credential") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"transaction_id":"t1"}""")
                }
            assertEquals(HttpStatusCode.OK, call().status)
            assertEquals(HttpStatusCode.BadRequest, call().status)
        }
    }

    @Test
    fun `token endpoint at capacity answers 503 not invalid_grant`() {
        val svc = service(maxActiveTokens = 1)
        host(svc) {
            liveToken(svc)
            val offer = svc.createOffer(listOf("T"))
            val r =
                client.submitForm(
                    "/token",
                    parameters {
                        append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        append("pre-authorized_code", offer.preAuthCode)
                    },
                )
            assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
            val error =
                Json
                    .parseToJsonElement(r.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content
            assertEquals("temporarily_unavailable", error)
        }
    }

    @Test
    fun `an unknown pre-authorized code is still invalid_grant`() =
        host(service()) {
            val r =
                client.submitForm(
                    "/token",
                    parameters {
                        append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        append("pre-authorized_code", "nope")
                    },
                )
            assertEquals(HttpStatusCode.BadRequest, r.status)
            assertTrue("invalid_grant" in r.bodyAsText())
        }

    @Test
    fun `credential request with a bad token is 401 and a missing proof is invalid_proof 400`() {
        val svc = service()
        host(svc) {
            val bad =
                client.post("/credential") {
                    header(HttpHeaders.Authorization, "Bearer nope")
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.Unauthorized, bad.status)
            val noProof =
                client.post("/credential") {
                    header(HttpHeaders.Authorization, "Bearer ${liveToken(svc)}")
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.BadRequest, noProof.status)
            assertTrue("invalid_proof" in noProof.bodyAsText())
        }
    }

    // ---- service-level bounds / expiry

    @Test
    fun `pending offers are bounded and fail loudly at the cap`() {
        val svc = service(maxPendingOffers = 2)
        svc.createOffer(listOf("T"))
        svc.createOffer(listOf("T"))
        assertFailsWith<IssuerCapacityExceededException> { svc.createOffer(listOf("T")) }
    }

    @Test
    fun `expired tokens free capacity`() {
        val svc = service(maxActiveTokens = 1, tokenTtlSeconds = 0)
        liveToken(svc)
        // The first token is already expired (ttl 0), so purge makes room.
        val offer = svc.createOffer(listOf("T"))
        assertNotNull(svc.exchangePreAuthCode(offer.preAuthCode, null))
        assertEquals(1, svc.retainedState().second)
    }

    @Test
    fun `deferred credentials expire and are bounded`() {
        val expiring = service(deferredTtlSeconds = 0)
        expiring.registerDeferredCredential("t", "{}")
        assertEquals(1, expiring.purgeExpired())
        assertEquals(0, expiring.retainedDeferredCount())

        val bounded = service(maxDeferredCredentials = 1)
        bounded.registerDeferredCredential("a", "{}")
        assertFailsWith<IssuerCapacityExceededException> { bounded.registerDeferredCredential("b", "{}") }
    }
}
