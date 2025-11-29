package com.trustweave.credential.wallet

import com.trustweave.wallet.Wallet
import com.trustweave.testkit.credential.InMemoryWallet
import java.util.UUID

/**
 * Factory for creating wallet instances.
 *
 * Provides convenient methods for creating different types of wallets.
 */
object Wallets {
    /**
     * Creates an in-memory wallet.
     *
     * @param holderDid The DID of the wallet holder
     * @param walletId Optional wallet ID (auto-generated if not provided)
     * @return InMemoryWallet instance
     */
    fun inMemory(
        holderDid: String,
        walletId: String? = null
    ): Wallet {
        val id = walletId ?: UUID.randomUUID().toString()
        return InMemoryWallet(
            walletId = id,
            holderDid = holderDid
        )
    }

    /**
     * Creates an in-memory wallet with separate wallet DID and holder DID.
     *
     * @param walletDid The DID representing the wallet (e.g., institution)
     * @param holderDid The DID of the credential holder
     * @param walletId Optional wallet ID (defaults to walletDid if not provided)
     * @return InMemoryWallet instance
     */
    fun inMemoryWithWalletDid(
        walletDid: String,
        holderDid: String,
        walletId: String? = null
    ): Wallet {
        val id = walletId ?: walletDid
        return InMemoryWallet(
            walletId = id,
            holderDid = holderDid
        )
    }
}

