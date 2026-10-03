package org.trustweave.did.registrar.client

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.adapter.UniversalRegistrarProtocolAdapter
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.DidState
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.model.UpdateDidOptions
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Polling and fail-loud behaviour of [DefaultUniversalRegistrar], driven through a fake protocol
 * adapter (no network).
 */
class DefaultUniversalRegistrarTest {
    private fun response(
        state: OperationState,
        jobId: String? = null,
    ) = DidRegistrationResponse(
        jobId = jobId,
        didState =
            DidState(
                state = state,
                reason =
                    if (state ==
                        OperationState.FAILED
                    ) {
                        "nope"
                    } else {
                        null
                    },
            ),
    )

    private class FakeAdapter(
        private val statuses: List<DidRegistrationResponse>,
    ) : UniversalRegistrarProtocolAdapter {
        var polls = 0

        override suspend fun createDid(
            baseUrl: String,
            method: String,
            options: CreateDidOptions,
        ) = statuses.first()

        override suspend fun updateDid(
            baseUrl: String,
            did: String,
            document: DidDocument,
            options: UpdateDidOptions,
        ) = statuses.first()

        override suspend fun deactivateDid(
            baseUrl: String,
            did: String,
            options: DeactivateDidOptions,
        ) = statuses.first()

        override suspend fun getOperationStatus(
            baseUrl: String,
            jobId: String,
        ) = statuses[minOf(polls++, statuses.lastIndex)]
    }

    private fun registrar(
        adapter: FakeAdapter,
        attempts: Int = 3,
    ) = DefaultUniversalRegistrar("https://registrar.example", protocolAdapter = adapter, pollInterval = 1, maxPollAttempts = attempts)

    @Test
    fun `a completed response is returned without polling`() =
        runTest {
            val adapter = FakeAdapter(listOf(response(OperationState.FINISHED)))
            val done = response(OperationState.FINISHED)
            assertEquals(done, registrar(adapter).waitForCompletion(done))
            assertEquals(0, adapter.polls)
        }

    @Test
    fun `an unfinished operation without a jobId cannot be awaited and says so`() =
        runTest {
            val ex =
                assertFailsWith<TrustWeaveException> {
                    registrar(FakeAdapter(listOf(response(OperationState.WAIT)))).waitForCompletion(response(OperationState.WAIT))
                }
            assertTrue("jobId" in ex.message, ex.message)
        }

    @Test
    fun `polling follows the job until it finishes`() =
        runTest {
            val adapter = FakeAdapter(listOf(response(OperationState.WAIT, "j"), response(OperationState.FINISHED, "j")))
            val result = registrar(adapter).waitForCompletion(response(OperationState.WAIT, "j"))
            assertEquals(OperationState.FINISHED, result.didState.state)
            assertEquals(2, adapter.polls)
        }

    @Test
    fun `a failed job is returned as failed, never as finished`() =
        runTest {
            val adapter = FakeAdapter(listOf(response(OperationState.FAILED, "j")))
            assertEquals(OperationState.FAILED, registrar(adapter).waitForCompletion(response(OperationState.WAIT, "j")).didState.state)
        }

    @Test
    fun `a job that never completes fails loudly instead of returning a pending state`() =
        runTest {
            val adapter = FakeAdapter(listOf(response(OperationState.WAIT, "j")))
            val ex =
                assertFailsWith<TrustWeaveException> {
                    registrar(adapter, attempts = 2).waitForCompletion(response(OperationState.WAIT, "j"))
                }
            assertTrue("did not complete" in ex.message, ex.message)
            assertEquals(2, adapter.polls)
        }
}
