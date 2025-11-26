package com.trustweave.trust.types

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
 * Proof Type.
 * 
 * Represents a cryptographic proof/signature type used in verifiable credentials.
 * 
 * **Example:**
 * ```kotlin
 * val proofType = ProofType.Ed25519Signature2020
 * ```
 */
sealed class ProofType(val value: String) {
    object Ed25519Signature2020 : ProofType("Ed25519Signature2020")
    object JsonWebSignature2020 : ProofType("JsonWebSignature2020")
    object EcdsaSecp256k1Signature2019 : ProofType("EcdsaSecp256k1Signature2019")
    object BbsBlsSignature2020 : ProofType("BbsBlsSignature2020")
    
    /**
     * Create a ProofType from a string value.
     * 
     * @return ProofType if valid, null otherwise
     */
    companion object {
        fun fromString(value: String): ProofType? = when (value) {
            Ed25519Signature2020.value -> Ed25519Signature2020
            JsonWebSignature2020.value -> JsonWebSignature2020
            EcdsaSecp256k1Signature2019.value -> EcdsaSecp256k1Signature2019
            BbsBlsSignature2020.value -> BbsBlsSignature2020
            else -> null
        }
        
        /**
         * Get all supported proof types.
         */
        fun all(): List<ProofType> = listOf(
            Ed25519Signature2020,
            JsonWebSignature2020,
            EcdsaSecp256k1Signature2019,
            BbsBlsSignature2020
        )
    }
    
    override fun toString(): String = value
}

/**
 * Credential Type.
 * 
 * Represents a type of verifiable credential.
 * 
 * **Example:**
 * ```kotlin
 * val type = CredentialType.Education
 * ```
 */
sealed class CredentialType(val value: String) {
    // Standard types
    object VerifiableCredential : CredentialType("VerifiableCredential")
    
    // Domain-specific types
    object Education : CredentialType("EducationCredential")
    object Employment : CredentialType("EmploymentCredential")
    object Certification : CredentialType("CertificationCredential")
    object Degree : CredentialType("DegreeCredential")
    object Person : CredentialType("PersonCredential")
    
    /**
     * Create a custom credential type.
     */
    data class Custom(val customValue: String) : CredentialType(customValue)
    
    companion object {
        /**
         * Create a CredentialType from a string value.
         */
        fun fromString(value: String): CredentialType = when (value) {
            VerifiableCredential.value -> VerifiableCredential
            Education.value -> Education
            Employment.value -> Employment
            Certification.value -> Certification
            Degree.value -> Degree
            Person.value -> Person
            else -> Custom(value)
        }
    }
    
    override fun toString(): String = value
}

/**
 * Issuer Identity.
 * 
 * Bundles a DID and key ID together, representing a complete issuer identity
 * for credential signing operations.
 * 
 * **Example:**
 * ```kotlin
 * val issuer = IssuerIdentity(
 *     did = Did("did:key:z6Mk..."),
 *     keyId = KeyId("key-1")
 * )
 * ```
 */
data class IssuerIdentity(
    val did: Did,
    val keyId: KeyId
) {
    /**
     * Get the full verification method ID.
     * Format: `{did}#{keyId}`
     */
    val verificationMethodId: String
        get() = if (keyId.isFull) {
            keyId.value
        } else {
            "${did.value}#${keyId.value}"
        }
    
    companion object {
        /**
         * Create an IssuerIdentity from a DID and key ID string.
         */
        fun from(did: String, keyId: String): IssuerIdentity {
            return IssuerIdentity(
                did = Did(did),
                keyId = KeyId(keyId)
            )
        }
    }
}

