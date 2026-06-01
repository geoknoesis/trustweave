package org.trustweave.trust.domain.treasury

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.DomainId
import org.trustweave.trust.domain.events.DomainEvent
import org.trustweave.trust.domain.events.DomainEventSink
import org.trustweave.trust.domain.events.NoopDomainEventSink
import java.math.BigInteger
import java.util.UUID

/**
 * Reference [DomainTreasury] implementation. In-memory reservation accounting
 * over a swappable [TreasuryLedgerStore]. Production deployments may swap
 * the store for a durable backend; the reservation lock state is volatile
 * by design — reservations are short-lived (default 30 min) and orphaned
 * ones expire harmlessly.
 *
 * Reservation arithmetic uses [TreasuryConfig.safetyMarginNumerator] /
 * `safetyMarginDenominator` (default 11/10) to lock slightly more than the
 * estimate, absorbing intra-block fee-market drift.
 */
class InMemoryDomainTreasury(
    override val domainId: DomainId,
    private val accounts: Map<String, ChainAccount>,
    private val spendPolicy: SpendPolicy = SpendPolicy.DEFAULT,
    private val store: TreasuryLedgerStore = InMemoryTreasuryLedgerStore(),
    private val config: TreasuryConfig = TreasuryConfig(),
    private val eventSink: DomainEventSink = NoopDomainEventSink,
) : DomainTreasury {

    private val mutex = Mutex()
    private val reservations = HashMap<String, Reservation>()
    private val lockedByChain = HashMap<String, BigInteger>()

    override suspend fun account(chainId: String): ChainAccount? = accounts[chainId]

    override suspend fun accounts(): List<ChainAccount> = accounts.values.toList()

    override suspend fun policy(): SpendPolicy = spendPolicy

    override fun ledger(): TreasuryLedger = TreasuryLedger(domainId, store)

    override suspend fun reserve(ctx: PaymentContext, estimate: TokenAmount): Reservation {
        require(estimate.chainId == ctx.chainId) {
            "estimate.chainId (${estimate.chainId}) must match ctx.chainId (${ctx.chainId})"
        }

        enforceStrategy(ctx)
        enforceSponsor(ctx)
        enforceCallerCap(ctx, estimate)
        enforcePolicyCaps(ctx, estimate)

        val account = accounts[ctx.chainId]
            ?: throw TreasuryException.NoAccountForChain(domainId.value, ctx.chainId)

        val locked = applySafetyMargin(estimate)
        val balance = account.balance()
        val now = config.clock.now()

        val reservation = mutex.withLock {
            sweepExpired(now)
            val already = lockedByChain[ctx.chainId] ?: BigInteger.ZERO
            val needed = already + locked.amount
            if (balance.amount < needed) {
                throw TreasuryException.InsufficientFunds(
                    domainId = domainId.value,
                    chainId = ctx.chainId,
                    required = TokenAmount(ctx.chainId, locked.asset, needed),
                    available = balance,
                )
            }
            val r = Reservation(
                id = UUID.randomUUID().toString(),
                domainId = domainId,
                chainId = ctx.chainId,
                locked = locked,
                correlationId = ctx.correlationId,
                createdAt = now,
                ttl = config.reservationTtl,
            )
            reservations[r.id] = r
            lockedByChain[ctx.chainId] = needed

            store.append(
                TreasuryLedgerEntry(
                    correlationId = ctx.correlationId,
                    domainId = domainId.value,
                    payerDid = ctx.payerDid,
                    chainId = ctx.chainId,
                    operation = "reserve",
                    estimatedFeeAmount = estimate.amount.toString(),
                    actualFeeAmount = "0",
                    asset = TreasuryLedgerEntry.assetTag(locked.asset),
                    status = SettlementStatus.RESERVED,
                    atEpochMillis = now.toEpochMilliseconds(),
                ),
            )
            r
        }
        emitSafely(
            DomainEvent.OnChainSpendReserved(
                domainId = domainId,
                chainId = reservation.chainId,
                correlationId = reservation.correlationId,
                estimate = estimate,
            ),
        )
        return reservation
    }

    override suspend fun settle(reservation: Reservation, result: AnchorResult, success: Boolean) {
        val actual = result.fee ?: TokenAmount.zero(reservation.chainId, reservation.locked.asset)
        mutex.withLock {
            val held = reservations.remove(reservation.id)
                ?: throw TreasuryException.ReservationNotFound(reservation.id)
            val unlocked = (lockedByChain[held.chainId] ?: BigInteger.ZERO) - held.locked.amount
            lockedByChain[held.chainId] = unlocked.coerceAtLeast(BigInteger.ZERO)
        }
        store.update(
            correlationId = reservation.correlationId,
            status = if (success) SettlementStatus.SETTLED else SettlementStatus.FAILED,
            actualFee = actual,
            txHash = result.ref.txHash,
        )
        emitSafely(
            if (success) {
                DomainEvent.OnChainSpendSettled(
                    domainId = domainId,
                    chainId = reservation.chainId,
                    correlationId = reservation.correlationId,
                    actualFee = actual,
                    txHash = result.ref.txHash,
                    payerAddress = result.payerAddress,
                )
            } else {
                DomainEvent.OnChainSpendFailed(
                    domainId = domainId,
                    chainId = reservation.chainId,
                    correlationId = reservation.correlationId,
                    reason = "settle reported failure",
                )
            },
        )
    }

    override suspend fun cancel(reservation: Reservation) {
        val released = mutex.withLock {
            val held = reservations.remove(reservation.id) ?: return@withLock false
            val unlocked = (lockedByChain[held.chainId] ?: BigInteger.ZERO) - held.locked.amount
            lockedByChain[held.chainId] = unlocked.coerceAtLeast(BigInteger.ZERO)
            true
        }
        if (!released) return
        store.update(
            correlationId = reservation.correlationId,
            status = SettlementStatus.CANCELLED,
            actualFee = TokenAmount.zero(reservation.chainId, reservation.locked.asset),
            txHash = null,
        )
        emitSafely(
            DomainEvent.OnChainSpendCancelled(
                domainId = domainId,
                chainId = reservation.chainId,
                correlationId = reservation.correlationId,
            ),
        )
    }

    private suspend fun emitSafely(event: DomainEvent) {
        try {
            eventSink.emit(event)
        } catch (_: Throwable) {
            // telemetry must never break a treasury operation
        }
    }

    private fun applySafetyMargin(estimate: TokenAmount): TokenAmount {
        val num = BigInteger.valueOf(config.safetyMarginNumerator.toLong())
        val den = BigInteger.valueOf(config.safetyMarginDenominator.toLong())
        return estimate.copy(amount = estimate.amount * num / den)
    }

    private fun sweepExpired(now: kotlinx.datetime.Instant) {
        val expired = reservations.values.filter { it.isExpired(now) }
        for (r in expired) {
            reservations.remove(r.id)
            val unlocked = (lockedByChain[r.chainId] ?: BigInteger.ZERO) - r.locked.amount
            lockedByChain[r.chainId] = unlocked.coerceAtLeast(BigInteger.ZERO)
        }
    }

    private fun enforceStrategy(ctx: PaymentContext) {
        if (ctx.strategyKey !in spendPolicy.allowedStrategies) {
            throw TreasuryException.StrategyNotAllowed(
                domainId = domainId.value,
                chainId = ctx.chainId,
                strategy = ctx.strategyKey.simpleName ?: ctx.feeStrategy.toString(),
                allowed = spendPolicy.allowedStrategies.mapNotNull { it.simpleName }.toSet(),
            )
        }
    }

    private fun enforceSponsor(ctx: PaymentContext) {
        val strategy = ctx.feeStrategy
        if (strategy is FeeStrategy.Sponsored && spendPolicy.requireSponsorAllowList) {
            if (strategy.sponsorDid !in spendPolicy.sponsorAllowList) {
                throw TreasuryException.SponsorNotAllowed(
                    domainId = domainId.value,
                    sponsorDid = strategy.sponsorDid,
                )
            }
        }
    }

    private fun enforceCallerCap(ctx: PaymentContext, estimate: TokenAmount) {
        val maxFee = ctx.maxFee ?: return
        if (estimate.amount > maxFee.amount) {
            throw TreasuryException.CallerCapExceeded(
                correlationId = ctx.correlationId,
                chainId = ctx.chainId,
                estimated = estimate,
                callerMax = maxFee,
            )
        }
    }

    private suspend fun enforcePolicyCaps(ctx: PaymentContext, estimate: TokenAmount) {
        val ledger = ledger()
        val now = config.clock.now()
        for (cap in spendPolicy.caps) {
            if (cap.chainId != ctx.chainId) continue
            when (cap) {
                is Cap.PerOperation -> if (estimate.amount > cap.max.amount) {
                    throw TreasuryException.CapExceeded(
                        domainId = domainId.value,
                        chainId = ctx.chainId,
                        capKind = "PerOperation",
                        attempted = estimate,
                        cap = cap.max,
                    )
                }
                is Cap.PerWindow -> {
                    val state = ledger.windowState(ctx.chainId, cap.window, now)
                    val total = state.spent.amount + estimate.amount
                    if (total > cap.max.amount) {
                        throw TreasuryException.CapExceeded(
                            domainId = domainId.value,
                            chainId = ctx.chainId,
                            capKind = "PerWindow(${cap.window})",
                            attempted = TokenAmount(ctx.chainId, estimate.asset, total),
                            cap = cap.max,
                        )
                    }
                }
                is Cap.GasPriceCeiling -> {
                    // Gas-price ceiling is evaluated by EVM plugins against
                    // their estimated effectiveGasPrice; the treasury surface
                    // does not see gas price independent of fee, so this cap
                    // is a no-op here and enforced inside the plugin.
                }
            }
        }
    }
}
