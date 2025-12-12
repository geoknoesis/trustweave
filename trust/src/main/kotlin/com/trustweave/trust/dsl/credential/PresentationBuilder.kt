package com.trustweave.trust.dsl.credential

import com.trustweave.credential.CredentialService
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.requests.PresentationRequest
import com.trustweave.credential.proof.ProofOptions
import com.trustweave.credential.proof.ProofPurpose
import com.trustweave.credential.proof.proofOptionsForPresentation
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Presentation Builder DSL.
 *
 * Provides a fluent API for creating verifiable presentations.
 *
 * **Example Usage**:
 * ```kotlin
 * val presentation = presentation {
 *     credentials(credential1, credential2, credential3)
 *     holder("did:key:holder")
 *     challenge("verification-challenge-123")
 *     domain("example.com")
 *     selectiveDisclosure {
 *         reveal("degree.name", "degree.university")
 *         hide("degree.gpa")
 *     }
 * }
 * ```
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
     * Set holder DID.
     */
    fun holder(did: String) {
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
     * Build the verifiable presentation.
     */
    suspend fun build(): VerifiablePresentation = withContext(Dispatchers.IO) {
        val holder = holderDid ?: throw IllegalStateException(
            "Holder DID is required. Use holder(holderDid) to specify the credential holder."
        )

        if (credentials.isEmpty()) {
            throw IllegalStateException(
                "At least one credential is required. Use credential(credential) or credentials(vararg credentials) to add credentials to the presentation."
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
                    verificationMethod = verificationMethod
                )
            } else {
                ProofOptions(
                    purpose = ProofPurpose.Authentication,
                    challenge = null,
                    domain = domain,
                    verificationMethod = verificationMethod
                )
            }
        )

        credentialService.createPresentation(
            credentials = credentials,
            request = request
        )
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

    /**
     * Hide specific fields.
     * 
     * **Note:** Currently, selective disclosure is based on revealed fields only.
     * Fields not in the reveal list are automatically hidden. This method is
     * reserved for future implementation of explicit field hiding.
     * 
     * @param fields Fields to hide (not yet implemented)
     */
    @Deprecated(
        message = "Not yet implemented. Use reveal() to specify visible fields. Fields not in reveal list are automatically hidden.",
        level = DeprecationLevel.WARNING
    )
    fun hide(vararg fields: String) {
        // For now, selective disclosure is based on revealed fields only
        // Fields not in reveal list are automatically hidden
        // TODO: Implement explicit field hiding
    }
}

/**
 * DSL function to create a presentation.
 */
suspend fun CredentialDslProvider.presentation(block: PresentationBuilder.() -> Unit): VerifiablePresentation {
    val credentialService = getIssuer() as? CredentialService
        ?: throw IllegalStateException("CredentialService is not available. Configure it in TrustWeave.build { ... }")
    val builder = PresentationBuilder(credentialService)
    builder.block()
    return builder.build()
}

