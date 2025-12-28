package org.trustweave.credential.internal

/**
 * Constants used throughout the credential API module.
 * 
 * Centralizes magic strings and values to improve maintainability and reduce errors.
 */
internal object CredentialConstants {
    /**
     * Proof type constants.
     */
    object ProofTypes {
        /** Ed25519Signature2020 proof suite identifier. */
        const val ED25519_SIGNATURE_2020 = "Ed25519Signature2020"
    }
    
    /**
     * Proof purpose constants (W3C VC Data Model).
     */
    object ProofPurposes {
        /** Assertion method proof purpose - standard for credential issuance. */
        const val ASSERTION_METHOD = "assertionMethod"
        
        /** Authentication proof purpose. */
        const val AUTHENTICATION = "authentication"
    }
    
    /**
     * JSON-LD format constants.
     */
    object JsonLdFormats {
        /** N-Quads format for JSON-LD canonicalization. */
        const val N_QUADS = "application/n-quads"
    }
    
    /**
     * W3C VC Context URIs.
     */
    object VcContexts {
        /** W3C Verifiable Credentials 1.1 context. */
        const val VC_1_1 = "https://www.w3.org/2018/credentials/v1"
        
        /** W3C Verifiable Credentials 2.0 context. */
        const val VC_2_0 = "https://www.w3.org/ns/credentials/v2"
    }
    
    /**
     * Security suite context URIs.
     */
    object SecuritySuites {
        /** Ed25519 Signature Suite 2020 context. */
        const val ED25519_2020_V1 = "https://w3id.org/security/suites/ed25519-2020/v1"
    }
    
    /**
     * Default timeouts and limits for operations.
     */
    object OperationLimits {
        /** Default timeout for DID resolution (in milliseconds). */
        const val DEFAULT_DID_RESOLUTION_TIMEOUT_MS = 5000L
        
        /** Default timeout for revocation status checks (in milliseconds). */
        const val DEFAULT_REVOCATION_CHECK_TIMEOUT_MS = 3000L
        
        /** Default timeout for schema validation (in milliseconds). */
        const val DEFAULT_SCHEMA_VALIDATION_TIMEOUT_MS = 5000L
        
        /** Maximum retry attempts for transient failures. */
        const val MAX_RETRY_ATTEMPTS = 3
    }
}

