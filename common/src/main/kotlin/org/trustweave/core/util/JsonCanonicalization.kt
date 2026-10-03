package org.trustweave.core.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * JSON Canonicalization Scheme (JCS), RFC 8785.
 *
 * Produces the canonical UTF-8 serialization of a [JsonElement] so that two structurally equal
 * JSON values (same members, any key order or whitespace) always hash to the same bytes:
 *
 * - object members are sorted by the UTF-16 code units of their names (RFC 8785 §3.2.3),
 * - no insignificant whitespace,
 * - strings use the minimal escaping of ECMAScript `JSON.stringify` (RFC 8785 §3.2.2.2):
 *   only `"`, `\` and U+0000..U+001F are escaped, using the short forms `\b \t \n \f \r`
 *   and lowercase `\u00xx` otherwise; everything else (including non-ASCII) is emitted as is,
 * - numbers are parsed as IEEE-754 doubles and serialized like ECMAScript
 *   `Number.prototype.toString` (RFC 8785 §3.2.2.3), so `1.0`, `1`, and `1e0` are all `1`.
 *   Consequently integers beyond 2^53 lose precision exactly as they do in RFC 8785;
 *   non-finite numbers are rejected.
 *
 * Unlike [DigestUtils.canonicalizeJson] (key sorting only, numbers emitted verbatim), this output
 * is interoperable with any RFC 8785 implementation.
 */
object JsonCanonicalization {
    /** Canonical JSON text of [element] per RFC 8785. */
    @JvmStatic
    fun canonicalize(element: JsonElement): String = StringBuilder().also { write(it, element) }.toString()

    /** UTF-8 bytes of [canonicalize]; this is what RFC 8785 hashes and signs. */
    @JvmStatic
    fun canonicalizeToBytes(element: JsonElement): ByteArray = canonicalize(element).toByteArray(Charsets.UTF_8)

    private fun write(
        sb: StringBuilder,
        element: JsonElement,
    ) {
        when (element) {
            is JsonObject -> {
                sb.append('{')
                // String.compareTo compares UTF-16 code units, which is the order RFC 8785 requires.
                element.keys.sorted().forEachIndexed { i, key ->
                    if (i > 0) sb.append(',')
                    writeString(sb, key)
                    sb.append(':')
                    write(sb, element.getValue(key))
                }
                sb.append('}')
            }
            is JsonArray -> {
                sb.append('[')
                element.forEachIndexed { i, value ->
                    if (i > 0) sb.append(',')
                    write(sb, value)
                }
                sb.append(']')
            }
            JsonNull -> sb.append("null")
            is JsonPrimitive ->
                when {
                    element.isString -> writeString(sb, element.content)
                    element.content == "true" || element.content == "false" -> sb.append(element.content)
                    else -> sb.append(serializeNumber(element.content))
                }
        }
    }

    private fun writeString(
        sb: StringBuilder,
        value: String,
    ) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\t' -> sb.append("\\t")
                '\n' -> sb.append("\\n")
                '\u000C' -> sb.append("\\f")
                '\r' -> sb.append("\\r")
                else ->
                    if (ch < ' ') {
                        sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
            }
        }
        sb.append('"')
    }

    /**
     * ECMAScript Number::toString for the double nearest to [literal].
     *
     * Uses the shortest round-tripping digits from [java.lang.Double.toString] (shortest since
     * JDK 19, which this project's JVM 21 toolchain guarantees) and lays them out with the
     * ECMAScript rules (ECMA-262 §6.1.6.1.20).
     */
    internal fun serializeNumber(literal: String): String {
        val value =
            literal.toDoubleOrNull()
                ?: throw IllegalArgumentException("Not a JSON number: '$literal'")
        require(value.isFinite()) { "RFC 8785 cannot represent non-finite number '$literal'" }
        if (value == 0.0) return "0" // also covers -0

        val negative = value < 0
        val shortest = shortestDigits(Math.abs(value))
        val digits = shortest.unscaledValue().toString() // s, k = digits.length
        val k = digits.length
        val n = k - shortest.scale() // value = s × 10^(n−k)

        val body =
            when {
                n in k..21 -> digits + "0".repeat(n - k)
                n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
                n in -5..0 -> "0." + "0".repeat(-n) + digits
                else -> {
                    val exponent = n - 1
                    val sign = if (exponent >= 0) "+" else "-"
                    val mantissa = if (k == 1) digits else digits[0] + "." + digits.substring(1)
                    mantissa + "e" + sign + Math.abs(exponent)
                }
            }
        return if (negative) "-$body" else body
    }

    /**
     * Shortest decimal that round-trips to [positive] (ties: closest to the exact value).
     *
     * `Double.toString` is shortest except that it always prints at least two significant digits
     * (e.g. `4.9E-324` for `Double.MIN_VALUE`, whose shortest form is `5e-324`), so a one-digit
     * candidate is tried explicitly.
     */
    private fun shortestDigits(positive: Double): BigDecimal {
        val javaDigits = BigDecimal(java.lang.Double.toString(positive)).stripTrailingZeros()
        if (javaDigits.precision() != 2) return javaDigits
        val exact = BigDecimal(positive)
        return listOf(java.math.RoundingMode.DOWN, java.math.RoundingMode.UP)
            .map { javaDigits.round(java.math.MathContext(1, it)).stripTrailingZeros() }
            .filter { it.toDouble() == positive }
            .minByOrNull { (it - exact).abs() }
            ?: javaDigits
    }
}
