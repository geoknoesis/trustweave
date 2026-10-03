package org.trustweave.credential.oidc4vci

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.oidc4vci.exception.Oidc4VciException
import org.trustweave.credential.oidc4vci.exchange.Oidc4VciExchangeProtocol
import org.trustweave.credential.oidc4vci.exchange.spi.Oidc4VciExchangeProtocolProvider
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PKCE, exception contracts, the SPI provider and the exchange-protocol adapter. */
class Oidc4VciProtocolTest {
    private val service =
        Oidc4VciService(
            credentialIssuerUrl = "https://issuer.example.com",
            kms = InMemoryKeyManagementService(),
            httpClient = OkHttpClient(),
        )
    private val protocol = Oidc4VciExchangeProtocol(service)

    // ------------------------------------------------------------------ PKCE

    @Test
    fun `S256 challenge matches the RFC 7636 appendix B vector`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.codeChallengeS256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `generated verifiers are 43 url-safe characters and do not repeat`() {
        val verifiers = (1..200).map { Pkce.generateCodeVerifier() }
        assertTrue(verifiers.all { it.length == 43 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } })
        assertEquals(verifiers.size, verifiers.toSet().size)
    }

    // ------------------------------------------------------------------ exceptions

    @Test
    fun `exceptions carry stable codes and structured context`() {
        val http = Oidc4VciException.HttpRequestFailed("https://x", 502, "bad gateway")
        assertEquals("OIDC4VCI_HTTP_REQUEST_FAILED", http.code)
        assertEquals(502, http.context["statusCode"])
        assertTrue(http.message.contains("HTTP 502"))
        assertTrue(
            Oidc4VciException
                .HttpRequestFailed("https://x", null, "dns")
                .context
                .containsKey("statusCode")
                .not(),
        )

        assertEquals("OIDC4VCI_TOKEN_EXCHANGE_FAILED", Oidc4VciException.TokenExchangeFailed("denied").code)
        assertEquals("OIDC4VCI_METADATA_FETCH_FAILED", Oidc4VciException.MetadataFetchFailed("https://i", "404").code)
        assertEquals("OIDC4VCI_CREDENTIAL_REQUEST_FAILED", Oidc4VciException.CredentialRequestFailed("x").code)
        assertEquals("OIDC4VCI_CREDENTIAL_VERIFICATION_FAILED", Oidc4VciException.CredentialVerificationFailed("x").code)
        assertEquals("OIDC4VCI_TOKEN_ENDPOINT_RESOLUTION_FAILED", Oidc4VciException.TokenEndpointResolutionFailed("i", "x").code)
        assertEquals("OIDC4VCI_OFFER_PARSE_FAILED", Oidc4VciException.OfferParseFailed("uri", "bad").code)
        assertEquals(7, Oidc4VciException.CapacityExceeded(7).context["capacity"])
        val cause = IllegalStateException("root")
        assertEquals(cause, Oidc4VciException.TokenExchangeFailed("x", cause = cause).cause)
    }

    // ------------------------------------------------------------------ provider

    @Test
    fun `provider ignores other protocols and demands its required options`() {
        val provider = Oidc4VciExchangeProtocolProvider()
        assertNull(provider.create("didcomm", emptyMap()))
        assertFailsWith<IllegalArgumentException> { provider.create("oidc4vci", mapOf("kms" to InMemoryKeyManagementService())) }
        assertFailsWith<IllegalArgumentException> { provider.create("oidc4vci", mapOf("credentialIssuerUrl" to "https://i")) }
        val created =
            provider.create("oidc4vci", mapOf("credentialIssuerUrl" to "https://i.example", "kms" to InMemoryKeyManagementService()))
        assertTrue(created is Oidc4VciExchangeProtocol)
    }

    // ------------------------------------------------------------------ protocol

    @Test
    fun `capabilities cover issuance only and require transport security`() {
        assertEquals(
            setOf(ExchangeOperation.OFFER_CREDENTIAL, ExchangeOperation.REQUEST_CREDENTIAL, ExchangeOperation.ISSUE_CREDENTIAL),
            protocol.capabilities.supportedOperations,
        )
        assertTrue(protocol.capabilities.requiresTransportSecurity)
    }

    private fun offerRequest(metadata: Map<String, kotlinx.serialization.json.JsonElement>) =
        ExchangeRequest.Offer(
            protocolName = protocol.protocolName,
            issuerDid = Did("did:example:issuer"),
            holderDid = Did("did:example:holder"),
            credentialPreview = CredentialPreview(attributes = listOf(CredentialAttribute(name = "name", value = "Alice"))),
            options = ExchangeOptions(metadata = metadata),
        )

    @Test
    fun `an offer without credentialIssuer is refused`() {
        assertFailsWith<IllegalArgumentException> { runBlocking { protocol.offer(offerRequest(emptyMap())) } }
    }

    @Test
    fun `an offer is emitted as a JSON envelope carrying its id and uri`() =
        runBlocking<Unit> {
            val envelope =
                protocol.offer(
                    offerRequest(
                        mapOf(
                            "credentialIssuer" to JsonPrimitive("https://issuer.example.com"),
                            "credentialTypes" to JsonArray(listOf(JsonPrimitive("PersonCredential"))),
                        ),
                    ),
                )
            assertEquals(ExchangeMessageType.Offer, envelope.messageType)
            val data = envelope.messageData.jsonObject
            assertEquals("https://issuer.example.com", data["credentialIssuer"]!!.jsonPrimitive.content)
            assertEquals(envelope.metadata["offerId"]!!.jsonPrimitive.content, data["offerId"]!!.jsonPrimitive.content)
            assertTrue(
                envelope.metadata["offerUri"]!!
                    .jsonPrimitive.content
                    .startsWith("openid-credential-offer://"),
            )
        }

    @Test
    fun `proof operations are refused with a typed error`() {
        val request =
            org.trustweave.credential.exchange.request.ProofExchangeRequest.Request(
                protocolName = protocol.protocolName,
                verifierDid = Did("did:example:v"),
                proverDid = Did("did:example:p"),
                proofRequest =
                    org.trustweave.credential.exchange.request.ProofRequest(
                        "n",
                        requestedAttributes = emptyMap(),
                    ),
            )
        val e = assertFailsWith<TrustWeaveException.InvalidOperation> { runBlocking { protocol.requestProof(request) } }
        assertEquals("OPERATION_NOT_SUPPORTED", e.code)
        assertNotNull(e.context["supportedOperations"])
    }
}
