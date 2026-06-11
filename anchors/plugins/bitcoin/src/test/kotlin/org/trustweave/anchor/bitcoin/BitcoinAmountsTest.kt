package org.trustweave.anchor.bitcoin

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Precision tests for [BitcoinAmounts]: all conversions must be exact integer
 * satoshi math — the previous floating-point arithmetic lost satoshis on
 * amounts that have no exact binary representation.
 */
class BitcoinAmountsTest {

    @Test
    fun `btcToSatoshis converts exactly`() {
        assertEquals(0L, BitcoinAmounts.btcToSatoshis("0"))
        assertEquals(1L, BitcoinAmounts.btcToSatoshis("0.00000001"))
        assertEquals(100_000_000L, BitcoinAmounts.btcToSatoshis("1"))
        assertEquals(100_000_000L, BitcoinAmounts.btcToSatoshis("1.00000000"))
        assertEquals(12_345L, BitcoinAmounts.btcToSatoshis("0.00012345"))
        // 21M BTC supply cap fits in Long satoshis
        assertEquals(2_100_000_000_000_000L, BitcoinAmounts.btcToSatoshis("21000000"))
    }

    @Test
    fun `btcToSatoshis is exact where double arithmetic is not`() {
        // 0.1 has no exact binary representation: 0.1 * 1e8 as doubles can land
        // on 9999999.999... and truncate. The decimal path must be exact.
        assertEquals(10_000_000L, BitcoinAmounts.btcToSatoshis("0.1"))
        assertEquals(30_000_000L, BitcoinAmounts.btcToSatoshis("0.3"))
        assertEquals(123_456_789L, BitcoinAmounts.btcToSatoshis("1.23456789"))
        // Known double-truncation victims: (btc.toDouble() * 1e8).toLong() loses a satoshi
        assertEquals(2_900_000_000L, BitcoinAmounts.btcToSatoshis("29"))
        assertEquals(5_500_000_000L, BitcoinAmounts.btcToSatoshis("55"))
        assertEquals(12_212_000_000L, BitcoinAmounts.btcToSatoshis("122.12"))
    }

    @Test
    fun `btcToSatoshis rejects sub-satoshi precision`() {
        assertFailsWith<ArithmeticException> { BitcoinAmounts.btcToSatoshis("0.000000001") }
    }

    @Test
    fun `satoshisToBtcString renders plain decimal notation`() {
        assertEquals("0.00000001", BitcoinAmounts.satoshisToBtcString(1L))
        assertEquals("0.0001", BitcoinAmounts.satoshisToBtcString(10_000L))
        assertEquals("1", BitcoinAmounts.satoshisToBtcString(100_000_000L))
        assertEquals("1.23456789", BitcoinAmounts.satoshisToBtcString(123_456_789L))
        assertEquals("21000000", BitcoinAmounts.satoshisToBtcString(2_100_000_000_000_000L))
        // Never scientific notation (BigDecimal.toString would emit 1E-8)
        assertFalse(BitcoinAmounts.satoshisToBtcString(1L).contains('E', ignoreCase = true))
    }

    @Test
    fun `conversions round-trip`() {
        for (sats in listOf(0L, 1L, 999L, 1_000L, 12_345L, 100_000_000L, 2_100_000_000_000_000L)) {
            assertEquals(sats, BitcoinAmounts.btcToSatoshis(BitcoinAmounts.satoshisToBtcString(sats)))
        }
    }

    @Test
    fun `change computation stays exact in integer math`() {
        // The on-chain scenario: inputs minus the flat 1000-sat fee.
        val totalInputSats = BitcoinAmounts.btcToSatoshis("0.1")
        val changeSats = totalInputSats - 1_000L
        assertEquals(9_999_000L, changeSats)
        assertEquals("0.09999", BitcoinAmounts.satoshisToBtcString(changeSats))
    }
}
