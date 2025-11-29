package com.trustweave.credential

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation

/**
 * Pluggable credential service interface.
 *
 * Implementations can wrap walt.id, godiddy, or provide native implementations.
 * This interface provides a unified API for credential operations regardless
 * of the underlying provider.
 *
 * Implementations are typically registered with a `CredentialServiceRegistry`
 * that is carried inside `TrustWeaveContext`.
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
 *
 * Sealed class for exhaustive handling of verification outcomes.
 * Provides type-safe result handling with detailed error information.
 *
 * **Example Usage:**
 * ```kotlin
 * when (val result = verifier.verifyCredential(credential)) {
 *     is CredentialVerificationResult.Valid -> {
 *         println("Valid: ${result.credential.id}")
 *     }
 *     is CredentialVerificationResult.Invalid.Expired -> {
 *         println("Expired at ${result.expiredAt}")
 *     }
 *     is CredentialVerificationResult.Invalid.Revoked -> {
 *         println("Revoked at ${result.revokedAt}")
 *     }
 *     // Compiler ensures all cases handled
 * }
 * ```
 */
sealed class CredentialVerificationResult {
    /**
     * Credential verification succeeded.
     *
     * @param credential The verified credential
     * @param warnings Optional warnings (e.g., expiring soon)
     */
    data class Valid(
        val credential: com.trustweave.credential.models.VerifiableCredential,
        val warnings: List<String> = emptyList()
    ) : CredentialVerificationResult()

    /**
     * Credential verification failed.
     */
    sealed class Invalid : CredentialVerificationResult() {
        /**
         * Credential has expired.
         *
         * @param credential The expired credential
         * @param expiredAt When the credential expired
         * @param errors Additional error messages
         */
        data class Expired(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val expiredAt: java.time.Instant,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Credential has been revoked.
         *
         * @param credential The revoked credential
         * @param revokedAt When the credential was revoked
         * @param errors Additional error messages
         */
        data class Revoked(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val revokedAt: java.time.Instant? = null,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Proof signature is invalid.
         *
         * @param credential The credential with invalid proof
         * @param reason Reason for proof failure
         * @param errors Additional error messages
         */
        data class InvalidProof(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val reason: String,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Issuer DID could not be resolved or is invalid.
         *
         * @param credential The credential with invalid issuer
         * @param issuerDid The issuer DID that failed
         * @param reason Reason for failure
         * @param errors Additional error messages
         */
        data class InvalidIssuer(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val issuerDid: String,
            val reason: String,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Issuer is not trusted in the trust registry.
         *
         * @param credential The credential with untrusted issuer
         * @param issuerDid The untrusted issuer DID
         * @param errors Additional error messages
         */
        data class UntrustedIssuer(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val issuerDid: String,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Schema validation failed.
         *
         * @param credential The credential that failed schema validation
         * @param schemaId The schema ID that was validated
         * @param errors Schema validation errors
         */
        data class SchemaValidationFailed(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val schemaId: String?,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Blockchain anchor verification failed.
         *
         * @param credential The credential with invalid blockchain anchor
         * @param reason Reason for failure
         * @param errors Additional error messages
         */
        data class InvalidBlockchainAnchor(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val reason: String,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Proof purpose validation failed.
         *
         * @param credential The credential with invalid proof purpose
         * @param requiredPurpose The required proof purpose
         * @param actualPurpose The actual proof purpose
         * @param errors Additional error messages
         */
        data class InvalidProofPurpose(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val requiredPurpose: String,
            val actualPurpose: String?,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Delegation verification failed.
         *
         * @param credential The credential with invalid delegation
         * @param reason Reason for failure
         * @param errors Additional error messages
         */
        data class InvalidDelegation(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val reason: String,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        ) : Invalid()

        /**
         * Multiple validation failures.
         *
         * @param credential The credential that failed multiple checks
         * @param errors All error messages
         * @param warnings Optional warnings
         */
        data class MultipleFailures(
            val credential: com.trustweave.credential.models.VerifiableCredential,
            val errors: List<String>,
            val warnings: List<String> = emptyList()
        ) : Invalid()
    }

    /**
     * Convenience property to check if result is valid.
     */
    val isValid: Boolean
        get() = this is Valid

    /**
     * Get all errors from the result.
     */
    val allErrors: List<String>
        get() = when (this) {
            is Valid -> emptyList()
            is Invalid.Expired -> this.errors
            is Invalid.Revoked -> this.errors
            is Invalid.InvalidProof -> this.errors
            is Invalid.InvalidIssuer -> this.errors
            is Invalid.UntrustedIssuer -> this.errors
            is Invalid.SchemaValidationFailed -> this.errors
            is Invalid.InvalidBlockchainAnchor -> this.errors
            is Invalid.InvalidProofPurpose -> this.errors
            is Invalid.InvalidDelegation -> this.errors
            is Invalid.MultipleFailures -> this.errors
        }

    /**
     * Get all warnings from the result.
     */
    val allWarnings: List<String>
        get() = when (this) {
            is Valid -> this.warnings
            is Invalid.Expired -> this.warnings
            is Invalid.Revoked -> this.warnings
            is Invalid.InvalidProof -> this.warnings
            is Invalid.InvalidIssuer -> this.warnings
            is Invalid.UntrustedIssuer -> this.warnings
            is Invalid.SchemaValidationFailed -> this.warnings
            is Invalid.InvalidBlockchainAnchor -> this.warnings
            is Invalid.InvalidProofPurpose -> this.warnings
            is Invalid.InvalidDelegation -> this.warnings
            is Invalid.MultipleFailures -> this.warnings
        }

    /**
     * Backward compatibility: alias for isValid.
     */
    val valid: Boolean
        get() = isValid

    /**
     * Backward compatibility: check if proof is valid.
     */
    val proofValid: Boolean
        get() = when (this) {
            is Valid -> true
            is Invalid.InvalidProof -> false
            else -> {
                // Check if there are proof-related errors even if result type is not proof-specific
                // Check for "no proof", "proof signature", "proof verification", or just "proof" in error context
                !allErrors.any { 
                    val lower = it.lowercase()
                    lower.contains("no proof") || 
                    (lower.contains("proof") && (lower.contains("signature") || lower.contains("verification") || lower.contains("failed")))
                }
            }
        }

    /**
     * Backward compatibility: check if issuer is valid.
     */
    val issuerValid: Boolean
        get() = when (this) {
            is Valid -> true
            is Invalid.InvalidIssuer -> false
            is Invalid.UntrustedIssuer -> false
            else -> {
                // Check if there are issuer-related errors even if result type is not issuer-specific
                !allErrors.any { it.contains("issuer", ignoreCase = true) || it.contains("DID", ignoreCase = true) && it.contains("resolve", ignoreCase = true) }
            }
        }

    /**
     * Backward compatibility: check if credential is not expired.
     */
    val notExpired: Boolean
        get() = this !is Invalid.Expired

    /**
     * Backward compatibility: check if credential is not revoked.
     */
    val notRevoked: Boolean
        get() = this !is Invalid.Revoked

    /**
     * Backward compatibility: check if schema validation passed.
     */
    val schemaValid: Boolean
        get() = this !is Invalid.SchemaValidationFailed

    /**
     * Backward compatibility: check if blockchain anchor is valid.
     */
    val blockchainAnchorValid: Boolean
        get() = this !is Invalid.InvalidBlockchainAnchor
}

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
 * properties through [additionalProperties].
 */
data class CredentialServiceCreationOptions(
    val enabled: Boolean = true,
    val priority: Int? = null,
    val endpoint: String? = null,
    val apiKey: String? = null,
    val additionalProperties: Map<String, Any?> = emptyMap()
)

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

