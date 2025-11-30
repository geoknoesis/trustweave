package com.trustweave.trust.dsl.credential

import com.trustweave.credential.PresentationOptions
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation
import com.trustweave.credential.presentation.PresentationService
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.verifier.CredentialVerifier
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
    private val presentationService: PresentationService = PresentationService()
) {
    private val credentials = mutableListOf<VerifiableCredential>()
    private var holderDid: String? = null
    private var proofType: String = "Ed25519Signature2020"
    private var keyId: String? = null
    private var challenge: String? = null
    private var domain: String? = null
    private var selectiveDisclosure: Boolean = false
    private val disclosedFields = mutableListOf<String>()

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
        this.holderDid = did
    }

    /**
     * Set proof type.
     */
    fun proofType(type: String) {
        this.proofType = type
    }

    /**
     * Set key ID for signing.
     */
    fun keyId(keyId: String) {
        this.keyId = keyId
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
        selectiveDisclosure = true
        val builder = SelectiveDisclosureBuilder()
        builder.block()
        disclosedFields.addAll(builder.revealedFields)
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

        val options = PresentationOptions(
            holderDid = holder,
            proofType = proofType,
            keyId = keyId,
            challenge = challenge,
            domain = domain,
            selectiveDisclosure = selectiveDisclosure,
            disclosedFields = disclosedFields
        )

        presentationService.createPresentation(
            credentials = credentials,
            holderDid = holder,
            options = options
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
suspend fun presentation(block: PresentationBuilder.() -> Unit): VerifiablePresentation {
    val builder = PresentationBuilder()
    builder.block()
    return builder.build()
}

/**
 * DSL function to create a presentation with a custom [PresentationService].
 */
suspend fun presentation(
    service: PresentationService,
    block: PresentationBuilder.() -> Unit
): VerifiablePresentation {
    val builder = PresentationBuilder(service)
    builder.block()
    return builder.build()
}

