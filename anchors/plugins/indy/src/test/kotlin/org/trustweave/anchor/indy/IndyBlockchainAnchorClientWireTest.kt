package org.trustweave.anchor.indy

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises [IndyBlockchainAnchorClient] against a WireMock-backed indy-vdr-proxy.
 *
 * Verifies that the client builds a signed ATTRIB request on writes, parses the
 * resulting seqNo, and uses GET_ATTRIB to retrieve the inline payload on reads.
 */
class IndyBlockchainAnchorClientWireTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var httpClient: HttpClient

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        WireMock.configureFor("localhost", wireMock.port())
        httpClient = HttpClient(CIO)
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
        httpClient.close()
    }

    @Test
    fun `write payload signs and submits ATTRIB`() = runBlocking {
        wireMock.stubFor(
            post(urlPathEqualTo("/submit"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""{"op":"REPLY","result":{"seqNo":17,"txnTime":1700000001}}""")
                )
        )

        val seed = ByteArray(32) { it.toByte() }
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = mapOf(
                "poolEndpoint" to "http://localhost:${wireMock.port()}",
                "did" to "V4SGRU86Z58d6TV7PBUe6f",
                "signingKeySeed" to Base58.encode(seed)
            ),
            httpClient = httpClient
        )

        val payload = buildJsonObject { put("foo", JsonPrimitive("bar")) }
        val result = client.writePayload(payload)
        assertEquals("17", result.ref.txHash)
        assertEquals(IndyBlockchainAnchorClient.BCOVRIN_TESTNET, result.ref.chainId)

        wireMock.verify(
            WireMock.postRequestedFor(urlPathEqualTo("/submit"))
                .withRequestBody(matchingJsonPath("$.operation.type", WireMock.equalTo("100")))
                .withRequestBody(matchingJsonPath("$.identifier", WireMock.equalTo("V4SGRU86Z58d6TV7PBUe6f")))
                .withRequestBody(matchingJsonPath("$.signature"))
        )
    }

    @Test
    fun `read payload decodes inline raw payload`() = runBlocking {
        val rawJsonString = """{"digest":"abc123","mediaType":"application/json","payload":{"foo":"bar"}}"""
        val escaped = rawJsonString.replace("\"", "\\\"")
        wireMock.stubFor(
            post(urlPathEqualTo("/submit"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(
                            """{"op":"REPLY","result":{"seqNo":17,"txnTime":1700000001,"data":"$escaped"}}"""
                        )
                )
        )

        val seed = ByteArray(32) { it.toByte() }
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = mapOf(
                "poolEndpoint" to "http://localhost:${wireMock.port()}",
                "did" to "V4SGRU86Z58d6TV7PBUe6f",
                "signingKeySeed" to Base58.encode(seed)
            ),
            httpClient = httpClient
        )

        val read = client.readPayload(
            org.trustweave.anchor.AnchorRef(
                chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
                txHash = "17"
            )
        )
        assertEquals("17", read.ref.txHash)
        assertEquals("17", read.ref.extra["seqNo"])
        val payloadObj = read.payload.jsonObject
        assertEquals("bar", payloadObj["foo"]!!.jsonPrimitive.content)
        assertNotNull(read.timestamp)
        Unit
    }

    @Test
    fun `canSubmitTransaction is false without signing key`() {
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = mapOf(
                "did" to "V4SGRU86Z58d6TV7PBUe6f",
                org.trustweave.anchor.AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to true
            )
        )
        // Indirect: with no signer and the in-memory test mode explicitly enabled,
        // a write succeeds and returns a synthetic hash with "indy_test_" prefix.
        // Without the opt-in flag the same write fails closed (see
        // IndyBlockchainAnchorClientTest).
        val payload = buildJsonObject { put("k", JsonPrimitive("v")) }
        val result = runBlocking { client.writePayload(payload) }
        assertTrue(result.ref.txHash.startsWith("indy_test_"))
    }
}
