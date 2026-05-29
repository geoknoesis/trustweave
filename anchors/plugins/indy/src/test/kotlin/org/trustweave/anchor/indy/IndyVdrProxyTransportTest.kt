package org.trustweave.anchor.indy

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.anchor.exceptions.BlockchainException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * HTTP-level tests for [IndyVdrProxyTransport]. Uses WireMock to stand in for a real
 * `indy-vdr-proxy` and asserts the on-the-wire JSON exactly matches what the codec
 * produces. This complements [IndyRequestCodecTest] by exercising the serialization
 * boundary and the HTTP request/response handling.
 */
class IndyVdrProxyTransportTest {

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

    private fun transport() = IndyVdrProxyTransport(
        baseUrl = "http://localhost:${wireMock.port()}",
        httpClient = httpClient
    )

    @Test
    fun `submit ATTRIB request returns parsed seqNo`() = runBlocking {
        wireMock.stubFor(
            post(urlPathEqualTo("/submit"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"op":"REPLY","result":{"seqNo":42,"txnTime":1700000000}}"""
                        )
                )
        )

        val req = IndyRequestCodec.buildAttribRequest(
            submitterDid = "V4SGRU86Z58d6TV7PBUe6f",
            targetDid = "V4SGRU86Z58d6TV7PBUe6f",
            rawPayload = buildJsonObject { put("digest", JsonPrimitive("abc")) },
            reqId = 1L
        )
        val signed = IndyRequestCodec.attachSignature(req, "sig123")
        val reply = transport().submit(signed)

        assertEquals("42", IndyRequestCodec.parseWriteReply(reply))
        wireMock.verify(
            WireMock.postRequestedFor(urlPathEqualTo("/submit"))
                .withRequestBody(
                    equalToJson(
                        """
                        {
                          "operation": {
                            "type": "100",
                            "dest": "V4SGRU86Z58d6TV7PBUe6f",
                            "raw": "{\"digest\":\"abc\"}"
                          },
                          "identifier": "V4SGRU86Z58d6TV7PBUe6f",
                          "reqId": 1,
                          "protocolVersion": 2,
                          "signature": "sig123"
                        }
                        """.trimIndent()
                    )
                )
        )
    }

    @Test
    fun `submit maps non-2xx to ConnectionFailed`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/submit"))
                .willReturn(aResponse().withStatus(500).withBody("internal error"))
        )

        assertFailsWith<BlockchainException.ConnectionFailed> {
            runBlocking {
                transport().submit(
                    IndyRequestCodec.buildGetAttribRequest(
                        submitterDid = "V4SGRU86Z58d6TV7PBUe6f",
                        targetDid = "V4SGRU86Z58d6TV7PBUe6f",
                        reqId = 2L
                    )
                )
            }
        }
    }

    @Test
    fun `submit maps REJECT op to TransactionFailed`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/submit"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""{"op":"REJECT","reason":"unknown identifier"}""")
                )
        )

        assertFailsWith<BlockchainException.TransactionFailed> {
            runBlocking {
                transport().submit(
                    IndyRequestCodec.buildGetAttribRequest(
                        submitterDid = "V4SGRU86Z58d6TV7PBUe6f",
                        targetDid = "V4SGRU86Z58d6TV7PBUe6f",
                        reqId = 3L
                    )
                )
            }
        }
    }
}
