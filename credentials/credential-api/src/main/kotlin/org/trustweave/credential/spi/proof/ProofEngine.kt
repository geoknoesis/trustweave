package org.trustweave.credential.spi.proof

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.resolver.DidResolver

/**
 * Proof engine for W3C Verifiable Credential proof suites.
 * 
 * This is a **Service Provider Interface (SPI)** for TrustWeave proof suite implementations.
 * It is intended for plugin authors who want to add support for new VC proof suites.
 * 
 * Handles proof suite-specific proof operations (generation, verification, presentation creation)
 * for proof suites such as VC-LD, VC-JWT, and SD-JWT-VC.
 * 
 * **SPI Stability Note:**
 * SPI interfaces may evolve more frequently than the core API; minor version bumps
 * may include SPI changes. Check release notes when upgrading.
 * 
 * Normal SDK users should use [org.trustweave.credential.CredentialService] directly
 * rather than implementing this interface.
 */
interface ProofEngine {
    /**
     * Proof suite identifier.
     * 
     * Identifies which proof suite this engine handles (e.g., "vc-ld", "sd-jwt-vc").
     * Each ProofEngine is registered for a specific proof suite.
     */
    val format: ProofSuiteId
    
    /**
     * Human-readable proof suite name.
     */
    val formatName: String
    
    /**
     * Proof suite version supported.
     */
    val formatVersion: String
    
    /**
     * Proof engine capabilities.
     */
    val capabilities: ProofEngineCapabilities
    
    /**
     * Issue a Verifiable Credential.
     * 
     * @param request The issuance request aligned with W3C VC Data Model
     * @return The issued VerifiableCredential with proof attached
     * @throws IllegalArgumentException if request is invalid
     * @throws IllegalStateException if engine is not ready
     */
    suspend fun issue(request: IssuanceRequest): VerifiableCredential
    
    /**
     * Verify a Verifiable Credential.
     * 
     * @param credential The VerifiableCredential to verify
     * @param options Verification options
     * @return VerificationResult indicating success or failure
     */
    suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions
    ): VerificationResult
    
    /**
     * Create a Verifiable Presentation from Verifiable Credentials (if supported).
     * 
     * @param credentials List of VerifiableCredentials to include in presentation
     * @param request Presentation request with selective disclosure options
     * @return VerifiablePresentation containing the credentials
     */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation {
        require(capabilities.presentation) {
            "Proof suite ${format.value} does not support presentations"
        }
        throw UnsupportedOperationException(
            "createPresentation not implemented for proof suite ${format.value}"
        )
    }
    
    
    /**
     * Initialize proof engine (called once on registration).
     */
    suspend fun initialize(config: ProofEngineConfig = ProofEngineConfig()) {
        // Default: no-op
    }
    
    /**
     * Cleanup proof engine resources.
     */
    suspend fun close() {
        // Default: no-op
    }
    
    /**
     * Check if proof engine is ready.
     */
    fun isReady(): Boolean = true
}

/**
 * Proof engine capabilities.
 * 
 * Describes what capabilities a proof engine supports.
 */
data class ProofEngineCapabilities(
    val selectiveDisclosure: Boolean = false,
    val zeroKnowledge: Boolean = false,
    val revocation: Boolean = true,
    val presentation: Boolean = true,
    val predicates: Boolean = false
)

/**
 * Proof engine configuration.
 * 
 * Configuration options for initializing a proof engine.
 */
data class ProofEngineConfig(
    val properties: Map<String, Any> = emptyMap(),
    val didResolver: DidResolver? = null
) {
    /**
     * Get DID resolver from config (checks both direct property and properties map).
     */
    @JvmName("getDidResolverFromConfig")
    fun getDidResolver(): DidResolver? {
        return didResolver ?: (properties["didResolver"] as? DidResolver)
    }
}

