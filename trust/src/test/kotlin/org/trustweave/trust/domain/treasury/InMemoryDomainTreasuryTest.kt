package org.trustweave.trust.domain.treasury

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.DomainId
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours

private const val CHAIN = "eip155:137"

private class StubChainAccount(
    initialBalance: BigInteger,
    private val feeEstimate: BigInteger,
) : ChainAccount {
    override val chainId: String = CHAIN
    override val address: String = "0xDEADBEEF"
    override val keyRef: KmsKeyRef = KmsKeyRef("in-memory", "polygon-key")
    private var current = initialBalance

    override suspend fun balance(): TokenAmount =
        TokenAmount(chainId, AssetRef.Native, current)

    override suspend fun estimateFee(op: OperationDescriptor): TokenAmount =
        TokenAmount(chainId, AssetRef.Native, feeEstimate)

    override suspend fun sign(tx: UnsignedTx): SignedTx = SignedTx(tx.chainId, tx.bytes)
}

class InMemoryDomainTreasuryTest {

    private fun anchorResult(feeAmount: BigInteger): AnchorResult = AnchorResult(
        ref = AnchorRef(chainId = CHAIN, txHash = "0x1234"),
        payload = JsonObject(emptyMap()),
        fee = TokenAmount(CHAIN, AssetRef.Native, feeAmount),
        payerAddress = "0xDEADBEEF",
    )

    private fun ctx(maxFee: BigInteger? = null, strategy: FeeStrategy = FeeStrategy.DomainPays) = PaymentContext(
        domainId = "edu-algeria",
        payerDid = "did:web:example.org",
        chainId = CHAIN,
        feeStrategy = strategy,
        maxFee = maxFee?.let { TokenAmount(CHAIN, AssetRef.Native, it) },
    )

    @Test
    fun `reserve and settle records actual fee in ledger`() = runBlocking<Unit> {
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(1_000_000), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
        )

        val estimate = account.estimateFee(OperationDescriptor("did.create", CHAIN))
        val reservation = treasury.reserve(ctx(), estimate)

        val actualFee = BigInteger.valueOf(8_500)
        treasury.settle(reservation, anchorResult(actualFee), success = true)

        val entry = assertNotNull(treasury.ledger().get(reservation.correlationId))
        assertEquals(SettlementStatus.SETTLED, entry.status)
        assertEquals(actualFee.toString(), entry.actualFeeAmount)
        assertEquals("0x1234", entry.txHash)
    }

    @Test
    fun `reserve rejects when balance below estimate plus safety margin`() = runBlocking<Unit> {
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(10_500), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
        )
        val estimate = account.estimateFee(OperationDescriptor("did.create", CHAIN))
        assertThrows<TreasuryException.InsufficientFunds> {
            treasury.reserve(ctx(), estimate)
        }
    }

    @Test
    fun `cancel releases the lock and marks entry cancelled`() = runBlocking<Unit> {
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(1_000_000), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
        )
        val reservation = treasury.reserve(ctx(), account.estimateFee(OperationDescriptor("did.create", CHAIN)))
        treasury.cancel(reservation)

        val entry = assertNotNull(treasury.ledger().get(reservation.correlationId))
        assertEquals(SettlementStatus.CANCELLED, entry.status)

        // After cancel the lock is released — a second reserve of the same size must succeed.
        val again = treasury.reserve(ctx(), account.estimateFee(OperationDescriptor("did.create", CHAIN)))
        assertEquals(SettlementStatus.RESERVED, treasury.ledger().get(again.correlationId)?.status)
    }

    @Test
    fun `caller maxFee below estimate raises CallerCapExceeded before submission`() = runBlocking<Unit> {
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(1_000_000), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
        )
        val estimate = account.estimateFee(OperationDescriptor("did.create", CHAIN))
        assertThrows<TreasuryException.CallerCapExceeded> {
            treasury.reserve(ctx(maxFee = BigInteger.valueOf(5_000)), estimate)
        }
    }

    @Test
    fun `per-operation cap rejects oversized estimate`() = runBlocking<Unit> {
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(1_000_000), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
            spendPolicy = SpendPolicy(
                caps = listOf(Cap.PerOperation(CHAIN, TokenAmount(CHAIN, AssetRef.Native, BigInteger.valueOf(5_000)))),
            ),
        )
        val estimate = account.estimateFee(OperationDescriptor("did.create", CHAIN))
        assertThrows<TreasuryException.CapExceeded> {
            treasury.reserve(ctx(), estimate)
        }
    }

    @Test
    fun `strategy not in allowedStrategies raises StrategyNotAllowed`() = runBlocking<Unit> {
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(1_000_000), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
            // default policy allows only DomainPays
        )
        val estimate = account.estimateFee(OperationDescriptor("did.create", CHAIN))
        assertThrows<TreasuryException.StrategyNotAllowed> {
            treasury.reserve(ctx(strategy = FeeStrategy.SelfPay), estimate)
        }
    }

    @Test
    fun `concurrent reservations cannot oversubscribe balance`() = runBlocking<Unit> {
        // Balance 22_000, est 10_000 + 10% safety = 11_000 each → second reservation must fail.
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(22_000), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
        )
        val est = account.estimateFee(OperationDescriptor("did.create", CHAIN))
        treasury.reserve(ctx(), est)                        // locks 11_000
        treasury.reserve(ctx(), est)                        // locks 22_000 cumulative — exactly fits
        assertThrows<TreasuryException.InsufficientFunds> {
            treasury.reserve(ctx(), est)                    // would need 33_000 — must fail
        }
    }

    @Test
    fun `sponsor allow-list rejects unauthorised sponsor`() = runBlocking<Unit> {
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(1_000_000), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
            spendPolicy = SpendPolicy(
                allowedStrategies = setOf(FeeStrategy.DomainPays::class, FeeStrategy.Sponsored::class),
                requireSponsorAllowList = true,
                sponsorAllowList = setOf("did:web:trusted-sponsor.org"),
            ),
        )
        val estimate = account.estimateFee(OperationDescriptor("did.create", CHAIN))
        assertThrows<TreasuryException.SponsorNotAllowed> {
            treasury.reserve(
                ctx(strategy = FeeStrategy.Sponsored("did:web:unknown.org")),
                estimate,
            )
        }
    }

    @Test
    fun `per-window cap aggregates prior settled fees`() = runBlocking<Unit> {
        val account = StubChainAccount(initialBalance = BigInteger.valueOf(1_000_000), feeEstimate = BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = DomainId("edu-algeria"),
            accounts = mapOf(CHAIN to account),
            spendPolicy = SpendPolicy(
                caps = listOf(
                    Cap.PerWindow(CHAIN, 1.hours, TokenAmount(CHAIN, AssetRef.Native, BigInteger.valueOf(15_000))),
                ),
            ),
        )
        // First reservation: 10_000 estimate, settle at 9_000 → 9_000 spent in window.
        val r1 = treasury.reserve(ctx(), TokenAmount(CHAIN, AssetRef.Native, BigInteger.valueOf(10_000)))
        treasury.settle(r1, anchorResult(BigInteger.valueOf(9_000)), success = true)

        // Second reservation: 10_000 estimate → 9_000 + 10_000 = 19_000 > 15_000 → reject.
        assertThrows<TreasuryException.CapExceeded> {
            treasury.reserve(ctx(), TokenAmount(CHAIN, AssetRef.Native, BigInteger.valueOf(10_000)))
        }
    }
}
