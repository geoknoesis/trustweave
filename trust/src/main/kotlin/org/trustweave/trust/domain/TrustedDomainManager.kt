package org.trustweave.trust.domain

import kotlinx.serialization.json.JsonElement
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.events.DomainEvent
import org.trustweave.trust.domain.events.DomainEventSink
import org.trustweave.trust.domain.events.NoopDomainEventSink
import org.trustweave.trust.domain.treasury.DomainTreasury
import java.util.UUID

/**
 * Orchestrates the **payment plane** for one Trusted Domain. The single
 * broker between caller code and the anchor SPI: every ledger-touching
 * operation that should be billed to a domain treasury flows through
 * [anchor].
 *
 * Lifecycle of one call (see the design doc, §6):
 *
 *   1. resolve the active [DomainTreasury] for [domainId]
 *   2. pick a [FeeStrategy] (caller-supplied, defaults to `DomainPays`)
 *   3. estimate the fee against the corresponding [BlockchainAnchorClient]
 *   4. enforce caller-cap, per-op cap, per-window cap, sponsor allow-list
 *      (delegated to [DomainTreasury.reserve])
 *   5. reserve funds → handed-down [PaymentContext]
 *   6. call `client.writePayload(payload, ctx)`
 *   7. settle on success (or cancel on failure)
 *
 * Construction takes one treasury per domain; multi-tenant deployments wrap
 * this in a registry keyed by [DomainId].
 */
class TrustedDomainManager(
    val domainId: DomainId,
    val payerDid: String,
    private val treasury: DomainTreasury,
    private val registry: BlockchainAnchorRegistry,
    private val eventSink: DomainEventSink = NoopDomainEventSink,
) {

    /**
     * Anchor [payload] on [chainId] paid for by this domain's treasury (by
     * default). Returns the [AnchorResult] from the underlying plugin; the
     * fee actually charged is reflected on the result and in the treasury
     * ledger.
     *
     * @throws org.trustweave.anchor.exceptions.TreasuryException if policy /
     *   balance / caller-cap rejects the operation **before** submission
     * @throws BlockchainException if the plugin fails after submission; in
     *   that case the reservation is cancelled (no charge) before the
     *   exception is rethrown
     */
    suspend fun anchor(
        operationKind: String,
        chainId: String,
        payload: JsonElement,
        feeStrategy: FeeStrategy = FeeStrategy.DomainPays,
        maxFee: TokenAmount? = null,
        mediaType: String = "application/json",
    ): AnchorResult {
        val client = clientFor(chainId)
        val op = OperationDescriptor(
            kind = operationKind,
            chainId = chainId,
            payload = payload,
        )
        val estimate = client.estimate(op)

        val ctx = PaymentContext(
            domainId = domainId.value,
            payerDid = payerDid,
            chainId = chainId,
            feeStrategy = feeStrategy,
            maxFee = maxFee,
            correlationId = UUID.randomUUID().toString(),
        )

        val reservation = treasury.reserve(ctx, estimate)
        val result = try {
            client.writePayload(payload, ctx, mediaType)
        } catch (e: Throwable) {
            emitSafely(
                DomainEvent.OnChainSpendFailed(
                    domainId = domainId,
                    chainId = chainId,
                    correlationId = ctx.correlationId,
                    reason = e.message ?: e::class.simpleName ?: "unknown",
                ),
            )
            treasury.cancel(reservation)
            throw e
        }
        treasury.settle(reservation, result, success = true)
        return result
    }

    private suspend fun emitSafely(event: DomainEvent) {
        try {
            eventSink.emit(event)
        } catch (_: Throwable) {
            // telemetry must never break a treasury operation
        }
    }

    /** Direct access to the underlying treasury — for inspection / dashboards. */
    fun treasury(): DomainTreasury = treasury

    private fun clientFor(chainId: String): BlockchainAnchorClient {
        return registry.get(chainId)
            ?: throw BlockchainException.ChainNotRegistered(
                chainId = chainId,
                availableChains = registry.getAllChainIds(),
            )
    }
}
