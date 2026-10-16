package org.trustweave.anchor.bitcoin

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bitcoinj.base.Coin
import org.bitcoinj.base.Sha256Hash
import org.bitcoinj.core.Transaction
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.exceptions.BlockchainException
import java.nio.charset.StandardCharsets
import java.util.HexFormat
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BitcoinReadAndWriteChecksTest {
    private val chain = BitcoinBlockchainAnchorClient.MAINNET
    private val params = MainNetParams.get()
    private val payloadJson = """{"anchored":"payload"}"""
    private val blockHash = "bb".repeat(32)
    private val fundingTx = "ee".repeat(32)

    private val tx: Pair<String, String> =
        Transaction(params).let {
            it.addInput(Sha256Hash.wrap(fundingTx), 1L, ScriptBuilder.createEmpty())
            it.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(payloadJson.toByteArray(StandardCharsets.UTF_8)))
            HexFormat.of().formatHex(it.serialize()) to it.txId.toString()
        }
    private val rawHex = tx.first
    private val txId = tx.second

    private fun node(
        blockhash: String? = blockHash,
        confirmations: Long = 5,
        listed: List<String> = listOf(txId),
        verboseError: Boolean = false,
        fundingAddress: String = "bc1qfunder",
    ) = BitcoinRpcStub { method, rpcParams ->
        when (method) {
            "getrawtransaction" ->
                when {
                    rpcParams.size < 2 -> "\"$rawHex\""
                    rpcParams[0].jsonPrimitive.content == fundingTx ->
                        """{"txid":"$fundingTx","vout":[{"n":0,"scriptPubKey":{"address":"other"}},{"n":1,"scriptPubKey":{"address":"$fundingAddress"}}]}"""
                    verboseError -> throw BitcoinRpcStub.RpcError("No such mempool or blockchain transaction")
                    else ->
                        """{"txid":"$txId","vin":[{"txid":"$fundingTx","vout":1}]${blockhash?.let { ",\"blockhash\":\"$it\"" } ?: ""}}"""
                }
            "getblock" ->
                """{"hash":"$blockHash","confirmations":$confirmations,"time":1700000000,"tx":[${listed.joinToString(
                    ",",
                ) { "\"$it\"" }}]}"""
            else -> throw BitcoinRpcStub.RpcError("unexpected $method")
        }
    }

    private fun read(
        stub: BitcoinRpcStub,
        extra: Map<String, Any?> = emptyMap(),
    ) = BitcoinBlockchainAnchorClient(chain, stub.options + extra).use { client ->
        runBlocking { client.readPayload(AnchorRef(chain, txId)) }
    }

    // --- reads ---

    @Test
    fun `a buried transaction reads back with its block time`() {
        node().use { assertEquals(1_700_000_000L, read(it).timestamp) }
    }

    @Test
    fun `an unconfirmed transaction is rejected`() {
        node(blockhash = null).use { assertFailsWith<BlockchainException.TransactionFailed> { read(it) } }
    }

    @Test
    fun `an RPC error while resolving the block is an error not unconfirmed`() {
        node(verboseError = true).use { stub ->
            val failure = assertFailsWith<Exception> { read(stub) }
            assertTrue(failure.message.orEmpty().contains("No such mempool"), failure.toString())
        }
    }

    @Test
    fun `confirmation depth and best-chain membership are enforced`() {
        node(confirmations = 1).use {
            assertEquals(1_700_000_000L, read(it).timestamp)
            assertFailsWith<BlockchainException.TransactionFailed> {
                read(it, mapOf(BitcoinBlockchainAnchorClient.OPTION_MIN_CONFIRMATIONS to 6))
            }
        }
        node(confirmations = -1).use { assertFailsWith<BlockchainException.TransactionFailed> { read(it) } }
        node(listed = listOf("00".repeat(32))).use { assertFailsWith<BlockchainException.TransactionFailed> { read(it) } }
    }

    @Test
    fun `the expected sender must fund every input`() {
        node().use {
            assertEquals(1_700_000_000L, read(it, mapOf(BitcoinBlockchainAnchorClient.OPTION_EXPECTED_SENDER to "bc1qfunder")).timestamp)
            assertFailsWith<BlockchainException.TransactionFailed> {
                read(it, mapOf(BitcoinBlockchainAnchorClient.OPTION_EXPECTED_SENDER to "bc1qsomeoneelse"))
            }
        }
    }

    // --- writes ---

    private val p2wpkh = "0014" + "22".repeat(20)

    private fun walletNode(
        utxosBtc: List<String>,
        feerate: String? = "0.00002000",
        sent: MutableList<JsonArray> = mutableListOf(),
    ) = BitcoinRpcStub { method, rpcParams ->
        when (method) {
            "listunspent" ->
                utxosBtc.indices.joinToString(",", "[", "]") { i ->
                    """{"txid":"${"%064x".format(i + 1)}","vout":0,"amount":${utxosBtc[i]},"scriptPubKey":"$p2wpkh"}"""
                }
            "estimatesmartfee" ->
                if (feerate !=
                    null
                ) {
                    """{"feerate":$feerate,"blocks":6}"""
                } else {
                    """{"errors":["Insufficient data"],"blocks":0}"""
                }
            "createrawtransaction" -> {
                sent += rpcParams
                "\"rawhex\""
            }
            "signrawtransactionwithkey" -> """{"hex":"abcd","complete":true}"""
            "sendrawtransaction" -> "\"${"f".repeat(64)}\""
            else -> throw BitcoinRpcStub.RpcError("unexpected $method")
        }
    }

    private fun write(
        stub: BitcoinRpcStub,
        extra: Map<String, Any?> = emptyMap(),
    ) = BitcoinBlockchainAnchorClient(
        chain,
        stub.options + mapOf("address" to "bc1qfunder", "privateKey" to "key") + extra,
    ).use { client -> runBlocking { client.writePayload(kotlinx.serialization.json.JsonPrimitive("x")) } }

    @Test
    fun `only confirmed outputs are listed and the estimatesmartfee rate prices the transaction`() {
        val sent = mutableListOf<JsonArray>()
        walletNode(listOf("0.00001000", "1.00000000", "0.00002000"), sent = sent).use { stub ->
            write(stub)
            val listunspent = stub.calls.first { it.first == "listunspent" }.second
            assertEquals(1, listunspent[0].jsonPrimitive.content.toInt(), "minconf must be 1")
            val created = sent.single()
            val inputs = created[0].jsonArray
            assertEquals(1, inputs.size, "the large output alone covers the fee; the others must not be spent")
            val outputs = created[1].jsonObject
            val change =
                outputs.entries
                    .first { it.key != "data" }
                    .value.jsonPrimitive.content
            val changeSats =
                java.math
                    .BigDecimal(change)
                    .movePointRight(8)
                    .longValueExact()
            // 2 sat/vB * (11 + 68 + OP_RETURN output + change output)
            val fee = 100_000_000L - changeSats
            assertTrue(fee in 200..2_000, "fee $fee sats should be rate*vsize, not a flat 1000 or the whole input")
            assertEquals(0L, fee % 2, "2 sat/vB gives an even fee")
        }
    }

    @Test
    fun `a dust-sized remainder is not turned into a change output`() {
        val sent = mutableListOf<JsonArray>()
        walletNode(listOf("0.00000400"), feerate = "0.00001000", sent = sent).use { stub ->
            write(stub)
            assertEquals(setOf("data"), sent.single()[1].jsonObject.keys)
        }
    }

    @Test
    fun `no fee rate means no guess unless a fallback is configured`() {
        walletNode(listOf("1.00000000"), feerate = null).use { stub ->
            assertFailsWith<BlockchainException.TransactionFailed> { write(stub) }
            assertEquals(false, stub.calls.any { it.first == "createrawtransaction" })
            write(stub, mapOf(BitcoinBlockchainAnchorClient.OPTION_FALLBACK_FEE_RATE to 3))
            assertEquals(true, stub.calls.any { it.first == "createrawtransaction" })
        }
    }

    @Test
    fun `a fee rate above the configured maximum is refused`() {
        walletNode(listOf("1.00000000"), feerate = "0.01000000").use { stub ->
            assertFailsWith<BlockchainException.TransactionFailed> {
                write(stub, mapOf(BitcoinBlockchainAnchorClient.OPTION_MAX_FEE_RATE to 50))
            }
        }
    }
}
