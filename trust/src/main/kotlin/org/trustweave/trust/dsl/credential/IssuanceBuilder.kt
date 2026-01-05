package org.trustweave.trust.dsl.credential

import org.trustweave.credential.CredentialService
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.issuanceRequest
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.proof.ProofPurpose
import org.trustweave.credential.proof.proofOptionsForIssuance
import org.trustweave.trust.dsl.credential.CredentialBuilder
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.ProofType
import org.trustweave.trust.TrustWeave
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Optional DID resolver for auto-extracting key IDs from DIDs.
     * If provided, enables signedBy(Did) overload that auto-extracts key ID.
     */
    private val didResolver: org.trustweave.did.resolver.DidResolver? = null
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
     * Set issuer DID for signing, automatically extracting the key ID.
     *
     * This method resolves the DID and extracts the first verification method's
     * key ID automatically. The resolution happens during build().
     *
     * **Example:**
     * ```kotlin
     * val issuerDid = trustWeave.createDid().getOrFail()
     * val credential = trustWeave.issue {
     *     credential { ... }
     *     signedBy(issuerDid)  // Key ID auto-extracted during build
     * }
     * ```
     *
     * @param issuerDid The issuer DID (key ID will be auto-extracted during build)
     */
    fun signedBy(issuerDid: Did) {
        this.issuerDid = issuerDid
        // Key ID will be resolved during build() if didResolver is available
        this.issuerKeyId = null  // Mark for lazy resolution
    }

    /**
     * Set issuer DID and explicit key ID for signing.
     *
     * Use this method when you need to specify a particular key ID from the DID document.
     *
     * **Example:**
     * ```kotlin
     * val issuerDid = trustWeave.createDid().getOrFail()
     * val credential = trustWeave.issue {
     *     credential { ... }
     *     signedBy(issuerDid, "key-1")  // Explicit key ID
     * }
     * ```
     *
     * @param issuerDid The issuer DID
     * @param keyId The key ID (fragment part, e.g., "key-1")
     */
    fun signedBy(issuerDid: Did, keyId: String) {
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
        
        // Auto-resolve key ID if not provided but DID resolver is available
        val resolvedKeyId = issuerKeyId ?: run {
            val resolver = didResolver ?: return@run null
            val resolution = resolver.resolve(resolvedIssuerDid)
            when (resolution) {
                is org.trustweave.did.resolver.DidResolutionResult.Success -> {
                    resolution.document.verificationMethod.firstOrNull()?.let { vm ->
                        vm.id.value.substringAfter("#").takeIf { it.isNotEmpty() }
                    }
                }
                else -> null
            }
        } ?: return@withContext IssuanceResult.Failure.InvalidRequest(
            field = "issuerKeyId",
            reason = "Issuer key ID is required. Use signedBy(issuerDid, keyId) to specify the issuer, " +
                    "or ensure TrustWeave is properly configured with a DID resolver for auto-extraction."
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

// Note: TrustWeave.issue() is now a member function in TrustWeave class.
// This extension function has been removed to avoid duplication.
// Use trustWeave.issue { ... } directly.

