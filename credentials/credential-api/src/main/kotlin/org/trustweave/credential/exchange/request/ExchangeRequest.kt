package org.trustweave.credential.exchange.request

import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.identifiers.*
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.did.identifiers.Did

/**
 * Protocol-agnostic exchange request.
 * 
 * Sealed class hierarchy for type-safe exchange operations.
 * Each operation has its own request type.
 */
sealed class ExchangeRequest {
    /**
     * Protocol name - type-safe protocol selection.
     */
    abstract val protocolName: ExchangeProtocolName
    
    abstract val issuerDid: Did
    abstract val holderDid: Did
    abstract val options: ExchangeOptions
    
    /**
     * Request to offer a credential.
     */
    data class Offer(
        override val protocolName: ExchangeProtocolName,
        override val issuerDid: Did,
        override val holderDid: Did,
        val credentialPreview: CredentialPreview,
        override val options: ExchangeOptions = ExchangeOptions.Empty
    ) : ExchangeRequest()
    
    /**
     * Request to request a credential (after receiving offer).
     */
    data class Request(
        override val protocolName: ExchangeProtocolName,
        override val holderDid: Did,
        override val issuerDid: Did,
        val offerId: OfferId,
        override val options: ExchangeOptions = ExchangeOptions.Empty
    ) : ExchangeRequest()
    
    /**
     * Request to issue a credential (after receiving request).
     */
    data class Issue(
        override val protocolName: ExchangeProtocolName,
        override val issuerDid: Did,
        override val holderDid: Did,
        val credential: VerifiableCredential,
        val requestId: RequestId,
        override val options: ExchangeOptions = ExchangeOptions.Empty
    ) : ExchangeRequest()
}

/**
 * Protocol-agnostic proof exchange request.
 */
sealed class ProofExchangeRequest {
    /**
     * Protocol name - type-safe protocol selection.
     */
    abstract val protocolName: ExchangeProtocolName
    
    abstract val verifierDid: Did
    abstract val proverDid: Did
    abstract val options: ExchangeOptions
    
    /**
     * Request to request a proof presentation.
     */
    data class Request(
        override val protocolName: ExchangeProtocolName,
        override val verifierDid: Did,
        override val proverDid: Did,
        val proofRequest: ProofRequest,
        override val options: ExchangeOptions = ExchangeOptions.Empty
    ) : ProofExchangeRequest()
    
    /**
     * Request to present a proof (after receiving proof request).
     */
    data class Presentation(
        override val protocolName: ExchangeProtocolName,
        override val proverDid: Did,
        override val verifierDid: Did,
        val presentation: VerifiablePresentation,
        val requestId: RequestId,
        override val options: ExchangeOptions = ExchangeOptions.Empty
    ) : ProofExchangeRequest()
}

/**
 * Protocol-agnostic proof request.
 * 
 * Protocol-specific concepts (predicates, goal codes, etc.) go in options.metadata.
 */
data class ProofRequest(
    val name: String,
    val version: String = "1.0",
    val requestedAttributes: Map<String, AttributeRequest>,
    val options: ExchangeOptions = ExchangeOptions.Empty  // Predicates, goal codes, etc. go here
)

/**
 * Protocol-agnostic attribute request.
 */
data class AttributeRequest(
    val name: String,
    val restrictions: List<AttributeRestriction> = emptyList(),
    val required: Boolean = true
)

/**
 * Protocol-agnostic attribute restriction.
 * 
 * Protocol-specific restrictions go in metadata.
 */
data class AttributeRestriction(
    val issuerDid: Did? = null,
    val schemaId: SchemaId? = null,
    val credentialType: List<org.trustweave.credential.model.CredentialType>? = null,
    val metadata: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()  // Protocol-specific data
)

