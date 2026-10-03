package org.trustweave.credential.siop

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.AttributeRequest
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.identifiers.OfferId
import org.trustweave.credential.identifiers.RequestId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.siop.exchange.SiopV2ExchangeProtocol
import org.trustweave.credential.siop.exchange.spi.SiopV2ExchangeProtocolProvider
import org.trustweave.credential.siop.models.SiopClientIdScheme
import org.trustweave.credential.siop.models.SiopResponseMode
import org.trustweave.credential.siop.models.SiopV2AuthorizationRequest
import org.trustweave.credential.siop.models.SiopV2AuthorizationResponse
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The SIOPv2 exchange adapter, its SPI provider, config defaults and wire models. */
class SiopV2ProtocolTest {
    private val service = SiopV2Service(kms = InMemoryKeyManagementService(), httpClient = okhttp3.OkHttpClient())
    private val protocol = SiopV2ExchangeProtocol(service)

    private fun proofRequest(metadata: Map<String, String>) =
        ProofExchangeRequest.Request(
            protocolName = protocol.protocolName,
            verifierDid = Did("did:example:verifier"),
            proverDid = Did("did:example:prover"),
            proofRequest = ProofRequest("n", requestedAttributes = mapOf("a" to AttributeRequest("name"))),
            options = ExchangeOptions(metadata = metadata.mapValues { JsonPrimitive(it.value) }),
        )

    private fun presentation() =
        VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:example:prover"),
            verifiableCredential = emptyList(),
        )

    private fun presentRequest(
        sessionId: String,
        metadata: Map<String, String>,
    ) = ProofExchangeRequest.Presentation(
        protocolName = protocol.protocolName,
        proverDid = Did("did:example:prover"),
        verifierDid = Did("did:example:verifier"),
        presentation = presentation(),
        requestId = RequestId(sessionId),
        options = ExchangeOptions(metadata = metadata.mapValues { JsonPrimitive(it.value) }),
    )

    // ------------------------------------------------------------------ protocol

    @Test
    fun `only proof operations are advertised and credential operations are refused`() {
        assertEquals(setOf(ExchangeOperation.REQUEST_PROOF, ExchangeOperation.PRESENT_PROOF), protocol.capabilities.supportedOperations)
        assertTrue(protocol.capabilities.requiresTransportSecurity)

        val did = Did("did:example:x")
        val requests: List<suspend () -> Unit> =
            listOf(
                {
                    protocol.offer(
                        ExchangeRequest.Offer(
                            protocol.protocolName,
                            did,
                            did,
                            org.trustweave.credential.exchange.model
                                .CredentialPreview(attributes = emptyList()),
                        ),
                    )
                },
                { protocol.request(ExchangeRequest.Request(protocol.protocolName, did, did, OfferId("o"))) },
            )
        for (call in requests) {
            val e = assertFailsWith<TrustWeaveException.InvalidOperation> { runBlocking { call() } }
            assertEquals("OPERATION_NOT_SUPPORTED", e.code)
        }
    }

    @Test
    fun `a proof request needs a response uri and creates a retrievable session`() =
        runBlocking<Unit> {
            assertFailsWith<IllegalArgumentException> { protocol.requestProof(proofRequest(emptyMap())) }

            val envelope = protocol.requestProof(proofRequest(mapOf("responseUri" to "https://v.example/resp", "state" to "st-1")))
            val data = envelope.messageData.jsonObject
            val sessionId = envelope.metadata["sessionId"]!!.jsonPrimitive.content
            assertEquals(sessionId, data["sessionId"]!!.jsonPrimitive.content)
            assertEquals("did:example:verifier", data["clientId"]!!.jsonPrimitive.content, "client id defaults to the verifier DID")
            assertEquals("st-1", data["state"]!!.jsonPrimitive.content)
            assertTrue(data["nonce"]!!.jsonPrimitive.content.isNotBlank())
            assertNotNull(service.getSession(sessionId))
        }

    @Test
    fun `each proof request without a nonce gets a fresh one`() =
        runBlocking<Unit> {
            val a = protocol.requestProof(proofRequest(mapOf("responseUri" to "https://v.example/resp")))
            val b = protocol.requestProof(proofRequest(mapOf("responseUri" to "https://v.example/resp")))
            assertNotEquals(a.messageData.jsonObject["nonce"], b.messageData.jsonObject["nonce"])
            assertEquals(
                "fixed",
                protocol
                    .requestProof(
                        proofRequest(mapOf("responseUri" to "u", "nonce" to "fixed")),
                    ).messageData.jsonObject["nonce"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `presenting against an unknown session fails and does not submit anything`() {
        val e =
            assertFailsWith<TrustWeaveException.InvalidOperation> {
                runBlocking { protocol.presentProof(presentRequest("no-such-session", mapOf("keyId" to "k"))) }
            }
        assertEquals("SESSION_NOT_FOUND", e.code)
    }

    @Test
    fun `presenting without a key id is refused`() {
        assertFailsWith<IllegalArgumentException> { runBlocking { protocol.presentProof(presentRequest("s", emptyMap())) } }
    }

    // ------------------------------------------------------------------ provider

    @Test
    fun `provider only serves its own protocol and needs a kms`() {
        val provider = SiopV2ExchangeProtocolProvider()
        assertNull(provider.create("eudiw", emptyMap()))
        assertFailsWith<IllegalArgumentException> { provider.create("siop-v2", emptyMap()) }
        assertTrue(provider.create("siop-v2", mapOf("kms" to InMemoryKeyManagementService())) is SiopV2ExchangeProtocol)
        assertEquals(listOf("siop-v2"), provider.supportedProtocols)
    }

    // ------------------------------------------------------------------ config + models

    @Test
    fun `config defaults are the conservative ones`() {
        val config = SiopV2Config()
        assertEquals(SiopClientIdScheme.DID, config.defaultClientIdScheme)
        assertEquals(listOf(SiopResponseMode.DIRECT_POST), config.supportedResponseModes)
        assertEquals(300, config.requestUriTimeoutSeconds)
        assertTrue("none" !in config.idTokenSigningAlgorithms && "none" !in config.vpTokenSigningAlgorithms)
    }

    @Test
    fun `authorization request uses the OpenID wire names and round-trips`() {
        val request =
            SiopV2AuthorizationRequest(
                responseType = "vp_token",
                clientId = "did:example:v",
                clientIdScheme = SiopClientIdScheme.REDIRECT_URI,
                responseUri = "https://v.example/resp",
                responseMode = SiopResponseMode.DIRECT_POST_JWT,
                nonce = "n-1",
            )
        val text = Json { encodeDefaults = true }.encodeToString(SiopV2AuthorizationRequest.serializer(), request)
        val obj = Json.parseToJsonElement(text).jsonObject
        assertEquals("redirect_uri", obj["client_id_scheme"]!!.jsonPrimitive.content)
        assertEquals("direct_post.jwt", obj["response_mode"]!!.jsonPrimitive.content)
        assertEquals("openid", obj["scope"]!!.jsonPrimitive.content)
        assertEquals("https://v.example/resp", obj["response_uri"]!!.jsonPrimitive.content)
        assertEquals(request, Json.decodeFromString(SiopV2AuthorizationRequest.serializer(), text))
    }

    @Test
    fun `an unknown response mode or client id scheme is refused when decoding`() {
        val base = """{"response_type":"vp_token","client_id":"c","nonce":"n","%s":"%s"}"""
        assertFailsWith<kotlinx.serialization.SerializationException> {
            Json.decodeFromString(SiopV2AuthorizationRequest.serializer(), base.format("response_mode", "implicit"))
        }
        assertFailsWith<kotlinx.serialization.SerializationException> {
            Json.decodeFromString(SiopV2AuthorizationRequest.serializer(), base.format("client_id_scheme", "anything"))
        }
        // nonce is mandatory
        assertFailsWith<kotlinx.serialization.SerializationException> {
            Json.decodeFromString(SiopV2AuthorizationRequest.serializer(), """{"response_type":"vp_token","client_id":"c"}""")
        }
    }

    @Test
    fun `authorization response omits absent fields and keeps error details`() {
        val ok =
            Json {
                encodeDefaults = false
            }.encodeToString(SiopV2AuthorizationResponse.serializer(), SiopV2AuthorizationResponse(vpToken = "t", state = "s"))
        assertEquals(setOf("vp_token", "state"), Json.parseToJsonElement(ok).jsonObject.keys)
        val err = Json.decodeFromString(SiopV2AuthorizationResponse.serializer(), """{"error":"access_denied","error_description":"no"}""")
        assertEquals("access_denied", err.error)
        assertEquals("no", err.errorDescription)
        assertNull(err.vpToken)
    }
}
