package org.trustweave.credential.internal

/**
 * Security-related constants for input validation and resource limits.
 *
 * These constants help prevent denial-of-service (DoS) attacks and ensure system stability
 * by enforcing reasonable limits on input sizes and operation counts. All limits are set
 * conservatively to accommodate realistic use cases while preventing resource exhaustion.
 *
 * **Security Rationale:**
 *
 * **Size Limits (MAX_*_SIZE_BYTES):**
 * - Prevent memory exhaustion from extremely large inputs
 * - Protect against malicious actors sending oversized payloads
 * - Ensure reasonable processing times
 * - Based on typical credential/presentation sizes (most are < 10KB)
 *
 * **Count Limits (MAX_*_PER_*):**
 * - Prevent CPU exhaustion from processing too many items
 * - Limit concurrent operations to manageable levels
 * - Based on typical use cases (most credentials have < 50 claims)
 *
 * **Length Limits (MAX_*_LENGTH):**
 * - Prevent issues with extremely long identifiers
 * - Based on specifications (DIDs are typically 20-200 chars)
 * - Include generous safety margins for future-proofing
 *
 * **Usage:**
 * These constants are used by `InputValidation` and other validation utilities
 * throughout the credential API to enforce security boundaries.
 */
object SecurityConstants {
    /**
     * Maximum size for credential JSON (in bytes).
     *
     * Prevents denial-of-service attacks through extremely large credentials.
     * Default: 1MB, which is more than sufficient for any realistic credential.
     */
    const val MAX_CREDENTIAL_SIZE_BYTES = 1 * 1024 * 1024 // 1MB

    /**
     * Maximum size for presentation JSON (in bytes).
     *
     * Presentations can contain multiple credentials, so this limit is higher.
     * Default: 5MB to accommodate multiple credentials.
     */
    const val MAX_PRESENTATION_SIZE_BYTES = 5 * 1024 * 1024 // 5MB

    /**
     * Maximum number of credentials in a presentation.
     *
     * Prevents resource exhaustion from processing too many credentials at once.
     */
    const val MAX_CREDENTIALS_PER_PRESENTATION = 100

    /**
     * Maximum number of claims in a credential subject.
     *
     * Prevents excessive claim processing.
     */
    const val MAX_CLAIMS_PER_CREDENTIAL = 1000

    /**
     * Maximum length for credential ID (in characters).
     *
     * Prevents issues with extremely long identifiers.
     */
    const val MAX_CREDENTIAL_ID_LENGTH = 500

    /**
     * Maximum length for DID string (in characters).
     *
     * DIDs typically range from 20-200 characters. This provides a generous safety margin.
     */
    const val MAX_DID_LENGTH = 1000

    /**
     * Maximum length for schema ID (in characters).
     */
    const val MAX_SCHEMA_ID_LENGTH = 500

    /**
     * Maximum length for credential status entry ID (in characters).
     * Matches the VARCHAR(255) column in the database schema.
     */
    const val MAX_STATUS_ID_LENGTH = 255

    /**
     * Maximum length for verification method ID (in characters).
     */
    const val MAX_VERIFICATION_METHOD_ID_LENGTH = 1000

    /**
     * Maximum size for canonicalized document during signature verification (in bytes).
     *
     * After JSON-LD canonicalization, documents should remain reasonable in size.
     */
    const val MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES = 2 * 1024 * 1024 // 2MB

    /**
     * Maximum size of a JSON-LD document accepted for canonicalization (in bytes), measured
     * before any expansion work is done.
     *
     * [MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES] bounds the *output*, which only helps after the
     * expensive step has already run. This bounds the input.
     */
    const val MAX_PRE_CANONICALIZATION_SIZE_BYTES = 2 * 1024 * 1024 // 2MB

    /**
     * Maximum number of blank nodes in a document accepted for canonicalization.
     *
     * RDFC-1.0 (URDNA2015) is super-linear in blank nodes: mutually-referencing blank nodes drive
     * the hash-n-degree-quads recursion, so a small document can buy a verifier a great deal of
     * work. Set above [MAX_CLAIMS_PER_CREDENTIAL] so a credential whose every claim is a nested
     * object still canonicalizes.
     */
    const val MAX_BLANK_NODES_PER_DOCUMENT = 1024

    /**
     * Maximum number of status list entries to check in a single operation.
     *
     * Prevents excessive revocation list processing.
     */
    const val MAX_STATUS_LIST_CHECK_SIZE = 10000

    /**
     * Ed25519 signature length in bytes.
     *
     * Ed25519 signatures are always exactly 64 bytes as per the Ed25519 specification.
     * Used for signature validation to ensure correct signature format.
     */
    const val ED25519_SIGNATURE_LENGTH_BYTES = 64
}
