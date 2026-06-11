package org.trustweave.credential.oidc4vp

import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException
import org.trustweave.credential.oidc4vp.models.PresentableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.*
import java.util.*
import java.util.Base64

/**
 * Comprehensive tests for OIDC4VP Service.
 */
class Oidc4VpServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var service: Oidc4VpService
    private lateinit var keyHandle: KeyHandle
    private val holderDid = "did:key:holder"
    
    private val keyId: String
        get() = keyHandle.id.value // Use the actual key ID from generated key

    @BeforeTest
    fun setUp() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        kms = InMemoryKeyManagementService()
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        keyHandle = when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
            else -> throw AssertionError("Failed to generate key: $generateResult")
        }
        
        service = Oidc4VpService(
            kms = kms,
            httpClient = okhttp3.OkHttpClient()
        )
    }

    @AfterTest
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ========== parseAuthorizationUrl Tests ==========

    @Test
    fun `test parseAuthorizationUrl with valid openid4vp URL`() = runBlocking {
        val requestUri = "${mockWebServer.url("/request")}"
        val responseUri = "${mockWebServer.url("/response")}"
        
        // Mock authorization request response
        val authRequestJson = buildJsonObject {
            put("response_uri", responseUri)
            put("client_id", "test-client")
            put("nonce", "test-nonce-123")
            put("state", "test-state")
        }
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(authRequestJson.toString())
                .setHeader("Content-Type", "application/json")
        )
        
        val authorizationUrl = "openid4vp://authorize?client_id=test-client&request_uri=$requestUri"
        
        val permissionRequest = service.parseAuthorizationUrl(authorizationUrl)
        
        assertNotNull(permissionRequest)
        assertEquals(responseUri, permissionRequest.authorizationRequest.responseUri)
        assertEquals("test-client", permissionRequest.authorizationRequest.clientId)
        assertEquals("test-nonce-123", permissionRequest.authorizationRequest.nonce)
        assertEquals("test-state", permissionRequest.authorizationRequest.state)
    }

    @Test
    fun `test parseAuthorizationUrl with missing request_uri throws exception`() = runBlocking {
        val authorizationUrl = "openid4vp://authorize?client_id=test-client"
        
        assertFailsWith<Oidc4VpException.UrlParseFailed> {
            service.parseAuthorizationUrl(authorizationUrl)
        }
    }

    @Test
    fun `test parseAuthorizationUrl with invalid URL format throws exception`() = runBlocking {
        val invalidUrl = "not-a-valid-url"
        
        assertFailsWith<Oidc4VpException.UrlParseFailed> {
            service.parseAuthorizationUrl(invalidUrl)
        }
    }

    @Test
    fun `test parseAuthorizationUrl with HTTP error response throws exception`() = runBlocking {
        val requestUri = "${mockWebServer.url("/request")}"
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )
        
        val authorizationUrl = "openid4vp://authorize?request_uri=$requestUri"
        
        assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(authorizationUrl)
        }
    }

    // ========== createPermissionResponse Tests ==========

    @Test
    fun `test createPermissionResponse with valid credentials`() = runBlocking {
        // Create a test credential
        val credential = createTestCredential()
        val presentableCredential = PresentableCredential(
            credentialId = "cred-1",
            credential = credential,
            credentialType = "PersonCredential"
        )
        
        val permissionRequest = createTestPermissionRequest()
        
        val permissionResponse = service.createPermissionResponse(
            permissionRequest = permissionRequest,
            selectedCredentials = listOf(presentableCredential),
            holderDid = holderDid,
            keyId = keyId
        )
        
        assertNotNull(permissionResponse)
        assertEquals(permissionRequest.requestId, permissionResponse.requestId)
        assertNotNull(permissionResponse.vpToken)
        assertTrue(permissionResponse.vpToken.isNotEmpty())
        
        // Verify VP token is a valid JWT (has 3 parts separated by dots)
        val parts = permissionResponse.vpToken.split(".")
        assertEquals(3, parts.size, "VP token should be a valid JWT with 3 parts")
    }

    // ========== submitPermissionResponse Tests ==========

    @Test
    fun `test submitPermissionResponse with valid response`() = runBlocking {
        // First, parse an authorization URL which stores the permission request
        val requestUri = "${mockWebServer.url("/request")}"
        val responseUri = "${mockWebServer.url("/response")}"
        
        // Mock authorization request response
        val authRequestJson = buildJsonObject {
            put("response_uri", responseUri)
            put("client_id", "test-client")
            put("nonce", "test-nonce-123")
            put("state", "test-state")
        }
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(authRequestJson.toString())
                .setHeader("Content-Type", "application/json")
        )
        
        val authorizationUrl = "openid4vp://authorize?client_id=test-client&request_uri=$requestUri"
        val permissionRequest = service.parseAuthorizationUrl(authorizationUrl)
        
        // Create permission response
        val permissionResponse = service.createPermissionResponse(
            permissionRequest = permissionRequest,
            selectedCredentials = listOf(
                PresentableCredential(
                    credentialId = "cred-1",
                    credential = createTestCredential(),
                    credentialType = "PersonCredential"
                )
            ),
            holderDid = holderDid,
            keyId = keyId
        )
        
        // Mock successful response from verifier
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"success\"}")
                .setHeader("Content-Type", "application/json")
        )
        
        // Submit the response (should not throw)
        service.submitPermissionResponse(permissionResponse)
    }

    // ========== fetchVerifierMetadata Tests ==========

    @Test
    fun `test fetchVerifierMetadata with valid verifier`() = runBlocking {
        val verifierUrl = "${mockWebServer.url("")}"
        
        val metadataJson = buildJsonObject {
            put("credential_verifier", verifierUrl)
            put("authorization_endpoint", "$verifierUrl/authorize")
            put("response_types_supported", JsonArray(listOf(JsonPrimitive("vp_token"))))
        }
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(metadataJson.toString())
                .setHeader("Content-Type", "application/json")
        )
        
        val metadata = service.fetchVerifierMetadata(verifierUrl)
        
        assertNotNull(metadata)
        assertEquals(verifierUrl, metadata.credentialVerifier)
        assertTrue(metadata.responseTypesSupported.contains("vp_token"))
    }

    @Test
    fun `test fetchVerifierMetadata with HTTP error throws exception`() = runBlocking {
        val verifierUrl = "${mockWebServer.url("")}"
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )
        
        assertFailsWith<Oidc4VpException.MetadataFetchFailed> {
            service.fetchVerifierMetadata(verifierUrl)
        }
    }

    // ========== vp_token content Tests ==========

    @Test
    fun `vp_token embeds real credential JSON with request audience and EdDSA alg`() = runBlocking {
        val credential = createTestCredential()
        val permissionRequest = createTestPermissionRequest()

        val permissionResponse = service.createPermissionResponse(
            permissionRequest = permissionRequest,
            selectedCredentials = listOf(
                PresentableCredential("cred-1", credential, "PersonCredential")
            ),
            holderDid = holderDid,
            keyId = keyId
        )

        val parts = permissionResponse.vpToken.split(".")
        assertEquals(3, parts.size)

        val header = decodeJwtPart(parts[0])
        assertEquals("EdDSA", header["alg"]!!.jsonPrimitive.content, "JOSE alg for Ed25519 keys is EdDSA")

        val payload = decodeJwtPart(parts[1])
        assertEquals("test-client", payload["aud"]!!.jsonPrimitive.content, "aud must be the request client_id")
        assertEquals("test-nonce-123", payload["nonce"]!!.jsonPrimitive.content)
        assertEquals(holderDid, payload["iss"]!!.jsonPrimitive.content)

        val vp = payload["vp"]!!.jsonObject
        assertTrue(
            vp["@context"]!!.jsonArray.map { it.jsonPrimitive.content }
                .contains("https://www.w3.org/2018/credentials/v1")
        )
        assertTrue(
            vp["type"]!!.jsonArray.map { it.jsonPrimitive.content }.contains("VerifiablePresentation")
        )
        assertEquals(holderDid, vp["holder"]!!.jsonPrimitive.content)

        val vcArray = vp["verifiableCredential"]!!.jsonArray
        assertEquals(1, vcArray.size)
        val vcJson = vcArray[0].jsonObject
        assertFalse(
            vcJson.toString().contains("VerifiableCredential("),
            "Credential must be serialized as JSON, not Kotlin data-class toString"
        )
        assertTrue(
            vcJson["type"]!!.jsonArray.map { it.jsonPrimitive.content }.contains("VerifiableCredential")
        )

        // Verifier-consumable W3C shape (must match PresentationDefinitionMatcher.vcToDocument):
        // issuer is a plain IRI string, not a polymorphic object with a class discriminator
        assertEquals(
            "did:key:issuer",
            vcJson["issuer"]!!.jsonPrimitive.content,
            "issuer must be a plain IRI string"
        )
        // credentialSubject carries flattened claims next to id — no internal "claims" wrapper
        val subject = vcJson["credentialSubject"]!!.jsonObject
        assertFalse(subject.containsKey("claims"), "claims must be flattened, not nested under 'claims'")
        assertEquals(holderDid, subject["id"]!!.jsonPrimitive.content)
        assertEquals(
            "Test User",
            subject["name"]!!.jsonPrimitive.content,
            "Claims must be directly addressable at \$.credentialSubject.name"
        )
        // No kotlinx class discriminators / FQCNs may leak into the vp_token
        assertFalse(vcJson.toString().contains("@type"), "No '@type' class discriminator may leak")
        assertFalse(vcJson.toString().contains("org.trustweave"), "No FQCN may leak into the vp_token")
    }

    @Test
    fun `vp_token aud falls back to response endpoint when client_id is absent`() = runBlocking {
        val responseUri = "${mockWebServer.url("/response")}"
        val authRequest = org.trustweave.credential.oidc4vp.models.AuthorizationRequest(
            responseUri = responseUri,
            nonce = "test-nonce-123"
        )
        val permissionRequest = org.trustweave.credential.oidc4vp.models.PermissionRequest(
            requestId = UUID.randomUUID().toString(),
            authorizationRequest = authRequest
        )

        val permissionResponse = service.createPermissionResponse(
            permissionRequest = permissionRequest,
            selectedCredentials = listOf(
                PresentableCredential("cred-1", createTestCredential(), "PersonCredential")
            ),
            holderDid = holderDid,
            keyId = keyId
        )

        val payload = decodeJwtPart(permissionResponse.vpToken.split(".")[1])
        assertEquals(responseUri, payload["aud"]!!.jsonPrimitive.content)
    }

    // ========== presentation_submission Tests ==========

    @Test
    fun `presentation_submission descriptor paths align with vp_token credential order`() = runBlocking {
        val presentationDefinition = Json.parseToJsonElement(
            """
            {
              "id": "pd-1",
              "input_descriptors": [
                {
                  "id": "desc-name",
                  "constraints": { "fields": [ { "path": ["$.credentialSubject.name"] } ] }
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val credentialWithoutName = createTestCredential(
            id = "https://example.com/credential/no-name",
            claims = mapOf("email" to JsonPrimitive("user@example.com"))
        )
        val credentialWithName = createTestCredential(
            id = "https://example.com/credential/with-name",
            claims = mapOf("name" to JsonPrimitive("Test User"))
        )

        val permissionRequest = createTestPermissionRequest(presentationDefinition)

        val permissionResponse = service.createPermissionResponse(
            permissionRequest = permissionRequest,
            selectedCredentials = listOf(
                PresentableCredential("cred-0", credentialWithoutName, "PersonCredential"),
                PresentableCredential("cred-1", credentialWithName, "PersonCredential"),
            ),
            holderDid = holderDid,
            keyId = keyId
        )

        val submission = permissionResponse.presentationSubmission
        assertNotNull(submission, "presentation_submission must be populated from the presentation definition")
        assertEquals("pd-1", submission["definition_id"]!!.jsonPrimitive.content)

        val descriptorMap = submission["descriptor_map"]!!.jsonArray
        assertEquals(1, descriptorMap.size)
        val descriptor = descriptorMap[0].jsonObject
        assertEquals("desc-name", descriptor["id"]!!.jsonPrimitive.content)
        assertEquals("ldp_vc", descriptor["format"]!!.jsonPrimitive.content)
        assertEquals(
            "$.verifiableCredential[1]",
            descriptor["path"]!!.jsonPrimitive.content,
            "Path index must match the matching credential's position in the vp_token array"
        )

        // Cross-check: the credential at that index in the vp_token actually carries the matched claim
        val payload = decodeJwtPart(permissionResponse.vpToken.split(".")[1])
        val vcArray = payload["vp"]!!.jsonObject["verifiableCredential"]!!.jsonArray
        assertEquals(2, vcArray.size)
        assertTrue(vcArray[1].jsonObject.toString().contains("Test User"))
        assertFalse(vcArray[0].jsonObject.toString().contains("Test User"))
        // The matched JSONPath ($.credentialSubject.name) must resolve against the embedded
        // credential exactly as it did against the matcher's flattened document
        assertEquals(
            "Test User",
            vcArray[1].jsonObject["credentialSubject"]!!.jsonObject["name"]!!.jsonPrimitive.content,
            "Embedded credential must satisfy the field path it was matched on"
        )
    }

    @Test
    fun `presentation_submission is null without a presentation definition`() = runBlocking {
        val permissionResponse = service.createPermissionResponse(
            permissionRequest = createTestPermissionRequest(),
            selectedCredentials = listOf(
                PresentableCredential("cred-1", createTestCredential(), "PersonCredential")
            ),
            holderDid = holderDid,
            keyId = keyId
        )
        assertNull(permissionResponse.presentationSubmission)
    }

    // ========== Request object Tests ==========

    @Test
    fun `signed request object claims take precedence and URL params do not override them`() = runBlocking {
        val ecKey = ECKeyGenerator(Curve.P_256).keyID("verifier-key").generate()
        val responseUri = "${mockWebServer.url("/response")}"

        val requestObject = signedRequestObject(
            signingKey = ecKey,
            advertisedKey = ecKey,
            responseUri = responseUri
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(requestObject)
                .setHeader("Content-Type", "application/oauth-authz-req+jwt")
        )

        val requestUri = "${mockWebServer.url("/request")}"
        val authorizationUrl =
            "openid4vp://authorize?client_id=url-client&nonce=url-nonce&state=url-state&request_uri=$requestUri"

        val permissionRequest = service.parseAuthorizationUrl(authorizationUrl)

        assertEquals("jwt-client", permissionRequest.authorizationRequest.clientId)
        assertEquals("jwt-nonce", permissionRequest.authorizationRequest.nonce)
        assertEquals("jwt-state", permissionRequest.authorizationRequest.state)
        assertEquals(responseUri, permissionRequest.authorizationRequest.responseUri)
    }

    @Test
    fun `unsigned request object with alg none is rejected`() = runBlocking {
        val claims = JWTClaimsSet.Builder()
            .claim("client_id", "jwt-client")
            .claim("response_uri", "${mockWebServer.url("/response")}")
            .build()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(PlainJWT(claims).serialize())
                .setHeader("Content-Type", "application/oauth-authz-req+jwt")
        )

        val requestUri = "${mockWebServer.url("/request")}"
        val authorizationUrl = "openid4vp://authorize?request_uri=$requestUri"

        assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(authorizationUrl)
        }
    }

    @Test
    fun `request object signed with key not matching client_metadata jwks is rejected`() = runBlocking {
        val signingKey = ECKeyGenerator(Curve.P_256).keyID("verifier-key").generate()
        val otherKey = ECKeyGenerator(Curve.P_256).keyID("verifier-key").generate()

        val requestObject = signedRequestObject(
            signingKey = signingKey,
            advertisedKey = otherKey, // jwks advertises a different key
            responseUri = "${mockWebServer.url("/response")}"
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(requestObject)
                .setHeader("Content-Type", "application/oauth-authz-req+jwt")
        )

        val requestUri = "${mockWebServer.url("/request")}"
        val authorizationUrl = "openid4vp://authorize?request_uri=$requestUri"

        assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
            service.parseAuthorizationUrl(authorizationUrl)
        }
    }

    // ========== Helper Methods ==========

    private fun signedRequestObject(
        signingKey: ECKey,
        advertisedKey: ECKey,
        responseUri: String,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .claim("client_id", "jwt-client")
            .claim("nonce", "jwt-nonce")
            .claim("state", "jwt-state")
            .claim("response_uri", responseUri)
            .claim("response_mode", "direct_post")
            .claim(
                "client_metadata",
                mapOf("jwks" to mapOf("keys" to listOf(advertisedKey.toPublicJWK().toJSONObject())))
            )
            .build()

        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.keyID).build(),
            claims
        )
        jwt.sign(ECDSASigner(signingKey))
        return jwt.serialize()
    }

    private fun decodeJwtPart(part: String): JsonObject {
        val decoded = String(Base64.getUrlDecoder().decode(part), Charsets.UTF_8)
        return Json.parseToJsonElement(decoded).jsonObject
    }

    private fun createTestPermissionRequest(
        presentationDefinition: JsonObject? = null,
    ): org.trustweave.credential.oidc4vp.models.PermissionRequest {
        val authRequest = org.trustweave.credential.oidc4vp.models.AuthorizationRequest(
            responseUri = "${mockWebServer.url("/response")}",
            clientId = "test-client",
            requestUri = "${mockWebServer.url("/request")}",
            presentationDefinition = presentationDefinition,
            nonce = "test-nonce-123",
            state = "test-state"
        )

        return org.trustweave.credential.oidc4vp.models.PermissionRequest(
            requestId = UUID.randomUUID().toString(),
            authorizationRequest = authRequest,
            verifierUrl = "${mockWebServer.url("")}",
            requestedCredentialTypes = listOf("PersonCredential"),
            requestedClaims = emptyMap()
        )
    }

    private fun createTestCredential(
        id: String = "https://example.com/credential/1",
        claims: Map<String, kotlinx.serialization.json.JsonElement> = mapOf("name" to JsonPrimitive("Test User")),
    ): VerifiableCredential {
        val issuerDid = Did("did:key:issuer")
        val subjectDid = Did(holderDid)
        return VerifiableCredential(
            id = CredentialId(id),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Person),
            issuer = Issuer.fromDid(issuerDid),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromDid(
                did = subjectDid,
                claims = claims
            )
        )
    }
}


