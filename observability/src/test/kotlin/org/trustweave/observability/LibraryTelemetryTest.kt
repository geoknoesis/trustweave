package org.trustweave.observability

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Outcome
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.core.telemetry.TelemetryContext
import org.trustweave.core.telemetry.TelemetryEvent
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The bridge that gives library events somewhere to go. Without it, instrumentation stops at the
 * HTTP boundary and a host cannot join a slow request to the DID resolution inside it.
 */
class LibraryTelemetryTest {
    private val exporter = InMemorySpanExporter.create()
    private val sdk =
        OpenTelemetrySdk
            .builder()
            .setTracerProvider(
                SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build(),
            ).build()

    @AfterTest
    fun tearDown() {
        Telemetry.uninstall()
        sdk.close()
    }

    @Test
    fun `a library operation becomes a span carrying the host request id`() =
        runBlocking<Unit> {
            Telemetry.install(LibraryTelemetry(sdk))
            withContext(TelemetryContext("request-42")) {
                Telemetry.measure(Operation.DID_RESOLVE, mapOf("did.method" to "key")) { }
            }
            val span = exporter.finishedSpanItems.single()
            assertEquals("did_resolve", span.name)
            val attributes = span.attributes.asMap().mapKeys { it.key.key }
            assertEquals("did_resolve", attributes["trustweave.operation"])
            assertEquals("success", attributes["trustweave.outcome"])
            assertEquals("request-42", attributes["trustweave.request_id"])
            assertEquals("key", attributes["did.method"])
        }

    @Test
    fun `a failure marks the span in error with its reason code`() =
        runBlocking<Unit> {
            Telemetry.install(LibraryTelemetry(sdk))
            assertFailsWith<IllegalStateException> {
                Telemetry.measure(Operation.KMS_SIGN) { error("boom") }
            }
            val span = exporter.finishedSpanItems.single()
            assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.status.statusCode)
            assertEquals("IllegalStateException", span.attributes.asMap().mapKeys { it.key.key }["trustweave.reason"])
        }

    @Test
    fun `a rejection is not an error span`() =
        runBlocking<Unit> {
            Telemetry.install(LibraryTelemetry(sdk))
            Telemetry.rejected(Operation.CREDENTIAL_VERIFY, "SignatureInvalid")
            val span = exporter.finishedSpanItems.single()
            assertEquals(io.opentelemetry.api.trace.StatusCode.UNSET, span.status.statusCode)
            assertEquals("rejected", span.attributes.asMap().mapKeys { it.key.key }["trustweave.outcome"])
        }

    @Test
    fun `the span covers the measured duration rather than restarting the clock`() =
        runBlocking<Unit> {
            val library = LibraryTelemetry(sdk)
            library.record(
                TelemetryEvent(
                    operation = Operation.DID_RESOLVE,
                    outcome = Outcome.SUCCESS,
                    durationNanos = 250_000_000,
                ),
            )
            val span = exporter.finishedSpanItems.single()
            val observed = span.endEpochNanos - span.startEpochNanos
            assertTrue(observed in 200_000_000..300_000_000, "span covered ${observed}ns")
        }

    @Test
    fun `metrics count every operation and outcome pair without growing labels`() {
        val library = LibraryTelemetry(sdk, emitSpans = false)
        library.record(TelemetryEvent(Operation.DID_RESOLVE, Outcome.SUCCESS, 1_000_000))
        library.record(TelemetryEvent(Operation.DID_RESOLVE, Outcome.SUCCESS, 2_000_000))
        library.record(TelemetryEvent(Operation.DID_RESOLVE, Outcome.FAILURE, 3_000_000, reason = "Timeout"))
        val scrape = library.prometheus()
        assertTrue(
            "trustweave_library_operations_total{operation=\"did_resolve\",outcome=\"success\"} 2" in scrape,
            scrape,
        )
        assertTrue(
            "trustweave_library_operations_total{operation=\"did_resolve\",outcome=\"failure\"} 1" in scrape,
            scrape,
        )
        assertTrue("trustweave_library_operation_seconds_count{operation=\"did_resolve\"} 3" in scrape, scrape)
        // Both dimensions are closed enums, so the series count is fixed no matter what runs.
        val series = scrape.lines().count { it.startsWith("trustweave_library_operations_total{") }
        assertEquals(Operation.entries.size * Outcome.entries.size, series)
    }

    @Test
    fun `the scrape stays a valid exposition with no traffic`() {
        val scrape = LibraryTelemetry(sdk, emitSpans = false).prometheus()
        assertTrue(scrape.startsWith("# HELP"), scrape.take(40))
        assertTrue(scrape.trimEnd().endsWith("} 0"), scrape.takeLast(60))
    }

    @Test
    fun `spans can be turned off while metrics keep counting`() {
        val library = LibraryTelemetry(sdk, emitSpans = false)
        library.record(TelemetryEvent(Operation.WALLET_GET, Outcome.SUCCESS, 1))
        assertTrue(exporter.finishedSpanItems.isEmpty())
        assertTrue("operation=\"wallet_get\",outcome=\"success\"} 1" in library.prometheus())
    }
}
