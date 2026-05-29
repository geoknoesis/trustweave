package org.trustweave.integrations.entra.exchange

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.did.identifiers.Did
import org.trustweave.integrations.entra.EntraConfig
import org.trustweave.integrations.entra.EntraException
import org.trustweave.integrations.entra.EntraIntegration

class EntraExchangeProtocolTest {

    private lateinit var server: WireMockServer
    private lateinit var integration: EntraIntegration

    @BeforeEach
    fun setUp() {
        server = WireMockServer(wireMockConfig().dynamicPort())
        server.start()
        integration = EntraIntegration(
            EntraConfig(
                tenantId = "tenant-abc",
                clientId = "c",
                clientSecret = "s",
                authorityDid = "did:web:authority.example.com",
                apiBaseUrl = server.baseUrl(),
                tokenEndpointBaseUrl = server.baseUrl(),
            ),
        )
        server.stubFor(
            post(urlEqualTo("/tenant-abc/oauth2/v2.0/token")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"tk","token_type":"Bearer","expires_in":3600}"""),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `protocol capabilities advertise offer and proof request operations only`() {
        val proto = integration.exchangeProtocol
        assertEquals(ExchangeProtocolName("entra"), proto.protocolName)
        assertTrue(proto.supports(ExchangeOperation.OFFER_CREDENTIAL))
        assertTrue(proto.supports(ExchangeOperation.REQUEST_PROOF))
        assertTrue(!proto.supports(ExchangeOperation.REQUEST_CREDENTIAL))
        assertTrue(!proto.supports(ExchangeOperation.ISSUE_CREDENTIAL))
        assertTrue(!proto.supports(ExchangeOperation.PRESENT_PROOF))
        assertTrue(proto.capabilities.supportsAsync)
    }

    @Test
    fun `offer wires through to createIssuanceRequest and returns envelope with request URL`() = runBlocking {
        server.stubFor(
            post(urlEqualTo("/v1.0/verifiableCredentials/createIssuanceRequest"))
                .withHeader("Authorization", equalTo("Bearer tk"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("EmployeeCredential")))
                .withRequestBody(matchingJsonPath("$.callback.state", equalTo("corr-1")))
                .willReturn(
                    aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"requestId":"req-7","url":"openid-vc://?r=req-7","expiry":1234,"qrCode":"qr"}""",
                        ),
                ),
        )

        val request = ExchangeRequest.Offer(
            protocolName = ExchangeProtocolName("entra"),
            issuerDid = Did(integration.config.authorityDid),
            holderDid = Did("did:web:holder.example.com"),
            credentialPreview = CredentialPreview(
                attributes = listOf(CredentialAttribute("given_name", "Ada")),
            ),
            options = entraIssuanceOptions(
                manifestUrl = "https://verifiedid.did.msidentity.com/manifests/x",
                credentialType = "EmployeeCredential",
                callbackUrl = "https://example.com/cb",
                clientName = "Acme",
                state = "corr-1",
                claims = mapOf("given_name" to "Ada"),
            ),
        )

        val envelope = integration.exchangeProtocol.offer(request)
        assertEquals(ExchangeMessageType.Offer, envelope.messageType)
        val data = envelope.messageData as JsonObject
        assertEquals("req-7", data["requestId"]?.jsonPrimitive?.content)
        assertEquals("openid-vc://?r=req-7", data["url"]?.jsonPrimitive?.content)
        assertEquals("qr", data["qrCode"]?.jsonPrimitive?.content)
        assertEquals("corr-1", envelope.metadata["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `offer rejects missing required options`() {
        val request = ExchangeRequest.Offer(
            protocolName = ExchangeProtocolName("entra"),
            issuerDid = Did(integration.config.authorityDid),
            holderDid = Did("did:web:holder.example.com"),
            credentialPreview = CredentialPreview(attributes = emptyList()),
            // No metadata at all
        )
        assertThrows(EntraException.MissingOption::class.java) {
            runBlocking { integration.exchangeProtocol.offer(request) }
        }
    }

    @Test
    fun `requestProof builds a presentation request envelope`() = runBlocking {
        server.stubFor(
            post(urlEqualTo("/v1.0/verifiableCredentials/createPresentationRequest"))
                .withHeader("Authorization", equalTo("Bearer tk"))
                .withRequestBody(
                    matchingJsonPath(
                        "$.requestedCredentials[0].type",
                        equalTo("EmployeeCredential"),
                    ),
                )
                .willReturn(
                    aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("""{"requestId":"p-9","url":"openid-vc://?p=9","expiry":11}"""),
                ),
        )

        val options = org.trustweave.credential.exchange.options.ExchangeOptions.builder()
            .addMetadata("callbackUrl", "https://example.com/cb")
            .addMetadata("clientName", "Acme")
            .addMetadata("credentialType", "EmployeeCredential")
            .addMetadata("state", "v-1")
            .build()
        val req = ProofExchangeRequest.Request(
            protocolName = ExchangeProtocolName("entra"),
            verifierDid = Did(integration.config.authorityDid),
            proverDid = Did("did:web:prover.example.com"),
            proofRequest = ProofRequest(
                name = "employment-check",
                requestedAttributes = emptyMap(),
            ),
            options = options,
        )

        val envelope = integration.exchangeProtocol.requestProof(req)
        assertEquals(ExchangeMessageType.ProofRequest, envelope.messageType)
        val data = envelope.messageData as JsonObject
        assertEquals("p-9", data["requestId"]?.jsonPrimitive?.content)
        assertNotNull(envelope.metadata["requestId"])
        assertEquals("v-1", envelope.metadata["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `unsupported operations throw InvalidOperation`() {
        val req = ExchangeRequest.Request(
            protocolName = ExchangeProtocolName("entra"),
            holderDid = Did("did:web:holder.example.com"),
            issuerDid = Did(integration.config.authorityDid),
            offerId = org.trustweave.credential.identifiers.OfferId("offer-1"),
        )
        assertThrows(TrustWeaveException.InvalidOperation::class.java) {
            runBlocking { integration.exchangeProtocol.request(req) }
        }
    }
}
