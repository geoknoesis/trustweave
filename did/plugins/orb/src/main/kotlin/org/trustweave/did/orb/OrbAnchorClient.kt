package org.trustweave.did.orb

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.anchor.payment.isUnmanaged
import org.trustweave.anchor.exceptions.TreasuryException
import java.math.BigInteger

/**
 * Orb-flavoured anchoring facade.
 *
 * Mirrors the shape of [org.trustweave.anchor.BlockchainAnchorClient]
 * (estimate + payment-aware writePayload + legacy writePayload) so the
 * Trusted Domain Manager can route through it once the Orb plugin is wired
 * into the anchor registry. Lives in the Orb plugin (rather than implementing
 * the SPI directly) because Orb's cost model is fundamentally different:
 *
 * - **No on-chain fee is charged to the caller.** The Orb node *operator* pays
 *   the underlying chain (typically a VCT log, ActivityPub witness, or a
 *   tipped EVM/IPFS-backed anchor). Callers consume operator credits instead.
 * - One Sidetree operation == one operator credit. [estimate] therefore
 *   returns a constant `TokenAmount(chainId, OperatorCredit(operatorId), 1)`.
 * - There is no HTTP billing call — credit settlement is off-chain, brokered
 *   between the domain owner and the Orb operator. This client only surfaces
 *   the cost model so the treasury ledger can keep track.
 *
 * Implementations of write-time fee enforcement honour [PaymentContext.maxFee]:
 * if the cap is denominated in the same OperatorCredit asset and is less than
 * one credit, the write is rejected with [TreasuryException.CapExceeded] before
 * any HTTP round-trip.
 */
class OrbAnchorClient internal constructor(
    private val sidetree: SidetreeOrbClient,
    private val config: OrbDidConfig,
) {

    /** Chain identifier surfaced through [estimate] and [AnchorResult.payerAddress]. */
    val chainId: String get() = config.chainId

    /** Operator id this client bills against. */
    val operatorId: String get() = config.effectiveOperatorId

    /** Synthetic asset representing one Orb operator credit. */
    val asset: AssetRef.OperatorCredit get() = AssetRef.OperatorCredit(operatorId)

    /**
     * Constant cost model: one Sidetree operation == one operator credit,
     * regardless of payload size. Orb batches operations and amortises the
     * underlying on-chain fee across them; the per-operation credit charge is
     * the only number a caller can reason about up-front.
     */
    suspend fun estimate(@Suppress("UNUSED_PARAMETER") op: OperationDescriptor): TokenAmount =
        TokenAmount(chainId, asset, BigInteger.ONE)

    /**
     * Payment-aware submission. Threads [ctx] through for fee-cap enforcement
     * and accounting; the actual on-chain payment is the operator's concern.
     *
     * Returns an [AnchorResult] whose [AnchorResult.fee] is exactly one
     * operator credit and whose [AnchorResult.payerAddress] is
     * `operator:<operatorId>` so the treasury ledger can attribute the spend.
     *
     * The [ctx] is consulted only for [PaymentContext.maxFee]; unmanaged
     * contexts skip the cap check entirely and behave like the legacy path.
     */
    suspend fun writePayload(
        operation: JsonObject,
        ctx: PaymentContext,
        mediaType: String = "application/json",
    ): AnchorResult {
        val fee = TokenAmount(chainId, asset, BigInteger.ONE)
        if (!ctx.isUnmanaged) {
            ctx.maxFee?.let { cap ->
                require(cap.chainId == chainId) {
                    "PaymentContext.maxFee.chainId (${cap.chainId}) must match Orb chainId ($chainId)"
                }
                if (cap.asset == asset && cap < fee) {
                    throw TreasuryException.CallerCapExceeded(
                        correlationId = ctx.correlationId,
                        chainId = chainId,
                        estimated = fee,
                        callerMax = cap,
                    )
                }
            }
        }
        val response = sidetree.submitOperation(operation)
        if (!response.success) {
            throw OrbException.httpError(response.httpStatus, response.error ?: response.rawBody)
        }
        return AnchorResult(
            ref = AnchorRef(
                chainId = chainId,
                txHash = response.did ?: response.rawBody,
                contract = null,
                extra = buildMap {
                    response.did?.let { put("did", it) }
                    put("baseUrl", config.baseUrl)
                },
            ),
            payload = operation as JsonElement,
            mediaType = mediaType,
            timestamp = null,
            fee = fee,
            payerAddress = "operator:$operatorId",
        )
    }

    /**
     * Legacy entry-point — routes through the payment-aware overload with
     * [PaymentContext.unmanaged] so callers that don't yet know about the
     * treasury keep working.
     */
    suspend fun writePayload(
        operation: JsonObject,
        mediaType: String = "application/json",
    ): AnchorResult = writePayload(operation, PaymentContext.unmanaged(chainId), mediaType)
}
