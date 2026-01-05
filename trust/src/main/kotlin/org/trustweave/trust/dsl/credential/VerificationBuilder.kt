package org.trustweave.trust.dsl.credential

import org.trustweave.credential.CredentialService
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult as CredentialVerificationResult
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.credential.trust.TrustPolicy as CredentialTrustPolicy
import org.trustweave.trust.types.VerificationResult
import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.TrustPolicy
import org.trustweave.trust.TrustWeave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * Verification Builder DSL.
 *
 * Provides a fluent API for verifying credentials.
 * Focused only on credential-specific verification operations.
 *
 * **Example Usage**:
 * ```kotlin
 * val result = verifier.verify {
 *     credential(credential)
 *     checkRevocation()
 *     checkExpiration()
 *     validateSchema("https://example.edu/schemas/degree.json")
 * }
 * ```
 */
class VerificationBuilder(
    private val credentialService: CredentialService,
    /**
     * Coroutine dispatcher for I/O-bound operations.
     * Defaults to [Dispatchers.IO] if not provided.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    internal var credential: VerifiableCredential? = null
        private set
    private var checkRevocation: Boolean = true
    private var checkExpiration: Boolean = true
    private var validateSchema: Boolean = false
    private var schemaId: String? = null
    private var validateProofPurpose: Boolean = false
    private var trustPolicy: CredentialTrustPolicy? = null

    /**
     * Set the credential to verify.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)  // Set credential to verify
     *     checkRevocation()
     *     checkExpiration()
     * }
     * ```
     * 
     * @param credential The verifiable credential to verify
     */
    fun credential(credential: VerifiableCredential) {
        this.credential = credential
    }

    /**
     * Enable revocation checking.
     * 
     * This is enabled by default. Only needed to explicitly enable after disabling.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)
     *     checkRevocation()  // Explicitly enable (default)
     * }
     * ```
     */
    fun checkRevocation() {
        this.checkRevocation = true
    }

    /**
     * Disable revocation checking.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)
     *     skipRevocation()  // Skip revocation check
     * }
     * ```
     */
    fun skipRevocation() {
        this.checkRevocation = false
    }

    /**
     * Enable expiration checking.
     * 
     * This is enabled by default. Only needed to explicitly enable after disabling.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)
     *     checkExpiration()  // Explicitly enable (default)
     * }
     * ```
     */
    fun checkExpiration() {
        this.checkExpiration = true
    }

    /**
     * Disable expiration checking.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)
     *     skipExpiration()  // Skip expiration check
     * }
     * ```
     */
    fun skipExpiration() {
        this.checkExpiration = false
    }

    /**
     * Enable schema validation.
     * 
     * Validates the credential against a JSON Schema or SHACL shape.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)
     *     validateSchema("https://example.com/schemas/degree.json")
     * }
     * ```
     * 
     * @param schemaId The schema IRI to validate against
     */
    fun validateSchema(schemaId: String) {
        this.validateSchema = true
        this.schemaId = schemaId
    }

    /**
     * Disable schema validation.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)
     *     skipSchema()  // Skip schema validation
     * }
     * ```
     */
    fun skipSchema() {
        this.validateSchema = false
        this.schemaId = null
    }

    /**
     * Enable proof purpose validation.
     */
    fun validateProofPurpose() {
        this.validateProofPurpose = true
    }

    /**
     * Require issuer to be a trusted anchor in the trust registry.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)
     *     requireTrust(trustRegistry)
     * }
     * ```
     * 
     * @param registry The trust registry to check against
     */
    fun requireTrust(registry: TrustRegistry) {
        // TrustRegistry now implements CredentialTrustPolicy directly
        this.trustPolicy = registry
    }

    /**
     * Require a trust path from verifier to issuer.
     * 
     * **Example:**
     * ```kotlin
     * trustWeave.verify {
     *     credential(credential)
     *     requireTrustPath(trustRegistry, maxLength = 3)
     * }
     * ```
     * 
     * @param registry The trust registry to check against
     * @param maxPathLength Maximum length of trust path (default: 3)
     */
    fun requireTrustPath(registry: TrustRegistry, maxPathLength: Int = 3) {
        // TrustRegistry now implements CredentialTrustPolicy directly
        // Note: Trust path checking with maxPathLength constraint will be implemented in a future version
        this.trustPolicy = registry
    }

    /**
     * Use a custom trust policy.
     * 
     * **Example:**
     * ```kotlin
     * val policy = TrustPolicy.allowlist(trustedIssuers)
     * trustWeave.verify {
     *     credential(credential)
     *     withTrustPolicy(policy)
     * }
     * ```
     * 
     * @param policy The trust policy to use
     */
    fun withTrustPolicy(policy: CredentialTrustPolicy) {
        this.trustPolicy = policy
    }

    /**
     * Allow untrusted issuers (no trust checking).
     * This is the default behavior.
     */
    fun allowUntrusted() {
        this.trustPolicy = null
    }

    /**
     * Build and perform verification.
     * 
     * This operation performs I/O-bound work (credential verification, DID resolution, revocation checking)
     * and uses the configured dispatcher. It is non-blocking and can be cancelled.
     */
    suspend fun build(): CredentialVerificationResult = withContext(ioDispatcher) {
        val cred = credential ?: throw IllegalStateException(
            "Credential is required. Use credential(credential) to specify the credential to verify."
        )

        val options = VerificationOptions(
            checkRevocation = checkRevocation,
            checkExpiration = checkExpiration,
            checkNotBefore = true,
            resolveIssuerDid = true,
            validateSchema = validateSchema,
            schemaId = schemaId?.let { SchemaId(it) }
        )

        credentialService.verify(cred, trustPolicy = trustPolicy, options = options)
    }
}

/**
 * Extension function to verify credentials using TrustWeave.
 *
 * Returns a sealed VerificationResult for exhaustive error handling.
 *
 * **Example:**
 * ```kotlin
 * val result = trustWeave.verify {
 *     credential(credential)
 *     checkRevocation()
 * }
 *
 * when (result) {
 *     is VerificationResult.Valid -> println("Valid!")
 *     is VerificationResult.Invalid.Expired -> println("Expired")
 *     // ... compiler ensures all cases handled
 * }
 * ```
 */
// Note: TrustWeave.verify() is now a member function in TrustWeave class.
// This extension function has been removed to avoid duplication.
// Use trustWeave.verify { ... } directly.

