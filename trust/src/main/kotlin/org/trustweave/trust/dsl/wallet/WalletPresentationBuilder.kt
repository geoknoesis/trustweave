package org.trustweave.trust.dsl.wallet

import org.trustweave.trust.dsl.credential.SelectiveDisclosureBuilder
import org.trustweave.trust.dsl.credential.PresentationBuilder
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.PresentationResult
import org.trustweave.credential.CredentialService
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.trust.dsl.wallet.QueryBuilder
import org.trustweave.wallet.Wallet
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
 * val pr = trustWeave.presentationFromWalletResult(wallet) {
 *     fromWallet(masterId, job1Id, job2Id)
 *     holder(professionalDid.value)
 *     selectiveDisclosure { reveal("degree.field", "employment.company") }
 * }
 * // when (pr) { Success / Failure.* }
 * ```
 *
 * Published docs: `docs/api-reference/result-types-guide.md`, `docs/how-to/create-presentations.md`.
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
     * Build the verifiable presentation as a [PresentationResult].
     */
    suspend fun buildResult(): PresentationResult = withContext(Dispatchers.IO) {
        val holder = holderDid ?: return@withContext PresentationResult.Failure.InvalidRequest(
            listOf("Holder DID is required"),
        )

        val credentials = mutableListOf<VerifiableCredential>()
        for (credId in credentialIds) {
            val vc = wallet.get(credId)
                ?: return@withContext PresentationResult.Failure.InvalidRequest(
                    listOf("Credential not found in wallet: $credId"),
                )
            credentials.add(vc)
        }
        queryBuilder?.let { credentials.addAll(it.execute()) }

        if (credentials.isEmpty()) {
            return@withContext PresentationResult.Failure.InvalidRequest(
                listOf("At least one credential is required"),
            )
        }

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
        presentationBuilder.buildResult()
    }
}

/**
 * Create a presentation from wallet credentials, returning [PresentationResult].
 */
suspend fun TrustWeave.presentationFromWalletResult(
    wallet: Wallet,
    block: WalletPresentationBuilder.() -> Unit,
): PresentationResult {
    val credentialService = getCredentialService()
        ?: return PresentationResult.Failure.AdapterNotReady(
            reason = "CredentialService is not available. Configure it in TrustWeave.build { ... }",
        )
    val builder = WalletPresentationBuilder(wallet, credentialService)
    builder.block()
    return builder.buildResult()
}
