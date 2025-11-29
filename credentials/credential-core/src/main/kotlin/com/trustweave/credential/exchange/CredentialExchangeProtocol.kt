package com.trustweave.credential.exchange

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation

/**
 * Common interface for credential exchange protocols.
 *
 * All credential exchange protocols (DIDComm, OIDC4VCI, CHAPI, OIDC4VP, etc.)
 * implement this interface, allowing them to be used interchangeably.
 *
 * **Example Usage:**
 * ```kotlin
 * val registry = CredentialExchangeProtocolRegistry()
 * registry.register(DidCommExchangeProtocol(didCommService))
 * registry.register(Oidc4VciExchangeProtocol(oidc4vciService))
 *
 * // Use any protocol
 * val offer = registry.offerCredential(
 *     protocolName = "didcomm",
 *     request = CredentialOfferRequest(...)
 * )
 * ```
 */
interface CredentialExchangeProtocol {
    /**
     * Protocol identifier (e.g., "didcomm", "oidc4vci", "chapi").
     */
    val protocolName: String

    /**
     * Supported exchange operations.
     *
     * Not all protocols support all operations. For example:
     * - DIDComm supports all operations
     * - OIDC4VCI primarily supports issuance (offer, request, issue)
     * - CHAPI supports wallet interactions
     */
    val supportedOperations: Set<ExchangeOperation>

    /**
     * Creates a credential offer.
     *
     * The issuer offers a credential to the holder, who can then request it.
     *
     * @param request Offer request with issuer, holder, and credential preview
     * @return Offer response with protocol-specific offer data
     */
    suspend fun offerCredential(
        request: CredentialOfferRequest
    ): CredentialOfferResponse

    /**
     * Requests a credential (holder requests after receiving offer).
     *
     * The holder requests the credential that was offered.
     *
     * @param request Request with offer reference and holder information
     * @return Request response with protocol-specific request data
     */
    suspend fun requestCredential(
        request: CredentialRequestRequest
    ): CredentialRequestResponse

    /**
     * Issues a credential (issuer issues after receiving request).
     *
     * The issuer issues the credential to the holder.
     *
     * @param request Issue request with credential and request reference
     * @return Issue response with the issued credential
     */
    suspend fun issueCredential(
        request: CredentialIssueRequest
    ): CredentialIssueResponse

    /**
     * Requests a proof presentation.
     *
     * The verifier requests a proof from the prover.
     *
     * @param request Proof request with requested attributes/predicates
     * @return Proof request response with protocol-specific request data
     */
    suspend fun requestProof(
        request: ProofRequestRequest
    ): ProofRequestResponse

    /**
     * Presents a proof (prover presents after receiving request).
     *
     * The prover presents a verifiable presentation to the verifier.
     *
     * @param request Presentation request with presentation and request reference
     * @return Presentation response with the presentation
     */
    suspend fun presentProof(
        request: ProofPresentationRequest
    ): ProofPresentationResponse
}

/**
 * Supported exchange operations.
 */
enum class ExchangeOperation {
    /** Issuer offers a credential to holder */
    OFFER_CREDENTIAL,

    /** Holder requests a credential from issuer */
    REQUEST_CREDENTIAL,

    /** Issuer issues a credential to holder */
    ISSUE_CREDENTIAL,

    /** Verifier requests a proof from prover */
    REQUEST_PROOF,

    /** Prover presents a proof to verifier */
    PRESENT_PROOF
}

