package org.trustweave.anchor.zksync

import org.trustweave.anchor.evm.EvmGas
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZkSyncBlockchainAnchorClientTest {

    @Test
    fun `chain IDs`() {
        assertEquals("eip155:324", ZkSyncBlockchainAnchorClient.MAINNET)
        assertEquals("eip155:300", ZkSyncBlockchainAnchorClient.SEPOLIA)
    }

    @Test
    fun `gas limit uses eth_estimateGas result plus margin as primary`() {
        // zkSync Era charges bootloader/validation overhead and pubdata inside the
        // gas limit — a node estimate of several hundred thousand gas is normal.
        val data = """{"digest":"uABC123"}""".toByteArray()
        val estimated = BigInteger.valueOf(300_000)

        val gasLimit = ZkSyncBlockchainAnchorClient.gasLimitFrom(estimated, data)

        // estimate + 20% margin
        assertEquals(
            EvmGas.withMargin(estimated, ZkSyncBlockchainAnchorClient.ESTIMATE_GAS_MARGIN_PERCENT),
            gasLimit
        )
        assertEquals(BigInteger.valueOf(360_000), gasLimit)
    }

    @Test
    fun `gas limit is floored at the intrinsic gas when estimate is implausibly low`() {
        val data = ByteArray(1000) { 0x42 }
        val bogusEstimate = BigInteger.valueOf(1_000)

        val gasLimit = ZkSyncBlockchainAnchorClient.gasLimitFrom(bogusEstimate, data)

        assertEquals(EvmGas.intrinsicGas(data), gasLimit)
    }

    @Test
    fun `gas limit falls back to intrinsic calldata gas when estimation fails`() {
        val data = """{"digest":"uABC123"}""".toByteArray()

        val gasLimit = ZkSyncBlockchainAnchorClient.gasLimitFrom(null, data)

        assertEquals(EvmGas.txGasLimit(data), gasLimit)
        assertTrue(gasLimit >= EvmGas.intrinsicGas(data))
    }
}
