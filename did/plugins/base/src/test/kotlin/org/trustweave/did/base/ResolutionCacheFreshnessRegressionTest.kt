package org.trustweave.did.base

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DecentralizedResolutionStrategy
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Regression test for the §4.2 `retrieved` gap: nine DID method plugins (`did:ebsi`, `did:orb`,
 * `did:peer`, `did:plc`, `did:sol`, `did:cheqd`, `did:ethr`, `did:ens`, `did:polygon`) passed
 * `getDocumentMetadata(did)?.updated` to [DidMethodUtils.createSuccessResolutionResult] but never
 * `retrieved`. [DecentralizedResolutionStrategy.isFresh] (private, exercised only through
 * [DecentralizedResolutionStrategy.resolve] here) falls back to `updated`/`created` when
 * `retrieved` is absent — `created` is seeded once at first store and never advances — so the
 * local-cache short-circuit degraded from a sliding freshness window to a fixed one that expires
 * permanently one `maxCacheAge` after first store.
 *
 * This uses the exact pattern the fixed plugins now follow — resolve → [AbstractDidMethod.storeDocument]
 * → report `retrieved = `[AbstractDidMethod.getLastFetched] — and feeds the resulting
 * [DidResolutionResult.Success] into a real [DecentralizedResolutionStrategy], proving the fix
 * closes the regression end-to-end rather than merely that the field is non-null.
 */
class ResolutionCacheFreshnessRegressionTest {
    private companion object {
        const val DID = "did:test:cache-freshness"
    }

    /** Mirrors the fixed plugins: resolve -> storeDocument -> report retrieved = getLastFetched. */
    private class TestDidMethod(
        kms: KeyManagementService,
        private val remote: DidDocument,
    ) : AbstractDidMethod("test", kms) {
        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("not needed for this test")

        override suspend fun resolveDid(did: Did): DidResolutionResult {
            storeDocument(did, remote)
            val metadata = getDocumentMetadata(did)
            return DidMethodUtils.createSuccessResolutionResult(
                remote,
                method,
                metadata?.created,
                metadata?.updated,
                metadata?.deactivated ?: false,
                retrieved = getLastFetched(did),
            )
        }
    }

    private fun document(did: String): DidDocument =
        DidMethodUtils.buildDidDocument(
            did = did,
            verificationMethod =
                listOf(
                    DidMethodUtils.createVerificationMethod(
                        did = did,
                        keyHandle =
                            KeyHandle(
                                id = KeyId("key-1"),
                                algorithm = "Ed25519",
                                publicKeyMultibase = "z6Mk",
                            ),
                        algorithm = "Ed25519",
                    ),
                ),
        )

    @Test
    fun `a plugin-produced Success carries retrieved and isFresh short-circuits after created goes stale`() =
        runBlocking {
            val method = TestDidMethod(InMemoryKeyManagementService(), document(DID))
            val did = Did(DID)

            // First resolve seeds `created`. A later resolve (below) refreshes `retrieved`
            // without moving `created` — the never-Updated-DID shape every affected plugin hits.
            val first = method.resolveDid(did)
            assertTrue(first is DidResolutionResult.Success, "expected Success, got $first")
            assertTrue(first.resolutionMetadata.retrieved != null, "§4.2 retrieved must be populated")

            // Let `created` age well past the freshness window configured below. Generous versus
            // JVM/coroutine-dispatch jitter on a cold run, since the assertion below is a hard
            // wall-clock threshold rather than a before/after ordering check.
            delay(500)

            val second = method.resolveDid(did)
            assertTrue(second is DidResolutionResult.Success, "expected Success, got $second")
            assertTrue(second.resolutionMetadata.retrieved != null, "§4.2 retrieved must be populated")

            // Wired to fail the test if reached: a fresh `retrieved` must short-circuit the local
            // resolver before either fallback is ever consulted.
            val mustNotBeReached =
                DidResolver {
                    throw AssertionError(
                        "must not be reached: a fresh local Success (retrieved just now, even " +
                            "though created is already older than maxCacheAge) must short-circuit " +
                            "before falling through to method-specific/universal resolution",
                    )
                }

            val strategy =
                DecentralizedResolutionStrategy(
                    localResolver = DidResolver { second },
                    universalResolver = mustNotBeReached,
                    methodSpecificResolvers = mapOf("test" to mustNotBeReached),
                    // `created` is >=500ms stale by now; `retrieved` was just captured. A 200ms
                    // window admits only `retrieved`, proving isFresh() is reading it rather than
                    // falling back to `created` (the pre-fix regression).
                    maxCacheAge = 200.milliseconds,
                )

            val resolved = strategy.resolve(did)
            assertSame(
                second,
                resolved,
                "expected the fresh local Success to short-circuit; got a different result, " +
                    "meaning isFresh() fell through instead of reading `retrieved`",
            )
        }
}
