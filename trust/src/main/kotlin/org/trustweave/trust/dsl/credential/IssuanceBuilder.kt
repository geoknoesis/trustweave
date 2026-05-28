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
 * import org.trustweave.did.identifiers.Did
 * val issuerDid = Did("did:key:university")
 * val issuedCredential = trustWeave.issue {
 *     credential {
 *         id("https://example.edu/credentials/123")
 *         type("DegreeCredential")
 *         issuer(issuerDid)
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
 *     signedBy(issuerDid, "key-1")
 *     withProof(ProofType.Ed25519Signature2020)
 * }.getOrThrow()
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
     * Suite-specific options threaded into [ProofOptions.additionalOptions] when [build] runs.
     *
     * `internal` so that extension functions in sub-packages (e.g.
     * `org.trustweave.trust.dsl.credential.jades`) can configure proof-engine-specific knobs
     * without polluting the main DSL surface or forcing every caller to hand-build a
     * [ProofOptions] map.
     */
    internal val additionalProofOptions: MutableMap<String, Any?> = mutableMapOf()

    /**
     * Inject a suite-specific option into [ProofOptions.additionalOptions].
     *
     * Prefer typed extension functions (e.g. `withJadesProfile(...)`) where they exist; this
     * setter is the low-level escape hatch for engines that do not yet have a typed extension.
     */
    fun additionalOption(key: String, value: Any?) {
        require(key.isNotBlank()) { "Additional option key cannot be blank" }
        additionalProofOptions[key] = value
    }

    /**
     * Override the proof suite from outside this builder (used by typed extensions such as
     * `withJadesProfile` that imply a specific [ProofSuiteId]).
     */
    internal fun setProofSuite(suite: ProofSuiteId) {
        this.proofSuite = suite
    }

    /**
     * Guard flag: set to true the first time [build] runs so that a second call is rejected
     * before it can allocate another status-list index.
     * AtomicBoolean is used so that concurrent calls cannot both pass the guard simultaneously.
     */
    private val buildCalled = java.util.concurrent.atomic.AtomicBoolean(false)

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
     * val issuerDid = trustWeave.createDid { }.getOrThrowDid()
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
     * val issuerDid = trustWeave.createDid { }.getOrThrowDid()
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
     * **Must be called at most once per [IssuanceBuilder] instance.** When [withRevocation] is
     * enabled, a status-list index is allocated as part of this call.  Calling [build] a second
     * time would allocate a new (orphaned) index even if the first call failed, permanently
     * consuming capacity in the status list.  Create a new [IssuanceBuilder] instance for each
     * issuance attempt.
     *
     * This operation performs I/O-bound work (credential issuance, status list operations)
     * and uses the configured dispatcher. It is non-blocking and can be cancelled.
     *
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun build(): IssuanceResult = withContext(ioDispatcher) {
        // Prevent repeated calls from orphaning multiple status-list indices.
        // compareAndSet atomically flips false→true; if it returns false another call got here first.
        if (!buildCalled.compareAndSet(false, true)) {
            return@withContext IssuanceResult.Failure.InvalidRequest(
                field = "build",
                reason = "IssuanceBuilder.build() may only be called once per builder instance"
            )
        }

        val cred = credential ?: return@withContext IssuanceResult.Failure.InvalidRequest(
            field = "credential",
            reason = "Credential is required. Use credential { ... } to build the credential."
        )
        val resolvedIssuerDid = issuerDid ?: return@withContext IssuanceResult.Failure.InvalidRequest(
            field = "issuerIdentity",
            reason = "Issuer DID is required. Use signedBy(issuerDid, keyId) to specify the issuer."
        )
        
        // Auto-resolve key ID if not provided but DID resolver is available
        val resolvedKeyId: String? = if (issuerKeyId != null) {
            issuerKeyId
        } else {
            if (didResolver == null) {
                return@withContext IssuanceResult.Failure.InvalidRequest(
                    field = "issuerKeyId",
                    reason = "Issuer key ID is required and no DID resolver is configured for auto-extraction. " +
                        "Use signedBy(issuerDid, keyId) to specify the key ID explicitly."
                )
            }
            val resolution = didResolver.resolve(resolvedIssuerDid)
            when (resolution) {
                is org.trustweave.did.resolver.DidResolutionResult.Success -> {
                    resolution.document.verificationMethod.firstOrNull()?.let { vm ->
                        vm.id.value.substringAfter("#", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
                    } ?: return@withContext IssuanceResult.Failure.InvalidRequest(
                        field = "issuerKeyId",
                        reason = "DID '${resolvedIssuerDid.value}' resolved successfully but has no verification methods"
                    )
                }
                is org.trustweave.did.resolver.DidResolutionResult.Failure -> {
                    val errorMessage = when (resolution) {
                        is org.trustweave.did.resolver.DidResolutionResult.Failure.NotFound ->
                            resolution.reason ?: "DID not found"
                        is org.trustweave.did.resolver.DidResolutionResult.Failure.InvalidFormat ->
                            resolution.reason
                        is org.trustweave.did.resolver.DidResolutionResult.Failure.MethodNotRegistered ->
                            "DID method '${resolution.method}' is not registered"
                        is org.trustweave.did.resolver.DidResolutionResult.Failure.ResolutionError ->
                            resolution.reason
                        else -> resolution.toString()
                    }
                    return@withContext IssuanceResult.Failure.InvalidRequest(
                        field = "issuerDid",
                        reason = "Failed to resolve issuer DID '${resolvedIssuerDid.value}': $errorMessage"
                    )
                }
                else -> return@withContext IssuanceResult.Failure.InvalidRequest(
                    field = "issuerDid",
                    reason = "Unexpected resolution result for '${resolvedIssuerDid.value}'"
                )
            }
        }
        
        // Build verification method ID — normalize resolvedKeyId to just the fragment first so
        // that a full "did:example:abc#key-1" value and a bare "key-1" value both produce the
        // same result and there is no double-# ambiguity.
        // resolvedKeyId is guaranteed non-null here: every null-producing code path above
        // performs a return@withContext before reaching this line.
        val resolvedKeyIdNonNull = resolvedKeyId ?: return@withContext IssuanceResult.Failure.InvalidRequest(
            field = "issuerKeyId",
            reason = "Could not determine issuer key ID"
        )
        val normalizedKeyId = if (resolvedKeyIdNonNull.contains("#")) {
            resolvedKeyIdNonNull.substringAfter("#")
        } else {
            resolvedKeyIdNonNull
        }
        val verificationMethodId = "${resolvedIssuerDid.value}#$normalizedKeyId" // normalizedKeyId is non-null per guard above

        // Handle auto-revocation if enabled
        var credentialToIssue = cred
        if (autoRevocation && credentialToIssue.credentialStatus == null) {
            if (revocationManager != null) {
                // SEC-09: use findOrCreateStatusList to reuse an existing active status list for
                // the same issuer+purpose instead of creating a new list per credential, which
                // would exhaust status-list capacity very quickly.
                val statusListId = try {
                    revocationManager.findOrCreateStatusList(
                        issuerDid = resolvedIssuerDid.value,
                        purpose = StatusPurpose.REVOCATION
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext IssuanceResult.Failure.AdapterError(
                        format = proofSuite ?: defaultProofSuite,
                        reason = "Failed to find or create status list for revocation. " +
                                "This is required when withRevocation() is enabled. " +
                                "Error: ${e.message}",
                        cause = e
                    )
                }

                // Get a unique index for this credential.
                // If the credential has no ID yet, generate a UUID and assign it back so that
                // the status-list entry key and the issued credential ID are the same value.
                val credentialId = credentialToIssue.id?.value ?: java.util.UUID.randomUUID().toString()
                if (credentialToIssue.id == null) {
                    credentialToIssue = credentialToIssue.copy(
                        id = org.trustweave.credential.identifiers.CredentialId(credentialId)
                    )
                }
                val statusIndex = try {
                    revocationManager.assignCredentialIndex(credentialId, statusListId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext IssuanceResult.Failure.AdapterError(
                        format = proofSuite ?: defaultProofSuite,
                        reason = "Failed to assign credential status index: ${e.message}",
                        cause = e
                    )
                }

                // Add credential status to credential
                val credentialStatus = CredentialStatus(
                    id = StatusListId("urn:statuslist:${statusListId.value}#$statusIndex"),
                    type = "StatusList2021Entry",
                    statusPurpose = StatusPurpose.REVOCATION,
                    statusListIndex = statusIndex.toString(),
                    statusListCredential = statusListId
                )

                credentialToIssue = credentialToIssue.copy(credentialStatus = credentialStatus)
            } else {
                return@withContext IssuanceResult.Failure.AdapterError(
                    format = proofSuite ?: defaultProofSuite,
                    reason = "Revocation requested but revocation manager is not configured. " +
                            "Configure it in TrustWeave.build { revocation { provider(\"inMemory\") } }"
                )
            }
        }

        // Capture status-list coordinates (if allocated above) so that if issuance fails the
        // caller can learn which index was orphaned and attempt manual cleanup.
        // Known limitation: TrustWeave does not yet expose a releaseIndex() API; the caller
        // must interact with the CredentialRevocationManager directly to free the slot.
        val allocatedStatusListId = if (autoRevocation && credentialToIssue.credentialStatus != null) {
            credentialToIssue.credentialStatus?.statusListCredential
        } else null
        val allocatedStatusIndex = if (autoRevocation && credentialToIssue.credentialStatus != null) {
            credentialToIssue.credentialStatus?.statusListIndex
        } else null

        val proofSuiteToUse = proofSuite ?: defaultProofSuite

        val parsedVmId = try {
            VerificationMethodId.parse(verificationMethodId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext IssuanceResult.Failure.InvalidRequest(
                field = "issuerKeyId",
                reason = "Could not parse verification method ID '$verificationMethodId': ${e.message}"
            )
        }

        // Build IssuanceRequest from VerifiableCredential
        val request = IssuanceRequest(
            format = proofSuiteToUse,
            issuer = credentialToIssue.issuer,
            issuerKeyId = parsedVmId,
            credentialSubject = credentialToIssue.credentialSubject,
            type = credentialToIssue.type,
            id = credentialToIssue.id,
            issuedAt = credentialToIssue.issuanceDate ?: kotlinx.datetime.Clock.System.now(),  // null for VC 2.0-only credentials
            validFrom = credentialToIssue.validFrom,
            validUntil = credentialToIssue.validUntil ?: credentialToIssue.expirationDate,
            credentialStatus = credentialToIssue.credentialStatus,
            credentialSchema = credentialToIssue.credentialSchema,
            evidence = credentialToIssue.evidence,
            proofOptions = run {
                val base = if (challenge == null && domain == null) {
                    proofOptionsForIssuance(verificationMethod = verificationMethodId)
                } else {
                    ProofOptions(
                        purpose = ProofPurpose.AssertionMethod,
                        challenge = challenge,
                        domain = domain,
                        verificationMethod = verificationMethodId
                    )
                }
                if (additionalProofOptions.isEmpty()) base
                else base.withAdditionalOptions(additionalProofOptions.toMap())
            }
        )

        // Issue credential using CredentialService
        val issueResult = credentialService.issue(request)

        // If issuance failed after a status-list index was allocated, surface the orphaned
        // coordinates in the failure message so callers can release the slot manually via
        // CredentialRevocationManager (no built-in releaseIndex() exists yet — see TODO above).
        return@withContext when {
            issueResult is IssuanceResult.Failure && allocatedStatusListId != null -> {
                IssuanceResult.Failure.AdapterError(
                    format = proofSuiteToUse,
                    reason = "Credential issuance failed after status-list index was allocated " +
                             "(statusList=${allocatedStatusListId.value}, index=$allocatedStatusIndex). " +
                             "Original error: ${(issueResult as? IssuanceResult.Failure.AdapterError)?.reason ?: issueResult.toString()}",
                    cause = (issueResult as? IssuanceResult.Failure.AdapterError)?.cause
                )
            }
            else -> issueResult
        }
    }
}
