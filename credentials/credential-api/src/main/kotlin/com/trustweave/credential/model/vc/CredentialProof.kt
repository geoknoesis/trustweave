package com.trustweave.credential.model.vc

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.JsonElement
import kotlinx.datetime.Instant

/**
 * VC Proof - format-specific (VC-LD, VC-JWT, SD-JWT-VC).
 * 
 * Per W3C VC Data Model, proof can be in different formats:
 * - Linked Data Proofs (VC-LD) - JSON-LD proof structure
 * - JWT (VC-JWT) - JWT compact serialization
 * - SD-JWT-VC - Selective Disclosure JWT VC
 */
@Serializable
sealed class CredentialProof {
    /**
     * VC-LD (Linked Data Proofs) - JSON-LD proof structure.
     * 
     * Used for Verifiable Credentials with Linked Data Proofs.
     */
    @Serializable
    data class LinkedDataProof(
        val type: String,  // e.g., "Ed25519Signature2020", "JsonWebSignature2020"
        @Contextual val created: Instant,
        val verificationMethod: String,  // DID URL or IRI
        val proofPurpose: String,  // e.g., "assertionMethod", "authentication"
        val proofValue: String,  // Base58/Base64 encoded signature
        val additionalProperties: Map<String, JsonElement> = emptyMap()
    ) : CredentialProof()
    
    /**
     * VC-JWT - JWT compact serialization.
     * 
     * Used for Verifiable Credentials encoded as JWTs.
     */
    @Serializable
    data class JwtProof(
        val jwt: String  // Compact JWT string
    ) : CredentialProof()
    
    /**
     * SD-JWT-VC - Selective Disclosure JWT VC.
     * 
     * Used for Verifiable Credentials with selective disclosure capabilities.
     */
    @Serializable
    data class SdJwtVcProof(
        val sdJwtVc: String,  // SD-JWT-VC compact format
        val disclosures: List<String>? = null  // Optional disclosures
    ) : CredentialProof()
}

