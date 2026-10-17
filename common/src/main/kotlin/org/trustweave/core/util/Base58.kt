package org.trustweave.core.util

import java.math.BigInteger

/**
 * Base58btc (Bitcoin alphabet) encoding/decoding utilities.
 *
 * Shared implementation used across TrustWeave for multibase (`z`-prefixed) encodings,
 * did:key / did:peer identifiers, and digest representations.
 */
private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
private val BASE58_RADIX = BigInteger.valueOf(58)
private val BASE58_DIGITS =
    IntArray(128) { -1 }.also { digits ->
        BASE58_ALPHABET.forEachIndexed { index, character -> digits[character.code] = index }
    }

/**
 * Base58btc-encodes this byte array.
 *
 * Base58 encoding algorithm:
 * 1. Convert the byte array to a BigInteger (treating it as an unsigned big-endian integer).
 * 2. Repeatedly divide by 58, using the remainder as an index into the alphabet.
 * 3. Encode each leading zero byte as a '1' character (the first alphabet character).
 * 4. Reverse the result, since it is built from least to most significant digit.
 */
fun ByteArray.encodeBase58(): String {
    // BigInteger(1, bytes) treats the bytes as an unsigned big-endian integer.
    var num = BigInteger(1, this)
    val sb = StringBuilder()

    while (num > BigInteger.ZERO) {
        val (quotient, remainder) = num.divideAndRemainder(BASE58_RADIX)
        sb.append(BASE58_ALPHABET[remainder.toInt()])
        num = quotient
    }

    // Leading zero bytes are encoded as '1' characters and represent the most
    // significant digits, so append them after the division loop.
    for (byte in this) {
        if (byte.toInt() == 0) sb.append('1') else break
    }

    // Built from least significant to most significant digit, so reverse.
    return sb.reverse().toString()
}

/**
 * Decodes this base58btc-encoded string to bytes.
 *
 * @throws IllegalArgumentException if the string contains a character outside the base58 alphabet.
 */
fun String.decodeBase58(): ByteArray {
    var leadingZeros = 0
    while (leadingZeros < length && this[leadingZeros] == '1') leadingZeros++
    var num = BigInteger.ZERO
    for (index in leadingZeros until length) {
        val character = this[index]
        val digit = if (character.code < BASE58_DIGITS.size) BASE58_DIGITS[character.code] else -1
        require(digit >= 0) { "Invalid base58 character: $character" }
        num = num.multiply(BASE58_RADIX).add(BigInteger.valueOf(digit.toLong()))
    }
    val bytes = num.toByteArray()
    // BigInteger emits a sign byte for zero and for positive values with the high bit set.
    val signBytes = if (bytes[0] == 0.toByte()) 1 else 0
    return ByteArray(leadingZeros + bytes.size - signBytes).also { decoded ->
        bytes.copyInto(decoded, destinationOffset = leadingZeros, startIndex = signBytes)
    }
}
