package org.trustweave.trust.domain.events

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Reference [DomainEventSink] that forwards events to SLF4J. Useful for
 * dev/test, and as a baseline format for shipping to JSON-line aggregators
 * (Loki, CloudWatch, Datadog) via the underlying logging stack's appenders.
 *
 * Spend events log at INFO so they survive default production filters;
 * failures log at WARN.
 */
class LoggingDomainEventSink(
    private val logger: Logger = LoggerFactory.getLogger(LoggingDomainEventSink::class.java),
) : DomainEventSink {

    override suspend fun emit(event: DomainEvent) {
        when (event) {
            is DomainEvent.OnChainSpendReserved -> logger.info(
                "on-chain spend reserved domain={} chain={} correlationId={} estimate={}",
                event.domainId.value,
                event.chainId,
                event.correlationId,
                event.estimate.amount,
            )
            is DomainEvent.OnChainSpendSettled -> logger.info(
                "on-chain spend settled domain={} chain={} correlationId={} actualFee={} txHash={} payer={}",
                event.domainId.value,
                event.chainId,
                event.correlationId,
                event.actualFee.amount,
                event.txHash,
                event.payerAddress,
            )
            is DomainEvent.OnChainSpendCancelled -> logger.info(
                "on-chain spend cancelled domain={} chain={} correlationId={}",
                event.domainId.value,
                event.chainId,
                event.correlationId,
            )
            is DomainEvent.OnChainSpendFailed -> logger.warn(
                "on-chain spend failed domain={} chain={} correlationId={} reason={}",
                event.domainId.value,
                event.chainId,
                event.correlationId,
                event.reason,
            )
        }
    }
}
