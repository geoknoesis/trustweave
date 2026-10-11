package org.trustweave.anchor.bitcoin

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BitcoinTxPlannerTest {
    private val p2wpkh = "0014" + "11".repeat(20)

    private fun utxo(
        sats: Long,
        n: Int = 0,
    ) = BitcoinTxPlanner.Utxo("%064x".format(n), n, sats, p2wpkh)

    @Test
    fun `only as many inputs as needed are spent, largest first`() {
        val plan = BitcoinTxPlanner.plan(listOf(utxo(5_000, 1), utxo(100_000, 2), utxo(7_000, 3)), 40, BigDecimal(2))!!
        assertEquals(listOf(2), plan.inputs.map { it.vout })
        assertEquals(100_000, plan.inputs.sumOf { it.amountSats })
        assertEquals(plan.inputs.sumOf { it.amountSats }, plan.changeSats + plan.feeSats)
        // 11 + 68 + (8+1+1+1+40) + (8+1+22) = 161 vB at 2 sat/vB
        assertEquals(322, plan.feeSats)
    }

    @Test
    fun `several inputs are combined when one is not enough`() {
        val plan = BitcoinTxPlanner.plan(listOf(utxo(100, 1), utxo(100, 2), utxo(100, 3)), 20, BigDecimal.ONE)!!
        assertEquals(2, plan.inputs.size)
    }

    @Test
    fun `change below the dust threshold is dropped into the fee`() {
        // 1 input, 40 B payload at 1 sat/vB: fee without change = 11+68+51 = 130; leaves 100 sats of change (< 294 dust)
        val plan = BitcoinTxPlanner.plan(listOf(utxo(230)), 40, BigDecimal.ONE)!!
        assertEquals(0, plan.changeSats)
        assertEquals(230, plan.feeSats)
    }

    @Test
    fun `insufficient funds yield no plan`() {
        assertNull(BitcoinTxPlanner.plan(listOf(utxo(100)), 40, BigDecimal.ONE))
        assertNull(BitcoinTxPlanner.plan(emptyList(), 40, BigDecimal.ONE))
    }

    @Test
    fun `fees round up to whole satoshis`() {
        assertEquals(3, BitcoinTxPlanner.feeFor(2, BigDecimal("1.1")))
    }
}
