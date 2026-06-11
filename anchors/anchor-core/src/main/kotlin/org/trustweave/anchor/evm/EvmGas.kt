package org.trustweave.anchor.evm

import java.math.BigInteger

/**
 * Gas math for EVM data-carrying value transfers used to anchor payloads.
 *
 * An anchor transaction is a plain value transfer (to an externally-owned account)
 * carrying the payload as calldata, so its gas cost is exactly the intrinsic
 * transaction cost: 21,000 base gas plus per-byte calldata gas (EIP-2028:
 * 16 gas per non-zero byte, 4 gas per zero byte).
 *
 * EVM anchor plugins use this for both fee estimation and the actual transaction
 * gas limit instead of a blanket multi-million default, which would grossly
 * over-reserve gas and over-report estimated fees.
 */
object EvmGas {

    /** Base cost of any EVM transaction. */
    const val TX_BASE_GAS: Long = 21_000L

    /** Calldata cost per non-zero byte (EIP-2028). */
    const val NONZERO_BYTE_GAS: Long = 16L

    /** Calldata cost per zero byte. */
    const val ZERO_BYTE_GAS: Long = 4L

    /** Default safety margin applied on top of the intrinsic gas, in percent. */
    const val DEFAULT_MARGIN_PERCENT: Int = 10

    /**
     * Exact intrinsic gas for a value transfer carrying [data] as calldata:
     * `21000 + 16 * nonZeroBytes + 4 * zeroBytes`.
     */
    @JvmStatic
    fun intrinsicGas(data: ByteArray): BigInteger {
        var gas = TX_BASE_GAS
        for (byte in data) {
            gas += if (byte == 0.toByte()) ZERO_BYTE_GAS else NONZERO_BYTE_GAS
        }
        return BigInteger.valueOf(gas)
    }

    /**
     * Worst-case intrinsic gas for [sizeBytes] of calldata
     * (every byte assumed non-zero).
     */
    @JvmStatic
    fun intrinsicGasForSize(sizeBytes: Long): BigInteger {
        require(sizeBytes >= 0) { "sizeBytes must be >= 0" }
        return BigInteger.valueOf(TX_BASE_GAS + sizeBytes * NONZERO_BYTE_GAS)
    }

    /**
     * [intrinsicGas] plus a safety margin (default +10%), suitable as the
     * transaction gas limit for an anchor transaction.
     */
    @JvmStatic
    @JvmOverloads
    fun txGasLimit(data: ByteArray, marginPercent: Int = DEFAULT_MARGIN_PERCENT): BigInteger =
        withMargin(intrinsicGas(data), marginPercent)

    /**
     * Worst-case gas limit for [sizeBytes] of calldata plus a safety margin
     * (default +10%).
     */
    @JvmStatic
    @JvmOverloads
    fun txGasLimitForSize(sizeBytes: Long, marginPercent: Int = DEFAULT_MARGIN_PERCENT): BigInteger =
        withMargin(intrinsicGasForSize(sizeBytes), marginPercent)

    /**
     * Applies a percentage safety margin to [gas], rounding up.
     */
    @JvmStatic
    @JvmOverloads
    fun withMargin(gas: BigInteger, marginPercent: Int = DEFAULT_MARGIN_PERCENT): BigInteger {
        require(marginPercent >= 0) { "marginPercent must be >= 0" }
        return gas.multiply(BigInteger.valueOf(100L + marginPercent))
            .add(BigInteger.valueOf(99))
            .divide(BigInteger.valueOf(100))
    }
}
