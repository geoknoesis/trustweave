package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * RFC 8785 JSON Canonicalization Scheme over kotlinx JsonElement, byte-compatible with the
 * AVP-Micro Python harness (avp_crypto.py::jcs): keys sorted by UTF-16 code unit, no
 * whitespace, minimal string escaping, U+2028/U+2029 escaped. Floats are not used by
 * AVP-Micro; numeric primitives are emitted verbatim.
 */
object Jcs {
    fun canonicalize(element: JsonElement): ByteArray =
        StringBuilder().also { write(it, element) }.toString().toByteArray(Charsets.UTF_8)

    private fun write(sb: StringBuilder, e: JsonElement) {
        when (e) {
            is JsonObject -> {
                sb.append('{')
                e.keys.sorted().forEachIndexed { i, k ->
                    if (i > 0) sb.append(',')
                    writeString(sb, k)
                    sb.append(':')
                    write(sb, e.getValue(k))
                }
                sb.append('}')
            }
            is JsonArray -> {
                sb.append('[')
                e.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    write(sb, v)
                }
                sb.append(']')
            }
            is JsonPrimitive -> if (e.isString) writeString(sb, e.content) else sb.append(e.content)
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                ' ' -> sb.append("\\u2028")
                ' ' -> sb.append("\\u2029")
                else -> if (ch < ' ')
                    sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else sb.append(ch)
            }
        }
        sb.append('"')
    }
}
