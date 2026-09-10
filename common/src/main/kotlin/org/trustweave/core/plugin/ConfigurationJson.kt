package org.trustweave.core.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Bounds allocation and rejects duplicate decoded member names before schema decoding. */
internal object ConfigurationJson {
    const val MAX_BYTES = 1_048_576

    fun parse(text: String): JsonObject {
        require(text.length <= MAX_BYTES)
        val encoded =
            Charsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text))
        require(encoded.remaining() <= MAX_BYTES)
        val stack = ArrayDeque<Container>()
        var offset = 0
        while (offset < text.length) {
            when (val char = text[offset]) {
                '{', '[' -> {
                    require(stack.size < 64)
                    stack.addLast(Container(char))
                }
                '}', ']' -> {
                    require(stack.isNotEmpty())
                    require(stack.removeLast().kind == if (char == '}') '{' else '[')
                }
                ',' -> stack.lastOrNull()?.let { if (it.kind == '{') it.expectsKey = true }
                '"' -> {
                    val start = offset++
                    while (offset < text.length && text[offset] != '"') {
                        if (text[offset] == '\\') offset++
                        offset++
                    }
                    require(offset < text.length)
                    stack.lastOrNull()?.let {
                        if (it.kind == '{' && it.expectsKey) {
                            val key = Json.decodeFromString<String>(text.substring(start, offset + 1))
                            require(it.keys.add(key))
                            it.expectsKey = false
                        }
                    }
                }
            }
            offset++
        }
        require(stack.isEmpty())
        return Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException()
    }

    private class Container(
        val kind: Char,
        var expectsKey: Boolean = true,
        val keys: MutableSet<String> = HashSet(),
    )
}
