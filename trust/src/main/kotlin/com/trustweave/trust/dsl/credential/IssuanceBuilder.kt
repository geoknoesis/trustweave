package com.trustweave.trust.dsl.credential

import com.trustweave.credential.CredentialService
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.requests.IssuanceRequest
import com.trustweave.credential.requests.issuanceRequest
import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.revocation.CredentialRevocationManager
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.proof.ProofOptions
import com.trustweave.credential.proof.ProofPurpose
import com.trustweave.credential.proof.proofOptionsForIssuance
import com.trustweave.trust.dsl.credential.CredentialBuilder
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.credential.model.vc.Issuer
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
 *         issued(Clock.System.now())
 *         withRevocation() // Auto-creates status list if needed
 *     }
 *     signedBy("did:key:university", "key-1")
 *     withProof(ProofType.Ed25519Signature2020)
 * }
 * ```
 */
class IssuanceBuilder(
    private val credentialService: CredentialService,
    private val revocationManager: CredentialRevocationManager? = null,
    private val defaultProofSuite: ProofSuiteId = ProofSuiteId.VC_LD,
    /**
     * Coroutine dispatcher for I/O-bound operations.
     * Defaults to [Dispatchers.IO] if not provided.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var credential: VerifiableCredential? = null
    private var issuerDid: Did? = null
    private var issuerKeyId: String? = null
    private var proofSuite: ProofSuiteId? = null
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
     * @param issuer The issuer identity (DID)
     */
    fun signedBy(issuer: IssuerIdentity) {
        this.issuerDid = issuer
        // Extract keyId from DID if it's a full verification method ID
        // Otherwise, assume it's just the DID and keyId will be set separately
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
        this.issuerDid = Did(issuerDid)
        this.issuerKeyId = keyId
    }
    
    /**
     * Set issuer DID and key ID for signing.
     */
    fun signedBy(issuerDid: com.trustweave.did.identifiers.Did, keyId: String) {
        require(keyId.isNotBlank()) { "Key ID cannot be blank" }
        this.issuerDid = issuerDid
        this.issuerKeyId = keyId
    }

    /**
     * Set proof suite.
     *
     * @param suite The proof suite to use for signing
     */
    fun withProof(suite: ProofSuiteId) {
        this.proofSuite = suite
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
        val cred = credential ?: return@withContext IssuanceResult.Failure.InvalidRequest(
            field = "credential",
            reason = "Credential is required. Use credential { ... } to build the credential."
        )
        val resolvedIssuerDid = issuerDid ?: return@withContext IssuanceResult.Failure.InvalidRequest(
            field = "issuerIdentity",
            reason = "Issuer DID is required. Use signedBy(issuerDid, keyId) to specify the issuer."
        )
        val resolvedKeyId = issuerKeyId ?: return@withContext IssuanceResult.Failure.InvalidRequest(
            field = "issuerKeyId",
            reason = "Issuer key ID is required. Use signedBy(issuerDid, keyId) to specify the issuer."
        )
        
        // Build verification method ID
        val verificationMethodId = if (resolvedKeyId.contains("#")) {
            resolvedKeyId // Already a full verification method ID
        } else {
            "${resolvedIssuerDid.value}#$resolvedKeyId" // Construct full ID from DID + fragment
        }

        // Handle auto-revocation if enabled
        var credentialToIssue = cred
        if (autoRevocation && cred.credentialStatus == null) {
            if (revocationManager != null) {
                // Explicit error handling - don't silently fail
                val statusListId = try {
                    revocationManager.createStatusList(
                        issuerDid = resolvedIssuerDid.value,
                        purpose = StatusPurpose.REVOCATION
                    )
                } catch (e: Exception) {
                    return@withContext IssuanceResult.Failure.AdapterError(
                        format = proofSuite ?: defaultProofSuite,
                        reason = "Failed to create status list for revocation. " +
                                "This is required when withRevocation() is enabled. " +
                                "Error: ${e.message}",
                        cause = e
                    )
                }

                // Add credential status to credential
                val credentialStatus = CredentialStatus(
                    id = StatusListId("urn:statuslist:${statusListId.value}#0"),
                    type = "StatusList2021Entry",
                    statusPurpose = StatusPurpose.REVOCATION,
                    statusListIndex = "0",
                    statusListCredential = statusListId
                )

                credentialToIssue = cred.copy(credentialStatus = credentialStatus)
            } else {
                return@withContext IssuanceResult.Failure.AdapterError(
                    format = proofSuite ?: defaultProofSuite,
                    reason = "Revocation requested but revocation manager is not configured. " +
                            "Configure it in TrustWeave.build { revocation { provider(\"inMemory\") } }"
                )
            }
        }

        val proofSuiteToUse = proofSuite ?: defaultProofSuite

        // Build IssuanceRequest from VerifiableCredential
        val request = IssuanceRequest(
            format = proofSuiteToUse,
            issuer = cred.issuer, // Use issuer directly since IssuanceRequest accepts Issuer
            issuerKeyId = try {
                VerificationMethodId.parse(verificationMethodId)
            } catch (e: Exception) {
                null // If parsing fails, let CredentialService handle it
            },
            credentialSubject = cred.credentialSubject,
            type = cred.type,
            id = cred.id,
            issuedAt = cred.issuanceDate,
            validFrom = cred.validFrom,
            validUntil = cred.expirationDate,
            credentialStatus = credentialToIssue.credentialStatus,
            credentialSchema = cred.credentialSchema,
            evidence = cred.evidence,
            proofOptions = if (challenge == null && domain == null) {
                proofOptionsForIssuance(verificationMethod = verificationMethodId)
            } else {
                ProofOptions(
                    purpose = ProofPurpose.AssertionMethod,
                    challenge = challenge,
                    domain = domain,
                    verificationMethod = verificationMethodId
                )
            }
        )

        // Issue credential using CredentialService
        credentialService.issue(request)
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
    
    // Get CredentialService - try from getIssuer() or return error
    val issuerAny = getIssuer()
    val credentialService = issuerAny as? CredentialService
        ?: return IssuanceResult.Failure.AdapterNotReady(
            format = ProofSuiteId.VC_LD,
            reason = "CredentialService is not available. Issuer must be a CredentialService instance."
        )
    
    // Convert ProofType to ProofSuiteId
    val defaultProofType = getDefaultProofType()
    val defaultProofSuite = when (defaultProofType) {
        is com.trustweave.credential.model.ProofType.Ed25519Signature2020 -> ProofSuiteId.VC_LD
        is com.trustweave.credential.model.ProofType.JsonWebSignature2020 -> ProofSuiteId.VC_JWT
        is com.trustweave.credential.model.ProofType.BbsBlsSignature2020 -> ProofSuiteId.SD_JWT_VC
        is com.trustweave.credential.model.ProofType.Custom -> ProofSuiteId.VC_LD // Default fallback
    }
    
    val builder = IssuanceBuilder(
        credentialService = credentialService,
        revocationManager = getRevocationManager() as? CredentialRevocationManager,
        defaultProofSuite = defaultProofSuite,
        ioDispatcher = dispatcher
    )
    builder.block()
    return builder.build()
}

