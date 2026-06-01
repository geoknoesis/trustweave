package org.trustweave.anchor.exceptions

import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.core.exception.TrustWeaveException

/**
 * Errors raised by the payment plane — `DomainTreasury`, `SpendPolicy`,
 * `TrustedDomainManager` — before a transaction is dispatched to a
 * `BlockchainAnchorClient`. These fail-closed: when raised, no on-chain
 * activity has occurred and no funds were moved.
 */
sealed class TreasuryException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null,
) : TrustWeaveException(code, message, context, cause) {

    data class InsufficientFunds(
        val domainId: String,
        val chainId: String,
        val required: TokenAmount,
        val available: TokenAmount,
    ) : TreasuryException(
        code = "TREASURY_INSUFFICIENT_FUNDS",
        message = "[Domain: $domainId] [Chain: $chainId] " +
            "Required ${required.amount} ${required.asset} but only ${available.amount} available",
        context = mapOf(
            "domainId" to domainId,
            "chainId" to chainId,
            "required" to required.amount.toString(),
            "available" to available.amount.toString(),
            "asset" to required.asset.toString(),
        ),
    )

    data class CapExceeded(
        val domainId: String,
        val chainId: String,
        val capKind: String,
        val attempted: TokenAmount,
        val cap: TokenAmount,
    ) : TreasuryException(
        code = "TREASURY_CAP_EXCEEDED",
        message = "[Domain: $domainId] [Chain: $chainId] [Cap: $capKind] " +
            "Attempted ${attempted.amount} exceeds policy cap ${cap.amount}",
        context = mapOf(
            "domainId" to domainId,
            "chainId" to chainId,
            "capKind" to capKind,
            "attempted" to attempted.amount.toString(),
            "cap" to cap.amount.toString(),
        ),
    )

    data class CallerCapExceeded(
        val correlationId: String,
        val chainId: String,
        val estimated: TokenAmount,
        val callerMax: TokenAmount,
    ) : TreasuryException(
        code = "TREASURY_CALLER_CAP_EXCEEDED",
        message = "[Chain: $chainId] [Correlation: $correlationId] " +
            "Estimated ${estimated.amount} exceeds caller maxFee ${callerMax.amount}",
        context = mapOf(
            "correlationId" to correlationId,
            "chainId" to chainId,
            "estimated" to estimated.amount.toString(),
            "callerMax" to callerMax.amount.toString(),
        ),
    )

    data class StrategyNotAllowed(
        val domainId: String,
        val chainId: String,
        val strategy: String,
        val allowed: Set<String>,
    ) : TreasuryException(
        code = "TREASURY_STRATEGY_NOT_ALLOWED",
        message = "[Domain: $domainId] [Chain: $chainId] " +
            "Fee strategy '$strategy' is not permitted by SpendPolicy. Allowed: $allowed",
        context = mapOf(
            "domainId" to domainId,
            "chainId" to chainId,
            "strategy" to strategy,
            "allowed" to allowed,
        ),
    )

    data class SponsorNotAllowed(
        val domainId: String,
        val sponsorDid: String,
    ) : TreasuryException(
        code = "TREASURY_SPONSOR_NOT_ALLOWED",
        message = "[Domain: $domainId] Sponsor '$sponsorDid' is not on the allow-list",
        context = mapOf(
            "domainId" to domainId,
            "sponsorDid" to sponsorDid,
        ),
    )

    data class NoAccountForChain(
        val domainId: String,
        val chainId: String,
    ) : TreasuryException(
        code = "TREASURY_NO_ACCOUNT_FOR_CHAIN",
        message = "[Domain: $domainId] No ChainAccount configured for chain '$chainId'",
        context = mapOf(
            "domainId" to domainId,
            "chainId" to chainId,
        ),
    )

    data class ReservationNotFound(
        val reservationId: String,
    ) : TreasuryException(
        code = "TREASURY_RESERVATION_NOT_FOUND",
        message = "Reservation '$reservationId' was not found (already settled, cancelled, or expired)",
        context = mapOf("reservationId" to reservationId),
    )

    data class ReservationExpired(
        val reservationId: String,
    ) : TreasuryException(
        code = "TREASURY_RESERVATION_EXPIRED",
        message = "Reservation '$reservationId' has passed its TTL and was auto-cancelled",
        context = mapOf("reservationId" to reservationId),
    )
}
