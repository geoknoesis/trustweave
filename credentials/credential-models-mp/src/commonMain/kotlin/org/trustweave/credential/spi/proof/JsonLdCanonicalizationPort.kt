package org.trustweave.credential.spi.proof

import kotlinx.serialization.json.JsonObject

/**
 * Port interface for JSON-LD document canonicalization.
 *
 * Abstracts the JSON-LD canonicalization infrastructure from the credential domain.
 * Implementations may use any JSON-LD library (e.g., jsonld-java) or a fallback
 * JSON serialization strategy.
 */
interface JsonLdCanonicalizationPort {
    /**
     * Canonicalize a JSON-LD document to a deterministic string representation.
     *
     * @param document The JSON-LD document to canonicalize
     * @return Canonical string form (typically N-Quads format)
     * @throws IllegalArgumentException if the canonicalized form exceeds size limits
     */
    fun canonicalize(document: JsonObject): String
}
