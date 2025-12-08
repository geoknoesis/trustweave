package com.trustweave.credential.oidc4vci.exchange

import com.trustweave.credential.exchange.*
import com.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import com.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import com.trustweave.credential.exchange.model.ExchangeMessageType
import com.trustweave.credential.exchange.request.ExchangeRequest
import com.trustweave.credential.exchange.request.ProofExchangeRequest
import com.trustweave.credential.identifiers.ExchangeProtocolName
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.oidc4vci.Oidc4VciService
import com.trustweave.credential.oidc4vci.exception.Oidc4VciException
import kotlinx.serialization.json.*

/**
 * OIDC4VCI (OpenID Connect for Verifiable Credential Issuance) implementation
 * of CredentialExchangeProtocol.
 *
 * Provides credential exchange operations using OIDC4VCI protocol.
 * Primarily supports issuance operations (offer, request, issue).
 *
 * **OIDC4VCI Flow:**
 * 1. Issuer creates credential offer (URI or object)
 * 2. Holder requests credential using offer
 * 3. Issuer issues credential via token exchange
 *
 * **Example Usage:**
 * ```kotlin
 * val oidc4vciService = Oidc4VciService(
 *     credentialIssuerUrl = "https://issuer.example.com",
 *     kms = kms,
 *     httpClient = OkHttpClient()
 * )
 * val protocol = Oidc4VciExchangeProtocol(oidc4vciService)
 *
 * val registry = ExchangeProtocolRegistries.default()
 * registry.register(protocol)
 *
 * val envelope = registry.offer(ExchangeProtocolName.Oidc4Vci, request)
 * ```
 */
class Oidc4VciExchangeProtocol(
    private val oidc4vciService: Oidc4VciService
) : CredentialExchangeProtocol {

    override val protocolName = ExchangeProtocolName.Oidc4Vci

    override val capabilities = ExchangeProtocolCapabilities(
        supportedOperations = setOf(
            ExchangeOperation.OFFER_CREDENTIAL,
            ExchangeOperation.REQUEST_CREDENTIAL,
            ExchangeOperation.ISSUE_CREDENTIAL
            // Note: OIDC4VCI doesn't typically support proof operations
            // Use DIDComm or OIDC4VP for proof presentations
        ),
        supportsAsync = false,  // OIDC4VCI is synchronous HTTP-based
        supportsMultipleCredentials = true,
        supportsSelectiveDisclosure = false,
        requiresTransportSecurity = true
    )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
        // Extract DID strings from typed DIDs
        val issuerDid = request.issuerDid.value
        
        // Extract credential types from preview or options
        val credentialTypes = request.options.metadata["credentialTypes"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: request.credentialPreview.attributes.map { it.name }

        val credentialIssuer = request.options.metadata["credentialIssuer"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("credentialIssuer required in options.metadata for OIDC4VCI offer")

        val grants = request.options.metadata["grants"]?.jsonObject
            ?.mapValues { it.value }

        // Create OIDC4VCI credential offer
        val offer = oidc4vciService.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = credentialTypes,
            credentialIssuer = credentialIssuer,
            grants = grants ?: emptyMap()
        )

        // Convert OIDC4VCI offer to JSON
        val offerJson = Json { ignoreUnknownKeys = true }.encodeToJsonElement(offer) as JsonObject

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Offer,
            messageData = offerJson,
            metadata = mapOf(
                "offerId" to JsonPrimitive(offer.offerId),
                "offerUri" to JsonPrimitive(offer.offerUri)
            )
        )
    }

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope {
        // Extract DID string from typed DID
        val holderDid = request.holderDid.value
        
        // Extract offer ID
        val offerId = request.offerId.value
        
        val redirectUri = request.options.metadata["redirectUri"]?.jsonPrimitive?.content
        val authorizationCode = request.options.metadata["authorizationCode"]?.jsonPrimitive?.content

        // Create OIDC4VCI credential request
        val credentialRequest = oidc4vciService.createCredentialRequest(
            holderDid = holderDid,
            offerId = offerId,
            redirectUri = redirectUri,
            authorizationCode = authorizationCode
        )

        // Convert OIDC4VCI request to JSON
        val requestJson = Json { ignoreUnknownKeys = true }.encodeToJsonElement(credentialRequest) as JsonObject

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Request,
            messageData = requestJson,
            metadata = mapOf(
                "requestId" to JsonPrimitive(credentialRequest.requestId)
            )
        )
    }

    override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> {
        // Extract DID strings from typed DIDs
        val issuerDid = request.issuerDid.value
        val holderDid = request.holderDid.value
        
        // Extract request ID
        val requestId = request.requestId.value

        // Issue credential via OIDC4VCI
        // Note: Oidc4VciService.issueCredential may need updating to accept VerifiableCredential
        val issueResult = oidc4vciService.issueCredential(
            issuerDid = issuerDid,
            holderDid = holderDid,
            credential = request.credential, // May need conversion
            requestId = requestId
        )

        // Convert issue result to JSON
        val issueJson = Json { ignoreUnknownKeys = true }.encodeToJsonElement(issueResult.credentialResponse) as JsonObject

        val envelope = ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Issue,
            messageData = issueJson,
            metadata = mapOf(
                "issueId" to JsonPrimitive(issueResult.issueId)
            )
        )

        // Return the VerifiableCredential from the issue result
        return Pair(issueResult.credential, envelope)
    }

    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation REQUEST_PROOF not supported for protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to "REQUEST_PROOF",
                "supportedOperations" to capabilities.supportedOperations.map { it.name }
            )
        )
    }

    override suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation PRESENT_PROOF not supported for protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to "PRESENT_PROOF",
                "supportedOperations" to capabilities.supportedOperations.map { it.name }
            )
        )
    }
}
