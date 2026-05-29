package org.trustweave.integrations.entra

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EntraPresentationClientTest {

    private lateinit var server: WireMockServer
    private lateinit var config: EntraConfig
    private lateinit var tokenClient: EntraTokenClient
    private lateinit var client: EntraPresentationClient

    @BeforeEach
    fun setUp() {
        server = WireMockServer(wireMockConfig().dynamicPort())
        server.start()
        config = EntraConfig(
            tenantId = "tenant-abc",
            clientId = "client",
            clientSecret = "secret",
            authorityDid = "did:web:verifier.example.com",
            apiBaseUrl = server.baseUrl(),
            tokenEndpointBaseUrl = server.baseUrl(),
        )
        val http = OkHttpClient()
        tokenClient = EntraTokenClient(config = config, httpClient = http)
        client = EntraPresentationClient(config, tokenClient, http)
        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"tok","token_type":"Bearer","expires_in":3600}"""),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `buildRequestBody rejects empty requestedCredentials list`() {
        assertThrows(IllegalArgumentException::class.java) {
            client.buildRequestBody(
                requestedCredentials = emptyList(),
                callbackUrl = "https://cb",
                state = "s",
                clientName = "Acme",
            )
        }
    }

    @Test
    fun `createRequest issues bearer-authorized POST and parses response`() = runBlocking {
        server.stubFor(
            post(urlEqualTo("/v1.0/verifiableCredentials/createPresentationRequest"))
                .withHeader("Authorization", equalTo("Bearer tok"))
                .withRequestBody(matchingJsonPath("$.authority", equalTo("did:web:verifier.example.com")))
                .withRequestBody(
                    matchingJsonPath(
                        "$.requestedCredentials[0].type",
                        equalTo("EmployeeCredential"),
                    ),
                )
                .willReturn(
                    aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"requestId":"pres-1","url":"openid-vc://?req=x","expiry":99,"qrCode":"data:..."}""",
                        ),
                ),
        )
        val body = client.buildRequestBody(
            requestedCredentials = listOf(
                EntraRequestedCredential(
                    type = "EmployeeCredential",
                    acceptedIssuers = listOf("did:web:issuer.example.com"),
                ),
            ),
            callbackUrl = "https://cb",
            state = "s",
            clientName = "Acme",
        )
        val res = client.createRequest(body)
        assertEquals("pres-1", res.requestId)
        assertNotNull(res.qrCode)
    }

    @Test
    fun `parseCallbackPayload extracts presentation verified claims`() {
        val payload = client.parseCallbackPayload(
            """
            {
              "requestId": "req-1",
              "requestStatus": "presentation_verified",
              "state": "state-1",
              "subject": "did:web:holder.example.com",
              "verifiedCredentialsData": [
                {
                  "issuer": "did:web:issuer.example.com",
                  "type": ["VerifiableCredential","EmployeeCredential"],
                  "claims": { "given_name": "Ada", "employee_id": "E42" },
                  "domainValidation": { "url": "https://issuer.example.com" }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("req-1", payload.requestId)
        assertEquals("presentation_verified", payload.requestStatus)
        assertEquals("state-1", payload.state)
        assertEquals("did:web:holder.example.com", payload.subject)
        val cred = payload.verifiedCredentialsData?.single()
        assertNotNull(cred)
        assertEquals("did:web:issuer.example.com", cred!!.issuer)
        assertTrue("EmployeeCredential" in cred.type)
        assertEquals("Ada", cred.claims["given_name"]?.jsonPrimitive?.content)
        assertEquals("E42", cred.claims["employee_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parseCallbackPayload maps issuance_error payload to CallbackError`() {
        val raw = """
            {
              "requestId":"req-2",
              "requestStatus":"issuance_error",
              "state":"state-2",
              "error":{ "code":"badOrMissingField", "message":"manifest is missing" }
            }
        """.trimIndent()
        val ex = assertThrows(EntraException.CallbackError::class.java) {
            client.parseCallbackPayload(raw)
        }
        assertEquals("badOrMissingField", ex.errorCode)
        assertTrue(ex.message.contains("manifest is missing"))
    }

    @Test
    fun `parseCallbackPayload rejects malformed JSON`() {
        assertThrows(EntraException.MalformedResponse::class.java) {
            client.parseCallbackPayload("not-json")
        }
    }
}
