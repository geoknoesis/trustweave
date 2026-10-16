package org.trustweave.trust.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.trust.domain.events.DomainEvent
import org.trustweave.trust.domain.events.DomainEventSink
import org.trustweave.trust.domain.treasury.ChainAccount
import org.trustweave.trust.domain.treasury.InMemoryDomainTreasury
import org.trustweave.trust.domain.treasury.KmsKeyRef
import org.trustweave.trust.domain.treasury.SettlementStatus
import org.trustweave.trust.domain.treasury.SignedTx
import org.trustweave.trust.domain.treasury.UnsignedTx
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val CHAIN = "eip155:137"

private fun fee(amount: Long) = TokenAmount(CHAIN, AssetRef.Native, BigInteger.valueOf(amount))

class TrustedDomainManagerTest {
    private class FixedAccount(
        private val balance: Long,
    ) : ChainAccount {
        override val chainId = CHAIN
        override val address = "0xabc"
        override val keyRef = KmsKeyRef("kms", "key")

        override suspend fun balance() = fee(balance)

        override suspend fun estimateFee(op: OperationDescriptor) = fee(100)

        override suspend fun sign(tx: UnsignedTx) = SignedTx(tx.chainId, tx.bytes)
    }

    private class ScriptedClient(
        private val estimate: Long = 100,
        private val actualFee: Long = 80,
        private val failure: Throwable? = null,
    ) : BlockchainAnchorClient {
        val writes = mutableListOf<PaymentContext?>()
        var lastMediaType: String? = null

        override suspend fun writePayload(
            payload: kotlinx.serialization.json.JsonElement,
            mediaType: String,
        ): AnchorResult = error("the payment-aware overload must be used")

        override suspend fun writePayload(
            payload: kotlinx.serialization.json.JsonElement,
            ctx: PaymentContext,
            mediaType: String,
        ): AnchorResult {
            writes += ctx
            lastMediaType = mediaType
            failure?.let { throw it }
            return AnchorResult(
                ref = AnchorRef(CHAIN, "0xtx"),
                payload = payload,
                mediaType = mediaType,
                fee = fee(actualFee),
                payerAddress = "0xabc",
            )
        }

        override suspend fun estimate(op: OperationDescriptor) = fee(estimate)

        override suspend fun readPayload(ref: AnchorRef): AnchorResult = error("not used")
    }

    private class RecordingSink(
        private val throwing: Boolean = false,
    ) : DomainEventSink {
        val events = CopyOnWriteArrayList<DomainEvent>()

        override suspend fun emit(event: DomainEvent) {
            events += event
            if (throwing) throw IllegalStateException("sink down")
        }
    }

    private val payload = JsonObject(mapOf("hash" to JsonPrimitive("abc")))

    private fun manager(
        client: BlockchainAnchorClient?,
        balance: Long = 1_000_000,
        sink: DomainEventSink = RecordingSink(),
        treasuryPolicy: org.trustweave.trust.domain.treasury.SpendPolicy = org.trustweave.trust.domain.treasury.SpendPolicy.DEFAULT,
    ): Pair<TrustedDomainManager, InMemoryDomainTreasury> {
        val treasury =
            InMemoryDomainTreasury(
                domainId = DomainId("edu"),
                accounts = mapOf(CHAIN to FixedAccount(balance)),
                spendPolicy = treasuryPolicy,
                eventSink = sink,
            )
        val registry = BlockchainAnchorRegistry()
        if (client != null) registry.register(CHAIN, client)
        return TrustedDomainManager(DomainId("edu"), "did:web:payer", treasury, registry, sink) to treasury
    }

    @Test
    fun `anchoring reserves, writes with the payment context and settles the actual fee`() =
        runBlocking {
            val sink = RecordingSink()
            val client = ScriptedClient(estimate = 100, actualFee = 80)
            val (manager, treasury) = manager(client, sink = sink)

            val result = manager.anchor("did.anchor", CHAIN, payload, mediaType = "application/vc+ld+json")

            assertEquals("0xtx", result.ref.txHash)
            assertEquals("application/vc+ld+json", client.lastMediaType)
            val ctx = client.writes.single()!!
            assertEquals("edu", ctx.domainId)
            assertEquals("did:web:payer", ctx.payerDid)
            assertEquals(CHAIN, ctx.chainId)
            assertSame(FeeStrategy.DomainPays, ctx.feeStrategy)

            val entry = treasury.ledger().get(ctx.correlationId)!!
            assertEquals(SettlementStatus.SETTLED, entry.status)
            assertEquals("80", entry.actualFeeAmount)
            assertEquals("0xtx", entry.txHash)
            assertEquals(
                listOf(DomainEvent.OnChainSpendReserved::class, DomainEvent.OnChainSpendSettled::class),
                sink.events.map { it::class },
            )
            assertSame(treasury, manager.treasury())
            assertEquals(DomainId("edu"), manager.domainId)
        }

    @Test
    fun `anchoring on an unregistered chain lists the chains that are available`() =
        runBlocking {
            val (manager, _) = manager(client = null)

            val error = assertFailsWith<BlockchainException.ChainNotRegistered> { manager.anchor("k", CHAIN, payload) }

            assertEquals(CHAIN, error.chainId)
            assertTrue(error.availableChains.isEmpty())
        }

    @Test
    fun `a client failure cancels the reservation, reports the reason and rethrows the original error`() =
        runBlocking {
            val sink = RecordingSink()
            val boom = IllegalStateException("rpc down")
            val client = ScriptedClient(failure = boom)
            val (manager, treasury) = manager(client, sink = sink)

            val thrown = assertFailsWith<IllegalStateException> { manager.anchor("k", CHAIN, payload) }

            assertSame(boom, thrown)
            val correlationId = client.writes.single()!!.correlationId
            assertEquals(SettlementStatus.CANCELLED, treasury.ledger().get(correlationId)!!.status)
            val failed = sink.events.filterIsInstance<DomainEvent.OnChainSpendFailed>().single()
            assertEquals("rpc down", failed.reason)
            assertEquals(correlationId, failed.correlationId)
            assertTrue(sink.events.any { it is DomainEvent.OnChainSpendCancelled })
        }

    @Test
    fun `a failure without a message is reported by its class name`() =
        runBlocking {
            val sink = RecordingSink()
            val (manager, _) = manager(ScriptedClient(failure = IllegalStateException()), sink = sink)

            assertFailsWith<IllegalStateException> { manager.anchor("k", CHAIN, payload) }

            assertEquals(
                "IllegalStateException",
                sink.events
                    .filterIsInstance<DomainEvent.OnChainSpendFailed>()
                    .single()
                    .reason,
            )
        }

    @Test
    fun `a broken event sink never masks the underlying failure or blocks a success`() =
        runBlocking {
            val boom = IllegalStateException("rpc down")
            val (failing, _) = manager(ScriptedClient(failure = boom), sink = RecordingSink(throwing = true))
            assertSame(boom, assertFailsWith<IllegalStateException> { failing.anchor("k", CHAIN, payload) })

            val (ok, _) = manager(ScriptedClient(), sink = RecordingSink(throwing = true))
            assertEquals("0xtx", ok.anchor("k", CHAIN, payload).ref.txHash)
        }

    @Test
    fun `coroutine cancellation from the client is propagated and is not reported as a spend failure`() =
        runBlocking {
            val sink = RecordingSink()
            val (manager, _) = manager(ScriptedClient(failure = CancellationException("stop")), sink = sink)

            assertFailsWith<CancellationException> { manager.anchor("k", CHAIN, payload) }

            assertTrue(sink.events.none { it is DomainEvent.OnChainSpendFailed })
        }

    @Test
    fun `a strategy the policy does not allow is rejected before anything is written`() =
        runBlocking {
            val client = ScriptedClient()
            val (manager, _) = manager(client)

            assertFailsWith<TreasuryException.StrategyNotAllowed> {
                manager.anchor("k", CHAIN, payload, feeStrategy = FeeStrategy.SelfPay)
            }

            assertTrue(client.writes.isEmpty())
        }

    @Test
    fun `a caller fee ceiling below the estimate is enforced before writing`() =
        runBlocking {
            val client = ScriptedClient(estimate = 500)
            val (manager, _) = manager(client)

            assertFailsWith<TreasuryException.CallerCapExceeded> { manager.anchor("k", CHAIN, payload, maxFee = fee(100)) }

            assertTrue(client.writes.isEmpty())
        }

    @Test
    fun `insufficient funds stop the anchor and the estimate is never spent`() =
        runBlocking {
            val client = ScriptedClient(estimate = 5_000)
            val (manager, treasury) = manager(client, balance = 1_000)

            val error = assertFailsWith<TreasuryException.InsufficientFunds> { manager.anchor("k", CHAIN, payload) }

            assertIs<TreasuryException>(error)
            assertTrue(client.writes.isEmpty())
            assertTrue(treasury.ledger().all().isEmpty())
        }

    private class HangingClient : BlockchainAnchorClient {
        val started = kotlinx.coroutines.CompletableDeferred<PaymentContext>()

        override suspend fun writePayload(
            payload: kotlinx.serialization.json.JsonElement,
            mediaType: String,
        ): AnchorResult = error("the payment-aware overload must be used")

        override suspend fun writePayload(
            payload: kotlinx.serialization.json.JsonElement,
            ctx: PaymentContext,
            mediaType: String,
        ): AnchorResult {
            started.complete(ctx)
            kotlinx.coroutines.awaitCancellation()
        }

        override suspend fun estimate(op: OperationDescriptor) = fee(100)

        override suspend fun readPayload(ref: AnchorRef): AnchorResult = error("not used")
    }

    @Test
    fun `cancelling the caller releases the reservation`(): Unit =
        runBlocking {
            val client = HangingClient()
            val (manager, treasury) = manager(client)

            val job = launch { manager.anchor("k", CHAIN, payload) }
            val ctx = client.started.await()
            job.cancelAndJoin()

            assertEquals(SettlementStatus.CANCELLED, treasury.ledger().get(ctx.correlationId)!!.status)
        }
}
