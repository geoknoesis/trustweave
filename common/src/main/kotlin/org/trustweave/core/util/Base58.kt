package org.trustweave.core.util

import java.math.BigInteger

/**
 * Base58btc (Bitcoin alphabet) encoding/decoding utilities.
 *
 * Shared implementation used across TrustWeave for multibase (`z`-prefixed) encodings,
 * did:key / did:peer identifiers, and digest representations.
 */
private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

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
        val remainder = num.mod(BigInteger.valueOf(58))
        sb.append(BASE58_ALPHABET[remainder.toInt()])
        num = num.divide(BigInteger.valueOf(58))
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
    var num = BigInteger.ZERO
    var leadingZeros = 0
    for (ch in this) { if (ch == '1') leadingZeros++ else break }
    for (ch in this) {
        val digit = BASE58_ALPHABET.indexOf(ch)
        require(digit >= 0) { "Invalid base58 character: $ch" }
        num = num.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
    }
    var bytes = num.toByteArray()
    if (bytes.isNotEmpty() && bytes[0].toInt() == 0) bytes = bytes.sliceArray(1 until bytes.size)
    return ByteArray(leadingZeros) { 0 } + bytes
}
