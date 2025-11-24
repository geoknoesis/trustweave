package com.trustweave.wallet.exception

import com.trustweave.core.exception.TrustWeaveException

/**
 * Wallet-related error types.
 * 
 * These errors provide structured error codes and context for wallet operations.
 */
sealed class WalletError(
    open val code: String,
    override val message: String,
    open val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(message, cause) {
    
    data class WalletCreationFailed(
        val reason: String,
        val provider: String? = null,
        val walletId: String? = null
    ) : WalletError(
        code = "WALLET_CREATION_FAILED",
        message = "Wallet creation failed: $reason",
        context = mapOf(
            "reason" to reason,
            "provider" to provider,
            "walletId" to walletId
        ).filterValues { it != null }
    )
}

