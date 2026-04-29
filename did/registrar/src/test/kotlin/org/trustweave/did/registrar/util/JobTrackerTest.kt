package org.trustweave.did.registrar.util

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.PollableRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.DidState
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.model.UpdateDidOptions
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobTrackerTest {

    @Test
    fun `waitForCompletion returns immediately when response already complete`() = runTest {
        val doc = emptyDidDocument("did:test:done")
        val response = DidRegistrationResponse(
            jobId = null,
            didState = DidState(
                state = OperationState.FINISHED,
                did = "did:test:done",
                didDocument = doc,
            ),
        )
        val tracker = JobTracker(NonPollableRegistrar(), pollInterval = 10, maxAttempts = 5)
        val out = tracker.waitForCompletion(response)
        assertEquals(response, out)
    }

    @Test
    fun `waitForCompletion polls PollableRegistrar until finished`() = runTest {
        val waiting = DidRegistrationResponse(
            jobId = "job-1",
            didState = DidState(state = OperationState.WAIT, did = null, didDocument = null),
        )
        val finished = DidRegistrationResponse(
            jobId = null,
            didState = DidState(
                state = OperationState.FINISHED,
                did = "did:test:1",
                didDocument = emptyDidDocument("did:test:1"),
            ),
        )
        val registrar = SequencePollableRegistrar(listOf(finished))
        val tracker = JobTracker(registrar, pollInterval = 10, maxAttempts = 5)
        val out = tracker.waitForCompletion(waiting)
        assertEquals(OperationState.FINISHED, out.didState.state)
        assertEquals("did:test:1", out.didState.did)
        assertTrue(registrar.statusPollCount >= 1)
    }

    @Test
    fun `waitForCompletion throws when registrar is not PollableRegistrar`() = runTest {
        val waiting = DidRegistrationResponse(
            jobId = "job-1",
            didState = DidState(state = OperationState.WAIT, did = null, didDocument = null),
        )
        val tracker = JobTracker(NonPollableRegistrar(), pollInterval = 10, maxAttempts = 5)
        val ex = assertThrows<TrustWeaveException.InvalidOperation> {
            tracker.waitForCompletion(waiting)
        }
        assertTrue(ex.message.orEmpty().contains("PollableRegistrar"))
    }

    private fun emptyDidDocument(did: String) = DidDocument(
        id = Did(did),
        verificationMethod = emptyList(),
        authentication = emptyList(),
        assertionMethod = emptyList(),
        keyAgreement = emptyList(),
        capabilityInvocation = emptyList(),
        capabilityDelegation = emptyList(),
        service = emptyList(),
    )

    private class NonPollableRegistrar : DidRegistrar {
        override suspend fun createDid(method: String, options: CreateDidOptions): DidRegistrationResponse =
            error("unused")

        override suspend fun updateDid(did: String, document: DidDocument, options: UpdateDidOptions): DidRegistrationResponse =
            error("unused")

        override suspend fun deactivateDid(did: String, options: DeactivateDidOptions): DidRegistrationResponse =
            error("unused")
    }

    private class SequencePollableRegistrar(
        private val statusSequence: List<DidRegistrationResponse>,
    ) : DidRegistrar, PollableRegistrar {
        var statusPollCount = 0
        private var index = 0

        override suspend fun createDid(method: String, options: CreateDidOptions): DidRegistrationResponse =
            error("unused")

        override suspend fun updateDid(did: String, document: DidDocument, options: UpdateDidOptions): DidRegistrationResponse =
            error("unused")

        override suspend fun deactivateDid(did: String, options: DeactivateDidOptions): DidRegistrationResponse =
            error("unused")

        override suspend fun getOperationStatus(jobId: String): DidRegistrationResponse {
            statusPollCount++
            return statusSequence[index++]
        }

        override suspend fun waitForCompletion(response: DidRegistrationResponse): DidRegistrationResponse =
            error("JobTracker must not delegate to PollableRegistrar.waitForCompletion in this test")
    }
}
