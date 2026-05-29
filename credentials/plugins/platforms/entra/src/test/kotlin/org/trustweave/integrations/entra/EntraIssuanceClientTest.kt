package org.trustweave.integrations.entra

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class EntraIssuanceClientTest {

    private lateinit var server: WireMockServer
    private lateinit var config: EntraConfig
    private lateinit var tokenClient: EntraTokenClient
    private lateinit var client: EntraIssuanceClient

    @BeforeEach
    fun setUp() {
        server = WireMockServer(wireMockConfig().dynamicPort())
        server.start()
        config = EntraConfig(
            tenantId = "tenant-abc",
            clientId = "client-xyz",
            clientSecret = "secret",
            authorityDid = "did:web:authority.example.com",
            apiBaseUrl = server.baseUrl(),
            tokenEndpointBaseUrl = server.baseUrl(),
        )
        val http = OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(5))
            .build()
        tokenClient = EntraTokenClient(config = config, httpClient = http)
        client = EntraIssuanceClient(config, tokenClient, http)

        // Stub token endpoint
        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"access_token":"test-token","token_type":"Bearer","expires_in":3600}""",
                        ),
                ),
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `createRequest sends correctly-shaped wire payload and parses response`() = runBlocking {
        server.stubFor(
            post(urlEqualTo("/v1.0/verifiableCredentials/createIssuanceRequest"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withHeader("Content-Type", com.github.tomakehurst.wiremock.client.WireMock.containing("application/json"))
                .withRequestBody(matchingJsonPath("$.authority", equalTo("did:web:authority.example.com")))
                .withRequestBody(matchingJsonPath("$.type", equalTo("EmployeeCredential")))
                .withRequestBody(matchingJsonPath("$.manifest"))
                .withRequestBody(matchingJsonPath("$.callback.url"))
                .withRequestBody(matchingJsonPath("$.callback.state", equalTo("state-1")))
                .withRequestBody(matchingJsonPath("$.registration.clientName", equalTo("Acme")))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "requestId": "req-1",
                              "url": "openid-vc://?request_uri=https://verifiedid.example.com/req/req-1",
                              "expiry": 1700000000,
                              "qrCode": "data:image/png;base64,aaaa"
                            }
                            """.trimIndent(),
                        ),
                ),
        )

        val body = client.buildRequestBody(
            credentialType = "EmployeeCredential",
            manifestUrl = "https://verifiedid.did.msidentity.com/tenant/contract/manifest",
            callbackUrl = "https://example.com/entra/callback",
            state = "state-1",
            clientName = "Acme",
            claims = mapOf("given_name" to "Ada", "family_name" to "Lovelace"),
        )
        val response = client.createRequest(body)

        assertEquals("req-1", response.requestId)
        assertEquals(1700000000L, response.expiry)
        assertNotNull(response.qrCode)
    }

    @Test
    fun `encodeRequestBody omits null fields and uses Entra wire names`() {
        val body = client.buildRequestBody(
            credentialType = "EmployeeCredential",
            manifestUrl = "https://example.com/manifest",
            callbackUrl = "https://example.com/cb",
            state = "s",
            clientName = "Acme",
        )
        val json = client.encodeRequestBody(body)

        // includeQRCode is the camelCase Entra field, not includeQrCode
        assert(json.contains("\"includeQRCode\""))
        // No pin → must be absent
        assert(!json.contains("\"pin\""))
        // No claims → must be absent
        assert(!json.contains("\"claims\""))
    }

    @Test
    fun `propagates wire format exactly via equalToJson when claims provided`() = runBlocking {
        val expectedJson = """
            {
              "authority": "did:web:authority.example.com",
              "callback": { "url": "https://cb.example.com", "state": "s" },
              "registration": { "clientName": "Acme" },
              "type": "T",
              "manifest": "https://m.example.com",
              "claims": { "k": "v" },
              "includeQRCode": true,
              "includeReceipt": false
            }
        """.trimIndent()
        server.stubFor(
            post(urlEqualTo("/v1.0/verifiableCredentials/createIssuanceRequest"))
                .withRequestBody(equalToJson(expectedJson, true, true))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"requestId":"r","url":"u","expiry":0}"""),
                ),
        )
        val body = client.buildRequestBody(
            credentialType = "T",
            manifestUrl = "https://m.example.com",
            callbackUrl = "https://cb.example.com",
            state = "s",
            clientName = "Acme",
            claims = mapOf("k" to "v"),
        )
        val res = client.createRequest(body)
        assertEquals("r", res.requestId)
    }

    @Test
    fun `5xx response is mapped to RequestServiceError with body preserved`() = runBlocking {
        server.stubFor(
            post(urlEqualTo("/v1.0/verifiableCredentials/createIssuanceRequest"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withBody("downstream unavailable"),
                ),
        )
        val body = client.buildRequestBody(
            credentialType = "T",
            manifestUrl = "https://m",
            callbackUrl = "https://cb",
            state = "s",
            clientName = "Acme",
        )
        val ex = assertThrows(EntraException.RequestServiceError::class.java) {
            runBlocking { client.createRequest(body) }
        }
        assertEquals(503, ex.httpStatus)
        assertEquals("downstream unavailable", ex.responseBody)
    }
}
