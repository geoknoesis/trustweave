package org.trustweave.trust.dsl

import kotlinx.datetime.Instant

/**
 * Builder for trust anchor configuration using infix syntax.
 * 
 * Used with the `trusts` infix operator to build trust anchor metadata.
 * 
 * **Example:**
 * ```kotlin
 * trustWeave.trust {
 *     universityDid trusts "EducationCredential" because {
 *         description("Trusted university")
 *     }
 * }
 * ```
 */
class TrustAnchorBuilder {
    var credentialTypes: List<String>? = null
    var description: String? = null
    var addedAt: Instant? = null
    
    /**
     * Set credential types.
     */
    fun credentialTypes(type: String) {
        credentialTypes = listOf(type)
    }
    
    /**
     * Set credential types.
     */
    fun credentialTypes(types: List<String>) {
        credentialTypes = types
    }
    
    /**
     * Set description.
     */
    fun description(desc: String) {
        description = desc
    }
    
    /**
     * Set added at timestamp.
     */
    fun addedAt(instant: Instant) {
        addedAt = instant
    }
}

