package org.trustweave.anchor.evm

import java.math.BigInteger

/**
 * Gas math for EVM data-carrying value transfers used to anchor payloads.
 *
 * An anchor transaction is a plain value transfer (to an externally-owned account)
 * carrying the payload as calldata, so its gas cost is the intrinsic transaction
 * cost: 21,000 base gas plus per-byte calldata gas (EIP-2028: 16 gas per non-zero
 * byte, 4 gas per zero byte) - or, since EIP-7623 (Pectra), the calldata floor
 * `21000 + 10 * tokens` with `tokens = zeroBytes + 4 * nonZeroBytes` (40 gas per
 * non-zero byte, 10 per zero byte) when that is larger. A transaction whose gas
 * limit is below `max(intrinsic, floor)` is invalid, so [requiredGas] is the minimum
 * limit and what the limit helpers below are built on.
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

    /** EIP-7623 floor cost per calldata token. */
    const val FLOOR_TOKEN_GAS: Long = 10L

    /** Tokens per non-zero calldata byte (EIP-7623); a zero byte is one token. */
    const val TOKENS_PER_NONZERO_BYTE: Long = 4L

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

    /** EIP-7623 floor: `21000 + 10 * (zeroBytes + 4 * nonZeroBytes)`. */
    @JvmStatic
    fun calldataFloorGas(data: ByteArray): BigInteger {
        var tokens = 0L
        for (byte in data) {
            tokens += if (byte == 0.toByte()) 1L else TOKENS_PER_NONZERO_BYTE
        }
        return BigInteger.valueOf(TX_BASE_GAS + FLOOR_TOKEN_GAS * tokens)
    }

    /**
     * The minimum valid gas limit for a value transfer carrying [data]: the larger of
     * [intrinsicGas] and [calldataFloorGas].
     */
    @JvmStatic
    fun requiredGas(data: ByteArray): BigInteger = intrinsicGas(data).max(calldataFloorGas(data))

    /** [requiredGas] for [sizeBytes] of calldata, every byte assumed non-zero (the worst case). */
    @JvmStatic
    fun requiredGasForSize(sizeBytes: Long): BigInteger {
        require(sizeBytes >= 0) { "sizeBytes must be >= 0" }
        return intrinsicGasForSize(sizeBytes).max(
            BigInteger.valueOf(TX_BASE_GAS + FLOOR_TOKEN_GAS * TOKENS_PER_NONZERO_BYTE * sizeBytes),
        )
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
     * [requiredGas] (intrinsic or EIP-7623 floor, whichever is larger) plus a safety margin
     * (default +10%), suitable as the transaction gas limit for an anchor transaction.
     */
    @JvmStatic
    @JvmOverloads
    fun txGasLimit(
        data: ByteArray,
        marginPercent: Int = DEFAULT_MARGIN_PERCENT,
    ): BigInteger = withMargin(requiredGas(data), marginPercent)

    /**
     * Worst-case gas limit ([requiredGasForSize]) for [sizeBytes] of calldata plus a safety
     * margin (default +10%).
     */
    @JvmStatic
    @JvmOverloads
    fun txGasLimitForSize(
        sizeBytes: Long,
        marginPercent: Int = DEFAULT_MARGIN_PERCENT,
    ): BigInteger = withMargin(requiredGasForSize(sizeBytes), marginPercent)

    /**
     * Applies a percentage safety margin to [gas], rounding up.
     */
    @JvmStatic
    @JvmOverloads
    fun withMargin(
        gas: BigInteger,
        marginPercent: Int = DEFAULT_MARGIN_PERCENT,
    ): BigInteger {
        require(marginPercent >= 0) { "marginPercent must be >= 0" }
        return gas
            .multiply(BigInteger.valueOf(100L + marginPercent))
            .add(BigInteger.valueOf(99))
            .divide(BigInteger.valueOf(100))
    }
}
