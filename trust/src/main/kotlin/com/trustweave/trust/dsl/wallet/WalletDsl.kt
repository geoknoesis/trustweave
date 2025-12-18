package com.trustweave.trust.dsl.wallet

import com.trustweave.wallet.Wallet
import com.trustweave.wallet.services.WalletCreationOptionsBuilder
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wallet Builder DSL.
 *
 * Provides a fluent API for creating and configuring wallets using a wallet DSL provider.
 *
 * **Example Usage**:
 * ```kotlin
 * val walletProvider: WalletDslProvider = ...
 * val wallet = walletProvider.wallet {
 *     id("my-wallet")
 *     holder("did:key:holder")
 *     enableOrganization()
 *     enablePresentation()
 * }
 * ```
 */
class WalletBuilder(
    private val dslProvider: WalletDslProvider
) {
    private var walletId: String? = null
    private var holderDid: String? = null
    private var walletDid: String? = null
    private var provider: String = "inMemory" // Default to inMemory for testing
    private val optionsBuilder = WalletCreationOptionsBuilder()

    /**
     * Set wallet ID.
     */
    fun id(walletId: String) {
        this.walletId = walletId
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
     * Set wallet holder DID.
     */
    fun holder(did: Did) {
        this.holderDid = did.value
    }

    /**
     * Set wallet DID.
     * 
     * @param did Must be a valid DID starting with "did:"
     * @throws IllegalArgumentException if did is blank or doesn't start with "did:"
     */
    fun walletDid(did: String) {
        require(did.isNotBlank()) { "Wallet DID cannot be blank" }
        require(did.startsWith("did:")) { 
            "Wallet DID must start with 'did:'. Got: $did" 
        }
        this.walletDid = did
    }

    /**
     * Set wallet provider (e.g., "inMemory", "basic", "database", "file").
     */
    fun provider(name: String) {
        this.provider = name
    }

    /**
     * Use in-memory wallet (for testing).
     */
    fun inMemory() {
        this.provider = "inMemory"
    }

    /**
     * Use basic wallet (minimal features).
     */
    fun basic() {
        this.provider = "basic"
    }

    /**
     * Add custom option for wallet creation.
     */
    fun option(key: String, value: Any?) {
        optionsBuilder.property(key, value)
    }

    /**
     * Enable organization capabilities (collections, tags).
     */
    fun enableOrganization() {
        this.optionsBuilder.enableOrganization = true
    }

    /**
     * Enable presentation capabilities.
     */
    fun enablePresentation() {
        this.optionsBuilder.enablePresentation = true
    }

    /**
     * Build the wallet.
     */
    suspend fun build(): Wallet = withContext(Dispatchers.IO) {
        val walletIdStr = walletId ?: java.util.UUID.randomUUID().toString()
        val walletDidStr = walletDid ?: "did:key:test-wallet-$walletIdStr"

        // Add organization and presentation flags to options if needed
        val finalOptions = optionsBuilder.build()

        // Use WalletFactory from provider
        val walletFactory = dslProvider.getWalletFactory()
            ?: throw IllegalStateException(
                "WalletFactory not available. " +
                "Ensure TrustWeave-testkit is on classpath or provide a wallet factory via TrustWeaveConfig."
            )

        return@withContext walletFactory.create(
            providerName = provider,
            walletId = walletIdStr,
            walletDid = walletDidStr,
            holderDid = holderDid,
            options = finalOptions
        )
    }
}

/**
 * Extension function to create a wallet using a wallet DSL provider.
 */
suspend fun WalletDslProvider.wallet(block: WalletBuilder.() -> Unit): Wallet {
    val builder = WalletBuilder(this)
    builder.block()
    return builder.build()
}


