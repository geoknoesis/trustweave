package org.trustweave.integrations.entra

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The default HTTP client is SSRF-guarded; loopback test servers need an explicitly injected client. */
class EntraDefaultHttpClientTest {
    private lateinit var server: WireMockServer
    private lateinit var config: EntraConfig

    @BeforeEach
    fun setUp() {
        server = WireMockServer(wireMockConfig().dynamicPort())
        server.start()
        server.stubFor(
            post(urlEqualTo("/t/oauth2/v2.0/token")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"tk","token_type":"Bearer","expires_in":3600}"""),
            ),
        )
        // HttpUrlPolicy still admits loopback http for config validation.
        config =
            EntraConfig(
                tenantId = "t",
                clientId = "c",
                clientSecret = "s",
                authorityDid = "did:web:authority.example.com",
                tokenEndpointBaseUrl = server.baseUrl(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `default token client refuses loopback destinations`() =
        runBlocking<Unit> {
            val client = EntraTokenClient(config = config)
            assertThrows(Exception::class.java) { runBlocking { client.getAccessToken() } }
            assertEquals(0, server.allServeEvents.size)
        }

    @Test
    fun `injected client still reaches a loopback server`() =
        runBlocking<Unit> {
            val client = EntraTokenClient(config = config, httpClient = OkHttpClient())
            assertEquals("tk", client.getAccessToken())
        }
}
