package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.wallet.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wallet Builder DSL.
 * 
 * Provides a fluent API for creating and configuring wallets using trust layer configuration.
 * 
 * **Example Usage**:
 * ```kotlin
 * val wallet = trustLayer.wallet {
 *     id("my-wallet")
 *     holder("did:key:holder")
 *     enableOrganization()
 *     enablePresentation()
 * }
 * ```
 */
class WalletBuilder(
    private val context: TrustLayerContext
) {
    private var walletId: String? = null
    private var holderDid: String? = null
    private var enableOrganization: Boolean = false
    private var enablePresentation: Boolean = false
    
    /**
     * Set wallet ID.
     */
    fun id(walletId: String) {
        this.walletId = walletId
    }
    
    /**
     * Set holder DID.
     */
    fun holder(did: String) {
        this.holderDid = did
    }
    
    /**
     * Enable organization capabilities (collections, tags).
     */
    fun enableOrganization() {
        this.enableOrganization = true
    }
    
    /**
     * Enable presentation capabilities.
     */
    fun enablePresentation() {
        this.enablePresentation = true
    }
    
    /**
     * Build the wallet.
     */
    suspend fun build(): Wallet = withContext(Dispatchers.IO) {
        val finalHolderDid = holderDid 
            ?: throw IllegalStateException("Holder DID is required")
        
        // Use InMemoryWallet from testkit via reflection
        try {
            val walletClass = Class.forName("io.geoknoesis.vericore.testkit.credential.InMemoryWallet")
            val walletIdStr = walletId ?: java.util.UUID.randomUUID().toString()
            // InMemoryWallet constructor: (walletId: String, walletDid: String, holderDid: String)
            // walletDid defaults to "did:key:test-wallet-$walletId", but we'll pass it explicitly
            val walletDidStr = "did:key:test-wallet-$walletIdStr"
            val wallet = walletClass.getDeclaredConstructor(
                String::class.java, 
                String::class.java, 
                String::class.java
            ).newInstance(walletIdStr, walletDidStr, finalHolderDid)
            wallet as Wallet
        } catch (e: Exception) {
            throw IllegalStateException(
                "InMemoryWallet not found. " +
                "Ensure vericore-testkit is on classpath. Error: ${e.message}"
            )
        }
    }
}

