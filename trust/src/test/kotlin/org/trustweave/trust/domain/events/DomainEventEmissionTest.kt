package org.trustweave.trust.domain.events

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.DomainId
import org.trustweave.trust.domain.treasury.ChainAccount
import org.trustweave.trust.domain.treasury.InMemoryDomainTreasury
import org.trustweave.trust.domain.treasury.KmsKeyRef
import org.trustweave.trust.domain.treasury.SignedTx
import org.trustweave.trust.domain.treasury.UnsignedTx
import java.math.BigInteger

private const val CHAIN = "eip155:137"

private class RecordingSink : DomainEventSink {
    private val mutex = Mutex()
    private val recorded = mutableListOf<DomainEvent>()

    override suspend fun emit(event: DomainEvent) {
        mutex.withLock { recorded += event }
    }

    suspend fun snapshot(): List<DomainEvent> = mutex.withLock { recorded.toList() }
}

private class StubAccount(initial: BigInteger, private val fee: BigInteger) : ChainAccount {
    override val chainId: String = CHAIN
    override val address: String = "0xCAFEBABE"
    override val keyRef: KmsKeyRef = KmsKeyRef("in-memory", "k")
    private var current = initial
    override suspend fun balance(): TokenAmount = TokenAmount(chainId, AssetRef.Native, current)
    override suspend fun estimateFee(op: OperationDescriptor): TokenAmount =
        TokenAmount(chainId, AssetRef.Native, fee)

    override suspend fun sign(tx: UnsignedTx): SignedTx = SignedTx(tx.chainId, tx.bytes)
}

class DomainEventEmissionTest {

    private val domainId = DomainId("edu-algeria")

    private fun ctx() = PaymentContext(
        domainId = domainId.value,
        payerDid = "did:web:example.org",
        chainId = CHAIN,
        feeStrategy = FeeStrategy.DomainPays,
    )

    private fun result(fee: BigInteger): AnchorResult = AnchorResult(
        ref = AnchorRef(chainId = CHAIN, txHash = "0xabc"),
        payload = JsonObject(emptyMap()),
        fee = TokenAmount(CHAIN, AssetRef.Native, fee),
        payerAddress = "0xCAFEBABE",
    )

    @Test
    fun `reserve then settle emits Reserved then Settled`() = runBlocking<Unit> {
        val sink = RecordingSink()
        val account = StubAccount(BigInteger.valueOf(1_000_000), BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = domainId,
            accounts = mapOf(CHAIN to account),
            eventSink = sink,
        )

        val reservation = treasury.reserve(ctx(), account.estimateFee(OperationDescriptor("did.create", CHAIN)))
        treasury.settle(reservation, result(BigInteger.valueOf(9_000)), success = true)

        val events = sink.snapshot()
        assertEquals(2, events.size)
        val reserved = events[0]
        assertTrue(reserved is DomainEvent.OnChainSpendReserved)
        reserved as DomainEvent.OnChainSpendReserved
        assertEquals(domainId, reserved.domainId)
        assertEquals(CHAIN, reserved.chainId)
        assertEquals(reservation.correlationId, reserved.correlationId)
        assertEquals(BigInteger.valueOf(10_000), reserved.estimate.amount)

        val settled = events[1]
        assertTrue(settled is DomainEvent.OnChainSpendSettled)
        settled as DomainEvent.OnChainSpendSettled
        assertEquals(reservation.correlationId, settled.correlationId)
        assertEquals(BigInteger.valueOf(9_000), settled.actualFee.amount)
        assertEquals("0xabc", settled.txHash)
        assertEquals("0xCAFEBABE", settled.payerAddress)
    }

    @Test
    fun `reserve then cancel emits Reserved then Cancelled`() = runBlocking<Unit> {
        val sink = RecordingSink()
        val account = StubAccount(BigInteger.valueOf(1_000_000), BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = domainId,
            accounts = mapOf(CHAIN to account),
            eventSink = sink,
        )

        val reservation = treasury.reserve(ctx(), account.estimateFee(OperationDescriptor("did.create", CHAIN)))
        treasury.cancel(reservation)

        val events = sink.snapshot()
        assertEquals(2, events.size)
        assertTrue(events[0] is DomainEvent.OnChainSpendReserved)
        val cancelled = events[1]
        assertTrue(cancelled is DomainEvent.OnChainSpendCancelled)
        cancelled as DomainEvent.OnChainSpendCancelled
        assertEquals(reservation.correlationId, cancelled.correlationId)
        assertEquals(CHAIN, cancelled.chainId)
        assertEquals(domainId, cancelled.domainId)
    }

    @Test
    fun `settle with success=false emits Failed`() = runBlocking<Unit> {
        val sink = RecordingSink()
        val account = StubAccount(BigInteger.valueOf(1_000_000), BigInteger.valueOf(10_000))
        val treasury = InMemoryDomainTreasury(
            domainId = domainId,
            accounts = mapOf(CHAIN to account),
            eventSink = sink,
        )

        val reservation = treasury.reserve(ctx(), account.estimateFee(OperationDescriptor("did.create", CHAIN)))
        treasury.settle(reservation, result(BigInteger.ZERO), success = false)

        val events = sink.snapshot()
        assertEquals(2, events.size)
        val failed = events[1]
        assertTrue(failed is DomainEvent.OnChainSpendFailed)
        failed as DomainEvent.OnChainSpendFailed
        assertEquals(reservation.correlationId, failed.correlationId)
    }
}
