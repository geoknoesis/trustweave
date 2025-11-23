package com.trustweave.wallet.dsl

import com.trustweave.wallet.services.WalletFactory

/**
 * Provider interface for Wallet DSL operations.
 * 
 * This interface allows Wallet DSLs to work without directly depending on TrustLayerContext,
 * enabling them to be in the wallet:core module while being used by the trust module.
 */
interface WalletDslProvider {
    /**
     * Get the wallet factory.
     */
    fun getWalletFactory(): WalletFactory?
}

