package org.trustweave.credential.oidc4vp.exchange

import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.oidc4vp.Oidc4VpService
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException
import kotlinx.serialization.json.*

/**
 * OIDC4VP (OpenID Connect for Verifiable Presentations) implementation
 * of CredentialExchangeProtocol.
 *
 * Provides presentation exchange operations using OIDC4VP protocol.
 * Primarily supports proof operations (request proof, present proof).
 *
 * **OIDC4VP Flow:**
 * 1. Verifier creates authorization request (QR code or URL)
 * 2. Holder parses authorization URL and fetches request
 * 3. Holder creates presentation from selected credentials
 * 4. Holder submits presentation to verifier
 *
 * **Example Usage:**
 * ```kotlin
 * val oidc4vpService = Oidc4VpService(
 *     kms = kms,
 *     httpClient = OkHttpClient()
 * )
 * val protocol = Oidc4VpExchangeProtocol(oidc4vpService)
 *
 * val registry = ExchangeProtocolRegistries.default()
 * registry.register(protocol)
 *
 * val envelope = registry.requestProof(ExchangeProtocolName.Oidc4Vp, request)
 * ```
 */
class Oidc4VpExchangeProtocol(
    private val oidc4vpService: Oidc4VpService
) : CredentialExchangeProtocol {

    override val protocolName = ExchangeProtocolName.Oidc4Vp

    override val capabilities = ExchangeProtocolCapabilities(
        supportedOperations = setOf(
            ExchangeOperation.REQUEST_PROOF,
            ExchangeOperation.PRESENT_PROOF
            // Note: OIDC4VP doesn't support issuance operations
            // Use DIDComm or OIDC4VCI for credential issuance
        ),
        supportsAsync = false,  // OIDC4VP is synchronous HTTP-based
        supportsMultipleCredentials = true,
        supportsSelectiveDisclosure = true,  // OIDC4VP supports selective disclosure
        requiresTransportSecurity = true
    )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
        throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation OFFER_CREDENTIAL not supported for protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to "OFFER_CREDENTIAL",
                "supportedOperations" to capabilities.supportedOperations.map { it.name }
            )
        )
    }

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope {
        throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation REQUEST_CREDENTIAL not supported for protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to "REQUEST_CREDENTIAL",
                "supportedOperations" to capabilities.supportedOperations.map { it.name }
            )
        )
    }

    override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> {
        throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation ISSUE_CREDENTIAL not supported for protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to "ISSUE_CREDENTIAL",
                "supportedOperations" to capabilities.supportedOperations.map { it.name }
            )
        )
    }

    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        // Extract authorization URL from request
        val authorizationUrl = request.options.metadata["authorizationUrl"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("authorizationUrl required in options.metadata for OIDC4VP proof request")

        // Parse authorization URL to create PermissionRequest
        val permissionRequest = oidc4vpService.parseAuthorizationUrl(authorizationUrl)

        // Convert PermissionRequest to JSON
        val requestJson = buildJsonObject {
            put("requestId", permissionRequest.requestId)
            put("authorizationRequest", buildJsonObject {
                put("responseUri", permissionRequest.authorizationRequest.responseUri)
                permissionRequest.authorizationRequest.clientId?.let { put("clientId", it) }
                permissionRequest.authorizationRequest.requestUri?.let { put("requestUri", it) }
                permissionRequest.authorizationRequest.nonce?.let { put("nonce", it) }
                permissionRequest.authorizationRequest.state?.let { put("state", it) }
            })
            permissionRequest.verifierUrl?.let { put("verifierUrl", it) }
            put("requestedCredentialTypes", JsonArray(permissionRequest.requestedCredentialTypes.map { JsonPrimitive(it) }))
        }

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofRequest,
            messageData = requestJson,
            metadata = mapOf(
                "requestId" to JsonPrimitive(permissionRequest.requestId)
            )
        )
    }

    override suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        // Extract request ID and metadata
        val requestId = request.requestId.value
        val selectedCredentials = request.options.metadata["selectedCredentials"]?.jsonArray
            ?: throw IllegalArgumentException("selectedCredentials required in options.metadata for OIDC4VP presentation")
        val selectedFields = request.options.metadata["selectedFields"]?.jsonArray
        val holderDid = request.proverDid.value
        val keyId = request.options.metadata["keyId"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("keyId required in options.metadata for OIDC4VP presentation")

        // Parse the permission request (would be stored from requestProof call)
        // For now, we'll need to reconstruct it - in a full implementation, this would be stored
        // Note: This is a simplified implementation - full implementation would store the permission request
        throw IllegalStateException("OIDC4VP presentProof requires the permission request to be stored. Use Oidc4VpService.createPermissionResponse() directly instead.")
    }
}

