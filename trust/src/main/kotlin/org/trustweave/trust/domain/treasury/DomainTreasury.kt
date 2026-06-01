package org.trustweave.trust.domain.treasury

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.DomainId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Handle returned by [DomainTreasury.reserve]. Captures the amount locked
 * against a [ChainAccount] for the duration of a single ledger-touching
 * operation. The reservation **must** be settled or cancelled — orphaned
 * reservations are auto-cancelled after their [ttl].
 */
data class Reservation(
    val id: String,
    val domainId: DomainId,
    val chainId: String,
    val locked: TokenAmount,
    val correlationId: String,
    val createdAt: Instant,
    val ttl: Duration = DEFAULT_TTL,
) {
    val expiresAt: Instant
        get() = Instant.fromEpochMilliseconds(createdAt.toEpochMilliseconds() + ttl.inWholeMilliseconds)

    fun isExpired(now: Instant): Boolean = now >= expiresAt

    companion object {
        val DEFAULT_TTL: Duration = 30.minutes
    }
}

/**
 * The domain-scoped on-chain payment authority. Owns:
 * - one [ChainAccount] per supported chain
 * - a [SpendPolicy] enforced at reserve-time
 * - an append-only [TreasuryLedger] for audit / billing reconciliation
 *
 * Lifecycle of a single operation:
 *
 *   1. `account(chainId).estimateFee(op)`            → est
 *   2. `reserve(ctx, est)`                            → Reservation (funds locked)
 *   3. plugin submits the tx                          → AnchorResult(fee, payerAddress)
 *   4. `settle(reservation, result)`                  → reservation closed
 *      — or `cancel(reservation)` on failure
 *
 * Implementations must serialise reservations per [ChainAccount] (nonce
 * management is the plugin's responsibility but the reservation queue
 * prevents two writes from oversubscribing a tight balance).
 */
interface DomainTreasury {
    val domainId: DomainId

    suspend fun account(chainId: String): ChainAccount?
    suspend fun accounts(): List<ChainAccount>
    suspend fun policy(): SpendPolicy

    /**
     * Lock [estimate] × safety margin against the [ChainAccount] for
     * [ctx.chainId]. Throws [org.trustweave.anchor.exceptions.TreasuryException]
     * subclasses on policy violation or insufficient funds — never on a
     * recoverable mempool condition (that's the plugin's concern).
     */
    suspend fun reserve(ctx: PaymentContext, estimate: TokenAmount): Reservation

    /**
     * Commit a reservation against the actual fee reported in [result].
     * Releases the (typically smaller) delta back to the available balance
     * and appends the terminal `SETTLED` (or `FAILED` if [success] is false)
     * entry to the ledger.
     */
    suspend fun settle(reservation: Reservation, result: AnchorResult, success: Boolean = true)

    /**
     * Release the reservation in full. Used on submission errors before any
     * fee is incurred, and by the TTL sweeper for expired reservations.
     */
    suspend fun cancel(reservation: Reservation)

    /** Read view over the audit ledger. */
    fun ledger(): TreasuryLedger
}

/**
 * Clock + safety-margin knobs lifted out of the interface so production
 * deployments can tune them without subclassing.
 */
data class TreasuryConfig(
    val safetyMarginNumerator: Int = 11,
    val safetyMarginDenominator: Int = 10,
    val reservationTtl: Duration = Reservation.DEFAULT_TTL,
    val clock: Clock = Clock.System,
) {
    init {
        require(safetyMarginNumerator >= safetyMarginDenominator) {
            "safety margin must be ≥ 1.0 (numerator $safetyMarginNumerator / denominator $safetyMarginDenominator)"
        }
        require(safetyMarginDenominator > 0) { "denominator must be positive" }
    }
}
