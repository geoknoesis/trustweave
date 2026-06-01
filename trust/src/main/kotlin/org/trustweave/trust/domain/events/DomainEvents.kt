package org.trustweave.trust.domain.events

import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.DomainId

/**
 * Telemetry surface for on-chain spend lifecycle events. Emitted by the
 * trust-domain control plane ([org.trustweave.trust.domain.TrustedDomainManager]
 * and [org.trustweave.trust.domain.treasury.DomainTreasury] implementations) so
 * SaaS billing, dashboards, and audit pipelines can subscribe without coupling
 * to ledger internals.
 *
 * Events are fire-and-forget from the producer's perspective: a sink that
 * throws or hangs must not break treasury operations. Sinks are responsible
 * for their own backpressure and persistence.
 */
sealed class DomainEvent {
    abstract val domainId: DomainId
    abstract val chainId: String
    abstract val correlationId: String

    /** Funds locked against a [ChainAccount] for an upcoming on-chain operation. */
    data class OnChainSpendReserved(
        override val domainId: DomainId,
        override val chainId: String,
        override val correlationId: String,
        val estimate: TokenAmount,
    ) : DomainEvent()

    /** Operation submitted successfully; reservation closed against the real fee. */
    data class OnChainSpendSettled(
        override val domainId: DomainId,
        override val chainId: String,
        override val correlationId: String,
        val actualFee: TokenAmount,
        val txHash: String?,
        val payerAddress: String?,
    ) : DomainEvent()

    /** Reservation released without spend (caller-initiated rollback or TTL sweep). */
    data class OnChainSpendCancelled(
        override val domainId: DomainId,
        override val chainId: String,
        override val correlationId: String,
    ) : DomainEvent()

    /** Submission attempted but failed; surfaced for alerting / SLO accounting. */
    data class OnChainSpendFailed(
        override val domainId: DomainId,
        override val chainId: String,
        override val correlationId: String,
        val reason: String,
    ) : DomainEvent()
}

/**
 * Subscriber surface for [DomainEvent]s. Implementations should be cheap and
 * non-throwing — producers wrap [emit] in best-effort try/catch so a faulty
 * sink cannot abort an on-chain operation that has already been submitted.
 */
interface DomainEventSink {
    suspend fun emit(event: DomainEvent)
}

/** Default no-op sink used when telemetry is not configured. */
object NoopDomainEventSink : DomainEventSink {
    override suspend fun emit(event: DomainEvent) = Unit
}
