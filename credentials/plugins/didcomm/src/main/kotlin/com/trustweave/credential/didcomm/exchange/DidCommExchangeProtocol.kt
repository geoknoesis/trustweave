package com.trustweave.credential.didcomm.exchange

import com.trustweave.credential.didcomm.DidCommService
import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.protocol.CredentialProtocol
import com.trustweave.credential.didcomm.protocol.ProofProtocol
import com.trustweave.credential.didcomm.protocol.ProofRequest as DidCommProofRequest
import com.trustweave.credential.didcomm.protocol.RequestedAttribute
import com.trustweave.credential.didcomm.protocol.AttributeRestriction as DidCommAttributeRestriction
import com.trustweave.credential.didcomm.protocol.RequestedPredicate
import com.trustweave.credential.exchange.*
import com.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import com.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import com.trustweave.credential.exchange.model.ExchangeMessageType
import com.trustweave.credential.exchange.request.ExchangeRequest
import com.trustweave.credential.exchange.request.ProofExchangeRequest
import com.trustweave.credential.identifiers.ExchangeProtocolName
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import kotlinx.serialization.json.*

/**
 * DIDComm V2 implementation of CredentialExchangeProtocol.
 *
 * Provides credential exchange operations using DIDComm V2 messaging protocol.
 * Supports all exchange operations: offer, request, issue, proof request, and proof presentation.
 *
 * **Example Usage:**
 * ```kotlin
 * val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
 * val protocol = DidCommExchangeProtocol(didCommService)
 *
 * val registry = ExchangeProtocolRegistries.default()
 * registry.register(protocol)
 *
 * val (vc, envelope) = registry.issue(ExchangeProtocolName.DidComm, request)
 * ```
 */
class DidCommExchangeProtocol(
    private val didCommService: DidCommService
) : CredentialExchangeProtocol {

    override val protocolName = ExchangeProtocolName.DidComm

    override val capabilities = ExchangeProtocolCapabilities(
        supportedOperations = setOf(
            ExchangeOperation.OFFER_CREDENTIAL,
            ExchangeOperation.REQUEST_CREDENTIAL,
            ExchangeOperation.ISSUE_CREDENTIAL,
            ExchangeOperation.REQUEST_PROOF,
            ExchangeOperation.PRESENT_PROOF
        ),
        supportsAsync = true,
        supportsMultipleCredentials = true,
        supportsSelectiveDisclosure = true,
        requiresTransportSecurity = true
    )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
        // Extract DID strings from typed DIDs
        val fromDid = request.issuerDid.value
        val toDid = request.holderDid.value
        
        // Extract thread ID from options
        val thid = request.options.metadata["thid"]?.jsonPrimitive?.content

        // Create DIDComm credential offer message
        val message = CredentialProtocol.createCredentialOffer(
            fromDid = fromDid,
            toDid = toDid,
            credentialPreview = request.credentialPreview,
            thid = thid
        )

        // Convert DIDComm message to JSON
        val messageJson = message.toJsonObject()

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Offer,
            messageData = messageJson,
            metadata = mapOf(
                "messageId" to JsonPrimitive(message.id),
                "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true))
            )
        )
    }

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope {
        // Extract DID strings from typed DIDs
        val fromDid = request.holderDid.value
        val toDid = request.issuerDid.value
        
        // Get the offer message to extract thread ID
        // Note: offerId is in the request, but we need to resolve it to get the message
        // This may require accessing didCommService.getMessage(request.offerId.value)
        val thid = request.options.metadata["thid"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Thread ID (thid) required in options.metadata for DIDComm request")

        // Create DIDComm credential request message
        val message = CredentialProtocol.createCredentialRequest(
            fromDid = fromDid,
            toDid = toDid,
            thid = thid
        )

        // Convert DIDComm message to JSON
        val messageJson = message.toJsonObject()

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Request,
            messageData = messageJson,
            metadata = mapOf(
                "messageId" to JsonPrimitive(message.id),
                "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true))
            )
        )
    }

    override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> {
        // Extract DID strings from typed DIDs
        val fromDid = request.issuerDid.value
        val toDid = request.holderDid.value
        
        // Get the request message to extract thread ID
        val thid = request.options.metadata["thid"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Thread ID (thid) required in options.metadata for DIDComm issue")

        // Create DIDComm credential issue message
        // Note: CredentialProtocol.createCredentialIssue expects Credential type
        // This needs to be updated to accept VerifiableCredential
        // For now, we'll need to convert or update the protocol helper
        val message = CredentialProtocol.createCredentialIssue(
            fromDid = fromDid,
            toDid = toDid,
            credential = request.credential, // This may need conversion
            thid = thid
        )

        // Convert DIDComm message to JSON
        val messageJson = Json.encodeToJsonElement(message) as JsonObject

        val envelope = ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Issue,
            messageData = messageJson,
            metadata = mapOf(
                "messageId" to JsonPrimitive(message.id),
                "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true))
            )
        )

        return Pair(request.credential, envelope)
    }

    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        // Extract DID strings from typed DIDs
        val fromDid = request.verifierDid.value
        val toDid = request.proverDid.value
        
        // Convert protocol-agnostic proof request to DIDComm-specific format
        val didCommProofRequest = DidCommProofRequest(
            name = request.proofRequest.name,
            version = request.proofRequest.version,
            requestedAttributes = request.proofRequest.requestedAttributes.mapValues { (key, attr) ->
                RequestedAttribute(
                    name = attr.name,
                    restrictions = attr.restrictions.map { restriction ->
                        DidCommAttributeRestriction(
                            issuerDid = restriction.issuerDid?.value,
                            schemaId = restriction.schemaId?.value,
                            credentialDefinitionId = restriction.metadata["credentialDefinitionId"]?.jsonPrimitive?.content
                        )
                    }
                )
            },
            requestedPredicates = emptyMap(), // TODO: Extract from options.metadata if needed
            goalCode = request.options.metadata["goalCode"]?.jsonPrimitive?.content,
            willConfirm = request.options.metadata["willConfirm"]?.jsonPrimitive?.boolean ?: true
        )

        // Extract thread ID from options
        val thid = request.options.metadata["thid"]?.jsonPrimitive?.content

        // Create DIDComm proof request message
        val message = ProofProtocol.createProofRequest(
            fromDid = fromDid,
            toDid = toDid,
            proofRequest = didCommProofRequest,
            thid = thid
        )

        // Convert DIDComm message to JSON
        val messageJson = message.toJsonObject()

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofRequest,
            messageData = messageJson,
            metadata = mapOf(
                "messageId" to JsonPrimitive(message.id),
                "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true))
            )
        )
    }

    override suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        // Extract DID strings from typed DIDs
        val fromDid = request.proverDid.value
        val toDid = request.verifierDid.value
        
        // Get the proof request message to extract thread ID
        val thid = request.options.metadata["thid"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Thread ID (thid) required in options.metadata for DIDComm presentation")

        // Create DIDComm proof presentation message
        // Note: ProofProtocol.createProofPresentation expects Credential type
        // This needs to be updated to accept VerifiablePresentation
        val message = ProofProtocol.createProofPresentation(
            fromDid = fromDid,
            toDid = toDid,
            presentation = request.presentation, // This may need conversion
            thid = thid
        )

        // Convert DIDComm message to JSON
        val messageJson = Json.encodeToJsonElement(message) as JsonObject

        val envelope = ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofPresentation,
            messageData = messageJson,
            metadata = mapOf(
                "messageId" to JsonPrimitive(message.id),
                "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true))
            )
        )

        return Pair(request.presentation, envelope)
    }
}
