package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Decentralized Resolution Strategy.
 *
 * Implements multi-source resolution with fallback to maintain decentralization principles.
 * Tries multiple resolution sources in order:
 * 1. Local storage (fastest, but may be stale)
 * 2. Method-specific resolver (most authoritative)
 * 3. Universal Resolver (decentralized fallback)
 *
 * **Decentralization Benefits:**
 * - No single point of failure
 * - Multiple resolution sources
 * - Freshness checking for cached results
 * - Graceful degradation
 *
 * **Example Usage:**
 * ```kotlin
 * val strategy = DecentralizedResolutionStrategy(
 *     localResolver = RegistryBasedResolver(registry),
 *     universalResolver = DefaultUniversalResolver("https://dev.uniresolver.io"),
 *     methodSpecificResolvers = mapOf(
 *         "web" to WebDidResolver(),
 *         "key" to KeyDidResolver()
 *     )
 * )
 *
 * val result = strategy.resolve(Did("did:web:example.com"))
 * ```
 */
class DecentralizedResolutionStrategy(
    private val localResolver: DidResolver,
    private val universalResolver: DidResolver,
    private val methodSpecificResolvers: Map<String, DidResolver> = emptyMap(),
    private val maxCacheAge: Duration = 1.hours
) : DidResolver {
    
    override suspend fun resolve(did: Did): DidResolutionResult {
        // 1. Try local storage first (fastest, but may be stale)
        localResolver.resolve(did).takeIf { result ->
            result is DidResolutionResult.Success && isFresh(result)
        }?.let { result ->
            return result
        }
        
        // 2. Try method-specific resolver (most authoritative)
        methodSpecificResolvers[did.method]?.resolve(did)
            ?.takeIf { it is DidResolutionResult.Success }
            ?.let { result ->
                return result
            }
        
        // 3. Fall back to Universal Resolver (decentralized)
        return universalResolver.resolve(did)
    }
    
    /**
     * Checks if a cached resolution result is still fresh.
     */
    private fun isFresh(result: DidResolutionResult.Success): Boolean {
        val metadata = result.documentMetadata
        val updated = metadata.updated ?: metadata.created ?: return false
        
        val age = Clock.System.now() - updated
        return age < maxCacheAge
    }
}

/**
 * Resolution Fallback Strategy.
 *
 * Tries multiple resolvers in order until one succeeds.
 * Useful for redundancy and high availability.
 *
 * **Example Usage:**
 * ```kotlin
 * val fallbackStrategy = ResolutionFallbackStrategy(
 *     resolvers = listOf(
 *         primaryResolver,
 *         secondaryResolver,
 *         universalResolver
 *     )
 * )
 * ```
 */
class ResolutionFallbackStrategy(
    private val resolvers: List<DidResolver>
) : DidResolver {
    
    override suspend fun resolve(did: Did): DidResolutionResult {
        val errors = mutableListOf<String>()
        
        for (resolver in resolvers) {
            try {
                val result = resolver.resolve(did)
                if (result is DidResolutionResult.Success) {
                    return result
                } else {
                    errors.add(
                        (result as? DidResolutionResult.Failure)?.let {
                            when (it) {
                                is DidResolutionResult.Failure.NotFound -> "Not found"
                                is DidResolutionResult.Failure.InvalidFormat -> "Invalid format: ${it.reason}"
                                is DidResolutionResult.Failure.MethodNotRegistered -> "Method not registered: ${it.method}"
                                is DidResolutionResult.Failure.ResolutionError -> "Resolution error: ${it.reason}"
                            }
                        } ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                errors.add("Exception: ${e.message ?: "Unknown error"}")
            }
        }
        
        // All resolvers failed
        return DidResolutionResult.Failure.ResolutionError(
            did = did,
            reason = "All resolution attempts failed: ${errors.joinToString(", ")}",
            resolutionMetadata = DidResolutionMetadata(
                error = "resolutionError",
                errorMessage = "All resolution attempts failed",
                properties = mapOf("attemptedResolvers" to resolvers.size.toString())
            )
        )
    }
}

