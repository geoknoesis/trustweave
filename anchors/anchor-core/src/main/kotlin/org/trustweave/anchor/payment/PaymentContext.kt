package org.trustweave.anchor.payment

import java.util.UUID
import kotlin.reflect.KClass

/**
 * Strategy describing **who pays** the on-chain fee for a single operation.
 *
 * - [DomainPays] — debit the Trusted Domain treasury. The default.
 * - [SelfPay] — the operation's subject pays from their own account; the
 *   subject must hold the signing key (or have delegated it via KMS).
 * - [Sponsored] — a third-party relayer pays. Maps onto ERC-4337 paymasters
 *   (EVM), the Algorand fee-payer atomic group, and pre-signed PSBT
 *   sponsorship (Bitcoin, future).
 * - [Escrowed] — the fee is drawn from a pre-funded escrow contract identified
 *   by [Escrowed.escrowRef].
 */
sealed interface FeeStrategy {
    object DomainPays : FeeStrategy

    object SelfPay : FeeStrategy

    data class Sponsored(val sponsorDid: String) : FeeStrategy

    data class Escrowed(val escrowRef: String) : FeeStrategy
}

/**
 * Sponsor-specific routing detail attached to a [PaymentContext] when
 * [FeeStrategy.Sponsored] is used. Optional — plugins choose a sensible
 * default channel when absent.
 */
data class SponsorRef(
    val sponsorDid: String,
    val channel: String,
    val params: Map<String, String> = emptyMap(),
)

/**
 * The single piece of state every payment-aware blockchain operation carries.
 *
 * Threaded through the SPI (`BlockchainAnchorClient.writePayload(payload, ctx)`)
 * and consumed by the Trusted Domain Manager to enforce policy, reserve funds,
 * and settle against the treasury ledger.
 *
 * @property domainId  the Trusted Domain ID this operation is billed to
 * @property payerDid  the DID being debited — domain DID for [FeeStrategy.DomainPays],
 *                     subject DID for [FeeStrategy.SelfPay], sponsor DID for [FeeStrategy.Sponsored]
 * @property chainId   CAIP-2 chain ID (e.g. `eip155:137`, `algorand:mainnet`)
 * @property feeStrategy how the fee is routed
 * @property maxFee    caller's optional hard cap; the client must abort if the
 *                     estimate exceeds this even when policy would allow it
 * @property sponsor   sponsor routing detail (only meaningful when
 *                     [feeStrategy] is [FeeStrategy.Sponsored])
 * @property correlationId stable ID used to reconcile reservations, receipts
 *                         and ledger entries across the reserve→submit→settle
 *                         lifecycle
 */
data class PaymentContext(
    val domainId: String,
    val payerDid: String,
    val chainId: String,
    val feeStrategy: FeeStrategy,
    val maxFee: TokenAmount? = null,
    val sponsor: SponsorRef? = null,
    val correlationId: String = UUID.randomUUID().toString(),
) {
    init {
        require(chainId.isNotBlank()) { "chainId must not be blank" }
        require(correlationId.isNotBlank()) { "correlationId must not be blank" }
        if (maxFee != null) {
            require(maxFee.chainId == chainId) {
                "maxFee.chainId (${maxFee.chainId}) must match ctx.chainId ($chainId)"
            }
        }
        if (feeStrategy is FeeStrategy.Sponsored && sponsor != null) {
            require(sponsor.sponsorDid == feeStrategy.sponsorDid) {
                "sponsor.sponsorDid (${sponsor.sponsorDid}) must match " +
                    "FeeStrategy.Sponsored.sponsorDid (${feeStrategy.sponsorDid})"
            }
        }
    }

    val strategyKey: KClass<out FeeStrategy>
        get() = feeStrategy::class

    companion object {
        /**
         * The "no-treasury, no-policy" context used by legacy callers and by
         * the default [org.trustweave.anchor.BlockchainAnchorClient.writePayload]
         * shim. Plugins that recognise this context fall back to their
         * embedded account credentials and do not call into the treasury.
         */
        fun unmanaged(chainId: String): PaymentContext = PaymentContext(
            domainId = UNMANAGED_DOMAIN,
            payerDid = UNMANAGED_DOMAIN,
            chainId = chainId,
            feeStrategy = FeeStrategy.SelfPay,
        )

        const val UNMANAGED_DOMAIN: String = "unmanaged"
    }
}

/**
 * True for any [PaymentContext] returned by [PaymentContext.unmanaged]; plugins
 * use this to decide whether to consult the treasury or fall back to legacy
 * embedded-credentials behaviour.
 */
val PaymentContext.isUnmanaged: Boolean
    get() = domainId == PaymentContext.UNMANAGED_DOMAIN
