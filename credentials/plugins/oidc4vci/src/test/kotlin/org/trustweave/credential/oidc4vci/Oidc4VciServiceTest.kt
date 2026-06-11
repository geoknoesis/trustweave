package org.trustweave.credential.oidc4vci

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.oidc4vci.exception.Oidc4VciException
import org.trustweave.credential.oidc4vci.models.TxCode
import org.trustweave.did.identifiers.Did
import org.trustweave.kms.Algorithm
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.*
import java.util.Base64

/**
 * Tests for OIDC4VCI Service: pre-authorized code flow, c_nonce threading,
 * EdDSA proof-of-possession JWTs, and credential_offer URI round-trips.
 */
class Oidc4VciServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var service: Oidc4VciService
    private lateinit var issuerUrl: String
    private val issuerDid = "did:key:issuer"
    private val holderDid = "did:key:holder"
    private val holderKeyId = "$holderDid#key-1"

    @BeforeTest
    fun setUp() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        issuerUrl = mockWebServer.url("").toString().trimEnd('/')

        kms = InMemoryKeyManagementService()
        // Register the holder key under the key ID the service derives from the holder DID
        kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to holderKeyId))

        service = Oidc4VciService(
            credentialIssuerUrl = issuerUrl,
            kms = kms,
            httpClient = okhttp3.OkHttpClient()
        )
    }

    @AfterTest
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ========== Pre-authorized code flow ==========

    @Test
    fun `pre-authorized code flow exchanges the code at the token endpoint`() = runBlocking {
        val offer = service.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = listOf("PersonCredential"),
            credentialIssuer = issuerUrl,
            grants = mapOf(
                Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE to mapOf("pre-authorized_code" to "code-123")
            ),
        )

        enqueueMetadata()
        enqueueTokenResponse(accessToken = "tok-1", cNonce = "nonce-abc")

        val request = service.createCredentialRequest(
            holderDid = holderDid,
            offerId = offer.offerId,
            txCodeValue = "1234",
        )

        assertEquals("tok-1", request.accessToken, "Pre-auth flow must yield an access token")

        // First recorded request: metadata fetch; second: token exchange
        mockWebServer.takeRequest()
        val tokenRequest = mockWebServer.takeRequest()
        assertEquals("POST", tokenRequest.method)
        val tokenBody = tokenRequest.body.readUtf8()
        assertTrue(
            tokenBody.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code"),
            "Token request must use the pre-authorized_code grant type, got: $tokenBody"
        )
        assertTrue(tokenBody.contains("pre-authorized_code=code-123"))
        assertTrue(tokenBody.contains("tx_code=1234"))
    }

    @Test
    fun `pre-authorized code flow without token leaves issueCredential failing with TokenExchangeFailed`() = runBlocking {
        // Offer without any grant — no token can be obtained
        val offer = service.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = listOf("PersonCredential"),
            credentialIssuer = issuerUrl,
        )

        enqueueMetadata()
        val request = service.createCredentialRequest(holderDid = holderDid, offerId = offer.offerId)
        assertNull(request.accessToken)

        assertFailsWith<Oidc4VciException.TokenExchangeFailed> {
            service.issueCredential(issuerDid, holderDid, createTestCredential(), request.requestId)
        }
    }

    // ========== c_nonce threading + EdDSA alg ==========

    @Test
    fun `proof of possession JWT uses token endpoint c_nonce, EdDSA alg and issuer audience`() = runBlocking {
        val offer = service.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = listOf("PersonCredential"),
            credentialIssuer = issuerUrl,
            grants = mapOf(
                Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE to mapOf("pre-authorized_code" to "code-123")
            ),
        )

        enqueueMetadata()
        enqueueTokenResponse(accessToken = "tok-1", cNonce = "nonce-abc")
        mockWebServer.enqueue(
            jsonResponse(buildJsonObject { put("credential", "issued-credential-jwt") }.toString())
        )

        val request = service.createCredentialRequest(holderDid = holderDid, offerId = offer.offerId)
        val result = service.issueCredential(issuerDid, holderDid, createTestCredential(), request.requestId)
        assertNotNull(result.credential)

        mockWebServer.takeRequest() // metadata
        mockWebServer.takeRequest() // token
        val credentialRequest = mockWebServer.takeRequest()
        assertEquals("Bearer tok-1", credentialRequest.getHeader("Authorization"))

        val requestJson = Json.parseToJsonElement(credentialRequest.body.readUtf8()).jsonObject
        val proofJwt = requestJson["proof"]!!.jsonObject["jwt"]!!.jsonPrimitive.content
        val parts = proofJwt.split(".")
        assertEquals(3, parts.size, "Proof must be a compact JWS")

        val header = decodeJwtPart(parts[0])
        assertEquals("EdDSA", header["alg"]!!.jsonPrimitive.content, "JOSE alg for Ed25519 keys is EdDSA")
        assertEquals("openid4vci-proof+jwt", header["typ"]!!.jsonPrimitive.content)
        assertEquals(holderKeyId, header["kid"]!!.jsonPrimitive.content)

        val payload = decodeJwtPart(parts[1])
        assertEquals("nonce-abc", payload["nonce"]!!.jsonPrimitive.content, "PoP nonce must be the token endpoint c_nonce")
        assertEquals(issuerUrl, payload["aud"]!!.jsonPrimitive.content, "PoP audience must be the credential issuer identifier")
        assertEquals(holderDid, payload["iss"]!!.jsonPrimitive.content)
    }

    @Test
    fun `credential endpoint invalid_proof error refreshes c_nonce and retries once`() = runBlocking {
        val offer = service.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = listOf("PersonCredential"),
            credentialIssuer = issuerUrl,
            grants = mapOf(
                Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE to mapOf("pre-authorized_code" to "code-123")
            ),
        )

        enqueueMetadata()
        enqueueTokenResponse(accessToken = "tok-1", cNonce = "n1")
        // First credential call fails with a fresh c_nonce
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(buildJsonObject {
                    put("error", "invalid_proof")
                    put("c_nonce", "n2")
                }.toString())
                .setHeader("Content-Type", "application/json")
        )
        // Retry succeeds
        mockWebServer.enqueue(
            jsonResponse(buildJsonObject { put("credential", "issued-credential-jwt") }.toString())
        )

        val request = service.createCredentialRequest(holderDid = holderDid, offerId = offer.offerId)
        val result = service.issueCredential(issuerDid, holderDid, createTestCredential(), request.requestId)
        assertNotNull(result.credential)

        mockWebServer.takeRequest() // metadata
        mockWebServer.takeRequest() // token
        val firstAttempt = extractProofNonce(mockWebServer.takeRequest().body.readUtf8())
        val secondAttempt = extractProofNonce(mockWebServer.takeRequest().body.readUtf8())
        assertEquals("n1", firstAttempt)
        assertEquals("n2", secondAttempt, "Retry must use the refreshed c_nonce from the error response")
    }

    @Test
    fun `second invalid_proof failure surfaces as CredentialRequestFailed not a raw RuntimeException`() = runBlocking {
        val offer = service.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = listOf("PersonCredential"),
            credentialIssuer = issuerUrl,
            grants = mapOf(
                Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE to mapOf("pre-authorized_code" to "code-123")
            ),
        )

        enqueueMetadata()
        enqueueTokenResponse(accessToken = "tok-1", cNonce = "n1")
        // Both the initial call AND the retry fail with invalid_proof + fresh c_nonce
        repeat(2) { attempt ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody(buildJsonObject {
                        put("error", "invalid_proof")
                        put("c_nonce", "n${attempt + 2}")
                    }.toString())
                    .setHeader("Content-Type", "application/json")
            )
        }

        val request = service.createCredentialRequest(holderDid = holderDid, offerId = offer.offerId)
        // Must be the public typed exception — the private FreshNonceRequired signal
        // must not escape issueCredential on the second failure.
        assertFailsWith<Oidc4VciException.CredentialRequestFailed> {
            service.issueCredential(issuerDid, holderDid, createTestCredential(), request.requestId)
        }
        assertEquals(4, mockWebServer.requestCount, "Exactly one retry: metadata + token + 2 credential calls")
    }

    @Test
    fun `non invalid_proof error with stray c_nonce does not trigger a retry`() = runBlocking {
        val offer = service.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = listOf("PersonCredential"),
            credentialIssuer = issuerUrl,
            grants = mapOf(
                Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE to mapOf("pre-authorized_code" to "code-123")
            ),
        )

        enqueueMetadata()
        enqueueTokenResponse(accessToken = "tok-1", cNonce = "n1")
        // HTTP 500 whose body happens to contain a c_nonce — must NOT be treated as
        // an invalid_proof retry trigger.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody(buildJsonObject {
                    put("error", "server_error")
                    put("c_nonce", "stray-nonce")
                }.toString())
                .setHeader("Content-Type", "application/json")
        )

        val request = service.createCredentialRequest(holderDid = holderDid, offerId = offer.offerId)
        assertFailsWith<Oidc4VciException.CredentialRequestFailed> {
            service.issueCredential(issuerDid, holderDid, createTestCredential(), request.requestId)
        }
        assertEquals(3, mockWebServer.requestCount, "No retry: metadata + token + a single credential call")
    }

    // ========== credential_offer URI round-trip ==========

    @Test
    fun `credential offer URI uses a single credential_offer query parameter`() = runBlocking {
        val offer = service.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = listOf("PersonCredential", "EducationCredential"),
            credentialIssuer = issuerUrl,
        )

        assertTrue(
            offer.offerUri.startsWith("openid-credential-offer://?credential_offer="),
            "Offer must be carried in a single credential_offer parameter, got: ${offer.offerUri}"
        )
        assertFalse(offer.offerUri.contains("credential_issuer="), "Raw top-level params are not spec-compliant")
        assertFalse(offer.offerUri.contains("credential_configuration_ids="))
    }

    @Test
    fun `credential offer URI round-trips through parseCredentialOfferUri`() = runBlocking {
        val txCode = TxCode(inputMode = "numeric", length = 4, description = "PIN from SMS")
        val offer = service.createCredentialOffer(
            issuerDid = issuerDid,
            credentialTypes = listOf("PersonCredential"),
            credentialIssuer = issuerUrl,
            grants = mapOf(
                Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE to mapOf("pre-authorized_code" to "code-xyz")
            ),
            txCode = txCode,
        )

        val parsed = service.parseCredentialOfferUri(offer.offerUri)

        assertEquals(issuerUrl, parsed.credentialIssuer)
        assertEquals(listOf("PersonCredential"), parsed.credentialTypes)
        assertEquals(txCode, parsed.txCode)

        @Suppress("UNCHECKED_CAST")
        val preAuthGrant = parsed.grants[Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE] as? Map<String, Any?>
        assertNotNull(preAuthGrant, "Pre-authorized grant must survive the round-trip")
        assertEquals("code-xyz", preAuthGrant["pre-authorized_code"])
    }

    @Test
    fun `parseCredentialOfferUri rejects URIs without credential_offer`() = runBlocking {
        assertFailsWith<Oidc4VciException.OfferParseFailed> {
            service.parseCredentialOfferUri("openid-credential-offer://?credential_issuer=$issuerUrl")
        }
    }

    // ========== credential_offer_uri https enforcement ==========

    @Test
    fun `credential_offer_uri over http to a non-loopback host is rejected without any request`() = runBlocking {
        val before = mockWebServer.requestCount
        assertFailsWith<Oidc4VciException.OfferParseFailed> {
            service.parseCredentialOfferUri(
                "openid-credential-offer://?credential_offer_uri=http://169.254.169.254/x"
            )
        }
        assertEquals(before, mockWebServer.requestCount, "Rejected URIs must never be fetched")
    }

    @Test
    fun `credential_offer_uri with absurd scheme is rejected as OfferParseFailed`() = runBlocking {
        assertFailsWith<Oidc4VciException.OfferParseFailed> {
            service.parseCredentialOfferUri(
                "openid-credential-offer://?credential_offer_uri=ftp://issuer.example.com/offer"
            )
        }
    }

    @Test
    fun `credential_offer_uri over http to localhost is allowed`() = runBlocking {
        val offerJson = buildJsonObject {
            put("credential_issuer", issuerUrl)
            put("credential_configuration_ids", JsonArray(listOf(JsonPrimitive("PersonCredential"))))
        }
        mockWebServer.enqueue(jsonResponse(offerJson.toString()))

        // MockWebServer serves over http on a loopback host
        val byReferenceUri = "$issuerUrl/offer"
        val parsed = service.parseCredentialOfferUri(
            "openid-credential-offer://?credential_offer_uri=$byReferenceUri"
        )

        assertEquals(issuerUrl, parsed.credentialIssuer)
        assertEquals(listOf("PersonCredential"), parsed.credentialTypes)
    }

    // ========== Helpers ==========

    private fun enqueueMetadata() {
        val metadataJson = buildJsonObject {
            put("credential_issuer", issuerUrl)
            put("credential_endpoint", "$issuerUrl/credential")
            put("token_endpoint", "$issuerUrl/token")
        }
        mockWebServer.enqueue(jsonResponse(metadataJson.toString()))
    }

    private fun enqueueTokenResponse(accessToken: String, cNonce: String?) {
        val tokenJson = buildJsonObject {
            put("access_token", accessToken)
            put("token_type", "Bearer")
            cNonce?.let { put("c_nonce", it) }
        }
        mockWebServer.enqueue(jsonResponse(tokenJson.toString()))
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .setHeader("Content-Type", "application/json")

    private fun decodeJwtPart(part: String): JsonObject {
        val decoded = String(Base64.getUrlDecoder().decode(part), Charsets.UTF_8)
        return Json.parseToJsonElement(decoded).jsonObject
    }

    private fun extractProofNonce(credentialRequestBody: String): String? {
        val requestJson = Json.parseToJsonElement(credentialRequestBody).jsonObject
        val proofJwt = requestJson["proof"]!!.jsonObject["jwt"]!!.jsonPrimitive.content
        return decodeJwtPart(proofJwt.split(".")[1])["nonce"]?.jsonPrimitive?.contentOrNull
    }

    private fun createTestCredential(): VerifiableCredential {
        val issuer = Did(issuerDid)
        val subject = Did(holderDid)
        return VerifiableCredential(
            id = CredentialId("https://example.com/credential/1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Person),
            issuer = Issuer.fromDid(issuer),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromDid(
                did = subject,
                claims = mapOf("name" to JsonPrimitive("Test User"))
            )
        )
    }
}
