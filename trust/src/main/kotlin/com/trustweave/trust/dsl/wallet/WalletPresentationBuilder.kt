package com.trustweave.trust.dsl.wallet

import com.trustweave.trust.dsl.credential.SelectiveDisclosureBuilder
import com.trustweave.trust.dsl.credential.PresentationBuilder
import com.trustweave.trust.dsl.credential.CredentialDslProvider
import com.trustweave.credential.CredentialService
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.trust.dsl.wallet.QueryBuilder
import com.trustweave.wallet.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wallet Presentation Builder DSL.
 *
 * Provides a fluent API for creating presentations from wallet credentials.
 * Automatically retrieves credentials from wallet by ID or query.
 *
 * **Example Usage**:
 * ```kotlin
 * // Create presentation from credential IDs
 * val presentation = wallet.presentation {
 *     fromWallet(masterId, job1Id, job2Id)
 *     holder(professionalDid.id)
 *     selectiveDisclosure {
 *         reveal("degree.field", "employment.company")
 *     }
 * }
 *
 * // Create presentation from query
 * val presentation = wallet.presentation {
 *     fromQuery {
 *         type("CertificationCredential")
 *         notExpired()
 *     }
 *     holder(professionalDid.id)
 * }
 * ```
 */
class WalletPresentationBuilder(
    private val wallet: Wallet,
    private val credentialService: CredentialService
) {
    private val credentialIds = mutableListOf<String>()
    private var queryBuilder: QueryBuilder? = null
    private var holderDid: String? = null
    private var verificationMethod: String? = null
    private var challenge: String? = null
    private var domain: String? = null
    private var selectiveDisclosure: Boolean = false
    private val disclosedFields = mutableListOf<String>()

    /**
     * Add credentials from wallet by ID.
     */
    fun fromWallet(vararg credentialIds: String) {
        this.credentialIds.addAll(credentialIds)
    }

    /**
     * Add credentials from wallet by ID list.
     */
    fun fromWallet(credentialIds: List<String>) {
        this.credentialIds.addAll(credentialIds)
    }

    /**
     * Add credentials from wallet query.
     */
    fun fromQuery(block: QueryBuilder.() -> Unit) {
        val builder = QueryBuilder(wallet)
        builder.block()
        queryBuilder = builder
    }

    /**
     * Set holder DID.
     * 
     * @param did Must be a valid DID starting with "did:"
     * @throws IllegalArgumentException if did is blank or doesn't start with "did:"
     */
    fun holder(did: String) {
        require(did.isNotBlank()) { "Holder DID cannot be blank" }
        require(did.startsWith("did:")) { 
            "Holder DID must start with 'did:'. Got: $did" 
        }
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
        selectiveDisclosure = true
        val builder = SelectiveDisclosureBuilder()
        builder.block()
        disclosedFields.addAll(builder.revealedFields)
    }

    /**
     * Build the verifiable presentation.
     * 
     * This operation performs I/O-bound work (credential retrieval, presentation creation)
     * and is dispatched to I/O threads. It is non-blocking and can be cancelled.
     *
     * @return Verifiable presentation
     */
    suspend fun build(): VerifiablePresentation = withContext(Dispatchers.IO) {
        val holder = holderDid ?: throw IllegalStateException("Holder DID is required")

        // Get credentials from wallet
        val credentials = mutableListOf<VerifiableCredential>()

        // Add credentials from IDs
        for (credId in credentialIds) {
            val cred = wallet.get(credId)
            if (cred != null) {
                credentials.add(cred)
            }
        }

        // Add credentials from query
        queryBuilder?.let { builder ->
            credentials.addAll(builder.execute())
        }

        if (credentials.isEmpty()) {
            throw IllegalStateException("At least one credential is required")
        }

        // Use existing presentation builder
        val presentationBuilder = PresentationBuilder(credentialService)
        presentationBuilder.credentials(credentials)
        presentationBuilder.holder(holder)
        verificationMethod?.let { presentationBuilder.verificationMethod(it) }
        challenge?.let { presentationBuilder.challenge(it) }
        domain?.let { presentationBuilder.domain(it) }
        if (selectiveDisclosure) {
            presentationBuilder.selectiveDisclosure {
                reveal(*disclosedFields.toTypedArray())
            }
        }
        presentationBuilder.build()
    }
}

/**
 * Extension function to create a presentation from wallet credentials.
 */
suspend fun CredentialDslProvider.presentationFromWallet(
    wallet: Wallet,
    block: WalletPresentationBuilder.() -> Unit
): VerifiablePresentation {
    val issuer = getIssuer()
    val credentialService = issuer as? CredentialService
        ?: throw IllegalStateException("CredentialService is not available. Configure it in TrustWeave.build { ... }")
    val builder = WalletPresentationBuilder(wallet, credentialService)
    builder.block()
    return builder.build()
}

