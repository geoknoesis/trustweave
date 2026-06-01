package org.trustweave.trust.domain.treasury

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.DomainId
import java.math.BigInteger

/**
 * In-memory implementation of [TreasuryLedgerStore]. Default for tests and
 * for the `testkit` module. Production deployments swap in a SQL-backed
 * store (separate module — out of scope for Phase 1).
 *
 * Thread-safe via an internal [Mutex]; entries are kept per-correlation so
 * `update` is O(1).
 */
class InMemoryTreasuryLedgerStore : TreasuryLedgerStore {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, TreasuryLedgerEntry>()

    override suspend fun append(entry: TreasuryLedgerEntry) {
        mutex.withLock { entries[entry.correlationId] = entry }
    }

    override suspend fun update(
        correlationId: String,
        status: SettlementStatus,
        actualFee: TokenAmount,
        txHash: String?,
    ) {
        mutex.withLock {
            val prev = entries[correlationId]
                ?: error("No entry for correlationId=$correlationId")
            entries[correlationId] = prev.copy(
                status = status,
                actualFeeAmount = actualFee.amount.toString(),
                txHash = txHash ?: prev.txHash,
            )
        }
    }

    override suspend fun entries(domainId: DomainId, chainId: String?): List<TreasuryLedgerEntry> =
        mutex.withLock {
            entries.values
                .filter { it.domainId == domainId.value }
                .filter { chainId == null || it.chainId == chainId }
                .toList()
        }

    override suspend fun get(correlationId: String): TreasuryLedgerEntry? =
        mutex.withLock { entries[correlationId] }

    override suspend fun spentSince(
        domainId: DomainId,
        chainId: String,
        since: Instant,
    ): TokenAmount = mutex.withLock {
        val sinceMs = since.toEpochMilliseconds()
        val total = entries.values
            .asSequence()
            .filter { it.domainId == domainId.value }
            .filter { it.chainId == chainId }
            .filter { it.status == SettlementStatus.SETTLED || it.status == SettlementStatus.FAILED }
            .filter { it.atEpochMillis >= sinceMs }
            .map { BigInteger(it.actualFeeAmount) }
            .fold(BigInteger.ZERO, BigInteger::add)
        TokenAmount(chainId, AssetRef.Native, total)
    }
}
