package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * Local-cache freshness tests for [DecentralizedResolutionStrategy].
 *
 * `isFresh()` used to date a cached result by §4.3 `documentMetadata.updated`. That only ever
 * looked right because the DID methods bumped `updated` to "now" on every cache-store — a DID
 * Resolution 1.0 §4.3 violation, since `updated` is "the timestamp of the last Update operation",
 * not of the last fetch. With that fabrication removed, `updated` on a live DID is usually absent
 * (never updated) or old, so freshness follows the fetch time to its correct home: §4.2
 * [DidResolutionMetadata.retrieved], resolution-*process* metadata.
 *
 * These tests pin both halves of that move: `retrieved` now decides freshness, and resolvers that
 * report no `retrieved` keep behaving exactly as they did before.
 */
class ResolutionFreshnessTest {
    private val did = Did("did:test:123")

    private class CountingResolver(
        private val result: () -> DidResolutionResult,
    ) : DidResolver {
        var callCount = 0
            private set

        override suspend fun resolve(did: Did): DidResolutionResult {
            callCount++
            return result()
        }
    }

    private fun strategy(
        local: DidResolver,
        methodSpecific: DidResolver,
        maxCacheAge: kotlin.time.Duration = 1.hours,
    ): DecentralizedResolutionStrategy =
        DecentralizedResolutionStrategy(
            localResolver = local,
            universalResolver = CountingResolver { DidResolutionResult.Failure.NotFound(did) },
            methodSpecificResolvers = mapOf("test" to methodSpecific),
            maxCacheAge = maxCacheAge,
        )

    /**
     * The case the `updated` fix would have broken if freshness had not moved with the fetch time:
     * a document whose last Update operation was years ago, fetched a moment ago, is a *fresh*
     * cache entry. Dating it by §4.3 `updated` would make every cached document look stale and
     * defeat the local-cache short-circuit repo-wide.
     */
    @Test
    fun `a local Success fetched recently is fresh even though it was last updated long ago`() =
        runBlocking {
            val local =
                CountingResolver {
                    DidResolutionResult.Success(
                        document = DidDocument(id = did),
                        documentMetadata =
                            DidDocumentMetadata(
                                created = Instant.parse("2020-01-01T00:00:00Z"),
                                updated = Instant.parse("2020-06-01T00:00:00Z"),
                            ),
                        resolutionMetadata = DidResolutionMetadata(retrieved = Clock.System.now()),
                    )
                }
            val methodSpecific = CountingResolver { DidResolutionResult.Success(DidDocument(id = did)) }

            val result = strategy(local, methodSpecific).resolve(did)

            assertTrue(result is DidResolutionResult.Success)
            assertEquals(1, local.callCount)
            assertEquals(
                0,
                methodSpecific.callCount,
                "a locally cached document fetched seconds ago must short-circuit, however old its `updated` is",
            )
        }

    /** A `retrieved` older than `maxCacheAge` is stale, whatever the document timestamps say. */
    @Test
    fun `a local Success fetched longer ago than maxCacheAge falls through`() =
        runBlocking {
            val stale =
                DidResolutionResult.Success(
                    document = DidDocument(id = did),
                    documentMetadata = DidDocumentMetadata(created = Clock.System.now()),
                    resolutionMetadata = DidResolutionMetadata(retrieved = Clock.System.now() - 2.hours),
                )
            val fresh = DidResolutionResult.Success(DidDocument(id = did))
            val methodSpecific = CountingResolver { fresh }

            val result = strategy(CountingResolver { stale }, methodSpecific).resolve(did)

            assertSame(fresh, result, "a `retrieved` older than maxCacheAge must not short-circuit")
        }

    /**
     * Unchanged-behaviour guard. Every third-party [DidResolver], and any Universal Resolver
     * response that omits `retrieved`, must keep being dated exactly as before: `updated`, then
     * `created`.
     */
    @Test
    fun `a local Success without retrieved still falls back to updated then created`() =
        runBlocking {
            val datedByUpdated =
                CountingResolver {
                    DidResolutionResult.Success(
                        document = DidDocument(id = did),
                        documentMetadata =
                            DidDocumentMetadata(
                                created = Instant.parse("2020-01-01T00:00:00Z"),
                                updated = Clock.System.now(),
                            ),
                    )
                }
            val datedByCreated =
                CountingResolver {
                    DidResolutionResult.Success(
                        document = DidDocument(id = did),
                        documentMetadata = DidDocumentMetadata(created = Clock.System.now()),
                    )
                }
            val methodSpecific = CountingResolver { DidResolutionResult.Success(DidDocument(id = did)) }

            listOf(datedByUpdated, datedByCreated).forEach { local ->
                assertTrue(strategy(local, methodSpecific).resolve(did) is DidResolutionResult.Success)
            }

            assertEquals(
                0,
                methodSpecific.callCount,
                "both §4.3 fallbacks must still be treated as fresh when no `retrieved` is reported",
            )
        }

    /** A result carrying no timestamp at all is still treated as stale (pre-existing behaviour). */
    @Test
    fun `a local Success with no timestamps at all is stale`() =
        runBlocking {
            val fresh = DidResolutionResult.Success(DidDocument(id = did))
            val methodSpecific = CountingResolver { fresh }

            val result =
                strategy(
                    CountingResolver { DidResolutionResult.Success(DidDocument(id = did)) },
                    methodSpecific,
                ).resolve(did)

            assertSame(fresh, result)
            assertEquals(1, methodSpecific.callCount)
        }
}
