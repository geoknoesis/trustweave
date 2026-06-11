package org.trustweave.anchor

import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

/**
 * Tests for digest payload mode ([AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE])
 * and third-party anchor verification ([BlockchainAnchorClient.verifyAnchor]),
 * exercised through the opt-in in-memory test mode and a fake submitting chain.
 */
class AnchorVerificationTest {

    private val payload = buildJsonObject {
        put("id", "credential-1")
        put("subject", buildJsonObject { put("name", "Alice") })
    }

    private val tampered = buildJsonObject {
        put("id", "credential-1")
        put("subject", buildJsonObject { put("name", "Mallory") })
    }

    // ---------------------------------------------------------------------
    // Full mode (default) — backward compatible
    // ---------------------------------------------------------------------

    @Test
    fun `full mode roundtrip verifies true`() = runBlocking {
        val client = TestAnchorClient()

        val result = client.writePayload(payload)

        assertTrue(client.verifyAnchor(payload, result.ref))
    }

    @Test
    fun `full mode tampered payload verifies false`() = runBlocking {
        val client = TestAnchorClient()

        val result = client.writePayload(payload)

        assertFalse(client.verifyAnchor(tampered, result.ref))
    }

    @Test
    fun `full mode comparison is structural not string equality`() = runBlocking {
        val client = TestAnchorClient()
        val result = client.writePayload(payload)

        // Same structure, different key order: still verifies in full mode.
        val reordered = buildJsonObject {
            put("subject", buildJsonObject { put("name", "Alice") })
            put("id", "credential-1")
        }

        assertTrue(client.verifyAnchor(reordered, result.ref))
    }

    @Test
    fun `full mode write does not mark payloadMode in extra`() = runBlocking {
        val client = TestAnchorClient()

        val result = client.writePayload(payload)

        assertNull(result.ref.extra[AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE])
        assertEquals(payload, client.readPayload(result.ref).payload)
    }

    // ---------------------------------------------------------------------
    // Digest mode
    // ---------------------------------------------------------------------

    @Test
    fun `digest mode write returns original payload and marks the ref`() = runBlocking {
        val client = TestAnchorClient(digestMode = true)

        val result = client.writePayload(payload)

        assertEquals(payload, result.payload, "write result echoes the caller payload")
        assertEquals(
            AbstractBlockchainAnchorClient.PAYLOAD_MODE_DIGEST,
            result.ref.extra[AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE]
        )
    }

    @Test
    fun `digest mode read returns the envelope with the expected shape`() = runBlocking {
        val client = TestAnchorClient(digestMode = true)
        val result = client.writePayload(payload)

        val read = client.readPayload(result.ref)
        val envelope = read.payload

        assertTrue(AnchorDigest.isEnvelope(envelope), "on-chain data must be a digest envelope")
        envelope as JsonObject
        assertEquals("SHA-256", envelope[AnchorDigest.FIELD_ALG]?.jsonPrimitive?.content)
        assertEquals("application/json", envelope[AnchorDigest.FIELD_MEDIA_TYPE]?.jsonPrimitive?.content)

        // The digest is base64url(sha256(payload bytes)) over the exact serialized bytes.
        val payloadBytes = Json.encodeToString(JsonElement.serializer(), payload)
            .toByteArray(StandardCharsets.UTF_8)
        val expectedDigest = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(payloadBytes))
        assertEquals(expectedDigest, envelope[AnchorDigest.FIELD_DIGEST]?.jsonPrimitive?.content)

        // The read marks the ref so callers can tell the envelope is not the payload.
        assertEquals(
            AbstractBlockchainAnchorClient.PAYLOAD_MODE_DIGEST,
            read.ref.extra[AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE]
        )
    }

    @Test
    fun `digest mode write then verify is true`() = runBlocking {
        val client = TestAnchorClient(digestMode = true)

        val result = client.writePayload(payload)

        assertTrue(client.verifyAnchor(payload, result.ref))
    }

    @Test
    fun `digest mode tampered payload verifies false`() = runBlocking {
        val client = TestAnchorClient(digestMode = true)

        val result = client.writePayload(payload)

        assertFalse(client.verifyAnchor(tampered, result.ref))
    }

    @Test
    fun `digest mode submits the envelope bytes - never the payload - on chain`() = runBlocking {
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
        val exception = assertFailsWith<BlockchainException.ConfigurationFailed> {
            TestAnchorClient(payloadMode = "hashed")
        }
        assertTrue(exception.message.contains("payload mode"))
    }

    // ---------------------------------------------------------------------
    // verifyAnchor error handling
    // ---------------------------------------------------------------------

    @Test
    fun `verifyAnchor returns false when the anchor does not exist`() = runBlocking {
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
                }
            )
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
                }
            ),
            "missing mediaType must be rejected"
        )
        assertFalse(
            AnchorDigest.isEnvelope(
                buildJsonObject {
                    put("digest", validDigest)
                    put("mediaType", "application/json")
                }
            ),
            "missing alg must be rejected"
        )
        assertFalse(AnchorDigest.isEnvelope(buildJsonObject {}), "empty object must be rejected")
    }

    @Test
    fun `isEnvelope requires a digest of exactly 32 bytes`() {
        fun envelopeWithDigest(digest: String): JsonObject = buildJsonObject {
            put("alg", "SHA-256")
            put("digest", digest)
            put("mediaType", "application/json")
        }

        val b64 = Base64.getUrlEncoder().withoutPadding()
        assertFalse(AnchorDigest.isEnvelope(envelopeWithDigest("x")), "undecodable digest")
        assertFalse(AnchorDigest.isEnvelope(envelopeWithDigest("not base64url!!!")), "undecodable digest")
        assertFalse(
            AnchorDigest.isEnvelope(envelopeWithDigest(b64.encodeToString(ByteArray(31)))),
            "31-byte digest must be rejected"
        )
        assertFalse(
            AnchorDigest.isEnvelope(envelopeWithDigest(b64.encodeToString(ByteArray(33)))),
            "33-byte digest must be rejected"
        )
        assertTrue(
            AnchorDigest.isEnvelope(envelopeWithDigest(b64.encodeToString(ByteArray(32)))),
            "32-byte digest must be accepted"
        )
    }

    @Test
    fun `matches rejects undecodable digests`() {
        val broken = buildJsonObject {
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
    ) : AbstractBlockchainAnchorClient(
        chainId = "test:unit",
        options = buildMap {
            put(AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE, true)
            payloadMode?.let { put(AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE, it) }
        }
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
            val bytes = submittedBytes[txHash]
                ?: throw TrustWeaveException.NotFound(resource = "Transaction not found: $txHash")
            return AnchorResult(
                ref = buildAnchorRef(txHash),
                payload = Json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)),
                mediaType = "application/json"
            )
        }

        override fun generateTestTxHash(): String = "test_tx_${uniqueTestHashSuffix()}"

        override fun getBlockchainName(): String = "Test Chain"
    }
}
