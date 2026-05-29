package org.trustweave.referencewallet.lib

/**
 * Minimal Base58btc (Bitcoin alphabet) codec.
 *
 * Used by the did:key encoder. Standalone implementation avoids pulling in a wallet-grade
 * crypto library just for ~50 lines of base conversion. Mirrors the bs58 npm package the
 * web wallet uses so the two implementations stay aligned.
 */
internal object Base58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val ENCODED_ZERO = ALPHABET[0]
    private val INDEXES = IntArray(128) { -1 }.also {
        ALPHABET.forEachIndexed { i, c -> it[c.code] = i }
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        // Count leading zeros.
        var leadingZeros = 0
        while (leadingZeros < input.size && input[leadingZeros] == 0.toByte()) leadingZeros++

        val source = input.copyOf()
        val encoded = CharArray(input.size * 2)  // Upper bound for base58.
        var outputStart = encoded.size
        var startAt = leadingZeros
        while (startAt < source.size) {
            val mod = divmod(source, startAt, 256, 58)
            if (source[startAt] == 0.toByte()) startAt++
            encoded[--outputStart] = ALPHABET[mod.toInt()]
        }
        // Strip trailing zero-equivalent chars that may have been left by divmod.
        while (outputStart < encoded.size && encoded[outputStart] == ENCODED_ZERO) outputStart++
        // Prepend one '1' for each leading zero byte.
        repeat(leadingZeros) { encoded[--outputStart] = ENCODED_ZERO }
        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        // Convert input chars to base58 indices.
        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid base58 character '$c' at index $i" }
            input58[i] = digit.toByte()
        }
        // Count leading zeros (encoded as '1').
        var leadingZeros = 0
        while (leadingZeros < input58.size && input58[leadingZeros] == 0.toByte()) leadingZeros++

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var startAt = leadingZeros
        while (startAt < input58.size) {
            val mod = divmod(input58, startAt, 58, 256)
            if (input58[startAt] == 0.toByte()) startAt++
            decoded[--outputStart] = mod
        }
        // Skip leading-zero bytes left by divmod, then add back the leading-zero bytes.
        while (outputStart < decoded.size && decoded[outputStart] == 0.toByte()) outputStart++
        outputStart -= leadingZeros
        if (outputStart < 0) outputStart = 0
        // Initialise the prepended zero bytes (they're already 0 because ByteArray init).
        return decoded.copyOfRange(outputStart, decoded.size)
    }

    /**
     * Divide `number[firstDigit..)` (treated as a positional big-endian number in `base`)
     * by `divisor` in place, returning the remainder.
     */
    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Byte {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xff
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder.toByte()
    }
}
