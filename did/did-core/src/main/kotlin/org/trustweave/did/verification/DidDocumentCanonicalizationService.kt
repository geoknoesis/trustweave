package org.trustweave.did.verification

import org.trustweave.did.model.DidDocument
import java.security.MessageDigest
import java.util.Base64

/**
 * DID Document Canonicalization Service.
 *
 * Implements JSON-LD canonicalization per W3C specification (URDNA2015 algorithm).
 * Used for computing deterministic hashes of DID documents for integrity verification.
 *
 * **Canonicalization Purpose:**
 * - Ensures consistent representation of documents
 * - Enables deterministic hash computation
 * - Supports integrity verification
 * - Enables document comparison
 *
 * **Example Usage:**
 * ```kotlin
 * val canonicalizationService = JsonLdCanonicalizationService()
 *
 * // Canonicalize document
 * val canonical = canonicalizationService.canonicalize(document)
 *
 * // Compute digest
 * val digest = canonicalizationService.computeDigest(document)
 * ```
 */
interface DidDocumentCanonicalizationService {
    /**
     * Canonicalize DID document using URDNA2015 algorithm.
     *
     * @param document The DID document to canonicalize
     * @return Canonicalized JSON-LD string
     */
    suspend fun canonicalize(document: DidDocument): String
    
    /**
     * Compute document digest for integrity verification.
     *
     * Uses SHA-256 hash of canonicalized document, encoded in multibase format.
     *
     * @param document The DID document
     * @return Multibase-encoded digest (e.g., "zQm...")
     */
    suspend fun computeDigest(document: DidDocument): String
    
    /**
     * Normalize document for comparison.
     *
     * Removes null values, sorts arrays, etc. for consistent comparison.
     *
     * @param document The DID document
     * @return Normalized document
     */
    suspend fun normalize(document: DidDocument): DidDocument
}

/**
 * JSON-LD canonicalization implementation.
 *
 * **Note**: Full URDNA2015 implementation requires a JSON-LD library.
 * This is a simplified implementation that provides basic canonicalization.
 * For production use, consider integrating with a full JSON-LD library.
 */
class JsonLdCanonicalizationService : DidDocumentCanonicalizationService {
    
    override suspend fun canonicalize(document: DidDocument): String {
        // Simplified canonicalization
        // Full implementation would use URDNA2015 algorithm from JSON-LD spec
        // For now, we use a deterministic JSON serialization
        
        val normalized = normalize(document)
        
        // Serialize to JSON with deterministic ordering
        // In production, use a proper JSON-LD canonicalization library
        return kotlinx.serialization.json.Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = false
        }.encodeToString(
            org.trustweave.did.model.DidDocument.serializer(),
            normalized
        )
    }
    
    override suspend fun computeDigest(document: DidDocument): String {
        val canonical = canonicalize(document)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        
        // Multibase encoding (base58btc)
        // Simplified - full implementation would use proper multibase library
        return "z" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
    
    override suspend fun normalize(document: DidDocument): DidDocument {
        // Normalize: sort arrays, remove duplicates, etc.
        return document.copy(
            verificationMethod = document.verificationMethod
                .sortedBy { it.id.value },
            service = document.service
                .sortedBy { it.id },
            authentication = document.authentication
                .sortedBy { it.value },
            assertionMethod = document.assertionMethod
                .sortedBy { it.value },
            keyAgreement = document.keyAgreement
                .sortedBy { it.value },
            capabilityInvocation = document.capabilityInvocation
                .sortedBy { it.value },
            capabilityDelegation = document.capabilityDelegation
                .sortedBy { it.value }
        )
    }
}

