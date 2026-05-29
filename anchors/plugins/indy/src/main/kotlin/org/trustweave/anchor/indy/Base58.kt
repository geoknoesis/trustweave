package org.trustweave.anchor.indy

import java.math.BigInteger

/**
 * Bitcoin-style Base58 encoder/decoder.
 *
 * Indy DIDs, verkeys, signatures and request IDs are exchanged as Base58 strings on
 * the wire. We ship a small dependency-free implementation rather than pulling in
 * bitcoinj or commons-codec for a single function.
 *
 * The alphabet is identical to the one used by Bitcoin and indy-node:
 * `123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz` (no `0`, `O`, `I`, `l`).
 */
internal object Base58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128).also { arr ->
        arr.fill(-1)
        ALPHABET.forEachIndexed { i, c -> arr[c.code] = i }
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        var zeroCount = 0
        while (zeroCount < input.size && input[zeroCount].toInt() == 0) zeroCount++

        val temp = input.copyOf()
        val encoded = StringBuilder()
        var start = zeroCount
        while (start < temp.size) {
            val mod = divmod58(temp, start)
            if (temp[start].toInt() == 0) start++
            encoded.append(ALPHABET[mod.toInt()])
        }

        repeat(zeroCount) { encoded.append(ALPHABET[0]) }
        return encoded.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Illegal Base58 character at position $i: '$c'" }
            input58[i] = digit.toByte()
        }

        var zeroCount = 0
        while (zeroCount < input58.size && input58[zeroCount].toInt() == 0) zeroCount++

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeroCount
        while (inputStart < input58.size) {
            decoded[--outputStart] = divmod256(input58, inputStart)
            if (input58[inputStart].toInt() == 0) inputStart++
        }

        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) outputStart++
        outputStart -= zeroCount
        return decoded.copyOfRange(outputStart, decoded.size)
    }

    private fun divmod58(number: ByteArray, startAt: Int): Byte {
        var remainder = 0
        for (i in startAt until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * 256 + digit
            number[i] = (temp / 58).toByte()
            remainder = temp % 58
        }
        return remainder.toByte()
    }

    private fun divmod256(number58: ByteArray, startAt: Int): Byte {
        var remainder = 0
        for (i in startAt until number58.size) {
            val digit = number58[i].toInt() and 0xFF
            val temp = remainder * 58 + digit
            number58[i] = (temp / 256).toByte()
            remainder = temp % 256
        }
        return remainder.toByte()
    }

    /** Convenience for round-trip tests / fixtures. */
    fun encodeBigInteger(value: BigInteger): String = encode(value.toByteArray())
}
