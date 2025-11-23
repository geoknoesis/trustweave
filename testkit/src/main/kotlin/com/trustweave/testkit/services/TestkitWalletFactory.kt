package com.trustweave.testkit.services

import com.trustweave.wallet.services.WalletFactory
import com.trustweave.wallet.services.WalletCreationOptions
import com.trustweave.wallet.Wallet
import com.trustweave.testkit.credential.InMemoryWallet
import com.trustweave.testkit.credential.BasicWallet
import java.util.UUID

/**
 * Wallet Factory implementation for testkit.
 * 
 * Supports creating in-memory wallets for testing.
 * Can be extended to support other wallet types (database, file-based, etc.).
 */
class TestkitWalletFactory : WalletFactory {
    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Wallet {
        return when (providerName.lowercase()) {
            "inmemory", "in_memory", "in-memory" -> {
                val finalWalletId = walletId ?: UUID.randomUUID().toString()
                val finalWalletDid = walletDid ?: "did:key:test-wallet-$finalWalletId"
                val finalHolderDid = holderDid ?: throw IllegalArgumentException(
                    "holderDid is required for InMemoryWallet"
                )
                InMemoryWallet(finalWalletId, finalWalletDid, finalHolderDid)
            }
            "basic" -> {
                val finalWalletId = walletId ?: UUID.randomUUID().toString()
                BasicWallet(finalWalletId)
            }
            else -> {
                throw IllegalStateException(
                    "Wallet provider '$providerName' not found. " +
                    "Supported providers: 'inMemory', 'basic'. " +
                    "Ensure appropriate wallet implementation is on classpath."
                )
            }
        }
    }
    
    override suspend fun createInMemory(
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Wallet {
        return create("inMemory", walletId, walletDid, holderDid, options)
    }
}

