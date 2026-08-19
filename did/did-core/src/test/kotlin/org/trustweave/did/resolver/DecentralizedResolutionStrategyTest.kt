package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Correctness tests for [DecentralizedResolutionStrategy]'s per-stage handling of
 * [DidResolutionResult.Deactivated] (§4.4).
 *
 * A deactivated verdict is a terminal, authoritative answer — deactivation cannot be undone
 * (W3C DID Core §7.3) — so it must stop the fallback chain exactly like [DidResolutionResult.Success],
 * never be treated as an absence of information that warrants falling through to a
 * less-authoritative source that could resurrect a revoked DID's document.
 */
class DecentralizedResolutionStrategyTest {

    private val did = Did("did:test:123")

    private class CountingResolver(private val result: suspend () -> DidResolutionResult) : DidResolver {
        var callCount = 0
            private set

        override suspend fun resolve(did: Did): DidResolutionResult {
            callCount++
            return result()
        }
    }

    @Test
    fun `a first-stage Deactivated verdict short-circuits and is returned unchanged`() = runBlocking {
        val deactivated = DidResolutionResult.Deactivated(did)
        val local = CountingResolver { deactivated }
        val methodSpecific = CountingResolver { DidResolutionResult.Success(DidDocument(id = did)) }
        val universal = CountingResolver { DidResolutionResult.Success(DidDocument(id = did)) }

        val strategy = DecentralizedResolutionStrategy(
            localResolver = local,
            universalResolver = universal,
            methodSpecificResolvers = mapOf("test" to methodSpecific)
        )

        val result = strategy.resolve(did)

        assertSame(deactivated, result)
        assertEquals(1, local.callCount)
        assertEquals(0, methodSpecific.callCount, "method-specific resolver must not be consulted")
        assertEquals(0, universal.callCount, "universal resolver must not be consulted")
    }

    @Test
    fun `a first-stage Failure still falls through to the next stage`() = runBlocking {
        val failure = DidResolutionResult.Failure.ResolutionError(did, "local storage miss")
        val success = DidResolutionResult.Success(DidDocument(id = did))
        val local = CountingResolver { failure }
        val methodSpecific = CountingResolver { success }
        val universal = CountingResolver { DidResolutionResult.Failure.NotFound(did) }

        val strategy = DecentralizedResolutionStrategy(
            localResolver = local,
            universalResolver = universal,
            methodSpecificResolvers = mapOf("test" to methodSpecific)
        )

        val result = strategy.resolve(did)

        assertSame(success, result)
        assertEquals(1, local.callCount)
        assertEquals(1, methodSpecific.callCount)
        assertEquals(0, universal.callCount)
    }

    @Test
    fun `a method-specific Deactivated verdict short-circuits before the universal resolver`() = runBlocking {
        val deactivated = DidResolutionResult.Deactivated(did)
        val local = CountingResolver { DidResolutionResult.Failure.NotFound(did) }
        val methodSpecific = CountingResolver { deactivated }
        val universal = CountingResolver { DidResolutionResult.Success(DidDocument(id = did)) }

        val strategy = DecentralizedResolutionStrategy(
            localResolver = local,
            universalResolver = universal,
            methodSpecificResolvers = mapOf("test" to methodSpecific)
        )

        val result = strategy.resolve(did)

        assertSame(deactivated, result)
        assertEquals(0, universal.callCount, "universal resolver must not be consulted")
    }

    @Test
    fun `a stale local Success still falls through to the next stage`() = runBlocking {
        // No created/updated timestamps -> isFresh() treats it as not fresh.
        val stale = DidResolutionResult.Success(DidDocument(id = did))
        val success = DidResolutionResult.Success(DidDocument(id = did))
        val local = CountingResolver { stale }
        val methodSpecific = CountingResolver { success }

        val strategy = DecentralizedResolutionStrategy(
            localResolver = local,
            universalResolver = CountingResolver { DidResolutionResult.Failure.NotFound(did) },
            methodSpecificResolvers = mapOf("test" to methodSpecific)
        )

        val result = strategy.resolve(did)

        assertSame(success, result)
    }
}
