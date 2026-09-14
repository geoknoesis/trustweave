package org.trustweave.did.telemetry

import kotlinx.coroutines.test.runTest
import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Outcome
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.core.telemetry.TelemetryEvent
import org.trustweave.core.telemetry.TelemetrySink
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolutionResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TelemetryDidMethodTest {
    private val recorded = CopyOnWriteArrayList<TelemetryEvent>()
    private val sink = TelemetrySink { recorded += it }

    @AfterTest
    fun tearDown() {
        Telemetry.uninstall()
    }

    /** A method that answers, so the decorator is the only thing under test. */
    private class FakeDidMethod(
        override val method: String = "fake",
        private val deactivates: Boolean = true,
        private val failure: Throwable? = null,
    ) : DidMethod {
        private val document = DidDocument(id = Did("did:fake:subject"))

        override suspend fun createDid(options: DidCreationOptions): DidDocument {
            failure?.let { throw it }
            return document
        }

        override suspend fun resolveDid(did: Did): DidResolutionResult = DidResolutionResult.Success(document)

        override suspend fun updateDid(
            did: Did,
            updater: (DidDocument) -> DidDocument,
        ): DidDocument = updater(document)

        override suspend fun deactivateDid(did: Did): Boolean = deactivates
    }

    private fun eventsFor(operation: Operation) = recorded.filter { it.operation == operation }

    @Test
    fun `nothing is recorded until a host installs a sink`() =
        runTest {
            FakeDidMethod().withTelemetry().createDid()
            assertTrue(recorded.isEmpty())
        }

    @Test
    fun `the write lifecycle is reported and tagged with the method`() =
        runTest {
            Telemetry.install(sink)
            val method = FakeDidMethod().withTelemetry()
            method.createDid()
            method.updateDid(Did("did:fake:subject")) { it }
            method.deactivateDid(Did("did:fake:subject"))

            assertEquals(1, eventsFor(Operation.DID_CREATE).size)
            assertEquals(1, eventsFor(Operation.DID_UPDATE).size)
            assertEquals(1, eventsFor(Operation.DID_DEACTIVATE).size)
            assertTrue(recorded.all { it.attributes["did.method"] == "fake" }, recorded.toString())
            assertTrue(recorded.all { it.outcome == Outcome.SUCCESS })
        }

    @Test
    fun `a declined deactivation is rejected rather than a success`() =
        runTest {
            Telemetry.install(sink)
            val method = FakeDidMethod(deactivates = false).withTelemetry()
            method.deactivateDid(Did("did:fake:subject"))
            val outcomes = eventsFor(Operation.DID_DEACTIVATE).map { it.outcome }
            assertTrue(Outcome.REJECTED in outcomes, "a method that answers no has declined: $recorded")
        }

    @Test
    fun `a thrown registrar error is reported as a failure and rethrown`() =
        runTest {
            Telemetry.install(sink)
            val method = FakeDidMethod(failure = IllegalStateException("registrar down")).withTelemetry()
            assertFailsWith<IllegalStateException> { method.createDid() }
            val event = eventsFor(Operation.DID_CREATE).single()
            assertEquals(Outcome.FAILURE, event.outcome)
            assertEquals("IllegalStateException", event.reason, "the reason must be a class name, never a message")
        }

    @Test
    fun `no DID reaches an event`() =
        runTest {
            Telemetry.install(sink)
            FakeDidMethod().withTelemetry().updateDid(Did("did:fake:subject")) { it }
            val values = recorded.flatMap { it.attributes.values + listOfNotNull(it.reason) }
            assertTrue(values.none { it.startsWith("did:") }, "attributes carried a DID: $values")
        }

    @Test
    fun `wrapping twice does not double-report`() =
        runTest {
            Telemetry.install(sink)
            val once = FakeDidMethod().withTelemetry()
            assertSame(once, once.withTelemetry())
            once.createDid()
            assertEquals(1, eventsFor(Operation.DID_CREATE).size)
        }

    @Test
    fun `registering a method is what applies the instrumentation`() =
        runTest {
            Telemetry.install(sink)
            val registry = DidMethodRegistry()
            registry.register(FakeDidMethod())
            // A host that never heard of TelemetryDidMethod still gets instrumented writes.
            requireNotNull(registry["fake"]).createDid()
            assertEquals(1, eventsFor(Operation.DID_CREATE).size, recorded.toString())
        }
}
