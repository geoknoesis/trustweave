package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolution.ResolutionOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An invalidation of one DID must not stop in-flight resolutions of other DIDs from being cached. */
class CachingDidResolverGenerationTest {
    private fun success(did: Did) =
        DidResolutionResult.Success(document = DidDocument(id = did), documentMetadata = DidDocumentMetadata())

    private val victims = (1..30).map { Did("did:example:victim$it") }
    private val other = Did("did:example:other")

    private fun runScenario(interfere: (CachingDidResolver) -> Unit): Int =
        runBlocking {
            lateinit var resolver: CachingDidResolver
            val calls = HashMap<String, Int>()
            val delegate =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult {
                        calls.merge(did.value, 1, Int::plus)
                        if (did in victims) interfere(resolver) // another caller acts while this resolve is in flight
                        return success(did)
                    }
                }
            resolver = CachingDidResolver(delegate)
            victims.forEach { resolver.resolve(it) }
            victims.forEach { resolver.resolve(it) }
            victims.count { calls[it.value] == 1 }
        }

    @Test
    fun `invalidating an unrelated DID mid-flight does not suppress other writes`() {
        val cached = runScenario { it.invalidate(other) }
        assertTrue(cached >= 28, "only $cached of ${victims.size} in-flight results were cached")
    }

    @Test
    fun `a noCache resolve of an unrelated DID mid-flight does not suppress other writes`() {
        val cached = runScenario { r -> runBlocking { r.resolve(other, ResolutionOptions(noCache = true)) } }
        assertTrue(cached >= 28, "only $cached of ${victims.size} in-flight results were cached")
    }

    @Test
    fun `clear mid-flight still suppresses every in-flight write`() {
        assertEquals(0, runScenario { it.clear() })
    }

    @Test
    fun `invalidating the same DID mid-flight still suppresses its write`() {
        lateinit var resolver: CachingDidResolver
        var calls = 0
        val did = Did("did:example:same")
        resolver =
            CachingDidResolver(
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult {
                        calls++
                        resolver.invalidate(did)
                        return success(did)
                    }
                },
            )
        runBlocking {
            resolver.resolve(did)
            resolver.resolve(did)
        }
        assertEquals(2, calls)
    }
}
