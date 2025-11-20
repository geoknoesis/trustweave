package com.geoknoesis.vericore.credential

import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.models.VerifiablePresentation
import com.geoknoesis.vericore.spi.SchemaFormat

/**
 * Pluggable credential service interface.
 * 
 * Implementations can wrap walt.id, godiddy, or provide native implementations.
 * This interface provides a unified API for credential operations regardless
 * of the underlying provider.
 * 
 * Implementations are typically registered with a `CredentialServiceRegistry`
 * that is carried inside `VeriCoreContext`.
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
    fun create(options: CredentialServiceCreationOptions = CredentialServiceCreationOptions()): CredentialService?
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
    val checkTrustRegistry: Boolean = false,
    val trustRegistry: Any? = null, // TrustRegistry - using Any to avoid dependency
    val statusListManager: Any? = null, // StatusListManager - using Any to avoid dependency
    val verifyDelegation: Boolean = false,
    val validateProofPurpose: Boolean = false,
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
    val additionalOptions: Map<String, Any?> = emptyMap(),
    val providerName: String? = null
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
    val blockchainAnchorValid: Boolean = false,
    val trustRegistryValid: Boolean = false,
    val delegationValid: Boolean = false,
    val proofPurposeValid: Boolean = false
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

/**
 * Structured configuration passed to [CredentialServiceProvider.create].
 *
 * Keeps common toggles explicit while still allowing provider-specific
 * properties through [additionalProperties]. Providers that still expect legacy
 * map-based configuration can use [toLegacyMap].
 */
data class CredentialServiceCreationOptions(
    val enabled: Boolean = true,
    val priority: Int? = null,
    val endpoint: String? = null,
    val apiKey: String? = null,
    val additionalProperties: Map<String, Any?> = emptyMap()
) {
    fun toLegacyMap(): Map<String, Any?> = buildMap {
        put("enabled", enabled)
        priority?.let { put("priority", it) }
        endpoint?.let { put("endpoint", it) }
        apiKey?.let { put("apiKey", it) }
        putAll(additionalProperties)
    }
}

class CredentialServiceCreationOptionsBuilder {
    var enabled: Boolean = true
    var priority: Int? = null
    var endpoint: String? = null
    var apiKey: String? = null
    private val properties = mutableMapOf<String, Any?>()

    fun property(key: String, value: Any?) {
        properties[key] = value
    }

    fun build(): CredentialServiceCreationOptions =
        CredentialServiceCreationOptions(
            enabled = enabled,
            priority = priority,
            endpoint = endpoint,
            apiKey = apiKey,
            additionalProperties = properties.toMap()
        )
}

fun credentialServiceCreationOptions(
    block: CredentialServiceCreationOptionsBuilder.() -> Unit
): CredentialServiceCreationOptions {
    val builder = CredentialServiceCreationOptionsBuilder()
    builder.block()
    return builder.build()
}

