package org.trustweave.observability

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HostDocumentationExampleTest {
    @Test
    fun `documented host setup serves requests and protects metrics`() {
        // Synthetic test secret only. A deployed host supplies its own secret and TLS.
        val token = "documentation-fixture-token-32-characters"
        val telemetry = HostTelemetry(maxConcurrentRequests = 8, maxQueuedRequests = 4, queueTimeoutMillis = 500)
        testApplication {
            application {
                HostObservability(telemetry, metricsBearerToken = token).install(this, HostKind.VC_API)
                routing {
                    get("/verify") {
                        val result = telemetry.phase(HostPhase.VERIFY) { "verified" }
                        call.respondText(result)
                    }
                }
            }
            val response = client.get("/verify")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("verified", response.bodyAsText())
            assertNotNull(response.headers["X-Request-ID"])
            assertEquals(HttpStatusCode.Unauthorized, client.get("/internal/metrics").status)
            val metrics = client.get("/internal/metrics") { header("Authorization", "Bearer $token") }
            assertEquals(HttpStatusCode.OK, metrics.status)
            assertEquals("no-store", metrics.headers["Cache-Control"])
            assertTrue(metrics.bodyAsText().contains("host=\"vc_api\",outcome=\"success\"} 1"))
        }
    }
}
