package org.trustweave.core.telemetry

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryTest {
    private val recorded = CopyOnWriteArrayList<TelemetryEvent>()
    private val sink = TelemetrySink { recorded += it }

    @AfterTest
    fun tearDown() {
        Telemetry.uninstall()
    }

    @Test
    fun `nothing is recorded until a host installs a sink`() =
        runBlocking<Unit> {
            assertTrue(!Telemetry.isInstalled())
            Telemetry.measure(Operation.DID_RESOLVE) { "value" }
            assertTrue(recorded.isEmpty())
            Telemetry.install(sink)
            assertTrue(Telemetry.isInstalled())
            Telemetry.measure(Operation.DID_RESOLVE) { "value" }
            assertEquals(1, recorded.size)
        }

    @Test
    fun `install returns the previous sink so a host can restore it`() {
        val first = TelemetrySink { }
        assertEquals(TelemetrySink.None, Telemetry.install(first))
        assertEquals(first, Telemetry.install(sink))
        assertEquals(sink, Telemetry.uninstall())
        assertTrue(!Telemetry.isInstalled())
    }

    @Test
    fun `a successful operation records its outcome and returns its value`() =
        runBlocking<Unit> {
            Telemetry.install(sink)
            val value = Telemetry.measure(Operation.CREDENTIAL_ISSUE) { 42 }
            assertEquals(42, value)
            val event = recorded.single()
            assertEquals(Operation.CREDENTIAL_ISSUE, event.operation)
            assertEquals(Outcome.SUCCESS, event.outcome)
            assertNull(event.reason)
            assertTrue(event.durationNanos >= 0)
        }

    @Test
    fun `a failure records a stable reason code and rethrows`() =
        runBlocking<Unit> {
            Telemetry.install(sink)
            assertFailsWith<IllegalStateException> {
                Telemetry.measure(Operation.KMS_SIGN) { error("the key id is did:key:zSECRET") }
            }
            val event = recorded.single()
            assertEquals(Outcome.FAILURE, event.outcome)
            assertEquals("IllegalStateException", event.reason)
        }

    @Test
    fun `the exception message never reaches the event`() =
        runBlocking<Unit> {
            Telemetry.install(sink)
            assertFailsWith<IllegalArgumentException> {
                Telemetry.measure(Operation.WALLET_STORE) { throw IllegalArgumentException("secret-passphrase") }
            }
            // Reason codes become metric labels, so a raw message would be both high-cardinality
            // and a disclosure risk.
            assertTrue(recorded.none { "secret-passphrase" in it.toString() }, recorded.toString())
        }

    @Test
    fun `cancellation is reported as cancelled, not as a failure`() {
        Telemetry.install(sink)
        val job = Job()
        val started = CompletableDeferred<Unit>()
        assertFailsWith<Exception> {
            runBlocking {
                val work =
                    CoroutineScope(Dispatchers.Default + job).async {
                        Telemetry.measure(Operation.DID_RESOLVE) {
                            started.complete(Unit)
                            delay(30_000)
                        }
                    }
                started.await()
                job.cancel()
                work.await()
            }
        }
        val event = recorded.single()
        assertEquals(Outcome.CANCELLED, event.outcome)
        assertEquals("cancelled", event.reason)
    }

    @Test
    fun `a rejection is separate from a failure`() =
        runBlocking<Unit> {
            Telemetry.install(sink)
            Telemetry.rejected(Operation.CREDENTIAL_VERIFY, "SignatureInvalid")
            val event = recorded.single()
            assertEquals(Outcome.REJECTED, event.outcome)
            assertEquals("SignatureInvalid", event.reason)
        }

    @Test
    fun `the correlation id travels in the coroutine context`() =
        runBlocking<Unit> {
            Telemetry.install(sink)
            withContext(TelemetryContext("request-7")) {
                Telemetry.measure(Operation.DID_RESOLVE) { }
            }
            Telemetry.measure(Operation.DID_RESOLVE) { }
            assertEquals(listOf("request-7", null), recorded.map { it.correlationId })
        }

    @Test
    fun `a sink that throws cannot fail the operation it was observing`() =
        runBlocking<Unit> {
            Telemetry.install { error("sink is broken") }
            assertEquals("ok", Telemetry.measure(Operation.DID_RESOLVE) { "ok" })
        }

    @Test
    fun `attributes are carried through unchanged`() =
        runBlocking<Unit> {
            Telemetry.install(sink)
            Telemetry.measure(Operation.DID_RESOLVE, mapOf("did.method" to "key")) { }
            assertEquals(mapOf("did.method" to "key"), recorded.single().attributes)
        }

    @Test
    fun `the context element reports its correlation id`() {
        assertEquals("TelemetryContext(abc)", TelemetryContext("abc").toString())
        assertEquals(TelemetryContext, TelemetryContext("abc").key)
    }
}
