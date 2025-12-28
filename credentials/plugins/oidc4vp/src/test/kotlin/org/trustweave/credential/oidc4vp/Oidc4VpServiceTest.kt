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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.*
import java.util.*

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

    // ========== Helper Methods ==========

    private fun createTestPermissionRequest(): org.trustweave.credential.oidc4vp.models.PermissionRequest {
        val authRequest = org.trustweave.credential.oidc4vp.models.AuthorizationRequest(
            responseUri = "${mockWebServer.url("/response")}",
            clientId = "test-client",
            requestUri = "${mockWebServer.url("/request")}",
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

    private fun createTestCredential(): VerifiableCredential {
        val issuerDid = Did("did:key:issuer")
        val subjectDid = Did(holderDid)
        return VerifiableCredential(
            id = CredentialId("https://example.com/credential/1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Person),
            issuer = Issuer.fromDid(issuerDid),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromDid(
                did = subjectDid,
                claims = buildJsonObject {
                    put("name", "Test User")
                }.entries.associate { it.key to it.value }
            )
        )
    }
}


