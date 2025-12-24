package org.trustweave.wallet.exception

import org.trustweave.core.exception.TrustWeaveException

/**
 * Wallet-related exception types.
 *
 * These exceptions provide structured error codes and context for wallet operations.
 */
sealed class WalletException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class WalletCreationFailed(
        val reason: String,
        val provider: String? = null,
        val walletId: String? = null
    ) : WalletException(
        code = "WALLET_CREATION_FAILED",
        message = "Wallet creation failed: $reason",
        context = mapOf(
            "reason" to reason,
            "provider" to provider,
            "walletId" to walletId
        ).filterValues { it != null }
    )
}

