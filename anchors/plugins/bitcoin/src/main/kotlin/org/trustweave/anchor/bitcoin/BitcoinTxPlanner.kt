package org.trustweave.anchor.bitcoin

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Coin selection, fee and change arithmetic for an OP_RETURN anchor transaction. Pure integer satoshis
 * (rates are exact decimals); virtual sizes are conservative estimates by script type.
 */
internal object BitcoinTxPlanner {
    data class Utxo(
        val txid: String,
        val vout: Int,
        val amountSats: Long,
        val scriptPubKeyHex: String,
    )

    /** [inputs] to spend, the [changeSats] to send back (0 when dropped) and the [feeSats] actually left to miners. */
    data class Plan(
        val inputs: List<Utxo>,
        val changeSats: Long,
        val feeSats: Long,
    )

    private enum class ScriptType(
        /** Virtual bytes to spend an output of this type (signed input). */
        val inputVBytes: Int,
        /** Dust threshold at the 3 sat/vB dust relay rate (Bitcoin Core's formula). */
        val dustSats: Long,
    ) {
        P2WPKH(68, 294),
        P2TR(58, 330),
        P2WSH(105, 330),
        P2PKH(148, 546),
        P2SH(91, 540),
        OTHER(148, 546),
    }

    private fun typeOf(scriptHex: String): ScriptType =
        when {
            scriptHex.length == 44 && scriptHex.startsWith("0014") -> ScriptType.P2WPKH
            scriptHex.length == 68 && scriptHex.startsWith("0020") -> ScriptType.P2WSH
            scriptHex.length == 68 && scriptHex.startsWith("5120") -> ScriptType.P2TR
            scriptHex.length == 50 && scriptHex.startsWith("76a914") && scriptHex.endsWith("88ac") -> ScriptType.P2PKH
            scriptHex.length == 46 && scriptHex.startsWith("a914") && scriptHex.endsWith("87") -> ScriptType.P2SH
            else -> ScriptType.OTHER
        }

    /** Version, locktime, input/output counts and the segwit marker, rounded up. */
    private const val OVERHEAD_VBYTES = 11

    /** Virtual size of an OP_RETURN output carrying [dataLen] bytes: amount, script length, OP_RETURN, push, data. */
    fun opReturnOutputVBytes(dataLen: Int): Int = 8 + 1 + 1 + (if (dataLen <= 75) 1 else 2) + dataLen

    private fun outputVBytes(scriptHex: String): Int {
        val scriptLen = scriptHex.length / 2
        return 8 + (if (scriptLen < 253) 1 else 3) + scriptLen
    }

    /** Fee for [vsize] at [rate] sat/vB, rounded up to whole satoshis. */
    fun feeFor(
        vsize: Int,
        rate: BigDecimal,
    ): Long = rate.multiply(BigDecimal(vsize)).setScale(0, RoundingMode.CEILING).longValueExact()

    /**
     * Picks inputs largest-first until the amount covers the fee, adding a change output (to the script
     * of the first selected input, i.e. the funding address) only when it is above the dust threshold;
     * otherwise the remainder goes to the fee. Returns null when [utxos] cannot cover the fee.
     */
    fun plan(
        utxos: List<Utxo>,
        payloadSize: Int,
        feeRateSatPerVb: BigDecimal,
    ): Plan? {
        val selected = mutableListOf<Utxo>()
        var total = 0L
        var inputVBytes = 0
        val opReturn = opReturnOutputVBytes(payloadSize)
        for (utxo in utxos.filter { it.amountSats > 0 }.sortedByDescending { it.amountSats }) {
            selected += utxo
            total += utxo.amountSats
            val type = typeOf(utxo.scriptPubKeyHex)
            inputVBytes += type.inputVBytes

            val changeScript = selected.first().scriptPubKeyHex
            val withChange = feeFor(OVERHEAD_VBYTES + inputVBytes + opReturn + outputVBytes(changeScript), feeRateSatPerVb)
            val change = total - withChange
            if (change >= typeOf(changeScript).dustSats) {
                return Plan(selected.toList(), change, withChange)
            }
            val withoutChange = feeFor(OVERHEAD_VBYTES + inputVBytes + opReturn, feeRateSatPerVb)
            if (total >= withoutChange) {
                return Plan(selected.toList(), 0L, total)
            }
        }
        return null
    }
}
