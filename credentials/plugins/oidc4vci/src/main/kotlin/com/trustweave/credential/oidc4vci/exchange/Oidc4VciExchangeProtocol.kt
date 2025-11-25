package com.trustweave.credential.oidc4vci.exchange

import com.trustweave.credential.exchange.*
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.oidc4vci.Oidc4VciService

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
 * val registry = CredentialExchangeProtocolRegistry()
 * registry.register(protocol)
 * 
 * val offer = registry.offerCredential("oidc4vci", request)
 * ```
 */
class Oidc4VciExchangeProtocol(
    private val oidc4vciService: Oidc4VciService
) : CredentialExchangeProtocol {
    
    override val protocolName = "oidc4vci"
    
    override val supportedOperations = setOf(
        ExchangeOperation.OFFER_CREDENTIAL,
        ExchangeOperation.REQUEST_CREDENTIAL,
        ExchangeOperation.ISSUE_CREDENTIAL
        // Note: OIDC4VCI doesn't typically support proof operations
        // Use DIDComm or OIDC4VP for proof presentations
    )
    
    override suspend fun offerCredential(
        request: CredentialOfferRequest
    ): CredentialOfferResponse {
        // Extract credential types from preview or options
        val credentialTypes = request.options["credentialTypes"] as? List<String>
            ?: request.credentialPreview.attributes.map { it.name }
        
        val credentialIssuer = request.options["credentialIssuer"] as? String
            ?: throw IllegalArgumentException("Missing 'credentialIssuer' in options")
        
        val grants = request.options["grants"] as? Map<String, Any?> ?: emptyMap()
        
        // Create OIDC4VCI credential offer
        val offer = oidc4vciService.createCredentialOffer(
            issuerDid = request.issuerDid,
            credentialTypes = credentialTypes,
            credentialIssuer = credentialIssuer,
            grants = grants
        )
        
        return CredentialOfferResponse(
            offerId = offer.offerId,
            offerData = offer, // OIDC4VCI offer format
            protocolName = protocolName
        )
    }
    
    override suspend fun requestCredential(
        request: CredentialRequestRequest
    ): CredentialRequestResponse {
        val redirectUri = request.options["redirectUri"] as? String
        val authorizationCode = request.options["authorizationCode"] as? String
        
        // Create OIDC4VCI credential request
        val credentialRequest = oidc4vciService.createCredentialRequest(
            holderDid = request.holderDid,
            offerId = request.offerId,
            redirectUri = redirectUri,
            authorizationCode = authorizationCode
        )
        
        return CredentialRequestResponse(
            requestId = credentialRequest.requestId,
            requestData = credentialRequest,
            protocolName = protocolName
        )
    }
    
    override suspend fun issueCredential(
        request: CredentialIssueRequest
    ): CredentialIssueResponse {
        // Issue credential via OIDC4VCI
        val issueResult = oidc4vciService.issueCredential(
            issuerDid = request.issuerDid,
            holderDid = request.holderDid,
            credential = request.credential,
            requestId = request.requestId
        )
        
        return CredentialIssueResponse(
            issueId = issueResult.issueId,
            credential = issueResult.credential,
            issueData = issueResult.credentialResponse,
            protocolName = protocolName
        )
    }
    
    override suspend fun requestProof(
        request: ProofRequestRequest
    ): ProofRequestResponse {
        throw UnsupportedOperationException(
            "OIDC4VCI does not support proof requests. Use DIDComm or OIDC4VP instead."
        )
    }
    
    override suspend fun presentProof(
        request: ProofPresentationRequest
    ): ProofPresentationResponse {
        throw UnsupportedOperationException(
            "OIDC4VCI does not support proof presentations. Use DIDComm or OIDC4VP instead."
        )
    }
}

