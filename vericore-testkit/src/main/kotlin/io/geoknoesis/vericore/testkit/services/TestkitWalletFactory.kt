package io.geoknoesis.vericore.testkit.services

import io.geoknoesis.vericore.spi.services.WalletFactory
import io.geoknoesis.vericore.credential.wallet.Wallet
import io.geoknoesis.vericore.testkit.credential.InMemoryWallet
import io.geoknoesis.vericore.testkit.credential.BasicWallet
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
        options: Map<String, Any?>
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
        holderDid: String?
    ): Wallet {
        return create("inMemory", walletId, walletDid, holderDid)
    }
}

