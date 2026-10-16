package org.trustweave.credential.spi.proof

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProofEngineCapabilitiesTest {
    @Test
    fun `defaults describe a plain engine with revocation and presentation only`() {
        val defaults = ProofEngineCapabilities()
        assertFalse(defaults.selectiveDisclosure)
        assertFalse(defaults.zeroKnowledge)
        assertTrue(defaults.revocation)
        assertTrue(defaults.presentation)
        assertFalse(defaults.predicates)
    }

    @Test
    fun `capabilities are compared and copied by value`() {
        val zk = ProofEngineCapabilities(selectiveDisclosure = true, zeroKnowledge = true, predicates = true)
        assertEquals(zk, ProofEngineCapabilities(true, true, true, true, true))
        assertNotEquals(zk, ProofEngineCapabilities())
        val reduced = zk.copy(zeroKnowledge = false, revocation = false)
        assertTrue(reduced.selectiveDisclosure)
        assertFalse(reduced.zeroKnowledge)
        assertFalse(reduced.revocation)
        assertEquals(zk.hashCode(), zk.copy().hashCode())
    }

    /** A canonicalizer that sorts keys, enough to exercise the port contract without a JSON-LD library. */
    private class SortedKeyCanonicalizer(
        private val maxLength: Int,
    ) : JsonLdCanonicalizationPort {
        override fun canonicalize(document: JsonObject): String {
            val text =
                document.entries.sortedBy { it.key }.joinToString(
                    "\n",
                ) { (key, value) -> "$key ${(value as JsonPrimitive).content}" }
            require(text.length <= maxLength) { "canonical form exceeds $maxLength characters" }
            return text
        }
    }

    @Test
    fun `a canonicalization port produces the same text regardless of key order`() {
        val port: JsonLdCanonicalizationPort = SortedKeyCanonicalizer(maxLength = 100)
        val one =
            buildJsonObject {
                put("b", "2")
                put("a", "1")
            }
        val two =
            buildJsonObject {
                put("a", "1")
                put("b", "2")
            }
        assertEquals(port.canonicalize(one), port.canonicalize(two))
        assertEquals("a 1\nb 2", port.canonicalize(one))
    }

    @Test
    fun `a canonicalization port may refuse an oversized document`() {
        val port: JsonLdCanonicalizationPort = SortedKeyCanonicalizer(maxLength = 5)
        val big = buildJsonObject { put("key", "a long value") }
        val error = kotlin.runCatching { port.canonicalize(big) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
