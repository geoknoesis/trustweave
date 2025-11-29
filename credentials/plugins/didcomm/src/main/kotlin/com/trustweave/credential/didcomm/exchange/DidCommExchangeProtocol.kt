package com.trustweave.credential.didcomm.exchange

import com.trustweave.credential.didcomm.DidCommService
import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.protocol.CredentialProtocol
import com.trustweave.credential.didcomm.protocol.ProofProtocol
import com.trustweave.credential.didcomm.protocol.ProofRequest
import com.trustweave.credential.didcomm.protocol.RequestedAttribute
import com.trustweave.credential.didcomm.protocol.AttributeRestriction
import com.trustweave.credential.didcomm.protocol.RequestedPredicate
import com.trustweave.credential.exchange.*
import com.trustweave.credential.exchange.exception.ExchangeException
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation

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
 * val registry = CredentialExchangeProtocolRegistry()
 * registry.register(protocol)
 *
 * val offer = registry.offerCredential("didcomm", request)
 * ```
 */
class DidCommExchangeProtocol(
    private val didCommService: DidCommService
) : CredentialExchangeProtocol {

    override val protocolName = "didcomm"

    override val supportedOperations = setOf(
        ExchangeOperation.OFFER_CREDENTIAL,
        ExchangeOperation.REQUEST_CREDENTIAL,
        ExchangeOperation.ISSUE_CREDENTIAL,
        ExchangeOperation.REQUEST_PROOF,
        ExchangeOperation.PRESENT_PROOF
    )

    override suspend fun offerCredential(
        request: CredentialOfferRequest
    ): CredentialOfferResponse {
        // Use common preview directly (CredentialProtocol accepts it)
        val message = CredentialProtocol.createCredentialOffer(
            fromDid = request.issuerDid,
            toDid = request.holderDid,
            credentialPreview = request.credentialPreview,
            thid = request.options["thid"] as? String
        )

        val fromKeyId = request.options["fromKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "fromKeyId",
                protocolName = protocolName
            )
        val toKeyId = request.options["toKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "toKeyId",
                protocolName = protocolName
            )

        val messageId = didCommService.sendMessage(
            message = message,
            fromDid = request.issuerDid,
            fromKeyId = fromKeyId,
            toDid = request.holderDid,
            toKeyId = toKeyId,
            encrypt = request.options["encrypt"] as? Boolean ?: true
        )

        return CredentialOfferResponse(
            offerId = messageId,
            offerData = message,
            protocolName = protocolName
        )
    }

    override suspend fun requestCredential(
        request: CredentialRequestRequest
    ): CredentialRequestResponse {
        // Get the offer message to extract thread ID
        val offerMessage = didCommService.getMessage(request.offerId)
            ?: throw ExchangeException.OfferNotFound(offerId = request.offerId)

        val message = CredentialProtocol.createCredentialRequest(
            fromDid = request.holderDid,
            toDid = request.issuerDid,
            thid = offerMessage.id
        )

        val fromKeyId = request.options["fromKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "fromKeyId",
                protocolName = protocolName
            )
        val toKeyId = request.options["toKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "toKeyId",
                protocolName = protocolName
            )

        val messageId = didCommService.sendMessage(
            message = message,
            fromDid = request.holderDid,
            fromKeyId = fromKeyId,
            toDid = request.issuerDid,
            toKeyId = toKeyId,
            encrypt = request.options["encrypt"] as? Boolean ?: true
        )

        return CredentialRequestResponse(
            requestId = messageId,
            requestData = message,
            protocolName = protocolName
        )
    }

    override suspend fun issueCredential(
        request: CredentialIssueRequest
    ): CredentialIssueResponse {
        // Get the request message to extract thread ID
        val requestMessage = didCommService.getMessage(request.requestId)
            ?: throw ExchangeException.RequestNotFound(requestId = request.requestId)

        val message = CredentialProtocol.createCredentialIssue(
            fromDid = request.issuerDid,
            toDid = request.holderDid,
            credential = request.credential,
            thid = requestMessage.id
        )

        val fromKeyId = request.options["fromKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "fromKeyId",
                protocolName = protocolName
            )
        val toKeyId = request.options["toKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "toKeyId",
                protocolName = protocolName
            )

        val messageId = didCommService.sendMessage(
            message = message,
            fromDid = request.issuerDid,
            fromKeyId = fromKeyId,
            toDid = request.holderDid,
            toKeyId = toKeyId,
            encrypt = request.options["encrypt"] as? Boolean ?: true
        )

        return CredentialIssueResponse(
            issueId = messageId,
            credential = request.credential,
            issueData = message,
            protocolName = protocolName
        )
    }

    override suspend fun requestProof(
        request: ProofRequestRequest
    ): ProofRequestResponse {
        // Convert common proof request to DIDComm proof request
        val didCommProofRequest = ProofRequest(
            name = request.name,
            version = request.version,
            requestedAttributes = request.requestedAttributes.mapValues { (key, attr) ->
                RequestedAttribute(
                    name = attr.name,
                    restrictions = attr.restrictions.map { restriction ->
                        AttributeRestriction(
                            issuerDid = restriction.issuerDid,
                            schemaId = restriction.schemaId,
                            credentialDefinitionId = restriction.credentialDefinitionId
                        )
                    }
                )
            },
            requestedPredicates = request.requestedPredicates.mapValues { (key, pred) ->
                RequestedPredicate(
                    name = pred.name,
                    pType = pred.pType,
                    pValue = pred.pValue,
                    restrictions = pred.restrictions.map { restriction ->
                        AttributeRestriction(
                            issuerDid = restriction.issuerDid,
                            schemaId = restriction.schemaId,
                            credentialDefinitionId = restriction.credentialDefinitionId
                        )
                    }
                )
            },
            goalCode = request.goalCode,
            willConfirm = request.options["willConfirm"] as? Boolean ?: true
        )

        val message = ProofProtocol.createProofRequest(
            fromDid = request.verifierDid,
            toDid = request.proverDid,
            proofRequest = didCommProofRequest,
            thid = request.options["thid"] as? String
        )

        val fromKeyId = request.options["fromKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "fromKeyId",
                protocolName = protocolName
            )
        val toKeyId = request.options["toKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "toKeyId",
                protocolName = protocolName
            )

        val messageId = didCommService.sendMessage(
            message = message,
            fromDid = request.verifierDid,
            fromKeyId = fromKeyId,
            toDid = request.proverDid,
            toKeyId = toKeyId,
            encrypt = request.options["encrypt"] as? Boolean ?: true
        )

        return ProofRequestResponse(
            requestId = messageId,
            requestData = message,
            protocolName = protocolName
        )
    }

    override suspend fun presentProof(
        request: ProofPresentationRequest
    ): ProofPresentationResponse {
        // Get the proof request message to extract thread ID
        val proofRequestMessage = didCommService.getMessage(request.requestId)
            ?: throw ExchangeException.ProofRequestNotFound(requestId = request.requestId)

        val message = ProofProtocol.createProofPresentation(
            fromDid = request.proverDid,
            toDid = request.verifierDid,
            presentation = request.presentation,
            thid = proofRequestMessage.id
        )

        val fromKeyId = request.options["fromKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "fromKeyId",
                protocolName = protocolName
            )
        val toKeyId = request.options["toKeyId"] as? String
            ?: throw ExchangeException.MissingRequiredOption(
                optionName = "toKeyId",
                protocolName = protocolName
            )

        val messageId = didCommService.sendMessage(
            message = message,
            fromDid = request.proverDid,
            fromKeyId = fromKeyId,
            toDid = request.verifierDid,
            toKeyId = toKeyId,
            encrypt = request.options["encrypt"] as? Boolean ?: true
        )

        return ProofPresentationResponse(
            presentationId = messageId,
            presentation = request.presentation,
            presentationData = message,
            protocolName = protocolName
        )
    }
}

