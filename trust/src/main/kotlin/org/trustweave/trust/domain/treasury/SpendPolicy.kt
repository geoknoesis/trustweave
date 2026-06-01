package org.trustweave.trust.domain.treasury

import kotlinx.datetime.Instant
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.TokenAmount
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Single cap evaluated against an estimated spend.
 *
 * - [PerOperation] — single-transaction ceiling
 * - [PerWindow]    — rolling time-window ceiling (daily, monthly, …)
 * - [GasPriceCeiling] — EVM-specific guard against fee-market spikes
 */
sealed class Cap {
    abstract val chainId: String

    data class PerOperation(
        override val chainId: String,
        val max: TokenAmount,
    ) : Cap() {
        init { require(max.chainId == chainId) { "max.chainId must match cap.chainId" } }
    }

    data class PerWindow(
        override val chainId: String,
        val window: Duration,
        val max: TokenAmount,
    ) : Cap() {
        init {
            require(max.chainId == chainId) { "max.chainId must match cap.chainId" }
            require(window.isPositive()) { "window must be positive" }
        }
    }

    data class GasPriceCeiling(
        override val chainId: String,
        val maxGasPriceWei: BigInteger,
    ) : Cap() {
        init { require(maxGasPriceWei.signum() > 0) { "maxGasPriceWei must be positive" } }
    }
}

/**
 * The set of rules a domain treasury evaluates at `reserve` time. Exhausting
 * any cap raises [org.trustweave.anchor.exceptions.TreasuryException.CapExceeded]
 * **before** the operation reaches the SPI — never silently degraded.
 *
 * @property caps              per-chain spend ceilings
 * @property allowedStrategies the subset of [FeeStrategy] subclasses the
 *                             domain accepts; absence rejects the strategy
 * @property requireSponsorAllowList when true, a sponsored strategy is only
 *                                   accepted if the sponsor DID appears in
 *                                   [sponsorAllowList]
 * @property sponsorAllowList  DIDs permitted to sponsor this domain
 */
data class SpendPolicy(
    val caps: List<Cap> = emptyList(),
    val allowedStrategies: Set<KClass<out FeeStrategy>> = setOf(
        FeeStrategy.DomainPays::class,
    ),
    val requireSponsorAllowList: Boolean = false,
    val sponsorAllowList: Set<String> = emptySet(),
) {
    /**
     * Snapshot of recent spend used by [PerWindow] evaluation. Plumbed in
     * from the treasury ledger at evaluation time, not embedded in the policy.
     */
    data class WindowState(val chainId: String, val window: Duration, val spent: TokenAmount)

    companion object {
        /**
         * Permissive default — `DomainPays` only, no caps, no sponsor list.
         * Fine for development; production deployments should set explicit
         * caps and an allow-list.
         */
        val DEFAULT: SpendPolicy = SpendPolicy()
    }
}

/**
 * Lightweight projection of treasury ledger state used by policy evaluation.
 * Returned by [TreasuryLedger.windowState] and consumed by the treasury when
 * deciding whether a [Cap.PerWindow] is exhausted.
 */
data class PolicyEvaluationContext(
    val now: Instant,
    val windowStates: List<SpendPolicy.WindowState>,
)
