package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [FallbackDidResolver].
 *
 * Fallback semantics: only [DidResolutionResult.Failure.MethodNotRegistered] from the
 * primary triggers the fallback. NotFound / InvalidFormat / ResolutionError from a
 * registered method are authoritative and must be returned as-is.
 */
class FallbackDidResolverTest {

    private class RecordingResolver(
        private val handler: (Did) -> DidResolutionResult
    ) : DidResolver {
        var calls = 0
        override suspend fun resolve(did: Did): DidResolutionResult {
            calls++
            return handler(did)
        }
    }

    private fun success(did: Did): DidResolutionResult.Success =
        DidResolutionResult.Success(document = DidDocument(id = did))

    private fun methodNotRegistered(did: Did): DidResolutionResult =
        DidResolutionResult.Failure.MethodNotRegistered(
            method = did.method,
            availableMethods = listOf("key", "web")
        )

    // ─── Trigger cases ───

    @Test
    fun `method not registered on primary triggers fallback`() = runBlocking {
        val did = Did("did:ion:EiDexample")
        val fallbackResult = success(did)
        val primary = RecordingResolver { methodNotRegistered(it) }
        val fallback = RecordingResolver { fallbackResult }

        val result = FallbackDidResolver(primary, fallback).resolve(did)

        assertEquals(1, primary.calls)
        assertEquals(1, fallback.calls, "fallback must be consulted for an unregistered method")
        assertSame(fallbackResult, result)
    }

    @Test
    fun `fallback failure is returned when both resolvers fail`() = runBlocking {
        val did = Did("did:ion:EiDexample")
        val primary = RecordingResolver { methodNotRegistered(it) }
        val fallback = RecordingResolver {
            DidResolutionResult.Failure.NotFound(did = it, reason = "unknown upstream")
        }

        val result = FallbackDidResolver(primary, fallback).resolve(did)

        assertEquals(1, fallback.calls)
        assertTrue(result is DidResolutionResult.Failure.NotFound)
        assertEquals("unknown upstream", (result as DidResolutionResult.Failure.NotFound).reason)
    }

    // ─── Non-trigger cases ───

    @Test
    fun `primary success does not consult fallback`() = runBlocking {
        val did = Did("did:key:z6Mkexample")
        val primaryResult = success(did)
        val primary = RecordingResolver { primaryResult }
        val fallback = RecordingResolver { success(it) }

        val result = FallbackDidResolver(primary, fallback).resolve(did)

        assertEquals(0, fallback.calls, "fallback must not run when primary succeeds")
        assertSame(primaryResult, result)
    }

    @Test
    fun `not found from a registered method does not trigger fallback`() = runBlocking {
        // The primary's method driver authoritatively said the DID does not exist;
        // silently asking another source could resurrect deleted DIDs.
        val did = Did("did:key:z6Mkmissing")
        val primary = RecordingResolver {
            DidResolutionResult.Failure.NotFound(did = it, reason = "not found locally")
        }
        val fallback = RecordingResolver { success(it) }

        val result = FallbackDidResolver(primary, fallback).resolve(did)

        assertEquals(0, fallback.calls, "NotFound is authoritative — no fallback")
        assertTrue(result is DidResolutionResult.Failure.NotFound)
    }

    @Test
    fun `invalid format does not trigger fallback`() = runBlocking {
        val did = Did("did:key:z6Mkexample")
        val primary = RecordingResolver {
            DidResolutionResult.Failure.InvalidFormat(did = it.value, reason = "malformed identifier")
        }
        val fallback = RecordingResolver { success(it) }

        val result = FallbackDidResolver(primary, fallback).resolve(did)

        assertEquals(0, fallback.calls, "a malformed DID is malformed everywhere — no fallback")
        assertTrue(result is DidResolutionResult.Failure.InvalidFormat)
    }

    @Test
    fun `resolution error from a registered method does not trigger fallback`() = runBlocking {
        val did = Did("did:web:example.com")
        val primary = RecordingResolver {
            DidResolutionResult.Failure.ResolutionError(did = it, reason = "driver timeout")
        }
        val fallback = RecordingResolver { success(it) }

        val result = FallbackDidResolver(primary, fallback).resolve(did)

        assertEquals(0, fallback.calls, "operational errors must surface, not be masked by fallback")
        assertTrue(result is DidResolutionResult.Failure.ResolutionError)
    }

    // ─── Composition ───

    @Test
    fun `composes with CachingDidResolver`() = runBlocking {
        // CachingDidResolver(FallbackDidResolver(registry, universal)) — the documented
        // composition: fallback results are cached like any other success.
        val did = Did("did:ion:EiDexample")
        val primary = RecordingResolver { methodNotRegistered(it) }
        val fallback = RecordingResolver { success(it) }
        val resolver = CachingDidResolver(FallbackDidResolver(primary, fallback))

        val first = resolver.resolve(did)
        val second = resolver.resolve(did)

        assertTrue(first is DidResolutionResult.Success)
        assertSame(first, second)
        assertEquals(1, fallback.calls, "fallback success must be served from cache afterwards")
    }
}
