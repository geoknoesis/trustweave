package com.trustweave.trust.dsl.credential

import com.trustweave.credential.CredentialService
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.credential.results.VerificationResult as CredentialVerificationResult
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.trust.types.VerificationResult
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

    /**
     * Set the credential to verify.
     */
    fun credential(credential: VerifiableCredential) {
        this.credential = credential
    }

    /**
     * Enable revocation checking.
     */
    fun checkRevocation() {
        this.checkRevocation = true
    }

    /**
     * Disable revocation checking.
     */
    fun skipRevocationCheck() {
        this.checkRevocation = false
    }

    /**
     * Enable expiration checking.
     */
    fun checkExpiration() {
        this.checkExpiration = true
    }

    /**
     * Disable expiration checking.
     */
    fun skipExpirationCheck() {
        this.checkExpiration = false
    }

    /**
     * Enable schema validation.
     */
    fun validateSchema(schemaId: String) {
        this.validateSchema = true
        this.schemaId = schemaId
    }

    /**
     * Disable schema validation.
     */
    fun skipSchemaValidation() {
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

        credentialService.verify(cred, trustPolicy = null, options = options)
    }
}

/**
 * Extension function to verify credentials using CredentialDslProvider.
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
suspend fun CredentialDslProvider.verify(block: VerificationBuilder.() -> Unit): VerificationResult {
    val dispatcher = (this as? com.trustweave.trust.dsl.TrustWeaveContext)?.getConfig()?.ioDispatcher
        ?: Dispatchers.IO
    val credentialService = getIssuer() as? CredentialService
        ?: throw IllegalStateException("CredentialService is not available. Configure it in TrustWeave.build { ... }")
    val builder = VerificationBuilder(
        credentialService = credentialService,
        ioDispatcher = dispatcher
    )
    builder.block()
    val credential = builder.credential ?: throw IllegalStateException("Credential is required")
    val verificationResult = builder.build()
    return VerificationResult.from(credential, verificationResult)
}

