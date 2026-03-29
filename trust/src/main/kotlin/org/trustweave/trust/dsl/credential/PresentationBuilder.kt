package org.trustweave.trust.dsl.credential

import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.PresentationResult
import org.trustweave.credential.CredentialService
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.proof.ProofPurpose
import org.trustweave.credential.proof.proofOptionsForPresentation
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Presentation Builder DSL.
 *
 * Provides a fluent API for creating verifiable presentations.
 *
 * **Example Usage**:
 * ```kotlin
 * val pr = trustWeave.presentationResult {
 *     credentials(credential1, credential2, credential3)
 *     holder("did:key:holder")
 *     challenge("verification-challenge-123")
 *     domain("example.com")
 *     selectiveDisclosure { reveal("degree.name", "degree.university") }
 * }
 * // when (pr) { Success / Failure.* }
 * ```
 *
 * Published docs: `docs/api-reference/result-types-guide.md`, `docs/getting-started/api-patterns.md` (results vs exceptions).
 */
class PresentationBuilder(
    private val credentialService: CredentialService
) {
    private val credentials = mutableListOf<VerifiableCredential>()
    private var holderDid: Did? = null
    private var challenge: String? = null
    private var domain: String? = null
    private var verificationMethod: String? = null
    private val disclosedClaims = mutableSetOf<String>()

    /**
     * Add credentials to presentation.
     */
    fun credentials(vararg credentials: VerifiableCredential) {
        this.credentials.addAll(credentials)
    }

    /**
     * Add credentials from a list.
     */
    fun credentials(credentials: List<VerifiableCredential>) {
        this.credentials.addAll(credentials)
    }

    /**
     * Set holder DID (must be non-blank and start with `did:` — same rules as [WalletPresentationBuilder.holder]).
     */
    fun holder(did: String) {
        require(did.isNotBlank()) { "Holder DID cannot be blank" }
        require(did.startsWith("did:")) { "Holder DID must start with 'did:'. Got: $did" }
        this.holderDid = Did(did)
    }

    /**
     * Set holder DID.
     */
    fun holder(did: Did) {
        this.holderDid = did
    }

    /**
     * Set verification method for signing.
     */
    fun verificationMethod(verificationMethod: String) {
        this.verificationMethod = verificationMethod
    }

    /**
     * Set challenge.
     */
    fun challenge(challenge: String) {
        this.challenge = challenge
    }

    /**
     * Set domain.
     */
    fun domain(domain: String) {
        this.domain = domain
    }

    /**
     * Configure selective disclosure.
     */
    fun selectiveDisclosure(block: SelectiveDisclosureBuilder.() -> Unit) {
        val builder = SelectiveDisclosureBuilder()
        builder.block()
        disclosedClaims.addAll(builder.revealedFields)
    }

    /**
     * Build the verifiable presentation, returning a [PresentationResult] (no throw for validation or missing service).
     */
    suspend fun buildResult(): PresentationResult = withContext(Dispatchers.IO) {
        if (holderDid == null) {
            return@withContext PresentationResult.Failure.InvalidRequest(
                listOf(
                    "Holder DID is required. Use holder(holderDid) to specify the credential holder.",
                ),
            )
        }

        if (credentials.isEmpty()) {
            return@withContext PresentationResult.Failure.InvalidRequest(
                listOf(
                    "At least one credential is required. Use credentials(...) to add credentials to the presentation.",
                ),
            )
        }

        val challengeValue = challenge
        val request = PresentationRequest(
            disclosedClaims = if (disclosedClaims.isNotEmpty()) disclosedClaims else null,
            predicates = emptyList(),
            proofOptions = if (challengeValue != null) {
                proofOptionsForPresentation(
                    challenge = challengeValue,
                    domain = domain,
                    verificationMethod = verificationMethod,
                )
            } else {
                ProofOptions(
                    purpose = ProofPurpose.Authentication,
                    challenge = null,
                    domain = domain,
                    verificationMethod = verificationMethod,
                )
            },
        )

        try {
            PresentationResult.Success(
                credentialService.createPresentation(
                    credentials = credentials,
                    request = request,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PresentationResult.Failure.AdapterError(
                message = e.message ?: "Presentation creation failed",
                cause = e,
            )
        }
    }
}

/**
 * Selective Disclosure Builder.
 */
class SelectiveDisclosureBuilder {
    val revealedFields = mutableListOf<String>()

    /**
     * Reveal specific fields.
     */
    fun reveal(vararg fields: String) {
        revealedFields.addAll(fields)
    }
}

/**
 * Create a verifiable presentation, returning [PresentationResult] for exhaustive error handling.
 */
suspend fun TrustWeave.presentationResult(block: PresentationBuilder.() -> Unit): PresentationResult {
    val credentialService = getCredentialService()
        ?: return PresentationResult.Failure.AdapterNotReady(
            reason = "CredentialService is not available. Configure it in TrustWeave.build { ... }",
        )
    val builder = PresentationBuilder(credentialService)
    builder.block()
    return builder.buildResult()
}
