package org.trustweave.trust.domain.treasury

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.DomainId
import kotlin.time.Duration

enum class SettlementStatus { RESERVED, SETTLED, CANCELLED, FAILED }

/**
 * Append-only audit entry for one ledger-touching operation. The treasury
 * writes one entry per reservation (`RESERVED`), then updates it in place
 * with the terminal status (`SETTLED` / `CANCELLED` / `FAILED`).
 *
 * Implementations of [TreasuryLedgerStore] may choose to emit append-only
 * deltas instead of mutating; consumers must treat the latest entry per
 * `correlationId` as authoritative.
 */
@Serializable
data class TreasuryLedgerEntry(
    val correlationId: String,
    val domainId: String,
    val payerDid: String,
    val chainId: String,
    val operation: String,
    val estimatedFeeAmount: String,
    val actualFeeAmount: String,
    val asset: String,
    val txHash: String? = null,
    val status: SettlementStatus,
    val atEpochMillis: Long,
) {
    fun estimatedFee(): TokenAmount =
        TokenAmount(chainId, parseAsset(asset), java.math.BigInteger(estimatedFeeAmount))

    fun actualFee(): TokenAmount =
        TokenAmount(chainId, parseAsset(asset), java.math.BigInteger(actualFeeAmount))

    companion object {
        internal fun assetTag(asset: AssetRef): String = when (asset) {
            is AssetRef.Native -> "native"
            is AssetRef.Token -> "token:${asset.symbol}:${asset.contract}"
            is AssetRef.OperatorCredit -> "credit:${asset.operatorId}"
        }

        internal fun parseAsset(tag: String): AssetRef {
            val parts = tag.split(":")
            return when (parts[0]) {
                "native" -> AssetRef.Native
                "token" -> AssetRef.Token(parts[1], parts.drop(2).joinToString(":"))
                "credit" -> AssetRef.OperatorCredit(parts.drop(1).joinToString(":"))
                else -> error("Unrecognised asset tag: $tag")
            }
        }
    }
}

/**
 * The treasury-facing read/write port. Concrete stores plug in via the
 * `TrustedDomainManager` config — default is JSONL on local disk, optional
 * is SQL/Postgres for SaaS deployments.
 */
interface TreasuryLedgerStore {
    suspend fun append(entry: TreasuryLedgerEntry)
    suspend fun update(correlationId: String, status: SettlementStatus, actualFee: TokenAmount, txHash: String?)
    suspend fun entries(domainId: DomainId, chainId: String? = null): List<TreasuryLedgerEntry>
    suspend fun get(correlationId: String): TreasuryLedgerEntry?
    suspend fun spentSince(domainId: DomainId, chainId: String, since: Instant): TokenAmount
}

/**
 * Read view over a domain's ledger, exposed to callers and to policy
 * evaluation. Backed by a [TreasuryLedgerStore].
 */
class TreasuryLedger internal constructor(
    private val domainId: DomainId,
    private val store: TreasuryLedgerStore,
) {
    suspend fun all(chainId: String? = null): List<TreasuryLedgerEntry> =
        store.entries(domainId, chainId)

    suspend fun get(correlationId: String): TreasuryLedgerEntry? = store.get(correlationId)

    suspend fun windowState(chainId: String, window: Duration, now: Instant): SpendPolicy.WindowState {
        val since = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - window.inWholeMilliseconds)
        val spent = store.spentSince(domainId, chainId, since)
        return SpendPolicy.WindowState(chainId, window, spent)
    }
}
