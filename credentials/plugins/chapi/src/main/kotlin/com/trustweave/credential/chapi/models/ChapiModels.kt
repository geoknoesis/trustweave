package com.trustweave.credential.chapi.models

import com.trustweave.credential.exchange.CredentialPreview
import com.trustweave.credential.exchange.RequestedAttribute
import com.trustweave.credential.exchange.RequestedPredicate
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation
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
    val requestedAttributes: Map<String, RequestedAttribute>,
    val requestedPredicates: Map<String, RequestedPredicate>,
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

