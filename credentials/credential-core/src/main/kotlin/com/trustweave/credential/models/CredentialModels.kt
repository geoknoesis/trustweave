package com.trustweave.credential.models

import com.trustweave.credential.SchemaFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Core credential types - W3C VC 1.1 compliant.
 * 
 * These models are provider-agnostic and can be used with any credential service.
 */

/**
 * Verifiable Credential as defined by W3C VC Data Model 1.1.
 * 
 * @param id Optional unique identifier for the credential
 * @param type List of credential types (must include "VerifiableCredential")
 * @param issuer DID or URI of the credential issuer
 * @param credentialSubject The subject of the credential (as JSON)
 * @param issuanceDate ISO 8601 date string when credential was issued
 * @param expirationDate Optional ISO 8601 date string when credential expires
 * @param credentialStatus Optional credential status information (for revocation)
 * @param credentialSchema Optional schema reference for validation
 * @param evidence Optional list of evidence supporting the credential
 * @param proof Optional cryptographic proof (signature)
 * @param termsOfUse Optional terms of use for the credential
 * @param refreshService Optional refresh service for expiring credentials
 */
@Serializable
data class VerifiableCredential(
    val id: String? = null,
    val type: List<String>,
    val issuer: String, // DID or URI
    val credentialSubject: JsonElement,
    val issuanceDate: String,
    val expirationDate: String? = null,
    val credentialStatus: CredentialStatus? = null,
    val credentialSchema: CredentialSchema? = null,
    val evidence: List<Evidence>? = null,
    val proof: Proof? = null,
    val termsOfUse: TermsOfUse? = null,
    val refreshService: RefreshService? = null
)

/**
 * Credential schema reference.
 * 
 * @param id URI or DID of the schema
 * @param type Schema validator type (e.g., "JsonSchemaValidator2018", "ShaclValidator2020")
 * @param schemaFormat Format of the schema (JSON_SCHEMA or SHACL)
 */
@Serializable
data class CredentialSchema(
    val id: String,
    val type: String, // "JsonSchemaValidator2018" or "ShaclValidator2020"
    val schemaFormat: SchemaFormat = SchemaFormat.JSON_SCHEMA
)

/**
 * Credential status information.
 * Used for revocation and suspension status.
 * 
 * @param id URI of the status list credential
 * @param type Status list type (e.g., "StatusList2021Entry", "RevocationList2020")
 * @param statusPurpose Purpose of the status (e.g., "revocation", "suspension")
 * @param statusListIndex Index in the status list
 * @param statusListCredential URI of the status list credential
 */
@Serializable
data class CredentialStatus(
    val id: String,
    val type: String, // StatusList2021Entry, RevocationList2020, etc.
    val statusPurpose: String? = "revocation",
    val statusListIndex: String? = null,
    val statusListCredential: String? = null
)

/**
 * Cryptographic proof for a verifiable credential.
 * 
 * @param type Proof type (e.g., "Ed25519Signature2020", "JsonWebSignature2020", "BbsBlsSignature2020")
 * @param created ISO 8601 timestamp when proof was created
 * @param verificationMethod DID URL or key reference for verification
 * @param proofPurpose Purpose of the proof (e.g., "assertionMethod", "authentication")
 * @param proofValue Optional proof value (for JSON-LD proofs)
 * @param jws Optional JWS string (for JWT proofs)
 * @param challenge Optional challenge string (for authentication)
 * @param domain Optional domain string (for authentication)
 */
@Serializable
data class Proof(
    val type: String, // Ed25519Signature2020, JsonWebSignature2020, BbsBlsSignature2020
    val created: String,
    val verificationMethod: String,
    val proofPurpose: String,
    val proofValue: String? = null,
    val jws: String? = null,
    val challenge: String? = null,
    val domain: String? = null
)

/**
 * Evidence supporting a credential claim.
 * 
 * @param id Optional identifier for the evidence
 * @param type Type of evidence (e.g., "DocumentVerification", "IdentityDocument")
 * @param evidenceDocument Optional document reference
 * @param subject Optional subject of the evidence
 */
@Serializable
data class Evidence(
    val id: String? = null,
    val type: List<String>,
    val evidenceDocument: JsonElement? = null,
    val verifier: String? = null,
    val evidenceDate: String? = null
)

/**
 * Terms of use for a credential.
 * 
 * @param id Optional identifier for the terms
 * @param type Type of terms (e.g., "IssuerPolicy", "HolderPolicy")
 * @param instance Optional instance reference
 */
@Serializable
data class TermsOfUse(
    val id: String? = null,
    val type: String? = null,
    val termsOfUse: JsonElement
)

/**
 * Refresh service for expiring credentials.
 * 
 * @param id URI of the refresh service
 * @param type Type of refresh service
 */
@Serializable
data class RefreshService(
    val id: String,
    val type: String,
    val serviceEndpoint: String
)

