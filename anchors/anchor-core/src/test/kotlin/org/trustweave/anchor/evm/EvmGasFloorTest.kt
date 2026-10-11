package org.trustweave.anchor.evm

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** EIP-7623: floor = 21000 + 10 * tokens, tokens = zeroBytes + 4 * nonZeroBytes; gas used is the max with intrinsic. */
class EvmGasFloorTest {
    @Test
    fun `calldata floor follows the EIP-7623 token formula`() {
        val data = ByteArray(100) { 1 } + ByteArray(50) // 100 non-zero, 50 zero bytes
        assertEquals(BigInteger.valueOf(21_000 + 4 * 50 + 16 * 100), EvmGas.intrinsicGas(data))
        // tokens = 50 + 4 * 100 = 450
        assertEquals(BigInteger.valueOf(21_000 + 10 * 450), EvmGas.calldataFloorGas(data))
        assertEquals(EvmGas.calldataFloorGas(data), EvmGas.requiredGas(data))
    }

    @Test
    fun `required gas is the larger of intrinsic and floor`() {
        // For any calldata the floor (10/token) exceeds intrinsic (4/token); empty calldata is just the base cost.
        assertEquals(BigInteger.valueOf(21_000), EvmGas.requiredGas(ByteArray(0)))
        val zeros = ByteArray(100)
        assertEquals(BigInteger.valueOf(21_000 + 10 * 100), EvmGas.requiredGas(zeros))
        assertTrue(EvmGas.requiredGas(zeros) >= EvmGas.intrinsicGas(zeros))
    }

    @Test
    fun `gas limits cover the floor`() {
        val json = """{"anchored":"payload","digest":"abcdef0123456789"}""".toByteArray()
        assertTrue(EvmGas.txGasLimit(json) >= EvmGas.calldataFloorGas(json))
        assertTrue(EvmGas.txGasLimitForSize(json.size.toLong()) >= EvmGas.calldataFloorGas(json))
        assertEquals(BigInteger.valueOf(21_000 + 40 * 1000), EvmGas.requiredGasForSize(1000))
    }
}
