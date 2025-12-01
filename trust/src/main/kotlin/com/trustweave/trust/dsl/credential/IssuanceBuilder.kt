package com.trustweave.trust.dsl.credential

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.CredentialStatus
import com.trustweave.credential.revocation.StatusPurpose
import com.trustweave.trust.dsl.credential.CredentialBuilder
import com.trustweave.credential.issuer.CredentialIssuer
import com.trustweave.credential.revocation.StatusListManager
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.trust.types.ProofType
import com.trustweave.trust.types.Did
import com.trustweave.trust.types.KeyId
import com.trustweave.trust.types.IssuanceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Issuance Builder DSL.
 *
 * Provides a fluent API for issuing credentials.
 * Focused only on credential-specific operations.
 *
 * **Example Usage**:
 * ```kotlin
 * val issuedCredential = issuer.issue {
 *     credential {
 *         id("https://example.edu/credentials/123")
 *         type("DegreeCredential")
 *         issuer("did:key:university")
 *         subject {
 *             id("did:key:student")
 *             "degree" {
 *                 "type" to "BachelorDegree"
 *                 "name" to "Bachelor of Science"
 *             }
 *         }
 *         issued(Instant.now())
 *         withRevocation() // Auto-creates status list if needed
 *     }
 *     signedBy("did:key:university", "key-1")
 *     withProof(ProofType.Ed25519Signature2020)
 * }
 * ```
 */
class IssuanceBuilder(
    private val issuer: CredentialIssuer,
    private val statusListManager: StatusListManager? = null,
    private val defaultProofType: ProofType = ProofType.Ed25519Signature2020,
    /**
     * Coroutine dispatcher for I/O-bound operations.
     * Defaults to [Dispatchers.IO] if not provided.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var credential: VerifiableCredential? = null
    private var issuerIdentity: IssuerIdentity? = null
    private var proofType: ProofType? = null
    private var challenge: String? = null
    private var domain: String? = null
    private var autoRevocation: Boolean = false

    /**
     * Set the credential to issue (can be built inline).
     */
    fun credential(block: CredentialBuilder.() -> Unit) {
        val builder = CredentialBuilder()
        builder.block()
        credential = builder.build()
    }

    /**
     * Set the credential directly.
     */
    fun credential(credential: VerifiableCredential) {
        this.credential = credential
    }

    /**
     * Set issuer identity for signing.
     *
     * @param issuer The issuer identity containing DID and key ID
     */
    fun signedBy(issuer: IssuerIdentity) {
        this.issuerIdentity = issuer
    }

    /**
     * Set issuer identity for signing from DID and key ID strings.
     *
     * Domain-precise naming that clearly indicates signing operation.
     *
     * @param issuerDid Must be a valid DID starting with "did:"
     * @param keyId The key ID (can be just the fragment or full key ID)
     * @throws IllegalArgumentException if issuerDid is blank, doesn't start with "did:", or keyId is blank
     */
    fun signedBy(issuerDid: String, keyId: String) {
        require(issuerDid.isNotBlank()) { "Issuer DID cannot be blank" }
        require(issuerDid.startsWith("did:")) { 
            "Issuer DID must start with 'did:'. Got: $issuerDid" 
        }
        require(keyId.isNotBlank()) { "Key ID cannot be blank" }
        this.issuerIdentity = IssuerIdentity.from(issuerDid, keyId)
    }

    /**
     * Set proof type.
     *
     * @param type The proof type to use for signing
     */
    fun withProof(type: ProofType) {
        this.proofType = type
    }

    /**
     * Set challenge for proof.
     */
    fun challenge(challenge: String) {
        this.challenge = challenge
    }

    /**
     * Set domain for proof.
     */
    fun domain(domain: String) {
        this.domain = domain
    }

    /**
     * Enable automatic revocation support (creates status list if needed).
     */
    fun withRevocation() {
        this.autoRevocation = true
    }

    /**
     * Build and issue the credential.
     * 
     * This operation performs I/O-bound work (credential issuance, status list operations)
     * and uses the configured dispatcher. It is non-blocking and can be cancelled.
     *
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun build(): IssuanceResult = withContext(ioDispatcher) {
        val cred = credential ?: return@withContext IssuanceResult.Failure.InvalidCredential(
            reason = "Credential is required. Use credential { ... } to build the credential."
        )
        val resolvedIssuerIdentity = issuerIdentity ?: return@withContext IssuanceResult.Failure.InvalidCredential(
            reason = "Issuer identity is required. Use signedBy(IssuerIdentity.from(issuerDid, keyId)) to specify the issuer."
        )

        // Handle auto-revocation if enabled
        var credentialToIssue = cred
        if (autoRevocation && cred.credentialStatus == null) {
            if (statusListManager != null) {
                // Explicit error handling - don't silently fail
                val statusList = try {
                    statusListManager.createStatusList(
                        issuerDid = resolvedIssuerIdentity.did.value,
                        purpose = StatusPurpose.REVOCATION
                    )
                } catch (e: Exception) {
                    return@withContext IssuanceResult.Failure.Other(
                        reason = "Failed to create status list for revocation. " +
                                "This is required when withRevocation() is enabled. " +
                                "Error: ${e.message}",
                        cause = e
                    )
                }

                // Add credential status to credential
                val credentialStatus = CredentialStatus(
                    id = "${statusList.id}#0",
                    type = "StatusList2021Entry",
                    statusPurpose = "revocation",
                    statusListIndex = "0",
                    statusListCredential = statusList.id
                )

                credentialToIssue = cred.copy(credentialStatus = credentialStatus)
            } else {
                return@withContext IssuanceResult.Failure.Other(
                    reason = "Revocation requested but status list manager is not configured. " +
                            "Configure it in TrustWeave.build { revocation { provider(\"inMemory\") } }"
                )
            }
        }

        val proofTypeToUse = proofType ?: defaultProofType

        val options = CredentialIssuanceOptions(
            proofType = proofTypeToUse.value,
            keyId = resolvedIssuerIdentity.keyId.value,
            issuerDid = resolvedIssuerIdentity.did.value,
            challenge = challenge,
            domain = domain,
            anchorToBlockchain = false, // Anchoring should be handled by orchestration layer
            chainId = null,
            additionalOptions = mapOf("verificationMethod" to resolvedIssuerIdentity.verificationMethodId)
        )

        // Issue credential - use the class property issuer (CredentialIssuer instance)
        try {
            val issuedCredential = issuer.issue(
                credential = credentialToIssue,
                issuerDid = resolvedIssuerIdentity.did.value,
                keyId = resolvedIssuerIdentity.keyId.value,
                options = options
            )
            IssuanceResult.Success(issuedCredential)
        } catch (e: com.trustweave.did.exception.DidException.DidResolutionFailed) {
            IssuanceResult.Failure.IssuerResolutionFailed(
                issuerDid = resolvedIssuerIdentity.did.value,
                reason = e.message ?: "Failed to resolve issuer DID"
            )
        } catch (e: com.trustweave.kms.exception.KmsException.KeyNotFound) {
            IssuanceResult.Failure.KeyNotFound(
                keyId = resolvedIssuerIdentity.keyId.value,
                reason = e.message ?: "Key not found"
            )
        } catch (e: com.trustweave.credential.exception.CredentialException) {
            when (e) {
                is com.trustweave.credential.exception.CredentialException.CredentialIssuanceFailed -> {
                    IssuanceResult.Failure.Other(
                        reason = e.reason ?: "Credential issuance failed",
                        cause = e
                    )
                }
                else -> {
                    IssuanceResult.Failure.Other(
                        reason = e.message ?: "Credential error",
                        cause = e
                    )
                }
            }
        } catch (e: IllegalArgumentException) {
            IssuanceResult.Failure.InvalidCredential(
                reason = e.message ?: "Invalid credential parameters"
            )
        } catch (e: Exception) {
            IssuanceResult.Failure.Other(
                reason = e.message ?: "Unknown error during credential issuance",
                cause = e
            )
        }
    }
}

/**
 * Extension function to issue credentials using CredentialDslProvider.
 * 
 * Uses the configured dispatcher if the provider is a TrustWeaveContext,
 * otherwise defaults to Dispatchers.IO.
 *
 * @return Sealed result type with success or detailed failure information
 */
suspend fun CredentialDslProvider.issue(block: IssuanceBuilder.() -> Unit): IssuanceResult {
    val dispatcher = (this as? com.trustweave.trust.dsl.TrustWeaveContext)?.getConfig()?.ioDispatcher
        ?: Dispatchers.IO
    val builder = IssuanceBuilder(
        issuer = getIssuer(),
        statusListManager = getStatusListManager(),
        defaultProofType = getDefaultProofType(),
        ioDispatcher = dispatcher
    )
    builder.block()
    return builder.build()
}

