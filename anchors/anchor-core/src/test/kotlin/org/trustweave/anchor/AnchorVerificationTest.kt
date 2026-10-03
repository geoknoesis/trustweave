package org.trustweave.anchor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for digest payload mode ([AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE])
 * and third-party anchor verification ([BlockchainAnchorClient.verifyAnchor]),
 * exercised through the opt-in in-memory test mode and a fake submitting chain.
 */
class AnchorVerificationTest {
    private val payload =
        buildJsonObject {
            put("id", "credential-1")
            put("subject", buildJsonObject { put("name", "Alice") })
        }

    private val tampered =
        buildJsonObject {
            put("id", "credential-1")
            put("subject", buildJsonObject { put("name", "Mallory") })
        }

    // ---------------------------------------------------------------------
    // Full mode (default) — backward compatible
    // ---------------------------------------------------------------------

    @Test
    fun `full mode roundtrip verifies true`() =
        runBlocking<Unit> {
            val client = TestAnchorClient()

            val result = client.writePayload(payload)

            assertTrue(client.verifyAnchor(payload, result.ref))
        }

    @Test
    fun `full mode tampered payload verifies false`() =
        runBlocking<Unit> {
            val client = TestAnchorClient()

            val result = client.writePayload(payload)

            assertFalse(client.verifyAnchor(tampered, result.ref))
        }

    @Test
    fun `full mode comparison is structural not string equality`() =
        runBlocking<Unit> {
            val client = TestAnchorClient()
            val result = client.writePayload(payload)

            // Same structure, different key order: still verifies in full mode.
            val reordered =
                buildJsonObject {
                    put("subject", buildJsonObject { put("name", "Alice") })
                    put("id", "credential-1")
                }

            assertTrue(client.verifyAnchor(reordered, result.ref))
        }

    @Test
    fun `full mode write does not mark payloadMode in extra`() =
        runBlocking<Unit> {
            val client = TestAnchorClient()

            val result = client.writePayload(payload)

            assertNull(result.ref.extra[AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE])
            assertEquals(payload, client.readPayload(result.ref).payload)
        }

    // ---------------------------------------------------------------------
    // Digest mode
    // ---------------------------------------------------------------------

    @Test
    fun `digest mode write returns original payload and marks the ref`() =
        runBlocking<Unit> {
            val client = TestAnchorClient(digestMode = true)

            val result = client.writePayload(payload)

            assertEquals(payload, result.payload, "write result echoes the caller payload")
            assertEquals(
                AbstractBlockchainAnchorClient.PAYLOAD_MODE_DIGEST,
                result.ref.extra[AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE],
            )
        }

    @Test
    fun `digest mode read returns the envelope with the expected shape`() =
        runBlocking<Unit> {
            val client = TestAnchorClient(digestMode = true)
            val result = client.writePayload(payload)

            val read = client.readPayload(result.ref)
            val envelope = read.payload

            assertTrue(AnchorDigest.isEnvelope(envelope), "on-chain data must be a digest envelope")
            envelope as JsonObject
            assertEquals("SHA-256", envelope[AnchorDigest.FIELD_ALG]?.jsonPrimitive?.content)
            assertEquals("application/json", envelope[AnchorDigest.FIELD_MEDIA_TYPE]?.jsonPrimitive?.content)

            assertEquals("JCS", envelope[AnchorDigest.FIELD_CANONICALIZATION]?.jsonPrimitive?.content)

            // The digest is base64url(sha256(JCS(payload))) (RFC 8785).
            val payloadBytes =
                """{"id":"credential-1","subject":{"name":"Alice"}}"""
                    .toByteArray(StandardCharsets.UTF_8)
            val expectedDigest =
                Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(payloadBytes))
            assertEquals(expectedDigest, envelope[AnchorDigest.FIELD_DIGEST]?.jsonPrimitive?.content)

            // The read marks the ref so callers can tell the envelope is not the payload.
            assertEquals(
                AbstractBlockchainAnchorClient.PAYLOAD_MODE_DIGEST,
                read.ref.extra[AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE],
            )
        }

    @Test
    fun `digest mode write then verify is true`() =
        runBlocking<Unit> {
            val client = TestAnchorClient(digestMode = true)

            val result = client.writePayload(payload)

            assertTrue(client.verifyAnchor(payload, result.ref))
        }

    @Test
    fun `digest mode verifies a structurally equal payload with different key order and number spelling`() =
        runBlocking<Unit> {
            val client = TestAnchorClient(digestMode = true)
            val original =
                buildJsonObject {
                    put("id", "credential-1")
                    put("amount", JsonUnquotedLiteral("1.50"))
                    put("subject", buildJsonObject { put("name", "Alice") })
                }
            val result = client.writePayload(original)

            val reordered =
                buildJsonObject {
                    put("subject", buildJsonObject { put("name", "Alice") })
                    put("amount", 1.5)
                    put("id", "credential-1")
                }

            assertTrue(client.verifyAnchor(reordered, result.ref))
        }

    @Test
    fun `legacy digest envelopes without canon still verify with their original bytes`() =
        runBlocking<Unit> {
            val client = TestAnchorClient(canSubmit = true)
            val legacyBytes = Json.encodeToString(JsonElement.serializer(), payload).toByteArray(StandardCharsets.UTF_8)
            val legacyEnvelope = AnchorDigest.envelope(legacyBytes, "application/json")
            assertFalse(legacyEnvelope.containsKey(AnchorDigest.FIELD_CANONICALIZATION))
            // A pre-JCS digest-mode anchor: the envelope itself is what is on-chain.
            val ref = client.writePayload(legacyEnvelope).ref

            assertTrue(client.verifyAnchor(payload, ref), "old anchors must keep verifying")
            assertFalse(client.verifyAnchor(tampered, ref))
            val reordered =
                buildJsonObject {
                    put("subject", buildJsonObject { put("name", "Alice") })
                    put("id", "credential-1")
                }
            assertFalse(client.verifyAnchor(reordered, ref), "legacy digests are key-order sensitive")
        }

    @Test
    fun `requireCanonicalEnvelope rejects legacy envelopes but accepts JCS ones`() =
        runBlocking<Unit> {
            val strict =
                TestAnchorClient(
                    canSubmit = true,
                    extraOptions = mapOf(AbstractBlockchainAnchorClient.OPTION_REQUIRE_CANONICAL_ENVELOPE to true),
                )
            val legacyBytes = Json.encodeToString(JsonElement.serializer(), payload).toByteArray(StandardCharsets.UTF_8)
            val legacyRef = strict.writePayload(AnchorDigest.envelope(legacyBytes, "application/json")).ref
            val jcsRef = strict.writePayload(AnchorDigest.envelope(payload, "application/json")).ref

            assertFalse(strict.verifyAnchor(payload, legacyRef), "legacy envelope must be rejected when strict")
            val detail = strict.verifyAnchorDetailed(payload, legacyRef, requireCanonicalEnvelope = true)
            assertFalse(detail.verified)
            assertTrue(detail.reason!!.contains("legacy"))
            assertTrue(strict.verifyAnchor(payload, jcsRef), "JCS envelopes still verify")

            // The default (non-strict) client keeps accepting the legacy envelope.
            val lenient = TestAnchorClient(canSubmit = true)
            val ref = lenient.writePayload(AnchorDigest.envelope(legacyBytes, "application/json")).ref
            assertTrue(lenient.verifyAnchor(payload, ref))
        }

    @Test
    fun `AnchorDigest matches with requireCanonicalEnvelope rejects legacy envelopes`() {
        val legacyBytes = Json.encodeToString(JsonElement.serializer(), payload).toByteArray(StandardCharsets.UTF_8)
        val legacy = AnchorDigest.envelope(legacyBytes, "application/json")
        assertTrue(AnchorDigest.matches(legacy, payload))
        assertTrue(AnchorDigest.matches(legacy, payload, requireCanonicalEnvelope = false))
        assertFalse(AnchorDigest.matches(legacy, payload, requireCanonicalEnvelope = true))
        assertTrue(AnchorDigest.matches(AnchorDigest.envelope(payload, "application/json"), payload, requireCanonicalEnvelope = true))
    }

    @Test
    fun `verifyAnchorDetailed flags anchors served from the in-memory test fallback`() =
        runBlocking<Unit> {
            val testClient = TestAnchorClient()
            val inMemory = testClient.writePayload(payload).ref
            val detail = testClient.verifyAnchorDetailed(payload, inMemory, requireCanonicalEnvelope = false)
            assertTrue(detail.verified)
            assertTrue(detail.testMode, "a test-mode memory anchor is not chain evidence and must say so")

            val chain = TestAnchorClient(canSubmit = true, testMode = false)
            val real = chain.writePayload(payload).ref
            val realDetail = chain.verifyAnchorDetailed(payload, real, requireCanonicalEnvelope = false)
            assertTrue(realDetail.verified)
            assertFalse(realDetail.testMode)
        }

    @Test
    fun `memory fallback is never served when test mode is off`() =
        runBlocking<Unit> {
            val prod = TestAnchorClient(canSubmit = false, testMode = false)
            assertFailsWith<BlockchainException.ConfigurationFailed> { prod.writePayload(payload) }
            val ref = AnchorRef("test:unit", "test_tx_unknown")
            assertFalse(prod.verifyAnchor(payload, ref))
        }

    @Test
    fun `isEnvelope rejects an unknown canonicalization`() {
        val jcs = AnchorDigest.envelope(payload, "application/json")
        assertTrue(AnchorDigest.isEnvelope(jcs))
        val unknown = JsonObject(jcs + (AnchorDigest.FIELD_CANONICALIZATION to JsonPrimitive("URDNA2015")))
        assertFalse(AnchorDigest.isEnvelope(unknown))
        assertFalse(AnchorDigest.matches(unknown, payload))
    }

    @Test
    fun `in-memory test mode reads are marked as not on-chain`() =
        runBlocking<Unit> {
            val client = TestAnchorClient()
            val result = client.writePayload(payload)

            val read = client.readPayload(result.ref)

            assertEquals("true", read.ref.extra[AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE])
        }

    @Test
    fun `without test mode a failed chain read is never served from memory`() =
        runBlocking<Unit> {
            val client = TestAnchorClient(canSubmit = true, testMode = false)
            assertFailsWith<TrustWeaveException.NotFound> {
                client.readPayload(AnchorRef(chainId = "test:unit", txHash = "never-anchored"))
            }
            assertFalse(client.verifyAnchor(payload, AnchorRef(chainId = "test:unit", txHash = "never-anchored")))
        }

    @Test
    fun `digest mode tampered payload verifies false`() =
        runBlocking<Unit> {
            val client = TestAnchorClient(digestMode = true)

            val result = client.writePayload(payload)

            assertFalse(client.verifyAnchor(tampered, result.ref))
        }

    @Test
    fun `digest mode submits the envelope bytes - never the payload - on chain`() =
        runBlocking<Unit> {
            val client = TestAnchorClient(digestMode = true, canSubmit = true)

            val result = client.writePayload(payload)

            val onChain = String(client.submittedBytes.getValue(result.ref.txHash), StandardCharsets.UTF_8)
            assertFalse(onChain.contains("Alice"), "payload content must never go on-chain in digest mode")
            assertTrue(AnchorDigest.isEnvelope(Json.parseToJsonElement(onChain)))

            // Round-trip through the fake chain still verifies.
            assertTrue(client.verifyAnchor(payload, result.ref))
            assertFalse(client.verifyAnchor(tampered, result.ref))
        }

    @Test
    fun `unknown payload mode fails closed at construction`() {
        val exception =
            assertFailsWith<BlockchainException.ConfigurationFailed> {
                TestAnchorClient(payloadMode = "hashed")
            }
        assertTrue(exception.message.contains("payload mode"))
    }

    // ---------------------------------------------------------------------
    // verifyAnchor error handling
    // ---------------------------------------------------------------------

    @Test
    fun `verifyAnchor returns false when the anchor does not exist`() =
        runBlocking<Unit> {
            val client = TestAnchorClient()

            val missing = AnchorRef(chainId = "test:unit", txHash = "no-such-tx")

            assertFalse(client.verifyAnchor(payload, missing))
        }

    // ---------------------------------------------------------------------
    // AnchorDigest unit behavior
    // ---------------------------------------------------------------------

    @Test
    fun `isEnvelope rejects payloads that merely contain a digest field`() {
        assertFalse(AnchorDigest.isEnvelope(buildJsonObject { put("digest", "uABC123...") }))
        assertFalse(
            AnchorDigest.isEnvelope(
                buildJsonObject {
                    put("alg", "SHA-256")
                    put("digest", "x")
                    put("mediaType", "application/json")
                    put("other", "field")
                },
            ),
        )
        assertFalse(AnchorDigest.isEnvelope(JsonPrimitive("SHA-256")))
        assertTrue(AnchorDigest.isEnvelope(AnchorDigest.envelope("x".toByteArray(), "application/json")))
    }

    @Test
    fun `isEnvelope requires the exact envelope key set`() {
        val validDigest = AnchorDigest.digestBase64Url("x".toByteArray())

        // Subsets of the envelope fields are not envelopes — the writer always
        // emits all three of alg, digest and mediaType.
        assertFalse(
            AnchorDigest.isEnvelope(
                buildJsonObject {
                    put("alg", "SHA-256")
                    put("digest", validDigest)
                },
            ),
            "missing mediaType must be rejected",
        )
        assertFalse(
            AnchorDigest.isEnvelope(
                buildJsonObject {
                    put("digest", validDigest)
                    put("mediaType", "application/json")
                },
            ),
            "missing alg must be rejected",
        )
        assertFalse(AnchorDigest.isEnvelope(buildJsonObject {}), "empty object must be rejected")
    }

    @Test
    fun `isEnvelope requires a digest of exactly 32 bytes`() {
        fun envelopeWithDigest(digest: String): JsonObject =
            buildJsonObject {
                put("alg", "SHA-256")
                put("digest", digest)
                put("mediaType", "application/json")
            }

        val b64 = Base64.getUrlEncoder().withoutPadding()
        assertFalse(AnchorDigest.isEnvelope(envelopeWithDigest("x")), "undecodable digest")
        assertFalse(AnchorDigest.isEnvelope(envelopeWithDigest("not base64url!!!")), "undecodable digest")
        assertFalse(
            AnchorDigest.isEnvelope(envelopeWithDigest(b64.encodeToString(ByteArray(31)))),
            "31-byte digest must be rejected",
        )
        assertFalse(
            AnchorDigest.isEnvelope(envelopeWithDigest(b64.encodeToString(ByteArray(33)))),
            "33-byte digest must be rejected",
        )
        assertTrue(
            AnchorDigest.isEnvelope(envelopeWithDigest(b64.encodeToString(ByteArray(32)))),
            "32-byte digest must be accepted",
        )
    }

    @Test
    fun `matches rejects undecodable digests`() {
        val broken =
            buildJsonObject {
                put("alg", "SHA-256")
                put("digest", "not base64url!!!")
                put("mediaType", "application/json")
            }
        assertFalse(AnchorDigest.matches(broken, "x".toByteArray()))
    }

    /**
     * Minimal concrete client. With [canSubmit] = false it uses the opt-in in-memory
     * test fallback; with [canSubmit] = true it records the exact submitted bytes,
     * mimicking a chain that stores calldata verbatim.
     */
    private class TestAnchorClient(
        digestMode: Boolean = false,
        payloadMode: String? = if (digestMode) AbstractBlockchainAnchorClient.PAYLOAD_MODE_DIGEST else null,
        private val canSubmit: Boolean = false,
        testMode: Boolean = true,
        extraOptions: Map<String, Any?> = emptyMap(),
    ) : AbstractBlockchainAnchorClient(
            chainId = "test:unit",
            options =
                buildMap {
                    put(AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE, testMode)
                    payloadMode?.let { put(AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE, it) }
                    putAll(extraOptions)
                },
        ) {
        /** Exact bytes "anchored on-chain" by tx hash, for the canSubmit path. */
        val submittedBytes = ConcurrentHashMap<String, ByteArray>()
        private var txCounter = 0

        override fun canSubmitTransaction(): Boolean = canSubmit

        override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String {
            val txHash = "fake_tx_${txCounter++}"
            submittedBytes[txHash] = payloadBytes
            return txHash
        }

        override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult {
            val bytes =
                submittedBytes[txHash]
                    ?: throw TrustWeaveException.NotFound(resource = "Transaction not found: $txHash")
            return AnchorResult(
                ref = buildAnchorRef(txHash),
                payload = Json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)),
                mediaType = "application/json",
            )
        }

        override fun generateTestTxHash(): String = "test_tx_${uniqueTestHashSuffix()}"

        override fun getBlockchainName(): String = "Test Chain"
    }
}
