package org.trustweave.observability

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HostTelemetryEdgeTest {
    @Test
    fun `all hosts classify client and server responses and bound unknown HTTP methods`() {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { sdk ->
            for (kind in HostKind.entries) {
                val telemetry = HostTelemetry(sdk)
                testApplication {
                    application {
                        HostObservability(telemetry, trustRemoteParent = true).install(this, kind)
                        routing {
                            route("/client") { handle { call.respond(HttpStatusCode.BadRequest) } }
                            route("/server") { handle { call.respond(HttpStatusCode.InternalServerError) } }
                        }
                    }
                    for ((path, status) in listOf(
                        "/client" to HttpStatusCode.BadRequest,
                        "/server" to HttpStatusCode.InternalServerError,
                    )) {
                        val response =
                            client.request(path) {
                                method = HttpMethod("PRIVATE_METHOD")
                                header("traceparent", "sensitive".repeat(100))
                            }
                        assertEquals(status, response.status)
                    }
                }
                val spans = exporter.finishedSpanItems.takeLast(2)
                assertEquals(listOf(StatusCode.UNSET, StatusCode.ERROR), spans.map { it.status.statusCode })
                assertTrue(
                    spans.all {
                        it.attributes
                            .asMap()
                            .values
                            .contains("_OTHER")
                    },
                )
                assertFalse(spans.toString().contains("PRIVATE_METHOD"))
                assertFalse(spans.toString().contains("sensitive"))
                assertTrue(telemetry.prometheus().contains("outcome=\"client_error\"} 1"))
                assertTrue(telemetry.prometheus().contains("outcome=\"server_error\"} 1"))
            }
        }
    }

    @Test
    fun `admitted waiter runs after release and timed out waiter never runs`(): Unit =
        runBlocking {
            val telemetry = HostTelemetry(maxConcurrentRequests = 1, maxQueuedRequests = 1, queueTimeoutMillis = 100)
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
            var rejected = 0
            withTimeout(2000) {
                telemetry.request(HostKind.VC_API, Context.root(), { 200 }, {}, { rejected++ }) {
                    error("Timed out work must not run")
                }
            }
            assertEquals(1, rejected)
            assertTrue(telemetry.prometheus().contains("trustweave_host_queued_requests 0"))
            var ran = false
            val waiting = launch { request(telemetry) { ran = true } }
            withTimeout(2000) { while (!telemetry.prometheus().contains("trustweave_host_queued_requests 1")) delay(1) }
            release.complete(Unit)
            first.join()
            waiting.join()
            assertTrue(ran)
            assertTrue(telemetry.prometheus().contains("trustweave_host_active_requests 0"))
            assertTrue(telemetry.prometheus().contains("outcome=\"success\"} 2"))
        }

    @Test
    fun `request errors and callback failure preserve identity and release capacity`(): Unit =
        runBlocking {
            val telemetry = HostTelemetry(maxConcurrentRequests = 1)
            val exception = SQLException("private-error")
            val fatal = AssertionError("private-fatal")
            val cancelled = CancellationException("private-cancel")
            assertSame(exception, assertFailsWith<SQLException> { request(telemetry) { throw exception } })
            assertOriginal(fatal, assertFailsWith<AssertionError> { request(telemetry) { throw fatal } })
            assertOriginal(cancelled, assertFailsWith<CancellationException> { request(telemetry) { throw cancelled } })
            assertSame(
                exception,
                assertFailsWith<SQLException> {
                    telemetry.request(HostKind.VC_API, Context.root(), { 200 }, { throw exception }, {}) {}
                },
            )
            request(telemetry) {}
            val metrics = telemetry.prometheus()
            assertTrue(metrics.contains("outcome=\"server_error\"} 3"))
            assertTrue(metrics.contains("outcome=\"cancelled\"} 1"))
            assertTrue(metrics.contains("trustweave_host_active_requests 0"))
            assertFalse(metrics.contains("private"))
        }

    @Test
    fun `cancelled phase ends its span without counting a provider failure`(): Unit =
        runBlocking {
            val exporter = InMemorySpanExporter.create()
            val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
            OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { sdk ->
                val telemetry = HostTelemetry(sdk)
                val cancelled = CancellationException("private-cancel")
                assertOriginal(
                    cancelled,
                    assertFailsWith<CancellationException> {
                        telemetry.phase(HostPhase.PROVIDER) { throw cancelled }
                    },
                )
                assertEquals(1, exporter.finishedSpanItems.size)
                assertEquals(
                    StatusCode.UNSET,
                    exporter.finishedSpanItems
                        .single()
                        .status.statusCode,
                )
                assertTrue(
                    exporter.finishedSpanItems
                        .single()
                        .attributes
                        .asMap()
                        .values
                        .contains("cancelled"),
                )
                assertTrue(telemetry.prometheus().contains("phase_failures_total{phase=\"provider\"} 0"))
            }
        }

    @Test
    fun `credentialed acquisition returns the original connection and preserves rollback`() {
        val raw = JdbcDataSource().apply { setURL("jdbc:h2:mem:documented-transaction") }
        raw.connection.use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE example (id INT)") }
            var arguments: List<Any?>? = null
            val source =
                Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DataSource::class.java)) { _, method, args ->
                    if (method.name == "getConnection") {
                        arguments = args?.toList()
                        connection
                    } else {
                        method.invoke(raw, *(args ?: emptyArray()))
                    }
                } as DataSource
            val telemetry = HostTelemetry()
            val observed = telemetry.observeDataSource(source)
            assertSame(connection, observed.getConnection("private-user", "private-password"))
            assertEquals(listOf("private-user", "private-password"), arguments)
            connection.autoCommit = false
            connection.createStatement().use { it.executeUpdate("INSERT INTO example VALUES (1)") }
            connection.rollback()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM example").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
            }
            observed.loginTimeout = 3
            assertEquals(3, raw.loginTimeout)
            assertFalse(telemetry.prometheus(source).contains("private"))
            assertTrue(telemetry.prometheus(source).contains("trustweave_host_pool_available 0"))
        }
    }

    @Test
    fun `absent uninitialized and closed pools omit unavailable connection gauges`() {
        val telemetry = HostTelemetry()
        val pool = HikariDataSource()
        for (snapshot in listOf(telemetry.prometheus(), telemetry.prometheus(pool))) {
            assertTrue(snapshot.contains("trustweave_host_pool_available 0"))
            assertFalse(snapshot.contains("trustweave_host_pool_active"))
        }
        pool.close()
        assertTrue(telemetry.prometheus(pool).contains("trustweave_host_pool_available 0"))
        assertFalse(telemetry.prometheus(pool).contains("trustweave_host_pool_active"))
    }

    @Test
    fun `noop tracing omits trace header and metrics authentication rejects oversized values`() {
        val secret = "x".repeat(256)
        testApplication {
            application {
                HostObservability(metricsBearerToken = secret).install(this, HostKind.VC_API)
                routing { route("/ok") { handle { call.respond(HttpStatusCode.OK) } } }
            }
            assertNull(client.request("/ok").headers["X-Trace-ID"])
            val rejected = client.request("/internal/metrics") { header("Authorization", "Bearer $secret-extra") }
            assertEquals(HttpStatusCode.Unauthorized, rejected.status)
            assertEquals("Bearer", rejected.headers["WWW-Authenticate"])
            assertEquals(HttpStatusCode.OK, client.request("/internal/metrics") { header("Authorization", "Bearer $secret") }.status)
        }
        assertFailsWith<IllegalArgumentException> { HostTelemetry(queueTimeoutMillis = 60_001) }
        assertFailsWith<IllegalArgumentException> { HostObservability(metricsBearerToken = "x".repeat(257)) }
        assertFailsWith<IllegalArgumentException> { HostObservability(metricsBearerToken = "x".repeat(31)) }
        assertFailsWith<IllegalArgumentException> { HostObservability(metricsBearerToken = "x ".repeat(16)) }
        assertFailsWith<IllegalArgumentException> { HostObservability(metricsBearerToken = "é".repeat(32)) }
    }

    private fun assertOriginal(
        original: Throwable,
        actual: Throwable,
    ) {
        // Coroutine debug stack recovery may copy standard exceptions and retain
        // the original as cause. Do not disable debug recovery to make tests pass.
        assertEquals(original.javaClass, actual.javaClass)
        assertSame(original, if (actual === original) actual else actual.cause)
    }

    private suspend fun request(
        telemetry: HostTelemetry,
        block: suspend () -> Unit,
    ) {
        telemetry.request(HostKind.VC_API, Context.root(), { 200 }, {}, { error("Unexpected rejection") }, block)
    }
}
