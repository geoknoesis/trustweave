package org.trustweave.wallet

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
    private val registeredDids = mutableMapOf<String, List<String>>()

    /** Snapshots the identity; register again to refresh changed DIDs. The latest registration owns a shared DID. */
    @Synchronized
    fun register(wallet: Wallet) {
        // Read user-defined getters before mutating either index so a failed getter leaves state intact.
        val walletId = wallet.walletId
        val dids = if (wallet is DidManagement) listOf(wallet.walletDid, wallet.holderDid).distinct() else emptyList()
        wallets.put(walletId, wallet)?.let { removeDidMappings(walletId, it) }
        registeredDids[walletId] = dids
        dids.forEach { walletsByDid[it] = wallet }
    }

    @Synchronized
    fun get(walletId: String): Wallet? = wallets[walletId]

    @Synchronized
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

    @Synchronized
    fun findByCapability(feature: String): List<Wallet> = wallets.values.filter { it.capabilities.supports(feature) }

    @Synchronized
    fun getAll(): List<Wallet> = wallets.values.toList()

    @Synchronized
    fun unregister(walletId: String): Boolean {
        val wallet = wallets.remove(walletId) ?: return false
        removeDidMappings(walletId, wallet)
        return true
    }

    // A shared DID may have been claimed by a newer registration. Remove only this instance.
    private fun removeDidMappings(
        walletId: String,
        wallet: Wallet,
    ) {
        for (did in registeredDids.remove(walletId).orEmpty()) {
            if (walletsByDid[did] === wallet) walletsByDid.remove(did)
        }
    }

    @Synchronized
    fun clear() {
        wallets.clear()
        walletsByDid.clear()
        registeredDids.clear()
    }
}
