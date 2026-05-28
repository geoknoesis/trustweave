package org.trustweave.credential.siop

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SiopV2ServiceTest {

    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var service: SiopV2Service
    private lateinit var keyHandle: KeyHandle

    private val holderDid = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuias8sitwHwZbwfAX"
    private val verifierDid = "did:key:z6MkwXG2WjeQnNxSoynSGYU8V9j3QzP3JSqhdmkHc6SaE69X"
    private val responseUri = "https://verifier.example.com/response"

    private val keyId: String
        get() = keyHandle.id.value

    @BeforeTest
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        keyHandle = when (generateResult) {
            is GenerateKeyResult.Success -> generateResult.keyHandle
            else -> throw AssertionError("Failed to generate key: $generateResult")
        }
        service = SiopV2Service(
            kms = kms,
            httpClient = okhttp3.OkHttpClient(),
        )
    }

    @AfterTest
    fun tearDown() {
        // Nothing to tear down for in-memory service
    }

    @Test
    fun `createAuthorizationRequest returns session with correct fields`() = runBlocking {
        val session = service.createAuthorizationRequest(
            clientId = verifierDid,
            responseUri = responseUri,
            responseType = "vp_token",
        )

        assertNotNull(session.sessionId)
        assertTrue(session.sessionId.isNotBlank())
        assertEquals(verifierDid, session.request.clientId)
        assertEquals(responseUri, session.request.responseUri)
        assertEquals("vp_token", session.request.responseType)
        assertNotNull(session.request.nonce)
        assertTrue(session.request.nonce.isNotBlank())
    }

    @Test
    fun `createAuthorizationRequest stores session retrievable by sessionId`() = runBlocking {
        val session = service.createAuthorizationRequest(
            clientId = verifierDid,
            responseUri = responseUri,
        )

        val retrieved = service.getSession(session.sessionId)
        assertNotNull(retrieved)
        assertEquals(session.sessionId, retrieved.sessionId)
        assertEquals(session.request.clientId, retrieved.request.clientId)
    }

    @Test
    fun `getSession returns null for unknown sessionId`() {
        val result = service.getSession("non-existent-session-id")
        assertTrue(result == null)
    }

    @Test
    fun `buildAuthorizationResponse produces valid id_token JWT structure`() = runBlocking {
        val session = service.createAuthorizationRequest(
            clientId = verifierDid,
            responseUri = responseUri,
            responseType = "id_token",
        )

        val response = service.buildAuthorizationResponse(
            session = session,
            holderDid = holderDid,
            keyId = keyId,
        )

        val idToken = response.idToken
        assertNotNull(idToken)

        // A JWT has exactly 3 dot-separated parts
        val parts = idToken.split(".")
        assertEquals(3, parts.size, "ID Token should be a 3-part JWT")

        // Base64url-decode the header and verify typ=JWT
        val headerBytes = Base64.getUrlDecoder().decode(parts[0])
        val headerJson = Json { ignoreUnknownKeys = true }
            .parseToJsonElement(String(headerBytes))
            .jsonObject

        assertEquals("JWT", headerJson["typ"]?.jsonPrimitive?.content)
        assertEquals("EdDSA", headerJson["alg"]?.jsonPrimitive?.content)
        assertEquals(keyId, headerJson["kid"]?.jsonPrimitive?.content)

        // Base64url-decode the payload and verify iss/sub/nonce
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payloadJson = Json { ignoreUnknownKeys = true }
            .parseToJsonElement(String(payloadBytes))
            .jsonObject

        assertEquals(holderDid, payloadJson["iss"]?.jsonPrimitive?.content)
        assertEquals(holderDid, payloadJson["sub"]?.jsonPrimitive?.content)
        assertEquals(verifierDid, payloadJson["aud"]?.jsonPrimitive?.content)
        assertEquals(session.request.nonce, payloadJson["nonce"]?.jsonPrimitive?.content)
    }

    @Test
    fun `buildAuthorizationResponse with vp_token response type produces no id_token`() = runBlocking {
        val session = service.createAuthorizationRequest(
            clientId = verifierDid,
            responseUri = responseUri,
            responseType = "vp_token",
        )

        val response = service.buildAuthorizationResponse(
            session = session,
            holderDid = holderDid,
            keyId = keyId,
        )

        assertTrue(response.idToken == null, "id_token should be absent for vp_token response type")
        // vpToken is null because no presentation was supplied
        assertTrue(response.vpToken == null, "vp_token should be absent when no presentation is provided")
        assertEquals(session.request.state, response.state)
    }

    @Test
    fun `buildAuthorizationResponse preserves state from request`() = runBlocking {
        val expectedState = "test-state-xyz"
        val session = service.createAuthorizationRequest(
            clientId = verifierDid,
            nonce = "fixed-nonce",
            state = expectedState,
            responseUri = responseUri,
            responseType = "id_token",
        )

        val response = service.buildAuthorizationResponse(
            session = session,
            holderDid = holderDid,
            keyId = keyId,
        )

        assertEquals(expectedState, response.state)
    }
}
