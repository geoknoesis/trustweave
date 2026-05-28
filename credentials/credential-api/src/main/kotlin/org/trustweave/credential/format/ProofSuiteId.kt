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
    SD_JWT_VC("sd-jwt-vc"),

    /** ISO 18013-5 mDL / mDoc (CBOR/COSE). OID4VCI/OID4VP format identifier: mso_mdoc */
    MDOC("mso_mdoc"),

    /** W3C Data Integrity BBS Cryptosuite 2023 (selective disclosure + ZK proofs). */
    BBS_2023("bbs-2023"),

    /**
     * ETSI TS 119 182-1 JAdES (JSON Advanced Electronic Signatures).
     *
     * eIDAS 2.0 qualified-signature wrapper around a W3C VC JSON payload. The
     * [org.trustweave.credential.model.vc.CredentialProof.JAdES] variant stores the produced JWS
     * (JSON Flattened or Compact) verbatim. Supported profiles: B-B (basic) and B-T (basic +
     * signature-time-stamp). See `signatures:jades` for the implementation and
     * `docs/architecture/eidas-qes-design.md` for the design rationale.
     */
    JADES("jades");

    override fun toString(): String = value
    
    companion object {
        /**
         * Parse a string to a ProofSuiteId enum value.
         * 
         * @throws IllegalArgumentException if the string doesn't match any known proof suite
         */
        fun fromString(value: String): ProofSuiteId {
            return entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unknown proof suite ID: $value")
        }
    }
}
