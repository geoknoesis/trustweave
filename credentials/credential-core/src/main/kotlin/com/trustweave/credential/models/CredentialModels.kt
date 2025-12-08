package com.trustweave.credential.models

import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.IssuerId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.model.ProofType
import com.trustweave.did.identifiers.VerificationMethodId
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
 * @param id Optional unique identifier for the credential (typed)
 * @param type List of credential types (must include "VerifiableCredential") (typed)
 * @param issuer DID or URI of the credential issuer (typed)
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
    val id: CredentialId? = null,
    val type: List<CredentialType>,
    val issuer: IssuerId,
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
 * @param id URI or DID of the schema (typed)
 * @param type Schema validator type (e.g., "JsonSchemaValidator2018", "ShaclValidator2020")
 * @param schemaFormat Format of the schema (JSON_SCHEMA or SHACL) (typed)
 */
@Serializable
data class CredentialSchema(
    val id: SchemaId,
    val type: String, // "JsonSchemaValidator2018" or "ShaclValidator2020"
    val schemaFormat: SchemaFormat = SchemaFormat.JSON_SCHEMA
)

/**
 * Credential status information.
 * Used for revocation and suspension status.
 *
 * @param id URI of the status list credential (typed)
 * @param type Status list type (e.g., "StatusList2021Entry", "RevocationList2020")
 * @param statusPurpose Purpose of the status (e.g., "revocation", "suspension") (typed)
 * @param statusListIndex Index in the status list
 * @param statusListCredential URI of the status list credential (typed)
 */
@Serializable
data class CredentialStatus(
    val id: StatusListId,
    val type: String, // StatusList2021Entry, RevocationList2020, etc.
    val statusPurpose: StatusPurpose = StatusPurpose.REVOCATION,
    val statusListIndex: String? = null,
    val statusListCredential: StatusListId? = null
)

/**
 * Cryptographic proof for a verifiable credential.
 *
 * @param type Proof type (e.g., "Ed25519Signature2020", "JsonWebSignature2020", "BbsBlsSignature2020") (typed)
 * @param created ISO 8601 timestamp when proof was created
 * @param verificationMethod DID URL or key reference for verification (typed)
 * @param proofPurpose Purpose of the proof (e.g., "assertionMethod", "authentication")
 * @param proofValue Optional proof value (for JSON-LD proofs)
 * @param jws Optional JWS string (for JWT proofs)
 * @param challenge Optional challenge string (for authentication)
 * @param domain Optional domain string (for authentication)
 */
@Serializable
data class Proof(
    val type: ProofType,
    val created: String,
    val verificationMethod: VerificationMethodId,
    val proofPurpose: String,
    val proofValue: String? = null,
    val jws: String? = null,
    val challenge: String? = null,
    val domain: String? = null
)

/**
 * Evidence supporting a credential claim.
 *
 * @param id Optional identifier for the evidence (typed)
 * @param type Type of evidence (e.g., "DocumentVerification", "IdentityDocument")
 * @param evidenceDocument Optional document reference
 * @param verifier Optional verifier of the evidence (typed)
 * @param evidenceDate Optional date when evidence was created
 */
@Serializable
data class Evidence(
    val id: CredentialId? = null,
    val type: List<String>,
    val evidenceDocument: JsonElement? = null,
    val verifier: IssuerId? = null,
    val evidenceDate: String? = null
)

/**
 * Terms of use for a credential.
 *
 * @param id Optional identifier for the terms (typed)
 * @param type Type of terms (e.g., "IssuerPolicy", "HolderPolicy")
 * @param termsOfUse Terms of use JSON element
 */
@Serializable
data class TermsOfUse(
    val id: CredentialId? = null,
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

