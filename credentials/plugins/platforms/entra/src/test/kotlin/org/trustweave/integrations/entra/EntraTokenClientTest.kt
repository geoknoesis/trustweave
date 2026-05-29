package org.trustweave.integrations.entra

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class EntraTokenClientTest {

    private lateinit var server: WireMockServer
    private lateinit var config: EntraConfig

    @BeforeEach
    fun setUp() {
        server = WireMockServer(wireMockConfig().dynamicPort())
        server.start()
        config = EntraConfig(
            tenantId = "tenant-abc",
            clientId = "client-xyz",
            clientSecret = "super-secret",
            authorityDid = "did:web:authority.example.com",
            tokenEndpointBaseUrl = server.baseUrl(),
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `posts client_credentials grant with scope`() = runBlocking {
        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=client-xyz"))
                .withRequestBody(containing("scope=3db474b9-6a0c-4840-96ac-1fceb342124f%2F.default"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"tk","token_type":"Bearer","expires_in":3600}"""),
                ),
        )

        val client = EntraTokenClient(config = config, httpClient = OkHttpClient())
        val token = client.getAccessToken()
        assertEquals("tk", token)
    }

    @Test
    fun `caches token across concurrent calls and only hits AAD once`() = runBlocking {
        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"tk","token_type":"Bearer","expires_in":3600}"""),
            ),
        )
        val client = EntraTokenClient(config = config, httpClient = OkHttpClient())
        repeat(5) {
            assertEquals("tk", client.getAccessToken())
        }
        server.verify(1, postRequestedFor(urlEqualTo("/tenant-abc/oauth2/v2.0/token")))
    }

    @Test
    fun `refreshes token after it nears expiry`() = runBlocking {
        // Use a controllable clock so we can advance time past the expiry window
        val baseInstant = Instant.parse("2026-01-01T00:00:00Z")
        var nowSupplier: () -> Instant = { baseInstant }
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant(): Instant = nowSupplier()
        }

        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"tk1","token_type":"Bearer","expires_in":60}"""),
            ),
        )

        val client = EntraTokenClient(
            config = config,
            httpClient = OkHttpClient(),
            clock = clock,
            refreshSkew = Duration.ofSeconds(10),
        )
        assertEquals("tk1", client.getAccessToken())

        // Stub a new token for the second fetch
        server.resetMappings()
        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"tk2","token_type":"Bearer","expires_in":3600}"""),
            ),
        )

        // Advance clock past expiry minus skew → 55s into the future
        nowSupplier = { baseInstant.plusSeconds(55) }
        assertEquals("tk2", client.getAccessToken())
    }

    @Test
    fun `propagates AAD error_description in AuthenticationFailed`() {
        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token")).willReturn(
                aResponse().withStatus(401).withHeader("Content-Type", "application/json")
                    .withBody(
                        """{"error":"invalid_client","error_description":"AADSTS7000215: Invalid client secret","correlation_id":"abc"}""",
                    ),
            ),
        )
        val client = EntraTokenClient(config = config, httpClient = OkHttpClient())
        val ex = assertThrows(EntraException.AuthenticationFailed::class.java) {
            runBlocking { client.getAccessToken() }
        }
        assertTrue(ex.message.contains("invalid_client"))
        assertTrue(ex.message.contains("AADSTS7000215"))
    }

    @Test
    fun `invalidate forces fresh token request`() = runBlocking {
        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"tk","token_type":"Bearer","expires_in":3600}"""),
            ),
        )
        val client = EntraTokenClient(config = config, httpClient = OkHttpClient())
        client.getAccessToken()
        client.invalidate()
        client.getAccessToken()
        server.verify(2, postRequestedFor(urlEqualTo("/tenant-abc/oauth2/v2.0/token")))
    }
}
