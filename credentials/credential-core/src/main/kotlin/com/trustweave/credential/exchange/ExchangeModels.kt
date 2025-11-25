package com.trustweave.credential.exchange

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation

/**
 * Credential preview for offer messages.
 */
data class CredentialPreview(
    val attributes: List<CredentialAttribute>,
    val goalCode: String? = null,
    val replacementId: String? = null
)

/**
 * Credential attribute in preview.
 */
data class CredentialAttribute(
    val name: String,
    val value: String,
    val mimeType: String? = null
)

/**
 * Request for creating a credential offer.
 */
data class CredentialOfferRequest(
    val issuerDid: String,
    val holderDid: String,
    val credentialPreview: CredentialPreview,
    val options: Map<String, Any?> = emptyMap()
)

/**
 * Response from credential offer operation.
 */
data class CredentialOfferResponse(
    val offerId: String,
    val offerData: Any, // Protocol-specific format (DidCommMessage, OIDC4VCI offer, etc.)
    val protocolName: String
)

/**
 * Request for requesting a credential.
 */
data class CredentialRequestRequest(
    val holderDid: String,
    val issuerDid: String,
    val offerId: String, // Reference to the offer
    val options: Map<String, Any?> = emptyMap()
)

/**
 * Response from credential request operation.
 */
data class CredentialRequestResponse(
    val requestId: String,
    val requestData: Any, // Protocol-specific format
    val protocolName: String
)

/**
 * Request for issuing a credential.
 */
data class CredentialIssueRequest(
    val issuerDid: String,
    val holderDid: String,
    val credential: VerifiableCredential,
    val requestId: String, // Reference to the request
    val options: Map<String, Any?> = emptyMap()
)

/**
 * Response from credential issue operation.
 */
data class CredentialIssueResponse(
    val issueId: String,
    val credential: VerifiableCredential,
    val issueData: Any, // Protocol-specific format
    val protocolName: String
)

/**
 * Requested attribute in proof request.
 */
data class RequestedAttribute(
    val name: String,
    val restrictions: List<AttributeRestriction> = emptyList()
)

/**
 * Requested predicate in proof request.
 */
data class RequestedPredicate(
    val name: String,
    val pType: String, // e.g., ">=", "<=", "=="
    val pValue: Int,
    val restrictions: List<AttributeRestriction> = emptyList()
)

/**
 * Attribute restriction for proof requests.
 */
data class AttributeRestriction(
    val issuerDid: String? = null,
    val schemaId: String? = null,
    val credentialDefinitionId: String? = null
)

/**
 * Request for requesting a proof.
 */
data class ProofRequestRequest(
    val verifierDid: String,
    val proverDid: String,
    val name: String,
    val version: String = "1.0",
    val requestedAttributes: Map<String, RequestedAttribute>,
    val requestedPredicates: Map<String, RequestedPredicate> = emptyMap(),
    val goalCode: String? = null,
    val options: Map<String, Any?> = emptyMap()
)

/**
 * Response from proof request operation.
 */
data class ProofRequestResponse(
    val requestId: String,
    val requestData: Any, // Protocol-specific format
    val protocolName: String
)

/**
 * Request for presenting a proof.
 */
data class ProofPresentationRequest(
    val proverDid: String,
    val verifierDid: String,
    val presentation: VerifiablePresentation,
    val requestId: String, // Reference to the proof request
    val options: Map<String, Any?> = emptyMap()
)

/**
 * Response from proof presentation operation.
 */
data class ProofPresentationResponse(
    val presentationId: String,
    val presentation: VerifiablePresentation,
    val presentationData: Any, // Protocol-specific format
    val protocolName: String
)

