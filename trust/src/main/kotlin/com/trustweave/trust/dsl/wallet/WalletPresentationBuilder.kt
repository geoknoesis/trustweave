package com.trustweave.trust.dsl.wallet

import com.trustweave.trust.dsl.credential.SelectiveDisclosureBuilder
import com.trustweave.trust.dsl.credential.presentation
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation
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
    private val wallet: Wallet
) {
    private val credentialIds = mutableListOf<String>()
    private var queryBuilder: QueryBuilder? = null
    private var holderDid: String? = null
    private var proofType: String = "Ed25519Signature2020"
    private var keyId: String? = null
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
        presentation {
            credentials(credentials)
            holder(holder)
            proofType(proofType)
            keyId?.let { keyId(it) }
            challenge?.let { challenge(it) }
            domain?.let { domain(it) }
            if (selectiveDisclosure) {
                selectiveDisclosure {
                    reveal(*disclosedFields.toTypedArray())
                }
            }
        }
    }
}

/**
 * Extension function to create a presentation from wallet credentials.
 */
suspend fun Wallet.presentation(block: WalletPresentationBuilder.() -> Unit): VerifiablePresentation {
    val builder = WalletPresentationBuilder(this)
    builder.block()
    return builder.build()
}

