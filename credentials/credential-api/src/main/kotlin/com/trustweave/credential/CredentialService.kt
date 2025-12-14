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
     * **Example:**
     * ```kotlin
     * val request = IssuanceRequest(
     *     credential = credential,
     *     proofOptions = ProofOptions(...)
     * )
     * 
     * when (val result = service.issue(request)) {
     *     is IssuanceResult.Success -> {
     *         println("Credential issued: ${result.credential.id}")
     *     }
     *     is IssuanceResult.Failure -> {
     *         println("Issuance failed: ${result.errors}")
     *     }
     * }
     * ```
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
     * **Example:**
     * ```kotlin
     * val options = VerificationOptions(
     *     checkRevocation = true,
     *     checkExpiration = true,
     *     validateSchema = true,
     *     schemaId = "https://example.com/schemas/degree.json"
     * )
     * 
     * when (val result = service.verify(credential, trustPolicy, options)) {
     *     is VerificationResult.Valid -> {
     *         println("Credential valid: ${result.credential.id}")
     *         println("Issued at: ${result.issuedAt}")
     *     }
     *     is VerificationResult.Invalid.Expired -> {
     *         println("Credential expired at: ${result.expiredAt}")
     *     }
     *     is VerificationResult.Invalid.Revoked -> {
     *         println("Credential revoked at: ${result.revokedAt}")
     *     }
     *     // Compiler ensures all cases handled
     * }
     * ```
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
     * All verifications run in parallel using coroutines for improved performance.
     * 
     * **Example:**
     * ```kotlin
     * val credentials = listOf(cred1, cred2, cred3)
     * val results = service.verify(credentials, trustPolicy, options)
     * 
     * results.forEachIndexed { index, result ->
     *     when (result) {
     *         is VerificationResult.Valid -> {
     *             println("Credential $index: Valid")
     *         }
     *         is VerificationResult.Invalid -> {
     *             println("Credential $index: Invalid - ${result.errors}")
     *         }
     *     }
     * }
     * ```
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
     * Supports selective disclosure when [PresentationRequest.disclosedClaims] is specified.
     * 
     * **Example:**
     * ```kotlin
     * val request = PresentationRequest(
     *     disclosedClaims = setOf("name", "degree"), // Only disclose these claims
     *     proofOptions = ProofOptions(
     *         challenge = "random-challenge-string",
     *         domain = "verifier.example.com"
     *     )
     * )
     * 
     * val presentation = service.createPresentation(
     *     credentials = listOf(credential1, credential2),
     *     request = request
     * )
     * ```
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
     * Also verifies challenge and domain if specified in the presentation.
     * 
     * **Example:**
     * ```kotlin
     * val result = service.verifyPresentation(
     *     presentation = presentation,
     *     trustPolicy = trustPolicy,
     *     options = VerificationOptions(
     *         checkRevocation = true,
     *         checkExpiration = true
     *     )
     * )
     * 
     * when (result) {
     *     is VerificationResult.Valid -> {
     *         println("Presentation valid with ${presentation.verifiableCredential.size} credentials")
     *     }
     *     is VerificationResult.Invalid -> {
     *         println("Presentation invalid: ${result.errors}")
     *     }
     * }
     * ```
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
     * **Example:**
     * ```kotlin
     * val status = service.status(credential)
     * 
     * if (status.isValid) {
     *     println("Credential is valid")
     *     println("Expires at: ${status.expiresAt}")
     * } else {
     *     println("Credential is invalid")
     *     if (status.isRevoked) {
     *         println("Revoked at: ${status.revokedAt}")
     *     }
     *     if (status.isExpired) {
     *         println("Expired at: ${status.expiresAt}")
     *     }
     * }
     * ```
     * 
     * @param credential The credential to check
     * @return Status information including validity, revocation, and expiration
     */
    suspend fun status(credential: VerifiableCredential): CredentialStatusInfo
    
    /**
     * Check if format is supported.
     * 
     * **Example:**
     * ```kotlin
     * if (service.supports(ProofSuiteId.VcLdProof)) {
     *     // VC-LD proof format is supported
     * }
     * ```
     * 
     * @param format The proof format to check
     * @return true if the format is supported
     */
    fun supports(format: ProofSuiteId): Boolean
    
    /**
     * Get all supported formats.
     * 
     * **Example:**
     * ```kotlin
     * val formats = service.supportedFormats()
     * println("Supported formats: ${formats.joinToString { it.value }}")
     * ```
     * 
     * @return List of all supported proof formats
     */
    fun supportedFormats(): List<ProofSuiteId>
    
    /**
     * Check if format supports a capability.
     * 
     * **Example:**
     * ```kotlin
     * val supportsPresentation = service.supportsCapability(
     *     ProofSuiteId.VcLdProof
     * ) { presentation }
     * 
     * if (supportsPresentation) {
     *     // Can create presentations with this format
     * }
     * ```
     * 
     * @param format The proof format to check
     * @param capability Capability check function
     * @return true if the format supports the capability
     */
    fun supportsCapability(
        format: ProofSuiteId,
        capability: ProofEngineCapabilities.() -> Boolean
    ): Boolean
}
