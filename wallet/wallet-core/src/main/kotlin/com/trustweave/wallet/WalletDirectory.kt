package com.trustweave.wallet

import java.util.concurrent.ConcurrentHashMap

/**
 * Instance-scoped directory for managing wallets.
 *
 * Provides predictable, testable behaviour without shared mutable state.
 * Create a directory per application/service instead of relying on global registries.
 */
class WalletDirectory {
    private val wallets = ConcurrentHashMap<String, Wallet>()
    private val walletsByDid = ConcurrentHashMap<String, Wallet>()
    
    fun register(wallet: Wallet) {
        wallets[wallet.walletId] = wallet
        if (wallet is DidManagement) {
            walletsByDid[wallet.walletDid] = wallet
            walletsByDid[wallet.holderDid] = wallet
        }
    }
    
    fun get(walletId: String): Wallet? = wallets[walletId]
    
    fun getByDid(did: String): Wallet? = walletsByDid[did]
    
    /**
     * Find wallets by capability using type-safe checks.
     * 
     * @return List of wallets that support the capability type T
     */
    inline fun <reified T : Any> findByCapability(): List<T> {
        @Suppress("UNCHECKED_CAST")
        return getAll().filter { it is T }.map { it as T }
    }
    
    fun findByCapability(feature: String): List<Wallet> {
        return wallets.values.filter { it.capabilities.supports(feature) }
    }
    
    fun getAll(): List<Wallet> = wallets.values.toList()
    
    fun unregister(walletId: String): Boolean {
        val wallet = wallets.remove(walletId) ?: return false
        if (wallet is DidManagement) {
            walletsByDid.remove(wallet.walletDid)
            walletsByDid.remove(wallet.holderDid)
        }
        return true
    }
    
    fun clear() {
        wallets.clear()
        walletsByDid.clear()
    }
}

