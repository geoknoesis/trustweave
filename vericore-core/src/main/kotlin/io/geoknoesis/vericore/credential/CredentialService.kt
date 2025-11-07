package io.geoknoesis.vericore.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.spi.SchemaFormat

/**
 * Pluggable credential service interface.
 * 
 * Implementations can wrap walt.id, godiddy, or provide native implementations.
 * This interface provides a unified API for credential operations regardless
 * of the underlying provider.
 * 
 * **Example Usage**:
 * ```kotlin
 * // Register a credential service
 * val service = WaltIdCredentialService(...)
 * CredentialRegistry.register(service)
 * 
 * // Use unified API
 * val credential = CredentialRegistry.issue(credential, options)
 * val result = CredentialRegistry.verify(credential, options)
 * ```
 */
interface CredentialService {
    /**
     * Provider name (e.g., "waltid", "godiddy", "native").
     */
    val providerName: String
    
    /**
     * Supported proof types.
     * 
     * @return List of proof type identifiers (e.g., "Ed25519Signature2020", "JsonWebSignature2020")
     */
    val supportedProofTypes: List<String>
    
    /**
     * Supported schema formats.
     * 
     * @return List of supported schema validation formats
     */
    val supportedSchemaFormats: List<SchemaFormat>
    
    /**
     * Issue a verifiable credential.
     * 
     * @param credential Credential to issue (without proof)
     * @param options Issuance options (proof type, key ID, etc.)
     * @return Issued credential with proof
     */
    suspend fun issueCredential(
        credential: VerifiableCredential,
        options: CredentialIssuanceOptions
    ): VerifiableCredential
    
    /**
     * Verify a verifiable credential.
     * 
     * @param credential Credential to verify
     * @param options Verification options (check revocation, schema validation, etc.)
     * @return Verification result with detailed status
     */
    suspend fun verifyCredential(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions
    ): CredentialVerificationResult
    
    /**
     * Create a verifiable presentation.
     * 
     * @param credentials List of credentials to include in presentation
     * @param options Presentation options (proof type, challenge, domain, etc.)
     * @return Verifiable presentation
     */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        options: PresentationOptions
    ): VerifiablePresentation
    
    /**
     * Verify a verifiable presentation.
     * 
     * @param presentation Presentation to verify
     * @param options Verification options (challenge verification, etc.)
     * @return Verification result
     */
    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        options: PresentationVerificationOptions
    ): PresentationVerificationResult
}

/**
 * SPI Provider for credential services.
 * 
 * Allows automatic discovery of credential service implementations
 * via Java ServiceLoader.
 */
interface CredentialServiceProvider {
    /**
     * Provider name (e.g., "waltid", "godiddy").
     */
    val name: String
    
    /**
     * Create a credential service instance.
     * 
     * @param options Configuration options
     * @return Credential service instance, or null if creation failed
     */
    fun create(options: Map<String, Any?>): CredentialService?
}

/**
 * Credential issuance options.
 */
data class CredentialIssuanceOptions(
    val providerName: String? = null,
    val proofType: String = "Ed25519Signature2020",
    val keyId: String? = null,
    val issuerDid: String? = null,
    val challenge: String? = null,
    val domain: String? = null,
    val anchorToBlockchain: Boolean = false,
    val chainId: String? = null,
    val additionalOptions: Map<String, Any?> = emptyMap()
)

/**
 * Credential verification options.
 */
data class CredentialVerificationOptions(
    val providerName: String? = null,
    val checkRevocation: Boolean = true,
    val checkExpiration: Boolean = true,
    val validateSchema: Boolean = false,
    val schemaId: String? = null,
    val verifyBlockchainAnchor: Boolean = false,
    val chainId: String? = null,
    val additionalOptions: Map<String, Any?> = emptyMap()
)

/**
 * Presentation creation options.
 */
data class PresentationOptions(
    val holderDid: String,
    val proofType: String = "Ed25519Signature2020",
    val keyId: String? = null,
    val challenge: String? = null,
    val domain: String? = null,
    val selectiveDisclosure: Boolean = false,
    val disclosedFields: List<String> = emptyList(),
    val additionalOptions: Map<String, Any?> = emptyMap()
)

/**
 * Presentation verification options.
 */
data class PresentationVerificationOptions(
    val providerName: String? = null,
    val verifyChallenge: Boolean = true,
    val expectedChallenge: String? = null,
    val verifyDomain: Boolean = false,
    val expectedDomain: String? = null,
    val checkRevocation: Boolean = true,
    val additionalOptions: Map<String, Any?> = emptyMap()
)

/**
 * Credential verification result.
 */
data class CredentialVerificationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val proofValid: Boolean = false,
    val issuerValid: Boolean = false,
    val notExpired: Boolean = false,
    val notRevoked: Boolean = false,
    val schemaValid: Boolean = false,
    val blockchainAnchorValid: Boolean = false
)

/**
 * Presentation verification result.
 */
data class PresentationVerificationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val presentationProofValid: Boolean = false,
    val challengeValid: Boolean = false,
    val domainValid: Boolean = false,
    val credentialResults: List<CredentialVerificationResult> = emptyList()
)

