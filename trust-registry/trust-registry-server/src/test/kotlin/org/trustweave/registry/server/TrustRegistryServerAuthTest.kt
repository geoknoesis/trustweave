package org.trustweave.registry.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.trustweave.observability.HostAuthentication
import org.trustweave.registry.InMemoryTrustRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The two mechanisms compose: either one authorizes a mutation, neither leaves it refused.
 *
 * The point of wiring the shared gate in is that a deployment authenticating with mTLS or a
 * gateway can satisfy the registry without also inventing a bearer token for it — and that a
 * deployment that configures nothing still gets a refusal rather than an open registry.
 */
class TrustRegistryServerAuthTest {
    private val token = "c".repeat(48)

    private fun server(apiToken: String? = null) = TrustRegistryServer(InMemoryTrustRegistry(), apiToken = apiToken)

    private val revoke = "/registry/issuers/did:example:issuer/revoke"

    @Test
    fun `configuring neither mechanism leaves mutations refused`() =
        testApplication {
            val subject = server()
            application { with(subject) { configureApplication() } }
            val response = client.post(revoke)
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue("mutations_disabled" in response.bodyAsText(), response.bodyAsText())
        }

    @Test
    fun `reads stay open when nothing is configured`() =
        testApplication {
            val subject = server()
            application { with(subject) { configureApplication() } }
            assertEquals(HttpStatusCode.OK, client.get("/registry/issuers").status)
        }

    @Test
    fun `the host gate authorizes a mutation on its own`() =
        testApplication {
            // No apiToken at all: the gate is the whole of the authorization decision.
            val subject = server().withAuthentication(HostAuthentication.bearerToken(token))
            application { with(subject) { configureApplication() } }
            val response = client.post(revoke) { header("Authorization", "Bearer $token") }
            // Not found, because this issuer was never registered — but it reached the handler,
            // which is the point: the gate admitted it and the token check stood aside.
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `the host gate refuses a caller without its credential`() =
        testApplication {
            val subject = server().withAuthentication(HostAuthentication.bearerToken(token))
            application { with(subject) { configureApplication() } }
            assertEquals(HttpStatusCode.Unauthorized, client.post(revoke).status)
        }

    @Test
    fun `a declared proxy authorizes a mutation without a registry token`() =
        testApplication {
            val subject =
                server().withAuthentication(HostAuthentication.frontedByProxy("mTLS terminated at the ingress"))
            application { with(subject) { configureApplication() } }
            val response = client.post(revoke)
            assertNotEquals(
                HttpStatusCode.ServiceUnavailable,
                response.status,
                "a host that declared what protects it must not still be told mutations are disabled",
            )
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `the registry's own token still works on its own`() =
        testApplication {
            val subject = server(apiToken = token)
            application { with(subject) { configureApplication() } }
            assertEquals(HttpStatusCode.Unauthorized, client.post(revoke).status)
            assertEquals(
                HttpStatusCode.NotFound,
                client.post(revoke) { header("Authorization", "Bearer $token") }.status,
            )
        }

    private val readsOnlyGate get() = HostAuthentication.bearerToken(token, protect = setOf(HttpMethod.Get))

    @Test
    fun `a gate that protects only reads does not authorize writes`() =
        testApplication {
            val subject = server().withAuthentication(readsOnlyGate)
            application { with(subject) { configureApplication() } }
            // The anonymous POST used to reach the handler (404) because "a gate exists".
            val response = client.post(revoke)
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue("mutations_disabled" in response.bodyAsText())
        }

    @Test
    fun `a reads-only gate leaves writes to the registry token`() =
        testApplication {
            val subject = server(apiToken = "r".repeat(40)).withAuthentication(readsOnlyGate)
            application { with(subject) { configureApplication() } }
            assertEquals(HttpStatusCode.Unauthorized, client.post(revoke).status)
            assertEquals(
                HttpStatusCode.NotFound,
                client.post(revoke) { header("Authorization", "Bearer ${"r".repeat(40)}") }.status,
            )
        }

    @Test
    fun `an api token shorter than the host gate's minimum is refused`() {
        assertFailsWith<IllegalArgumentException> { server(apiToken = "short") }
        assertFailsWith<IllegalArgumentException> { server(apiToken = "x".repeat(31)) }
        server(apiToken = "x".repeat(32))
    }

    @Test
    fun `an oversized request body is refused with 413`() =
        testApplication {
            val subject =
                server()
                    .withAuthentication(HostAuthentication.frontedByProxy("test"))
                    .withMaxRequestBytes(64)
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/registry/issuers") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"did":"${"d".repeat(500)}"}""")
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }
}
