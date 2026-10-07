package org.trustweave.did.registrar.server

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.trustweave.did.registrar.client.KmsBasedRegistrar
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.observability.HostAuthentication
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DidRegistrarBodyLimitTest {
    private fun server(): DidRegistrarServer {
        val jobs = InMemoryJobStorage()
        val registrar = KmsBasedRegistrar(InMemoryKeyManagementService(), jobs) { _, kms -> DidKeyMockMethod(kms) }
        return DidRegistrarServer(registrar, jobStorage = jobs).withAuthentication(HostAuthentication.frontedByProxy("test"))
    }

    @Test
    fun `an oversized request body is refused with 413 before it is parsed`() =
        testApplication {
            val subject = server().withMaxRequestBytes(64)
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/1.0/dids") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"method":"key","options":{"pad":"${"x".repeat(500)}"}}""")
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `the default limit admits an ordinary request`() =
        testApplication {
            val subject = server()
            application { with(subject) { configureApplication() } }
            val response =
                client.post("/1.0/dids") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"method":"key","options":{}}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `a non-positive limit is refused`() {
        assertFailsWith<IllegalArgumentException> { server().withMaxRequestBytes(0) }
    }
}
