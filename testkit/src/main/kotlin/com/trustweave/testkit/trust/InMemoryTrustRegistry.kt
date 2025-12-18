package com.trustweave.testkit.trust

import com.trustweave.did.identifiers.Did
import com.trustweave.trust.TrustAnchorMetadata
import com.trustweave.trust.TrustRegistry
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.trust.types.TrustAnchor
import com.trustweave.trust.types.TrustPath
import com.trustweave.trust.types.VerifierIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * In-memory implementation of TrustRegistry for testing.
 *
 * Stores trust anchors in memory and uses BFS (Breadth-First Search) for trust path discovery.
 *
 * **Example Usage**:
 * ```kotlin
 * val registry = InMemoryTrustRegistry()
 *
 * registry.addTrustAnchor(
 *     anchorDid = "did:key:university",
 *     metadata = TrustAnchorMetadata(
 *         credentialTypes = listOf("EducationCredential"),
 *         description = "Trusted university"
 *     )
 * )
 *
 * val isTrusted = registry.isTrustedIssuer("did:key:university", "EducationCredential")
 * ```
 */
class InMemoryTrustRegistry : TrustRegistry {
    private val trustAnchors = mutableMapOf<String, TrustAnchorMetadata>()
    private val trustGraph = mutableMapOf<String, MutableSet<String>>() // DID -> Set of trusted DIDs

    override suspend fun isTrustedIssuer(issuerDid: String, credentialType: String?): Boolean =
        withContext(Dispatchers.Default) {
            val anchor = trustAnchors[issuerDid] ?: return@withContext false

            // If credential type is specified, check if anchor trusts this type
            if (credentialType != null) {
                anchor.credentialTypes?.let { types ->
                    return@withContext types.contains(credentialType)
                }
            }

            // If no credential type specified or anchor trusts all types, return true
            true
        }

    override suspend fun addTrustAnchor(anchorDid: String, metadata: TrustAnchorMetadata): Boolean =
        withContext(Dispatchers.Default) {
            if (trustAnchors.containsKey(anchorDid)) {
                return@withContext false
            }

            trustAnchors[anchorDid] = metadata

            // Add to trust graph (self-trust)
            trustGraph.getOrPut(anchorDid) { mutableSetOf() }.add(anchorDid)

            true
        }

    override suspend fun removeTrustAnchor(anchorDid: String): Boolean =
        withContext(Dispatchers.Default) {
            val removed = trustAnchors.remove(anchorDid) != null
            if (removed) {
                trustGraph.remove(anchorDid)
                // Remove from all other nodes' trust sets
                trustGraph.values.forEach { it.remove(anchorDid) }
            }
            removed
        }

    override suspend fun findTrustPath(
        from: VerifierIdentity,
        to: IssuerIdentity
    ): TrustPath {
        val fromDid = from  // VerifierIdentity is a typealias for Did
        val toDid = to      // IssuerIdentity is a typealias for Did
        val fromDidStr = fromDid.value
        val toDidStr = toDid.value
        
        // Direct trust check
        if (fromDidStr == toDidStr) {
            return TrustPath.Verified(
                from = from,
                to = to,
                anchors = emptyList(),
                verified = true,
                trustScore = 1.0
            )
        }

        // Check if both DIDs are trust anchors
        val fromIsAnchor = trustAnchors.containsKey(fromDidStr)
        val toIsAnchor = trustAnchors.containsKey(toDidStr)

        if (!fromIsAnchor && !toIsAnchor) {
            return TrustPath.NotFound(
                from = from,
                to = to,
                reason = "Neither ${fromDidStr} nor ${toDidStr} are trust anchors"
            )
        }

        // If both are anchors, find path using BFS
        val path = findPathBFS(fromDidStr, toDidStr)

        if (path.isEmpty()) {
            return TrustPath.NotFound(
                from = from,
                to = to,
                reason = "No trust path found between ${fromDidStr} and ${toDidStr}"
            )
        }

        // Calculate trust score based on path length
        val trustScore = calculateTrustScore(path.size)

        // Convert path to TrustAnchor list (excluding endpoints)
        val anchors = path.drop(1).dropLast(1).map { didString ->
            val metadata = trustAnchors[didString] ?: TrustAnchorMetadata()
            TrustAnchor(
                did = Did(didString),
                metadata = metadata
            )
        }

        return TrustPath.Verified(
            from = from,
            to = to,
            anchors = anchors,
            verified = true,
            trustScore = trustScore
        )
    }


    override suspend fun getTrustedIssuers(credentialType: String?): List<String> =
        withContext(Dispatchers.Default) {
            trustAnchors.entries
                .filter { (_, metadata) ->
                    if (credentialType != null) {
                        metadata.credentialTypes?.contains(credentialType) ?: true
                    } else {
                        true
                    }
                }
                .map { it.key }
        }

    /**
     * Finds a path between two DIDs using BFS.
     */
    private fun findPathBFS(start: String, end: String): List<String> {
        if (start == end) return listOf(start)

        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf<String>()

        queue.add(listOf(start))
        visited.add(start)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val current = path.last()

            // Get neighbors (trusted DIDs)
            val neighbors = trustGraph[current] ?: emptySet()

            for (neighbor in neighbors) {
                if (neighbor == end) {
                    return path + neighbor
                }

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor)
                    queue.add(path + neighbor)
                }
            }
        }

        return emptyList()
    }

    /**
     * Calculates trust score based on path length.
     * Shorter paths have higher trust scores.
     */
    private fun calculateTrustScore(pathLength: Int): Double {
        // Base score decreases with path length
        // Direct trust (length 1) = 1.0
        // Path length 2 = 0.8
        // Path length 3 = 0.6
        // etc.
        return when {
            pathLength <= 1 -> 1.0
            pathLength == 2 -> 0.8
            pathLength == 3 -> 0.6
            pathLength == 4 -> 0.4
            else -> maxOf(0.1, 1.0 - (pathLength - 1) * 0.2)
        }
    }

    /**
     * Adds a trust relationship between two DIDs.
     * This allows building a trust graph beyond just self-trust.
     */
    fun addTrustRelationship(fromDid: String, toDid: String) {
        trustGraph.getOrPut(fromDid) { mutableSetOf() }.add(toDid)
    }

    /**
     * Clears all trust anchors and relationships.
     * Useful for testing.
     */
    fun clear() {
        trustAnchors.clear()
        trustGraph.clear()
    }
}

