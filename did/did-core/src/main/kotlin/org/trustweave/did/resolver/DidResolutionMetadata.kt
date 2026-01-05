package org.trustweave.did.resolver

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

/**
 * Structured resolution metadata following W3C DID Core specification.
 *
 * This replaces the generic Map<String, Any?> in DidResolutionResult to provide
 * type-safe access to resolution metadata fields.
 *
 * **W3C DID Core Compliance:**
 * All fields align with the DID Resolution specification requirements.
 *
 * **Example Usage:**
 * ```kotlin
 * val metadata = DidResolutionMetadata(
 *     contentType = "application/did+ld+json",
 *     duration = 150L,
 *     retrieved = Clock.System.now()
 * )
 * ```
 */
@Serializable
data class DidResolutionMetadata(
    /**
     * Content type of the resolved document (REQUIRED per W3C spec).
     * Default: "application/did+ld+json"
     */
    val contentType: String = "application/did+ld+json",
    
    /**
     * Error code if resolution failed (REQUIRED if error occurred).
     */
    val error: String? = null,
    
    /**
     * Human-readable error message (REQUIRED if error occurred).
     */
    val errorMessage: String? = null,
    
    /**
     * Pattern used for resolution (e.g., "did:web", "did:key").
     */
    val pattern: String? = null,
    
    /**
     * Driver URL if Universal Resolver was used.
     */
    val driverUrl: String? = null,
    
    /**
     * Resolution duration in milliseconds.
     */
    val duration: Long? = null,
    
    /**
     * Timestamp when document was retrieved.
     */
    @Contextual val retrieved: Instant? = null,
    
    /**
     * Canonical ID if different from requested DID.
     */
    val canonicalId: String? = null,
    
    /**
     * List of equivalent DID identifiers.
     */
    val equivalentId: List<String> = emptyList(),
    
    /**
     * Timestamp indicating when to check for next update.
     */
    @Contextual val nextUpdate: Instant? = null,
    
    /**
     * Next version ID for versioned DIDs.
     */
    val nextVersionId: String? = null,
    
    /**
     * Additional properties for extensibility.
     */
    val properties: Map<String, String> = emptyMap()
) {
    /**
     * Creates metadata from a map (for backward compatibility).
     */
    companion object {
        fun fromMap(map: Map<String, Any?>): DidResolutionMetadata {
            return DidResolutionMetadata(
                contentType = (map["contentType"] as? String) ?: "application/did+ld+json",
                error = map["error"] as? String,
                errorMessage = map["errorMessage"] as? String,
                pattern = map["pattern"] as? String,
                driverUrl = map["driverUrl"] as? String,
                duration = (map["duration"] as? Number)?.toLong(),
                retrieved = (map["retrieved"] as? String)?.let { 
                    kotlinx.datetime.Instant.parse(it) 
                },
                canonicalId = map["canonicalId"] as? String,
                equivalentId = (map["equivalentId"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                nextUpdate = (map["nextUpdate"] as? String)?.let { 
                    kotlinx.datetime.Instant.parse(it) 
                },
                nextVersionId = map["nextVersionId"] as? String,
                properties = (map["properties"] as? Map<*, *>)?.mapNotNull { 
                    (it.key as? String)?.let { key -> 
                        key to (it.value?.toString() ?: "")
                    }
                }?.toMap() ?: emptyMap()
            )
        }
    }
    
    /**
     * Converts to map (for backward compatibility).
     */
    fun toMap(): Map<String, Any?> {
        return buildMap {
            put("contentType", contentType)
            error?.let { put("error", it) }
            errorMessage?.let { put("errorMessage", it) }
            pattern?.let { put("pattern", it) }
            driverUrl?.let { put("driverUrl", it) }
            duration?.let { put("duration", it) }
            retrieved?.let { put("retrieved", it.toString()) }
            canonicalId?.let { put("canonicalId", it) }
            if (equivalentId.isNotEmpty()) put("equivalentId", equivalentId)
            nextUpdate?.let { put("nextUpdate", it.toString()) }
            nextVersionId?.let { put("nextVersionId", it) }
            if (properties.isNotEmpty()) put("properties", properties)
        }
    }
}

