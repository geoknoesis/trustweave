package org.trustweave.observability

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException

class HostTelemetryTest {
    private val token = "local-fixture-metrics-token-32-characters"

    @Test
    fun `request and child span correlation survive coroutine dispatch without sensitive fields`() {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { sdk ->
            val telemetry = HostTelemetry(sdk)
            testApplication {
                application {
                    HostObservability(telemetry, token).install(this, HostKind.STATUS_LIST)
                    routing {
                        get("/items/{id}") {
                            val id =
                                telemetry.phase(HostPhase.VERIFY) {
                                    withContext(Dispatchers.Default) {
                                        delay(1)
                                        Span.current().spanContext.traceId
                                    }
                                }
                            call.respondText(id)
                        }
                    }
                }
                val responses =
                    coroutineScope {
                        (1..40)
                            .map { index ->
                                async {
                                    client.get("/items/secret-$index?credential=secret") {
                                        header("Authorization", "Bearer secret")
                                        header("X-Request-ID", "secret-forged")
                                        header("baggage", "credential=secret")
                                        header("traceparent", "00-11111111111111111111111111111111-2222222222222222-01")
                                    }
                                }
                            }.awaitAll()
                    }
                for (response in responses) {
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(response.headers["X-Trace-ID"], response.bodyAsText())
                    assertNotEquals("11111111111111111111111111111111", response.headers["X-Trace-ID"])
                    assertNotEquals("secret-forged", response.headers["X-Request-ID"])
                }
                assertEquals(40, responses.map { it.headers["X-Request-ID"] }.toSet().size)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/internal/metrics").status)
                assertEquals(
                    HttpStatusCode.Unauthorized,
                    client.get("/internal/metrics") { header("Authorization", "Bearer wrong") }.status,
                )
                val scrape = client.get("/internal/metrics") { header("Authorization", "Bearer $token") }
                assertEquals(HttpStatusCode.OK, scrape.status)
                assertEquals("no-store", scrape.headers["Cache-Control"])
                assertTrue(scrape.bodyAsText().contains("host=\"status_list\",outcome=\"success\"} 40"))
                assertFalse(scrape.bodyAsText().contains("secret"))
                val spans = exporter.finishedSpanItems
                assertEquals(80, spans.size)
                for (group in spans.groupBy { it.traceId }.values) {
                    val parent = group.single { it.name == "trustweave.status_list" }
                    val child = group.single { it.name == "trustweave.verify" }
                    assertEquals(parent.spanId, child.parentSpanId)
                    assertTrue(
                        responses.any {
                            it.headers["X-Request-ID"] ==
                                parent.attributes
                                    .asMap()
                                    .entries
                                    .single { e -> e.key.key == "trustweave.request_id" }
                                    .value
                        },
                    )
                }
                assertFalse(spans.toString().contains("secret"))
                val reports = Path.of("build/reports")
                Files.createDirectories(reports)
                Files.writeString(reports.resolve("host-metrics.prom"), scrape.bodyAsText())
                Files.writeString(
                    reports.resolve("host-traces.txt"),
                    spans.joinToString("\n") {
                        "${it.name} trace=${it.traceId} span=${it.spanId} parent=${it.parentSpanId} ${it.attributes}"
                    },
                )
            }
        }
    }

    @Test
    fun `trusted remote parent is explicit and baggage and malformed parents are not retained`() {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { sdk ->
            testApplication {
                application {
                    HostObservability(HostTelemetry(sdk), trustRemoteParent = true).install(this, HostKind.VC_API)
                    routing { get("/ok") { call.respondText("ok") } }
                }
                val response =
                    client.get("/ok") {
                        header("traceparent", "00-11111111111111111111111111111111-2222222222222222-01")
                        header("tracestate", "private=secret")
                        header("baggage", "private=secret")
                    }
                assertEquals("11111111111111111111111111111111", response.headers["X-Trace-ID"])
                assertEquals("2222222222222222", exporter.finishedSpanItems.single().parentSpanId)
                client.get("/ok") { header("traceparent", "secret-invalid") }
                assertNotEquals("11111111111111111111111111111111", exporter.finishedSpanItems.last().traceId)
                assertFalse(exporter.finishedSpanItems.toString().contains("secret"))
                assertEquals(HttpStatusCode.NotFound, client.get("/internal/metrics").status)
            }
        }
    }

    @Test
    fun `metrics remain available during saturation and queued cancellation releases capacity`() =
        runBlocking {
            val telemetry = HostTelemetry(maxConcurrentRequests = 1, maxQueuedRequests = 1, queueTimeoutMillis = 5000)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first =
                launch {
                    request(telemetry) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
            entered.await()
            val queued = launch { request(telemetry) { fail("Cancelled queued work must not execute") } }
            withTimeout(2000) { while (!telemetry.prometheus().contains("trustweave_host_queued_requests 1")) delay(1) }
            var rejected = false
            telemetry.request(HostKind.VC_API, Context.root(), { 200 }, {}, { rejected = true }) { fail("Queue is full") }
            assertTrue(rejected)
            queued.cancelAndJoin()
            assertTrue(telemetry.prometheus().contains("trustweave_host_queued_requests 0"))
            release.complete(Unit)
            first.join()
            request(telemetry) {}
            assertTrue(telemetry.prometheus().contains("trustweave_host_active_requests 0"))
            assertTrue(telemetry.prometheus().contains("outcome=\"cancelled\"} 1"))
            assertTrue(telemetry.prometheus().contains("outcome=\"rejected\"} 1"))
        }

    @Test
    fun `queue timeouts and cancellation races do not leak permits`() =
        runBlocking {
            val telemetry = HostTelemetry(maxConcurrentRequests = 1, maxQueuedRequests = 10, queueTimeoutMillis = 1)
            repeat(100) {
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val first =
                    launch {
                        request(telemetry) {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                entered.await()
                val waiters = (1..10).map { launch { telemetry.request(HostKind.VC_API, Context.root(), { 200 }, {}, {}) {} } }
                delay(1)
                waiters.take(5).forEach { it.cancel() }
                release.complete(Unit)
                first.join()
                waiters.forEach { it.join() }
                withTimeout(1000) { request(telemetry) {} }
            }
            assertTrue(telemetry.prometheus().contains("trustweave_host_active_requests 0"))
            assertTrue(telemetry.prometheus().contains("trustweave_host_queued_requests 0"))
        }

    @Test
    fun `bounded counters retain constant series for large workloads and preserve failures`() =
        runBlocking {
            val telemetry = HostTelemetry()
            val before = telemetry.prometheus().lineSequence().count { it.isNotBlank() && !it.startsWith("#") }
            coroutineScope { (1..8).map { async(Dispatchers.Default) { repeat(2500) { request(telemetry) {} } } }.awaitAll() }
            val error = SQLException("secret connection string")
            val thrown = assertThrows(SQLException::class.java) { runBlocking { telemetry.phase(HostPhase.PROVIDER) { throw error } } }
            assertSame(error, thrown)
            val after = telemetry.prometheus()
            assertEquals(before, after.lineSequence().count { it.isNotBlank() && !it.startsWith("#") })
            assertTrue(after.contains("host=\"vc_api\",outcome=\"success\"} 20000"))
            assertFalse(after.contains("secret"))
            assertTrue(after.length < 30000)
        }

    @Test
    fun `real connection pool wait time failure and recovery are visible without replacing connections`() =
        runBlocking {
            val exporter = InMemorySpanExporter.create()
            val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
            OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { sdk ->
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = "jdbc:h2:mem:observability"
                        maximumPoolSize = 1
                        minimumIdle = 1
                        connectionTimeout = 250
                        poolName = "secret-pool-name"
                    },
                ).use { pool ->
                    val telemetry = HostTelemetry(sdk)
                    val observed = telemetry.observeDataSource(pool)
                    observed.connection.use {
                        val failed =
                            async(Dispatchers.IO) {
                                assertThrows(SQLException::class.java) { observed.connection.use { fail("Pool should be exhausted") } }
                            }
                        withTimeout(2000) { while (!telemetry.prometheus(pool).contains("trustweave_host_pool_waiting 1")) delay(1) }
                        val scrape = telemetry.prometheus(pool)
                        assertTrue(scrape.contains("trustweave_host_pool_active 1"))
                        assertTrue(scrape.contains("trustweave_host_pool_capacity 1"))
                        assertFalse(scrape.contains("secret"))
                        failed.await()
                    }
                    observed.connection.use { connection ->
                        assertTrue(
                            connection.createStatement().use {
                                it.executeQuery("SELECT 1").use { result ->
                                    result.next() &&
                                        result.getInt(1) == 1
                                }
                            },
                        )
                    }
                    assertTrue(telemetry.prometheus(pool).contains("trustweave_host_pool_active 0"))
                    assertTrue(telemetry.prometheus(pool).contains("trustweave_host_phase_failures_total{phase=\"database_acquire\"} 1"))
                    assertEquals(3, exporter.finishedSpanItems.size)
                    assertFalse(exporter.finishedSpanItems.toString().contains("secret"))
                    assertTrue(exporter.finishedSpanItems.any { (it.endEpochNanos - it.startEpochNanos) >= 200_000_000 })
                }
            }
        }

    @Test
    fun `invalid settings and weak metrics secrets are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HostTelemetry(maxConcurrentRequests = 0) }
        assertThrows(IllegalArgumentException::class.java) { HostTelemetry(maxQueuedRequests = -1) }
        assertThrows(IllegalArgumentException::class.java) { HostTelemetry(queueTimeoutMillis = 0) }
        assertThrows(IllegalArgumentException::class.java) { HostObservability(metricsBearerToken = "short") }
        assertThrows(IllegalArgumentException::class.java) { HostObservability(metricsBearerToken = "secret\n".repeat(8)) }
    }

    private suspend fun request(
        telemetry: HostTelemetry,
        block: suspend () -> Unit,
    ) {
        telemetry.request(HostKind.VC_API, Context.root(), { 200 }, {}, { fail("Unexpected admission rejection") }, block)
    }
}
