package org.trustweave.anchor.evm

import kotlinx.coroutines.runBlocking
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.testkit.anchor.FakeEvmJsonRpcServer
import org.web3j.crypto.Credentials
import org.web3j.crypto.TransactionDecoder
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** eth_chainId verification, the EIP-7623 gas floor, `maxGasPrice` and send-time `PaymentContext.maxFee`. */
class EvmChainIdAndFeeTest {
    private val txHash = "0x" + "ab".repeat(32)
    private val key = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    private val account = Credentials.create(key).address
    private val chain = EvmChainConfig(1337L, "http://localhost:18545", "TestEvm", "test-evm-local")
    private val chainIdStr = "eip155:1337"

    private class Client(
        chain: EvmChainConfig,
        options: Map<String, Any?>,
    ) : AbstractEvmAnchorClient("eip155:${chain.numericChainId}", options, chain) {
        suspend fun submit(
            data: ByteArray,
            ctx: PaymentContext? = null,
        ) = submitTransaction(data, ctx)

        suspend fun read(txHash: String) = readTransactionFromBlockchain(txHash)
    }

    private fun options(
        rpc: FakeEvmJsonRpcServer,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        mapOf(
            "rpcUrl" to rpc.url,
            "privateKey" to key,
            AbstractBlockchainAnchorClient.OPTION_CONFIRMATION_TIMEOUT_MS to 5_000L,
            AbstractBlockchainAnchorClient.OPTION_CONFIRMATION_POLL_INTERVAL_MS to 10L,
        ) + extra

    private val data = """{"anchored":"payload","padding":"${"x".repeat(150)}"}""".toByteArray()

    private fun ctx(maxFee: BigInteger?) =
        PaymentContext(
            domainId = "d",
            payerDid = "did:key:p",
            chainId = chainIdStr,
            feeStrategy = FeeStrategy.SelfPay,
            maxFee = maxFee?.let { TokenAmount(chainIdStr, AssetRef.Native, it) },
        )

    // --- chain id ---

    @Test
    fun `a chain id mismatch fails closed before anything is signed or read`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account, chainId = 5L).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    assertFailsWith<BlockchainException.ConfigurationFailed> { client.submit(data) }
                    assertFailsWith<BlockchainException.ConfigurationFailed> { client.read(txHash) }
                    assertTrue(rpc.rawTransactions.isEmpty())
                    assertTrue("eth_sendRawTransaction" !in rpc.methods)
                }
            }
        }

    @Test
    fun `the chain id is verified once and a matching answer is remembered`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    client.submit(data)
                    client.submit(data)
                    assertEquals(1, rpc.methods.count { it == "eth_chainId" })
                    assertEquals(2, rpc.rawTransactions.size)
                }
            }
        }

    // --- gas ---

    @Test
    fun `the signed gas limit covers the EIP-7623 floor even when the node estimates less`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                rpc.estimateGasHex = "0x5208" // 21000
                Client(chain, options(rpc)).use { client ->
                    client.submit(data)
                    val gasLimit = TransactionDecoder.decode(rpc.rawTransactions.single()).gasLimit
                    assertTrue(gasLimit >= EvmGas.calldataFloorGas(data), "gas limit $gasLimit below the floor")
                    assertEquals(EvmGas.requiredGas(data), gasLimit)
                    assertTrue("eth_estimateGas" in rpc.methods)
                }
            }
        }

    @Test
    fun `a larger node estimate wins and a failed estimate falls back to the required gas plus margin`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                rpc.estimateGasHex = "0x" + BigInteger.valueOf(500_000).toString(16)
                Client(chain, options(rpc)).use { client ->
                    client.submit(data)
                    assertEquals(
                        EvmGas.withMargin(BigInteger.valueOf(500_000)),
                        TransactionDecoder.decode(rpc.rawTransactions.single()).gasLimit,
                    )
                }
            }
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                rpc.failEstimateGas = true
                Client(chain, options(rpc)).use { client ->
                    client.submit(data)
                    assertEquals(EvmGas.txGasLimit(data), TransactionDecoder.decode(rpc.rawTransactions.single()).gasLimit)
                }
            }
        }

    @Test
    fun `maxGasPrice rejects a higher quote before signing`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                rpc.gasPriceHex = "0x3b9aca00" // 1 gwei
                Client(chain, options(rpc, mapOf(AbstractEvmAnchorClient.OPTION_MAX_GAS_PRICE to 999_999_999L))).use { client ->
                    val failure = assertFailsWith<BlockchainException.TransactionFailed> { client.submit(data) }
                    assertTrue(failure.message.contains("maxGasPrice"))
                    assertTrue(rpc.rawTransactions.isEmpty())
                }
                Client(chain, options(rpc, mapOf(AbstractEvmAnchorClient.OPTION_MAX_GAS_PRICE to "1000000000"))).use { client ->
                    client.submit(data)
                    assertEquals(1, rpc.rawTransactions.size)
                }
            }
        }

    // --- maxFee at send time ---

    @Test
    fun `maxFee is enforced against the fee actually signed`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                val signedFee = EvmGas.requiredGas(data).multiply(BigInteger.valueOf(1_000_000_000L))
                Client(chain, options(rpc)).use { client ->
                    assertFailsWith<TreasuryException.CallerCapExceeded> { client.submit(data, ctx(signedFee - BigInteger.ONE)) }
                    assertTrue(rpc.rawTransactions.isEmpty(), "nothing may be signed over the cap")
                    client.submit(data, ctx(signedFee))
                    assertEquals(1, rpc.rawTransactions.size)
                }
            }
        }
}
