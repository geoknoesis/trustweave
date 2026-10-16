package org.trustweave.anchor.cardano

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A Cardano read reports the anchor block's time (never "now") and requires a buried block. */
class CardanoReadSettlementTest {
    private lateinit var server: MockWebServer
    private val txHash = "aa".repeat(32)

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun read(extra: Map<String, Any?> = emptyMap()) =
        run {
            val cfg =
                CardanoAnchorConfig(
                    blockfrostProjectId = "preview-test",
                    network = CardanoNetwork.Preview,
                    blockfrostBaseUrlOverride = server.url("/api/v0").toString().trimEnd('/'),
                )
            CardanoBlockchainAnchorClient(CardanoBlockchainAnchorClient.PREVIEW, cfg.toMap() + extra, cfg).use { client ->
                runBlocking { client.readPayload(AnchorRef(CardanoBlockchainAnchorClient.PREVIEW, txHash)) }
            }
        }

    @Test
    fun `the timestamp is the block time of the anchor, not the time of the read`() {
        server.dispatcher = cardanoDispatcher(txBody = txContentBody(txHash, time = 1_700_000_000L), latestBlockBody = latestBlockBody(110))
        assertEquals(1_700_000_000L, read().timestamp)
    }

    @Test
    fun `an unreported block time is null rather than invented`() {
        server.dispatcher = cardanoDispatcher(txBody = txContentBody(txHash, time = null), latestBlockBody = latestBlockBody(110))
        assertNull(read().timestamp)
    }

    @Test
    fun `a transaction that is not in a block is rejected`() {
        server.dispatcher =
            cardanoDispatcher(txBody = txContentBody(txHash, block = null, height = null), latestBlockBody = latestBlockBody(110))
        assertThrows<BlockchainException.TransactionFailed> { read() }
    }

    @Test
    fun `too few confirmations are rejected and the depth is configurable`() {
        server.dispatcher = cardanoDispatcher(txBody = txContentBody(txHash, height = 100), latestBlockBody = latestBlockBody(100))
        assertThrows<BlockchainException.TransactionFailed> { read() }
        assertThrows<BlockchainException.TransactionFailed> { read(mapOf(CardanoBlockchainAnchorClient.OPTION_MIN_CONFIRMATIONS to 5)) }
        assertEquals(1_700_000_000L, read(mapOf(CardanoBlockchainAnchorClient.OPTION_MIN_CONFIRMATIONS to 0)).timestamp)
    }

    @Test
    fun `transaction details for a different hash are rejected`() {
        server.dispatcher = cardanoDispatcher(txBody = txContentBody("dd".repeat(32)), latestBlockBody = latestBlockBody(110))
        assertThrows<BlockchainException.TransactionFailed> { read() }
    }

    @Test
    fun `unknown transaction details are NotFound`() {
        server.dispatcher = cardanoDispatcher(txBody = null, latestBlockBody = latestBlockBody(110))
        assertThrows<TrustWeaveException.NotFound> { read() }
    }
}
