package org.trustweave.core.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.BigInteger

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
 *   The literal must match the JSON number grammar (RFC 8259 §6): forms Kotlin/Java would
 *   accept but JSON does not (`1d`, `1f`, hex floats, `NaN`, `Infinity`, `+1`, leading zeros)
 *   are rejected, as are non-finite values. Integer literals (no fraction or exponent) whose
 *   magnitude exceeds 2^53 are REJECTED too: the double conversion RFC 8785 prescribes would
 *   silently map distinct integers to the same canonical bytes (and so the same digest), so such
 *   values must be carried as JSON strings,
 * - strings must be well-formed UTF-16: a lone surrogate cannot be encoded as UTF-8 and is
 *   rejected instead of being replaced by `?`.
 *
 * All rejections throw [IllegalArgumentException].
 *
 * Unlike [DigestUtils.canonicalizeJson] (key sorting only, numbers emitted verbatim), this output
 * is interoperable with any RFC 8785 implementation.
 */
object JsonCanonicalization {
    private val JSON_NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    private val JSON_INTEGER = Regex("-?(0|[1-9][0-9]*)")
    private val MAX_EXACT_INTEGER = BigInteger.valueOf(1L shl 53)

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
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (Character.isHighSurrogate(ch)) {
                require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                    "RFC 8785 requires well-formed Unicode: lone high surrogate at index $index"
                }
                sb.append(ch).append(value[index + 1])
                index += 2
                continue
            }
            require(!Character.isLowSurrogate(ch)) {
                "RFC 8785 requires well-formed Unicode: lone low surrogate at index $index"
            }
            index++
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
        require(JSON_NUMBER.matches(literal)) { "Not a JSON number: '$literal'" }
        if (JSON_INTEGER.matches(literal)) {
            require(BigInteger(literal).abs() <= MAX_EXACT_INTEGER) {
                "Integer '$literal' exceeds 2^53; RFC 8785 would lose precision. Carry it as a JSON string instead"
            }
        }
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
