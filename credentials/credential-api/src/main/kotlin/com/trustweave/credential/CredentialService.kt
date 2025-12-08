package com.trustweave.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.results.CredentialStatusInfo
import com.trustweave.credential.requests.IssuanceRequest
import com.trustweave.credential.requests.PresentationRequest
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.credential.results.VerificationResult
import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.spi.proof.ProofEngineCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Credential service for W3C Verifiable Credentials.
 * 
 * Focused on VC standards only (VC-LD, VC-JWT, SD-JWT-VC).
 * All operations work with VerifiableCredential aligned with W3C VC Data Model.
 * 
 * DSL builders are provided via extension functions in the trust module.
 */
interface CredentialService {
    /**
     * Issue a credential from request.
     * 
     * This operation is cancellable and will respect coroutine cancellation.
     * Typical duration: 50-200ms depending on proof generation.
     * 
     * @param request The issuance request containing credential details.
     * @return [IssuanceResult] encapsulating either a [IssuanceResult.Success] with the issued credential,
     *         or an [IssuanceResult.Failure] indicating the reason for failure.
     */
    suspend fun issue(request: IssuanceRequest): IssuanceResult
    
    /**
     * Verify a Verifiable Credential.
     * 
     * This operation is cancellable and will respect coroutine cancellation.
     * Typical duration: 100-500ms depending on DID resolution and proof verification.
     * 
     * @param credential The credential to verify
     * @param trustPolicy Optional trust policy for issuer validation (default: accept all)
     * @param options Verification options
     * @return Verification result with detailed success/failure information
     */
    suspend fun verify(
        credential: VerifiableCredential,
        trustPolicy: com.trustweave.credential.trust.TrustPolicy? = null,
        options: VerificationOptions = VerificationOptions()
    ): VerificationResult
    
    /**
     * Batch verify multiple Verifiable Credentials in parallel.
     * 
     * This operation is cancellable and will respect coroutine cancellation.
     * All verifications run in parallel using coroutines.
     * 
     * @param credentials List of credentials to verify
     * @param trustPolicy Optional trust policy for issuer validation (default: accept all)
     * @param options Verification options
     * @return List of verification results in the same order as input credentials
     */
    suspend fun verify(
        credentials: List<VerifiableCredential>,
        trustPolicy: com.trustweave.credential.trust.TrustPolicy? = null,
        options: VerificationOptions = VerificationOptions()
    ): List<VerificationResult> = coroutineScope {
        credentials.map { async { verify(it, trustPolicy, options) } }.awaitAll()
    }
    
    /**
     * Create a Verifiable Presentation from Verifiable Credentials.
     * 
     * This operation is cancellable and will respect coroutine cancellation.
     * 
     * @param credentials List of credentials to include in the presentation
     * @param request Presentation request with disclosure and proof options
     * @return Verifiable presentation
     */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation
    
    /**
     * Verify a Verifiable Presentation.
     * 
     * This operation is cancellable and will respect coroutine cancellation.
     * Verifies all credentials in the presentation and the presentation proof.
     * 
     * @param presentation The presentation to verify
     * @param trustPolicy Optional trust policy for issuer validation (default: accept all)
     * @param options Verification options
     * @return Verification result
     */
    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        trustPolicy: com.trustweave.credential.trust.TrustPolicy? = null,
        options: VerificationOptions = VerificationOptions()
    ): VerificationResult
    
    /**
     * Get credential status (revocation, expiration).
     * 
     * This operation is cancellable and will respect coroutine cancellation.
     * This is a fast operation that checks temporal validity and revocation status.
     * 
     * @param credential The credential to check
     * @return Status information including validity, revocation, and expiration
     */
    suspend fun status(credential: VerifiableCredential): CredentialStatusInfo
    
    /**
     * Check if format is supported.
     */
    fun supports(format: ProofSuiteId): Boolean
    
    /**
     * Get all supported formats.
     */
    fun supportedFormats(): List<ProofSuiteId>
    
    /**
     * Check if format supports a capability.
     */
    fun supportsCapability(
        format: ProofSuiteId,
        capability: ProofEngineCapabilities.() -> Boolean
    ): Boolean
}
