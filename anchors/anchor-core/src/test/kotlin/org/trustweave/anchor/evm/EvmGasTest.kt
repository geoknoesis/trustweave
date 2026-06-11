package org.trustweave.anchor.evm

import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [EvmGas] calldata gas math: `21000 + 16/nonzero byte + 4/zero byte`,
 * plus the safety margin used for transaction gas limits.
 */
class EvmGasTest {

    @Test
    fun `intrinsic gas of empty calldata is the base transaction cost`() {
        assertEquals(BigInteger.valueOf(21_000), EvmGas.intrinsicGas(ByteArray(0)))
    }

    @Test
    fun `intrinsic gas charges 4 per zero byte`() {
        // 10 zero bytes -> 21000 + 10 * 4
        assertEquals(BigInteger.valueOf(21_040), EvmGas.intrinsicGas(ByteArray(10)))
    }

    @Test
    fun `intrinsic gas charges 16 per non-zero byte`() {
        // 10 non-zero bytes -> 21000 + 10 * 16
        assertEquals(BigInteger.valueOf(21_160), EvmGas.intrinsicGas(ByteArray(10) { 1 }))
    }

    @Test
    fun `intrinsic gas of mixed calldata sums per-byte costs`() {
        // 3 non-zero + 2 zero -> 21000 + 3*16 + 2*4 = 21056
        val data = byteArrayOf(0x7f, 0x00, 0x01, 0x00, 0x42)
        assertEquals(BigInteger.valueOf(21_056), EvmGas.intrinsicGas(data))
    }

    @Test
    fun `intrinsic gas of a typical anchor payload stays far below blanket defaults`() {
        // A 1 KiB JSON payload of non-zero bytes: 21000 + 1024 * 16 = 37384,
        // nowhere near web3j's 9,000,000 default.
        val payload = ByteArray(1024) { 'a'.code.toByte() }
        val gas = EvmGas.intrinsicGas(payload)
        assertEquals(BigInteger.valueOf(37_384), gas)
        assertTrue(EvmGas.txGasLimit(payload) < BigInteger.valueOf(100_000))
    }

    @Test
    fun `worst-case size estimate assumes all bytes non-zero`() {
        assertEquals(BigInteger.valueOf(21_000 + 100 * 16), EvmGas.intrinsicGasForSize(100))
        assertEquals(BigInteger.valueOf(21_000), EvmGas.intrinsicGasForSize(0))
        assertFailsWith<IllegalArgumentException> { EvmGas.intrinsicGasForSize(-1) }
    }

    @Test
    fun `tx gas limit applies the default 10 percent margin rounding up`() {
        // 21000 * 1.1 = 23100 exactly
        assertEquals(BigInteger.valueOf(23_100), EvmGas.txGasLimit(ByteArray(0)))
        // 21004 * 110 / 100 = 23104.4 -> rounded up to 23105
        assertEquals(
            BigInteger.valueOf(23_105),
            EvmGas.withMargin(BigInteger.valueOf(21_004))
        )
    }

    @Test
    fun `margin of zero percent is the identity`() {
        assertEquals(
            BigInteger.valueOf(21_000),
            EvmGas.withMargin(BigInteger.valueOf(21_000), marginPercent = 0)
        )
        assertFailsWith<IllegalArgumentException> {
            EvmGas.withMargin(BigInteger.ONE, marginPercent = -1)
        }
    }

    @Test
    fun `tx gas limit is always at least the intrinsic gas`() {
        val data = ByteArray(333) { (it % 7).toByte() }
        assertTrue(EvmGas.txGasLimit(data) >= EvmGas.intrinsicGas(data))
        assertTrue(EvmGas.txGasLimitForSize(333) >= EvmGas.intrinsicGasForSize(333))
    }
}
