package org.trustweave.anchor.indy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wire-format tests for [IndyRequestCodec].
 *
 * These tests pin the exact JSON shape consumed by `indy-vdr-proxy` so that a future
 * refactor cannot silently drift away from the on-the-wire contract.
 */
class IndyRequestCodecTest {
    @Test
    fun `ATTRIB signing input matches native Indy VDR known answer`() {
        val request =
            IndyRequestCodec.buildAttribRequest(
                "V4SGRU86Z58d6TV7PBUe6f",
                "V4SGRU86Z58d6TV7PBUe6f",
                Json.parseToJsonElement("{\"trustweave\":{\"digest\":\"abc\"}}").jsonObject,
                reqId = 1,
            )
        // Captured from native indy-vdr 0.3.4 Request.signature_input, with reqId normalized to 1.
        assertEquals(
            "identifier:V4SGRU86Z58d6TV7PBUe6f|operation:dest:V4SGRU86Z58d6TV7PBUe6f|raw:1c33659376ce67d22ac6cd5e9930655f54392cfc365d46b0af746a79ee12e9de|type:100|protocolVersion:2|reqId:1",
            IndyRequestCodec.signingPayload(request).toString(Charsets.UTF_8),
        )
    }

    private val submitter = "V4SGRU86Z58d6TV7PBUe6f"
    private val target = "V4SGRU86Z58d6TV7PBUe6f"

    @Test
    fun `buildAttribRequest produces canonical ATTRIB envelope`() {
        val raw =
            buildJsonObject {
                put(IndyAttribFields.DIGEST, JsonPrimitive("abc123"))
                put(IndyAttribFields.MEDIA_TYPE, JsonPrimitive("application/json"))
            }

        val req =
            IndyRequestCodec.buildAttribRequest(
                submitterDid = submitter,
                targetDid = target,
                rawPayload = raw,
                reqId = 1700000000000_001L,
            )

        assertEquals(submitter, req["identifier"]!!.jsonPrimitive.content)
        assertEquals("1700000000000001", req["reqId"]!!.jsonPrimitive.content)
        assertEquals("2", req["protocolVersion"]!!.jsonPrimitive.content)

        val op = req["operation"]!!.jsonObject
        assertEquals(IndyTxnTypes.ATTRIB, op["type"]!!.jsonPrimitive.content)
        assertEquals(target, op["dest"]!!.jsonPrimitive.content)

        val rawString = op["raw"]!!.jsonPrimitive.content
        val rawParsed = IndyRequestCodec.json.parseToJsonElement(rawString).jsonObject
        assertEquals("abc123", rawParsed[IndyAttribFields.DIGEST]!!.jsonPrimitive.content)
        assertEquals("application/json", rawParsed[IndyAttribFields.MEDIA_TYPE]!!.jsonPrimitive.content)
    }

    @Test
    fun `buildGetAttribRequest produces canonical GET_ATTRIB envelope`() {
        val req =
            IndyRequestCodec.buildGetAttribRequest(
                submitterDid = submitter,
                targetDid = target,
                attributeName = IndyAttribFields.ATTRIB_NAME,
                reqId = 1700000000000_002L,
            )

        assertEquals(submitter, req["identifier"]!!.jsonPrimitive.content)
        val op = req["operation"]!!.jsonObject
        assertEquals(IndyTxnTypes.GET_ATTRIB, op["type"]!!.jsonPrimitive.content)
        assertEquals(target, op["dest"]!!.jsonPrimitive.content)
        assertEquals(IndyAttribFields.ATTRIB_NAME, op["raw"]!!.jsonPrimitive.content)
        assertNull(req["signature"])
    }

    @Test
    fun `signingPayload is deterministic and sorts keys lexicographically`() {
        val req1 =
            buildJsonObject {
                put("identifier", JsonPrimitive("ABC"))
                put(
                    "operation",
                    buildJsonObject {
                        put("type", JsonPrimitive("100"))
                        put("dest", JsonPrimitive("ABC"))
                    },
                )
                put("reqId", JsonPrimitive(42L))
            }
        val req2 =
            buildJsonObject {
                put("reqId", JsonPrimitive(42L))
                put(
                    "operation",
                    buildJsonObject {
                        put("dest", JsonPrimitive("ABC"))
                        put("type", JsonPrimitive("100"))
                    },
                )
                put("identifier", JsonPrimitive("ABC"))
            }

        val s1 = IndyRequestCodec.signingPayload(req1).toString(Charsets.UTF_8)
        val s2 = IndyRequestCodec.signingPayload(req2).toString(Charsets.UTF_8)

        assertEquals(s1, s2, "key order at construction time must not change the signing payload")
        assertTrue(s1.startsWith("identifier:ABC|operation:dest:ABC|type:100|reqId:42"))
    }

    @Test
    fun `attachSignature adds signature field without mutating the original`() {
        val unsigned =
            IndyRequestCodec.buildAttribRequest(
                submitterDid = submitter,
                targetDid = target,
                rawPayload = buildJsonObject { put("k", JsonPrimitive("v")) },
                reqId = 7,
            )
        val signed = IndyRequestCodec.attachSignature(unsigned, "deadbeef")

        assertEquals("deadbeef", signed["signature"]!!.jsonPrimitive.content)
        assertNull(unsigned["signature"], "original must remain unsigned")
    }

    @Test
    fun `parseGetAttribResponse extracts raw object`() {
        val reply =
            IndyRequestCodec.json
                .parseToJsonElement(
                    """
                    {
                      "op": "REPLY",
                      "result": {
                        "type": "104",
                        "dest": "$target",
                        "data": "{\"digest\":\"abc\",\"mediaType\":\"application/json\"}",
                        "seqNo": 17,
                        "txnTime": 1700000000
                      }
                    }
                    """.trimIndent(),
                ).jsonObject

        val parsed = IndyRequestCodec.parseGetAttribResponse(reply)
        assertEquals("abc", parsed.raw!![IndyAttribFields.DIGEST]!!.jsonPrimitive.content)
        assertEquals("application/json", parsed.raw[IndyAttribFields.MEDIA_TYPE]!!.jsonPrimitive.contentOrNull)
        assertEquals(17L, parsed.seqNo)
        assertEquals(1_700_000_000L, parsed.txnTime)
    }

    @Test
    fun `parseGetAttribResponse handles missing attribute`() {
        val reply =
            IndyRequestCodec.json
                .parseToJsonElement(
                    """{ "op": "REPLY", "result": { "data": null, "seqNo": 0 } }""",
                ).jsonObject
        val parsed = IndyRequestCodec.parseGetAttribResponse(reply)
        assertNull(parsed.raw)
    }

    @Test
    fun `parseWriteReply prefers seqNo over txnId`() {
        val reply =
            IndyRequestCodec.json
                .parseToJsonElement(
                    """{ "op": "REPLY", "result": { "seqNo": 99, "txnId": "abcd" } }""",
                ).jsonObject
        assertEquals("99", IndyRequestCodec.parseWriteReply(reply))
    }

    @Test
    fun `parseWriteReply falls back to txnMetadata seqNo`() {
        val reply =
            IndyRequestCodec.json
                .parseToJsonElement(
                    """{ "op": "REPLY", "result": { "txnMetadata": { "seqNo": 123 } } }""",
                ).jsonObject
        assertEquals("123", IndyRequestCodec.parseWriteReply(reply))
    }

    @Test
    fun `parseWriteReply throws when no identifier present`() {
        val reply =
            IndyRequestCodec.json
                .parseToJsonElement(
                    """{ "op": "REPLY", "result": { "unknown": true } }""",
                ).jsonObject
        assertFailsWith<IllegalArgumentException> { IndyRequestCodec.parseWriteReply(reply) }
    }

    @Test
    fun `sha256Hex produces lowercase hex of expected length`() {
        val digest = IndyRequestCodec.sha256Hex("hello".toByteArray())
        assertEquals(64, digest.length)
        assertEquals(digest.lowercase(), digest)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", digest)
    }

    @Test
    fun `nextReqId is monotonic under tight loops`() {
        val ids = (0 until 70_000).map { IndyRequestCodec.nextReqId() }
        val distinct = ids.toSet()
        assertEquals(ids.size, distinct.size, "request ids must be unique")
        assertTrue(ids.zipWithNext().all { (previous, next) -> next > previous })
    }

    @Test
    fun `buildAttribRequest rejects blank DIDs`() {
        assertFailsWith<IllegalArgumentException> {
            IndyRequestCodec.buildAttribRequest("", target, buildJsonObject { })
        }
        assertFailsWith<IllegalArgumentException> {
            IndyRequestCodec.buildAttribRequest(submitter, "", buildJsonObject { })
        }
    }
}
