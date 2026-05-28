package org.trustweave.signatures.tsa

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest

class BouncyCastleTsaClientTest {

    private lateinit var server: MockWebServer
    private lateinit var tsa: InProcessTsa

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        tsa = InProcessTsa.generate()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    // ------------------------------------------------------------------ happy paths

    @Test
    fun `returns a parsed token on the happy path`() = runBlocking {
        val digest = sha256("hello world")
        server.dispatcher = stampingDispatcher(tsa)

        val client = BouncyCastleTsaClient(TsaConfig(endpointUrl = server.url("/tsa").toString()))
        val token = client.requestTimeStamp(digest, TsaHashAlgorithm.SHA_256)

        assertNotNull(token.encoded)
        assertTrue(token.encoded.isNotEmpty())
        assertEquals(TsaHashAlgorithm.SHA_256, token.messageImprintAlgorithm)
        assertArrayEquals(digest, token.messageImprint)
        assertEquals(tsa.defaultPolicyOid, token.policyOid)
        assertTrue(
            token.tsaSubject.contains("TrustWeave Test TSA"),
            "expected subject to mention the TSA, got '${token.tsaSubject}'",
        )
    }

    @Test
    fun `request carries the application timestamp-query content type`() = runBlocking {
        val digest = sha256("payload")
        server.dispatcher = stampingDispatcher(tsa)

        val client = BouncyCastleTsaClient(TsaConfig(endpointUrl = server.url("/tsa").toString()))
        client.requestTimeStamp(digest, TsaHashAlgorithm.SHA_256)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(
            "application/timestamp-query",
            recorded.getHeader("Content-Type"),
        )
        assertTrue(recorded.bodySize > 0)
    }

    @Test
    fun `passes the caller-supplied nonce through to the TSA request`() = runBlocking {
        val digest = sha256("nonce-test")
        val nonce = ByteArray(16) { (it + 1).toByte() }
        val captured = mutableListOf<ByteArray>()
        server.dispatcher = stampingDispatcher(tsa, captureRequestBytesInto = captured)

        val client = BouncyCastleTsaClient(TsaConfig(endpointUrl = server.url("/tsa").toString()))
        client.requestTimeStamp(digest, TsaHashAlgorithm.SHA_256, nonce = nonce)

        assertEquals(1, captured.size, "expected exactly one request to the TSA")
        val tsRequest = org.bouncycastle.tsp.TimeStampRequest(captured[0])
        val sentNonce: java.math.BigInteger? = tsRequest.nonce
        assertNotNull(sentNonce)
        val expectedNonce = java.math.BigInteger(1, nonce)
        assertEquals(expectedNonce, sentNonce)
    }

    @Test
    fun `signer-cert pin verification passes when the matching cert is pinned`() = runBlocking {
        val digest = sha256("pin-pass")
        server.dispatcher = stampingDispatcher(tsa)

        val client = BouncyCastleTsaClient(
            TsaConfig(
                endpointUrl = server.url("/tsa").toString(),
                trustedSignerCertificates = listOf(tsa.certEncoded),
            ),
        )

        val token = client.requestTimeStamp(digest, TsaHashAlgorithm.SHA_256)
        assertNotNull(token)
    }

    // ------------------------------------------------------------------ failure paths

    @Test
    fun `rejects when digest size disagrees with the algorithm`() = runBlocking {
        val client = BouncyCastleTsaClient(
            TsaConfig(endpointUrl = server.url("/tsa").toString()),
        )
        assertThrows<IllegalArgumentException> {
            runBlocking { client.requestTimeStamp(ByteArray(8), TsaHashAlgorithm.SHA_256) }
        }
    }

    @Test
    fun `surfaces HTTP non-2xx as TsaException`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("upstream broken"))
        val client = BouncyCastleTsaClient(TsaConfig(endpointUrl = server.url("/tsa").toString()))

        val ex = assertThrows<TsaException> {
            runBlocking { client.requestTimeStamp(sha256("x"), TsaHashAlgorithm.SHA_256) }
        }
        assertTrue(ex.message!!.contains("HTTP 500"), "got: ${ex.message}")
    }

    @Test
    fun `surfaces malformed TimeStampResponse as TsaException`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/timestamp-reply")
                .setBody(Buffer().apply { write(byteArrayOf(0x42, 0x42, 0x42, 0x42)) }),
        )
        val client = BouncyCastleTsaClient(TsaConfig(endpointUrl = server.url("/tsa").toString()))

        val ex = assertThrows<TsaException> {
            runBlocking { client.requestTimeStamp(sha256("x"), TsaHashAlgorithm.SHA_256) }
        }
        assertTrue(
            ex.message!!.contains("TimeStampResponse"),
            "expected response-parse error, got: ${ex.message}",
        )
    }

    // Note on policy-mismatch coverage: BC's TimeStampResponseGenerator hard-echoes the
    // request's reqPolicy when one is set, so a server-side `policyOidOverride` is silently
    // ignored and the mismatch path in verifyPolicyIfRequired() is unreachable through the
    // normal HTTP round-trip. The defensive check stays in production code as a guard against
    // future TSA implementations or BC version changes; it is exercised via review, not by an
    // integration test.

    @Test
    fun `rejects when the signer cert is not on the pin list`() {
        val otherTsa = InProcessTsa.generate("CN=Some Other TSA")
        server.dispatcher = stampingDispatcher(tsa) // server signs with `tsa`...

        val client = BouncyCastleTsaClient(
            TsaConfig(
                endpointUrl = server.url("/tsa").toString(),
                // ... but we pin a different TSA's cert
                trustedSignerCertificates = listOf(otherTsa.certEncoded),
            ),
        )
        val ex = assertThrows<TsaException> {
            runBlocking { client.requestTimeStamp(sha256("pin-test"), TsaHashAlgorithm.SHA_256) }
        }
        assertTrue(
            ex.message!!.contains("trustedSignerCertificates"),
            "expected pin mismatch message, got: ${ex.message}",
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun sha256(s: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())

    /**
     * MockWebServer dispatcher that stamps every `POST /tsa` request with [tsa],
     * optionally rewriting the issuance policy. If [captureRequestBytesInto] is supplied,
     * the raw request bytes are appended to it so tests can inspect what was sent without
     * racing the consumed `RecordedRequest.body` buffer.
     */
    private fun stampingDispatcher(
        tsa: InProcessTsa,
        policyOverride: ((RecordedRequest) -> String?)? = null,
        captureRequestBytesInto: MutableList<ByteArray>? = null,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val body = request.body.readByteArray()
            captureRequestBytesInto?.add(body)
            val policy = policyOverride?.invoke(request)
            val responseBytes = tsa.stamp(body, policyOidOverride = policy)
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/timestamp-reply")
                .setBody(Buffer().apply { write(responseBytes) })
        }
    }
}
