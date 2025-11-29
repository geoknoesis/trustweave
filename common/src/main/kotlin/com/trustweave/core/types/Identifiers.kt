package com.trustweave.core.types

/**
 * Type-safe identifier types for TrustWeave API.
 *
 * These inline classes provide compile-time type safety and validation
 * for Web-of-Trust identifiers, preventing common errors like:
 * - Passing a DID where a KeyId is expected
 * - Using invalid identifier formats
 * - Missing validation
 */

/**
 * Decentralized Identifier (DID).
 *
 * Represents a self-sovereign identifier following the `did:method:identifier` pattern.
 *
 * **Example:**
 * ```kotlin
 * val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
 * ```
 */
@JvmInline
value class Did(val value: String) {
    init {
        require(value.startsWith("did:")) {
            "Invalid DID format: '$value'. DIDs must start with 'did:'"
        }
        require(value.split(":").size >= 3) {
            "Invalid DID format: '$value'. Expected format: did:method:identifier"
        }
    }

    /**
     * Get the DID method from this DID.
     */
    val method: String
        get() = value.substringAfter("did:").substringBefore(":")

    /**
     * Get the method-specific identifier.
     */
    val identifier: String
        get() = value.substringAfter("${method}:")

    override fun toString(): String = value
}

/**
 * Key Identifier.
 *
 * Represents a cryptographic key identifier, typically in the format
 * `did:method:identifier#key-id` or just `key-id` if scoped to a DID.
 *
 * **Example:**
 * ```kotlin
 * val keyId = KeyId("did:key:z6Mk...#key-1")
 * // OR
 * val keyId = KeyId("key-1")  // Relative to a DID
 * ```
 */
@JvmInline
value class KeyId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "Key ID cannot be blank"
        }
    }

    /**
     * Check if this is a full key ID (includes DID prefix).
     */
    val isFull: Boolean
        get() = value.contains("#") || value.startsWith("did:")

    /**
     * Get the fragment part (after #) if present.
     */
    val fragment: String?
        get() = value.substringAfter("#", "")
            .takeIf { it.isNotEmpty() }

    override fun toString(): String = value
}

/**
 * Credential Identifier.
 *
 * Represents a unique identifier for a verifiable credential.
 * Typically a URI or UUID following the credential's `id` field format.
 *
 * **Example:**
 * ```kotlin
 * val credentialId = CredentialId("https://example.com/credentials/123")
 * // OR
 * val credentialId = CredentialId("urn:uuid:550e8400-e29b-41d4-a716-446655440000")
 * ```
 */
@JvmInline
value class CredentialId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "Credential ID cannot be blank"
        }
        // Validate URI format (optional but recommended)
        require(value.startsWith("http://") || 
                value.startsWith("https://") || 
                value.startsWith("urn:") ||
                value.startsWith("did:") ||
                value.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            "Credential ID should be a valid URI, URN, DID, or identifier: '$value'"
        }
    }

    /**
     * Check if this is a URI-based credential ID.
     */
    val isUri: Boolean
        get() = value.startsWith("http://") || value.startsWith("https://")

    /**
     * Check if this is a URN-based credential ID.
     */
    val isUrn: Boolean
        get() = value.startsWith("urn:")

    /**
     * Check if this is a DID-based credential ID.
     */
    val isDid: Boolean
        get() = value.startsWith("did:")

    override fun toString(): String = value
}

