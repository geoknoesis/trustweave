package org.trustweave.anchor.cardano

import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardanoCip20MetadataTest {

    private val mapper = ObjectMapper()

    @Test
    fun `chunkUtf8 splits payload into 64-byte UTF-8 chunks`() {
        val s = "a".repeat(140)
        val chunks = CardanoBlockchainAnchorClient.chunkUtf8(s, 64)
        assertEquals(3, chunks.size)
        assertEquals(64, chunks[0].toByteArray(StandardCharsets.UTF_8).size)
        assertEquals(64, chunks[1].toByteArray(StandardCharsets.UTF_8).size)
        assertEquals(12, chunks[2].toByteArray(StandardCharsets.UTF_8).size)
        assertEquals(s, chunks.joinToString(""))
    }

    @Test
    fun `chunkUtf8 never splits a multibyte code point`() {
        // "é" is 2 bytes in UTF-8. With a 5-byte budget, 2 "é" then split before the 3rd.
        val s = "éééé"
        val chunks = CardanoBlockchainAnchorClient.chunkUtf8(s, 5)
        for (c in chunks) {
            assertTrue(c.toByteArray(StandardCharsets.UTF_8).size <= 5, "chunk over budget: $c")
        }
        assertEquals(s, chunks.joinToString(""))
    }

    @Test
    fun `chunkUtf8 handles empty input`() {
        assertEquals(listOf(""), CardanoBlockchainAnchorClient.chunkUtf8("", 64))
    }

    @Test
    fun `buildCip20Metadata serialises to CBOR under requested label`() {
        val payload = """{"hello":"cardano"}"""
        val md = CardanoBlockchainAnchorClient.buildCip20Metadata(
            payload.toByteArray(StandardCharsets.UTF_8),
            label = 674L,
        )
        val cbor = md.serialize()
        assertTrue(cbor.isNotEmpty(), "CBOR must be non-empty")
        // The first byte should encode a map (major type 5 = 0xA0..0xBB range).
        val major = (cbor[0].toInt() and 0xFF) ushr 5
        assertEquals(5, major, "top-level CBOR item must be a map, got major=$major (byte=${cbor[0]})")

        // Round-trip through CBORMetadata.deserialize to confirm structure.
        val round = com.bloxbean.cardano.client.metadata.cbor.CBORMetadata.deserialize(cbor)
        val labelValue = round.get(java.math.BigInteger.valueOf(674L))
        assertTrue(labelValue is com.bloxbean.cardano.client.metadata.MetadataMap)
    }

    @Test
    fun `buildCip20Metadata under custom label`() {
        val md = CardanoBlockchainAnchorClient.buildCip20Metadata(
            "abc".toByteArray(),
            label = 1337L,
        )
        val round = com.bloxbean.cardano.client.metadata.cbor.CBORMetadata.deserialize(md.serialize())
        assertNull(round.get(java.math.BigInteger.valueOf(674L)))
        assertTrue(round.get(java.math.BigInteger.valueOf(1337L)) is com.bloxbean.cardano.client.metadata.MetadataMap)
    }

    @Test
    fun `decodeCip20Payload reassembles chunks from Blockfrost JSON response`() {
        val payloadJson = """{"vc":"vc-12345","digest":"abcDEF"}"""
        val chunks = CardanoBlockchainAnchorClient.chunkUtf8(payloadJson, 64)
        val node = mapper.createObjectNode().apply {
            set<com.fasterxml.jackson.databind.JsonNode>(
                "msg",
                mapper.createArrayNode().apply { chunks.forEach { add(it) } },
            )
        }
        val entry = MetadataJSONContent("tx123", "674", node)
        val decoded = CardanoBlockchainAnchorClient.decodeCip20Payload(listOf(entry), 674L)
            ?: error("decode returned null")
        val obj: JsonObject = (decoded as JsonObject)
        assertEquals("vc-12345", obj["vc"]!!.jsonPrimitive.content)
        assertEquals("abcDEF", obj["digest"]!!.jsonPrimitive.content)
    }

    @Test
    fun `decodeCip20Payload returns null for missing label`() {
        val node = mapper.createObjectNode().apply { put("msg", "hi") }
        val entry = MetadataJSONContent("tx123", "674", node)
        assertNull(CardanoBlockchainAnchorClient.decodeCip20Payload(listOf(entry), 9999L))
    }

    @Test
    fun `decodeCip20Payload falls back to JsonPrimitive when payload is not JSON`() {
        val node = mapper.createObjectNode().apply {
            set<com.fasterxml.jackson.databind.JsonNode>(
                "msg",
                mapper.createArrayNode().apply { add("plain text message") },
            )
        }
        val entry = MetadataJSONContent("tx123", "674", node)
        val decoded = CardanoBlockchainAnchorClient.decodeCip20Payload(listOf(entry), 674L)
        assertEquals("plain text message", decoded?.jsonPrimitive?.content)
    }

    @Test
    fun `round-trip from build to decode via simulated Blockfrost response`() {
        val payloadString = """{"type":"VC","id":"urn:uuid:abc"}"""

        // 1. Build metadata as the client would.
        val md = CardanoBlockchainAnchorClient.buildCip20Metadata(
            payloadString.toByteArray(StandardCharsets.UTF_8),
            label = 674L,
        )

        // 2. Convert the bloxbean CBOR back to JSON exactly how Blockfrost surfaces it.
        val asJson = com.bloxbean.cardano.client.metadata.helper.MetadataToJsonNoSchemaConverter
            .cborBytesToJson(md.serialize())
        val tree = mapper.readTree(asJson)
        val labelNode = tree.get("674") ?: error("expected label 674 in JSON: $asJson")
        val entry = MetadataJSONContent("tx-mock", "674", labelNode)

        // 3. Decode
        val decoded = CardanoBlockchainAnchorClient.decodeCip20Payload(listOf(entry), 674L)
            ?: error("decode returned null")
        val obj = decoded as JsonObject
        assertEquals("VC", obj["type"]!!.jsonPrimitive.content)
        assertEquals("urn:uuid:abc", obj["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `hexToBytes decodes correctly`() {
        val bytes = CardanoBlockchainAnchorClient.hexToBytes("0Aff")
        assertEquals(2, bytes.size)
        assertEquals(0x0A.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[1])
    }

    @Test
    fun `hexToBytes strips 0x prefix`() {
        val bytes = CardanoBlockchainAnchorClient.hexToBytes("0xabcd")
        assertEquals(2, bytes.size)
    }
}
