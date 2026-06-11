package org.trustweave.trust.services

import org.trustweave.did.identifiers.Did
import org.trustweave.trust.TrustAnchorMetadata
import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.TrustAnchor
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.types.VerifierIdentity
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [TrustRegistryFactory] used when `trust { provider("inMemory") }` is configured
 * without an explicit factory (e.g. `TrustWeave.quickStart()` / `TrustWeave.inMemory()`).
 *
 * Only the in-memory provider names are supported; any other provider still requires a
 * caller-supplied factory via `Builder.factories(trustRegistryFactory = ...)`.
 */
internal object DefaultTrustRegistryFactory : TrustRegistryFactory {
    /** Provider names resolved by this factory. */
    internal val IN_MEMORY_PROVIDERS = setOf("inMemory", "in-memory", "default")

    override suspend fun create(providerName: String): TrustRegistry {
        if (providerName in IN_MEMORY_PROVIDERS) {
            return InMemoryTrustRegistry()
        }
        throw IllegalStateException(
            "TrustRegistry provider '$providerName' is not supported by the default factory. " +
                "Use \"inMemory\" or provide a custom TrustRegistryFactory via " +
                "Builder.factories(trustRegistryFactory = ...)."
        )
    }
}

/**
 * In-memory [TrustRegistry] backing [DefaultTrustRegistryFactory].
 *
 * Stores trust anchors in a concurrent map and discovers trust paths with BFS over an
 * in-memory trust graph. Suitable for prototypes and tests; production deployments
 * should wire a persistent registry through a custom [TrustRegistryFactory].
 */
internal class InMemoryTrustRegistry : TrustRegistry {
    private val trustAnchors = ConcurrentHashMap<String, TrustAnchorMetadata>()

    // DID -> set of DIDs it trusts (self-trust is added on anchor registration)
    private val trustGraph = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun isTrustedIssuer(issuerDid: String, credentialType: String?): Boolean {
        val anchor = trustAnchors[issuerDid] ?: return false
        if (credentialType != null) {
            anchor.credentialTypes?.let { types ->
                return types.contains(credentialType)
            }
        }
        // No credential type requested, or the anchor is trusted for all types.
        return true
    }

    override suspend fun addTrustAnchor(anchorDid: String, metadata: TrustAnchorMetadata): Boolean {
        if (trustAnchors.putIfAbsent(anchorDid, metadata) != null) {
            return false
        }
        trustGraph.getOrPut(anchorDid) { ConcurrentHashMap.newKeySet() }.add(anchorDid)
        return true
    }

    override suspend fun removeTrustAnchor(anchorDid: String): Boolean {
        val removed = trustAnchors.remove(anchorDid) != null
        if (removed) {
            trustGraph.remove(anchorDid)
            trustGraph.values.forEach { it.remove(anchorDid) }
        }
        return removed
    }

    override suspend fun findTrustPath(from: VerifierIdentity, to: IssuerIdentity): TrustPath {
        val fromDid = from.did.value
        val toDid = to.did.value

        if (fromDid == toDid) {
            return TrustPath.Verified(
                from = from,
                to = to,
                anchors = emptyList(),
                verified = true,
                verifiedAt = Clock.System.now(),
                trustScore = 1.0
            )
        }

        if (!trustAnchors.containsKey(fromDid) && !trustAnchors.containsKey(toDid)) {
            return TrustPath.NotFound(
                from = from,
                to = to,
                reason = "Neither $fromDid nor $toDid are trust anchors"
            )
        }

        val path = findPathBfs(fromDid, toDid)
        if (path.isEmpty()) {
            return TrustPath.NotFound(
                from = from,
                to = to,
                reason = "No trust path found between $fromDid and $toDid"
            )
        }

        val anchors = path.drop(1).dropLast(1).map { didString ->
            TrustAnchor(
                did = Did(didString),
                metadata = trustAnchors[didString] ?: TrustAnchorMetadata()
            )
        }

        return TrustPath.Verified(
            from = from,
            to = to,
            anchors = anchors,
            verified = true,
            verifiedAt = Clock.System.now(),
            trustScore = calculateTrustScore(path.size)
        )
    }

    override suspend fun getTrustedIssuers(credentialType: String?): List<String> =
        trustAnchors.entries
            .filter { (_, metadata) ->
                credentialType == null || (metadata.credentialTypes?.contains(credentialType) ?: true)
            }
            .map { it.key }

    /**
     * Adds a directed trust relationship between two DIDs (beyond anchor self-trust),
     * so multi-hop trust paths can be discovered.
     */
    fun addTrustRelationship(fromDid: String, toDid: String) {
        trustGraph.getOrPut(fromDid) { ConcurrentHashMap.newKeySet() }.add(toDid)
    }

    private fun findPathBfs(start: String, end: String): List<String> {
        if (start == end) return listOf(start)

        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf(start)
        queue.add(listOf(start))

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val current = path.last()
            for (neighbor in trustGraph[current] ?: emptySet()) {
                if (neighbor == end) {
                    return path + neighbor
                }
                if (visited.add(neighbor)) {
                    queue.add(path + neighbor)
                }
            }
        }
        return emptyList()
    }

    /** Shorter paths score higher: direct = 1.0, then decreasing by 0.2 per hop, floored at 0.1. */
    private fun calculateTrustScore(pathLength: Int): Double = when {
        pathLength <= 1 -> 1.0
        else -> maxOf(0.1, 1.0 - (pathLength - 1) * 0.2)
    }
}
