package org.trustweave.credential.siop.exchange

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.siop.SiopV2Service
import java.util.UUID

/**
 * SIOPv2 (Self-Issued OpenID Provider v2) implementation of [CredentialExchangeProtocol].
 *
 * Supports proof request and presentation operations using the SIOPv2 protocol.
 * Credential issuance operations (offer/request/issue) are not part of the SIOPv2 spec
 * and will throw [TrustWeaveException.InvalidOperation].
 *
 * **SIOPv2 Flow:**
 * 1. Verifier creates an Authorization Request (optionally with a Presentation Definition)
 * 2. Holder parses the request and builds an Authorization Response (ID Token / VP Token)
 * 3. Holder submits the response to the verifier's response_uri via direct_post
 */
class SiopV2ExchangeProtocol(
    private val siopV2Service: SiopV2Service,
) : CredentialExchangeProtocol {

    override val protocolName = ExchangeProtocolName.SiopV2

    override val capabilities = ExchangeProtocolCapabilities(
        supportedOperations = setOf(
            ExchangeOperation.REQUEST_PROOF,
            ExchangeOperation.PRESENT_PROOF,
        ),
        supportsAsync = false,
        supportsMultipleCredentials = true,
        supportsSelectiveDisclosure = true,
        requiresTransportSecurity = true,
    )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
        throw TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation OFFER_CREDENTIAL not supported for protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to "OFFER_CREDENTIAL",
                "supportedOperations" to capabilities.supportedOperations.map { it.name },
            ),
        )
    }

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope {
        throw TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation REQUEST_CREDENTIAL not supported for protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to "REQUEST_CREDENTIAL",
                "supportedOperations" to capabilities.supportedOperations.map { it.name },
            ),
        )
    }

    override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> {
        throw TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation ISSUE_CREDENTIAL not supported for protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to "ISSUE_CREDENTIAL",
                "supportedOperations" to capabilities.supportedOperations.map { it.name },
            ),
        )
    }

    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        val clientId = request.options.metadata["clientId"]?.jsonPrimitive?.content
            ?: request.verifierDid.value
        val responseUri = request.options.metadata["responseUri"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("responseUri required in options.metadata for SIOPv2 proof request")
        val nonce = request.options.metadata["nonce"]?.jsonPrimitive?.content
        val state = request.options.metadata["state"]?.jsonPrimitive?.content

        val session = siopV2Service.createAuthorizationRequest(
            clientId = clientId,
            nonce = nonce ?: UUID.randomUUID().toString(),
            state = state,
            responseUri = responseUri,
        )

        val requestData = buildJsonObject {
            put("sessionId", session.sessionId)
            put("clientId", session.request.clientId)
            put("responseUri", responseUri)
            put("nonce", session.request.nonce)
            session.request.state?.let { put("state", it) }
        }

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofRequest,
            messageData = requestData,
            metadata = mapOf(
                "sessionId" to JsonPrimitive(session.sessionId),
            ),
        )
    }

    override suspend fun presentProof(
        request: ProofExchangeRequest.Presentation,
    ): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        val sessionId = request.requestId.value
        val holderDid = request.proverDid.value
        val keyId = request.options.metadata["keyId"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("keyId required in options.metadata for SIOPv2 presentation")

        val session = siopV2Service.getSession(sessionId)
            ?: throw TrustWeaveException.InvalidOperation(
                code = "SESSION_NOT_FOUND",
                message = "No stored SIOPv2 session for sessionId=$sessionId. Call requestProof() first.",
                context = mapOf("sessionId" to sessionId),
            )

        val response = siopV2Service.buildAuthorizationResponse(
            session = session,
            holderDid = holderDid,
            keyId = keyId,
            presentation = request.presentation,
        )
        siopV2Service.submitResponse(session, response)

        val responseData = buildJsonObject {
            response.vpToken?.let { put("vpToken", it) }
            response.idToken?.let { put("idToken", it) }
            response.state?.let { put("state", it) }
        }

        val envelope = ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofPresentation,
            messageData = responseData,
            metadata = mapOf(
                "sessionId" to JsonPrimitive(sessionId),
            ),
        )
        return Pair(request.presentation, envelope)
    }
}
