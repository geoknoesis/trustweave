package io.geoknoesis.vericore.credential.wallet

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Registry for wallet management.
 * 
 * Supports lookup by ID, DID, and capability type.
 * Provides type-safe capability discovery.
 * 
 * **Example Usage**:
 * ```kotlin
 * // Register wallet
 * val wallet = createWallet()
 * WalletRegistry.register(wallet)
 * 
 * // Get by ID
 * val retrieved = WalletRegistry.get(wallet.walletId)
 * 
 * // Get by DID (if DidManagement supported)
 * val byDid = WalletRegistry.getByDid("did:key:wallet")
 * 
     * // Find wallets with specific capability (type-safe)
     * val orgWallets = WalletRegistry.findByCapability(CredentialOrganization::class)
 * 
 * // Find wallets by feature name (dynamic)
 * val walletsWithCollections = WalletRegistry.findByCapability("collections")
 * ```
 */
object WalletRegistry {
    private val wallets = ConcurrentHashMap<String, Wallet>()
    private val walletsByDid = ConcurrentHashMap<String, Wallet>()
    
    /**
     * Register a wallet.
     * 
     * @param wallet Wallet to register
     */
    fun register(wallet: Wallet) {
        wallets[wallet.walletId] = wallet
        if (wallet is DidManagement) {
            walletsByDid[wallet.walletDid] = wallet
            walletsByDid[wallet.holderDid] = wallet
        }
    }
    
    /**
     * Get a wallet by ID.
     * 
     * @param walletId Wallet ID
     * @return Wallet, or null if not found
     */
    fun get(walletId: String): Wallet? = wallets[walletId]
    
    /**
     * Get a wallet by DID.
     * 
     * @param did DID string
     * @return Wallet, or null if not found
     */
    fun getByDid(did: String): Wallet? = walletsByDid[did]
    
    /**
     * Find wallets that implement a specific capability.
     * 
     * Type-safe capability lookup.
     * 
     * **Example**:
     * ```kotlin
     * val orgWallets = WalletRegistry.findByCapability(CredentialOrganization::class)
     * orgWallets.forEach { org ->
     *     org.createCollection("Test")
     * }
     * ```
     * 
     * @return List of wallets implementing the capability
     */
    fun <T : Any> findByCapability(clazz: KClass<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return wallets.values.filter { clazz.isInstance(it) }.map { it as T }
    }
    
    /**
     * Find wallets by capability string (for dynamic discovery).
     * 
     * @param feature Feature name (e.g., "collections", "did-management")
     * @return List of wallets supporting the feature
     */
    fun findByCapability(feature: String): List<Wallet> {
        return wallets.values.filter { it.capabilities.supports(feature) }
    }
    
    /**
     * Get all registered wallets.
     * 
     * @return List of all wallets
     */
    fun getAll(): List<Wallet> = wallets.values.toList()
    
    /**
     * Unregister a wallet.
     * 
     * @param walletId Wallet ID
     * @return true if unregistered, false if not found
     */
    fun unregister(walletId: String): Boolean {
        val wallet = wallets.remove(walletId) ?: return false
        if (wallet is DidManagement) {
            walletsByDid.remove(wallet.walletDid)
            walletsByDid.remove(wallet.holderDid)
        }
        return true
    }
    
    /**
     * Clear all registered wallets.
     * Useful for testing.
     */
    fun clear() {
        wallets.clear()
        walletsByDid.clear()
    }
}

