package org.trustweave.credential.format

import kotlinx.serialization.Serializable

/**
 * Proof suite identifier.
 *
 * Identifies the proof suite that a ProofEngine handles.
 * Each ProofEngine is registered for a specific proof suite (e.g., VC_LD, SD_JWT_VC).
 * All proof suites are built-in and always available.
 *
 * **Example:**
 * ```kotlin
 * // Use enum values directly
 * val vcLdSuite = ProofSuiteId.VC_LD
 * val sdJwtVcSuite = ProofSuiteId.SD_JWT_VC
 * ```
 */
@Serializable
enum class ProofSuiteId(val value: String) {
    /** W3C Verifiable Credential with Linked Data Proofs (VC-LD). */
    VC_LD("vc-ld"),
    
    /** W3C Verifiable Credential as JWT (VC-JWT). */
    VC_JWT("vc-jwt"),
    
    /** IETF SD-JWT-VC (Selective Disclosure JWT Verifiable Credential). */
    SD_JWT_VC("sd-jwt-vc");

    override fun toString(): String = value
    
    companion object {
        /**
         * Parse a string to a ProofSuiteId enum value.
         * 
         * @throws IllegalArgumentException if the string doesn't match any known proof suite
         */
        fun fromString(value: String): ProofSuiteId {
            return values().firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unknown proof suite ID: $value")
        }
    }
}
