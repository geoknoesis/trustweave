package com.trustweave.credential.chapi.models

import com.trustweave.credential.exchange.model.CredentialPreview
import com.trustweave.credential.exchange.request.AttributeRequest
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import kotlinx.serialization.json.JsonObject

/**
 * CHAPI credential offer.
 */
data class ChapiOffer(
    val offerId: String,
    val issuerDid: String,
    val credentialPreview: CredentialPreview,
    val chapiMessage: JsonObject
)

/**
 * CHAPI credential store result.
 */
data class ChapiStoreResult(
    val credentialId: String,
    val holderDid: String,
    val credential: VerifiableCredential,
    val chapiMessage: JsonObject
)

/**
 * CHAPI proof request.
 */
data class ChapiProofRequest(
    val requestId: String,
    val verifierDid: String,
    val requestedAttributes: Map<String, AttributeRequest>,
    val requestedPredicates: Map<String, AttributeRequest>, // Predicates are now AttributeRequest with restrictions
    val chapiMessage: JsonObject
)

/**
 * CHAPI presentation result.
 */
data class ChapiPresentationResult(
    val presentationId: String,
    val presentation: VerifiablePresentation,
    val verifierDid: String,
    val chapiMessage: JsonObject
)

