package org.trustweave.credential.avpauth

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import org.trustweave.observability.HostAuthentication
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The server that authorizes payments must not authorize one because a port was reachable.
 */
class AvpAuthorizationServerAuthTest {
    private val now = Instant.parse("2026-03-25T21:30:30Z")
    private val token = "a".repeat(48)

    private fun res(name: String) = requireNotNull(this::class.java.getResource("/vectors/$name")).readText()

    private val payload = """{"authorization":${res("02-payment-authorization.json")},"quote":${res("01-payment-quote.json")}}"""

    private fun server() = AvpAuthorizationServer(AuthorizationEngine(clock = { now }))

    @Test
    fun `an unconfigured server refuses to authorize and says how to fix it`() =
        testApplication {
            val subject = server()
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/v1/authorizations/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = response.bodyAsText()
            assertTrue("authentication_not_configured" in body, body)
            assertTrue("withAuthentication" in body, "the refusal must say how to fix it: $body")
        }

    @Test
    fun `a caller without the token is refused`() =
        testApplication {
            val subject = server().withAuthentication(HostAuthentication.bearerToken(token))
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/v1/authorizations/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `a caller with the token reaches the engine`() =
        testApplication {
            val subject = server().withAuthentication(HostAuthentication.bearerToken(token))
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/v1/authorizations/verify") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue("allow" in response.bodyAsText(), response.bodyAsText())
        }

    @Test
    fun `a declared proxy is an accepted answer`() =
        testApplication {
            val subject =
                server().withAuthentication(
                    HostAuthentication.frontedByProxy("mTLS terminated at the ingress"),
                )
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/v1/authorizations/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `configuring authentication after the server has started is refused`() {
        // Port 0 so the test never contends with whatever else is listening on this machine.
        val subject = AvpAuthorizationServer(AuthorizationEngine(clock = { now }), port = 0)
        subject.start()
        try {
            val failure =
                runCatching { subject.withAuthentication(HostAuthentication.frontedByProxy("ingress")) }
                    .exceptionOrNull()
            assertTrue(failure is IllegalStateException, "expected an IllegalStateException, got $failure")
        } finally {
            subject.stop()
        }
    }
}
