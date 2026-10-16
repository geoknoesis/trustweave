package org.trustweave.did.batch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BatchOperationsBehaviourTest {
    private fun did(n: Int) = Did("did:example:$n")

    private val knownResolver =
        DidResolver { did ->
            if (did.value.endsWith(":404")) {
                DidResolutionResult.Failure.NotFound(did)
            } else {
                DidResolutionResult.Success(DidDocument(id = did))
            }
        }

    @Test
    fun `resolveBatch maps every DID to its own result`() =
        runTest {
            val service = DefaultBatchOperationsService(knownResolver)
            val dids = listOf(did(1), Did("did:example:404"), did(2))

            val results = service.resolveBatch(dids)

            assertEquals(dids.toSet(), results.keys)
            assertEquals(did(1), (results.getValue(did(1)) as DidResolutionResult.Success).document.id)
            assertIs<DidResolutionResult.Failure.NotFound>(results.getValue(Did("did:example:404")))
        }

    @Test
    fun `empty input gives empty output without touching the resolver`() =
        runTest {
            val calls = AtomicInteger()
            val service =
                DefaultBatchOperationsService(
                    DidResolver {
                        calls.incrementAndGet()
                        error("unused")
                    },
                )
            assertTrue(service.resolveBatch(emptyList()).isEmpty())
            assertTrue(service.verifyBatch(emptyList()).isEmpty())
            assertEquals(0, calls.get())
        }

    @Test
    fun `duplicate DIDs collapse to one entry`() =
        runTest {
            val service = DefaultBatchOperationsService(knownResolver)
            assertEquals(1, service.resolveBatch(listOf(did(1), did(1))).size)
        }

    @Test
    fun `concurrency is bounded by the chunk size`() =
        runTest {
            val inFlight = AtomicInteger()
            val peak = AtomicInteger()
            val resolver =
                DidResolver { did ->
                    val now = inFlight.incrementAndGet()
                    peak.updateAndGet { maxOf(it, now) }
                    delay(10)
                    inFlight.decrementAndGet()
                    DidResolutionResult.Success(DidDocument(id = did))
                }
            val service = DefaultBatchOperationsService(resolver)

            val results = service.resolveBatch((1..10).map(::did), maxConcurrency = 3)

            assertEquals(10, results.size)
            assertEquals(3, peak.get(), "chunks of 3 run in parallel but never more")
        }

    @Test
    fun `chunks run in parallel within themselves`() =
        runTest {
            // Two resolvers that can only finish if both are in flight together.
            val gate = CompletableDeferred<Unit>()
            val started = AtomicInteger()
            val resolver =
                DidResolver { did ->
                    if (started.incrementAndGet() == 2) gate.complete(Unit)
                    gate.await()
                    DidResolutionResult.Success(DidDocument(id = did))
                }
            val job = async { DefaultBatchOperationsService(resolver).resolveBatch(listOf(did(1), did(2)), maxConcurrency = 2) }
            assertEquals(2, job.await().size)
        }

    @Test
    fun `non-positive maxConcurrency is rejected`() =
        runTest {
            val service = DefaultBatchOperationsService(knownResolver)
            assertFailsWith<IllegalArgumentException> { service.resolveBatch(listOf(did(1)), maxConcurrency = 0) }
            assertFailsWith<IllegalArgumentException> { service.verifyBatch(listOf(DidDocument(did(1))), maxConcurrency = -1) }
        }

    @Test
    fun `a resolver failure propagates and fails the batch`() =
        runTest {
            val service = DefaultBatchOperationsService(DidResolver { error("network down") })
            val error = assertFailsWith<IllegalStateException> { service.resolveBatch(listOf(did(1), did(2))) }
            assertEquals("network down", error.message)
        }

    @Test
    fun `verifyBatch is true only for documents that currently resolve successfully`() =
        runTest {
            val service = DefaultBatchOperationsService(knownResolver)
            val good = DidDocument(did(1))
            val missing = DidDocument(Did("did:example:404"))

            val verdicts = service.verifyBatch(listOf(good, missing))

            assertEquals(mapOf(did(1) to true, Did("did:example:404") to false), verdicts)
        }

    @Test
    fun `verifyBatch treats a deactivated DID as not verified`() =
        runTest {
            val resolver = DidResolver { did -> DidResolutionResult.Deactivated(did) }
            val verdicts = DefaultBatchOperationsService(resolver).verifyBatch(listOf(DidDocument(did(1))))
            assertEquals(false, verdicts.getValue(did(1)))
        }
}
