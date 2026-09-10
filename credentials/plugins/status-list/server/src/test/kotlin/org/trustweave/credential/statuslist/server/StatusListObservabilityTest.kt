package org.trustweave.credential.statuslist.server

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.trustweave.observability.HostObservability
import org.trustweave.observability.HostTelemetry
import org.trustweave.revocation.token.TokenStatusListManager
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StatusListObservabilityTest {
    @Test
    @Timeout(90)
    fun `real embedded server exposes authenticated metrics during overload and correlates error with trace`() {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { sdk ->
            val telemetry = HostTelemetry(sdk, maxConcurrentRequests = 1)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val fail = AtomicBoolean(false)
            val backing = JdbcDataSource().apply { setURL("jdbc:h2:mem:host-status-errors;DB_CLOSE_DELAY=-1") }
            val source =
                object : javax.sql.DataSource by backing {
                    override fun getConnection(): java.sql.Connection {
                        if (fail.get()) {
                            entered.countDown()
                            check(release.await(60, TimeUnit.SECONDS)) { "Overload probe did not release the database request" }
                            throw java.sql.SQLException("secret database address and password")
                        }
                        return backing.connection
                    }
                }
            val manager =
                TokenStatusListManager(
                    telemetry.observeDataSource(source),
                    InMemoryKeyManagementService(),
                    "did:key:issuer",
                    "https://issuer.example/status",
                )
            exporter.reset()
            fail.set(true)
            val port = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
            val secret = "fixture-metrics-secret-at-least-32-characters"
            val server = StatusListServer(port = port, tokenManager = manager).withObservability(HostObservability(telemetry, secret))
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

            fun request(
                path: String,
                auth: Boolean = false,
                timeout: Duration = Duration.ofSeconds(10),
            ): HttpRequest =
                HttpRequest
                    .newBuilder(URI("http://127.0.0.1:$port$path"))
                    .timeout(timeout)
                    .apply { if (auth) header("Authorization", "Bearer $secret") }
                    .header("X-Request-ID", "secret-forged")
                    .build()
            server.start()
            val previous = System.err
            val bytes = java.io.ByteArrayOutputStream()
            val log = java.io.PrintStream(bytes, true, Charsets.UTF_8)
            try {
                System.setErr(log)
                // This request remains blocked while all three independent probes execute.
                // Its deadline must exceed their combined budgets; the test itself remains bounded.
                val pending =
                    client.sendAsync(
                        request("/token-status-lists/secret-id", timeout = Duration.ofSeconds(75)),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                val rejected = client.send(request("/token-status-lists/another-secret"), HttpResponse.BodyHandlers.ofString())
                assertEquals(503, rejected.statusCode())
                assertEquals("1", rejected.headers().firstValue("Retry-After").orElseThrow())
                assertEquals(401, client.send(request("/internal/metrics"), HttpResponse.BodyHandlers.discarding()).statusCode())
                val scrape = client.send(request("/internal/metrics", true), HttpResponse.BodyHandlers.ofString())
                assertEquals(200, scrape.statusCode())
                assertTrue(scrape.body().contains("trustweave_host_active_requests 1"))
                assertFalse(scrape.body().contains("secret"))
                release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(500, response.statusCode())
                val id = response.headers().firstValue("X-Request-ID").orElseThrow()
                assertEquals(1, response.headers().allValues("X-Request-ID").size)
                assertEquals(
                    id,
                    Json
                        .parseToJsonElement(response.body())
                        .jsonObject["requestId"]!!
                        .jsonPrimitive.content,
                )
                val trace = response.headers().firstValue("X-Trace-ID").orElseThrow()
                provider.forceFlush().join(2, TimeUnit.SECONDS)
                val spans = exporter.finishedSpanItems.filter { it.traceId == trace }
                assertEquals(2, spans.size)
                val parent = spans.single { it.name == "trustweave.status_list" }
                assertEquals(parent.spanId, spans.single { it.name == "trustweave.database_acquire" }.parentSpanId)
                assertTrue(bytes.toString(Charsets.UTF_8).contains("error_code=STORAGE_FAILURE request_id=$id"))
                assertFalse(bytes.toString(Charsets.UTF_8).contains("secret"))
                assertFalse(spans.toString().contains("secret"))
            } finally {
                release.countDown()
                System.setErr(previous)
                log.close()
                server.stop()
                client.close()
            }
        }
    }
}
