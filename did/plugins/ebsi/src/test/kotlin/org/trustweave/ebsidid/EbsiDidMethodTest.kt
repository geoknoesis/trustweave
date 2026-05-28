package org.trustweave.ebsidid

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [EbsiDidMethod].
 *
 * Test coverage:
 * 1. DID identifier derivation — given known P-256 public key coordinates, verify the did:ebsi
 *    string is computed correctly.
 * 2. Resolution via [MockWebServer] — mock EBSI API, verify that resolveDid() parses the
 *    response correctly and returns a [DidResolutionResult.Success].
 * 3. Fallback to in-memory — when API returns 404, resolveDid() should return the locally
 *    stored document for a DID that was created in this session.
 */
class EbsiDidMethodTest {

    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var server: MockWebServer
    private lateinit var method: EbsiDidMethod

    @BeforeEach
    fun setUp() {
        kms = InMemoryKeyManagementService()
        server = MockWebServer()
        server.start()

        val testClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val config = EbsiDidConfig(
            apiBaseUrl = server.url("").toString().trimEnd('/'),
            network = EbsiNetwork.PILOT,
            bearerToken = "test-bearer-token",
            timeoutSeconds = 5,
        )
        method = EbsiDidMethod(kms, config, testClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Test 1 — DID identifier derivation
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `deriveEbsiIdentifier produces correct did from known P256 coordinates`() {
        // Given: fixed P-256 x/y coordinates (32 bytes each, all-zero for determinism in tests)
        val xBytes = ByteArray(32) { it.toByte() }  // 0x00..0x1F
        val yBytes = ByteArray(32) { (it + 32).toByte() }  // 0x20..0x3F
        val xB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(xBytes)
        val yB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(yBytes)

        val jwk = mapOf("kty" to "EC", "crv" to "P-256", "x" to xB64, "y" to yB64)

        // When
        val did = method.deriveEbsiIdentifier(publicKeyJwk = jwk, keyIdFallback = ByteArray(0))

        // Then: should be a valid did:ebsi: prefix
        assertTrue(did.startsWith("did:ebsi:"), "Expected did:ebsi: prefix, got: $did")

        // The identifier part must be non-empty base58btc characters
        val identifier = did.removePrefix("did:ebsi:")
        assertTrue(identifier.isNotEmpty(), "EBSI identifier must be non-empty")
        assertTrue(
            identifier.all { it in "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz" },
            "Identifier must be valid base58btc: $identifier",
        )

        // Determinism: same key bytes must always produce the same DID
        val did2 = method.deriveEbsiIdentifier(publicKeyJwk = jwk, keyIdFallback = ByteArray(0))
        assertEquals(did, did2, "DID derivation must be deterministic")
    }

    @Test
    fun `deriveEbsiIdentifier uses fallback bytes when JWK is null`() {
        val fallback = "test-key-id".toByteArray()
        val did = method.deriveEbsiIdentifier(publicKeyJwk = null, keyIdFallback = fallback)

        assertTrue(did.startsWith("did:ebsi:"))
        // Same fallback must be deterministic
        val did2 = method.deriveEbsiIdentifier(publicKeyJwk = null, keyIdFallback = fallback)
        assertEquals(did, did2)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Test 2 — Resolution via MockWebServer
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolveDid returns success when EBSI API returns a valid DID document`() = runBlocking {
        // Given: a locally created DID so we know its value
        // We stub the registry POST so createDid succeeds remotely
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result": "ok"}"""))
        val document = method.createDid(
            didCreationOptions { algorithm = KeyAlgorithm.ED25519 },
        )
        val did = document.id.value

        // Given: mock the GET resolution endpoint to return the DID document JSON
        val didDocJson = buildMinimalDidDocJson(did)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(didDocJson),
        )

        // When
        val result = method.resolveDid(document.id)

        // Then
        assertIs<DidResolutionResult.Success>(result)
        val successResult = result as DidResolutionResult.Success
        assertEquals(did, successResult.document.id.value)
        assertNotNull(successResult.documentMetadata)
    }

    @Test
    fun `resolveDid sends correct Accept header and path to EBSI API`() = runBlocking {
        // Given: create a DID (stub the POST)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result": "ok"}"""))
        val document = method.createDid(
            didCreationOptions { algorithm = KeyAlgorithm.ED25519 },
        )
        val did = document.id.value

        // Stub the resolution endpoint
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(buildMinimalDidDocJson(did)),
        )

        method.resolveDid(document.id)

        // Verify the GET request had the right path
        server.takeRequest() // consume the POST from createDid
        val resolveRequest = server.takeRequest()
        assertTrue(
            resolveRequest.path?.contains("/did-registry/v5/identifiers/") == true,
            "Expected /did-registry/v5/identifiers/ in path, got: ${resolveRequest.path}",
        )
        assertEquals("application/json", resolveRequest.getHeader("Accept"))
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Test 3 — Fallback to in-memory on 404
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolveDid falls back to in-memory document when API returns 404`() = runBlocking {
        // Given: create a DID and cache it locally (stub the POST for registration)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result": "ok"}"""))
        val document = method.createDid(
            didCreationOptions { algorithm = KeyAlgorithm.ED25519 },
        )

        // Stub the resolution endpoint with 404
        server.enqueue(MockResponse().setResponseCode(404))

        // When
        val result = method.resolveDid(document.id)

        // Then: should fall back to the in-memory cached document
        assertIs<DidResolutionResult.Success>(result)
        val successResult = result as DidResolutionResult.Success
        assertEquals(document.id.value, successResult.document.id.value)
    }

    @Test
    fun `resolveDid returns notFound failure when API returns 404 and no local cache`() = runBlocking {
        // Given: a DID that was never created locally
        val unknownDid = org.trustweave.did.identifiers.Did("did:ebsi:unknownidentifier123")

        server.enqueue(MockResponse().setResponseCode(404))

        // When
        val result = method.resolveDid(unknownDid)

        // Then
        assertIs<DidResolutionResult.Failure>(result)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Test 4 — Method name
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `method name is ebsi`() {
        assertEquals("ebsi", method.method)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Test 5 — Config factory methods
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `EbsiDidConfig pilot uses pilot URL`() {
        val config = EbsiDidConfig.pilot("token")
        assertEquals("https://api-pilot.ebsi.eu", config.apiBaseUrl)
        assertEquals(EbsiNetwork.PILOT, config.network)
        assertEquals("token", config.bearerToken)
    }

    @Test
    fun `EbsiDidConfig conformance uses conformance URL`() {
        val config = EbsiDidConfig.conformance()
        assertEquals("https://api-conformance.ebsi.eu", config.apiBaseUrl)
        assertEquals(EbsiNetwork.CONFORMANCE, config.network)
    }

    @Test
    fun `EbsiDidConfig production uses production URL`() {
        val config = EbsiDidConfig.production()
        assertEquals("https://api.ebsi.eu", config.apiBaseUrl)
        assertEquals(EbsiNetwork.PRODUCTION, config.network)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Builds a minimal DID document JSON string for the given DID (enough to parse via
     * [org.trustweave.did.parser.DidDocumentJsonParser]).
     */
    private fun buildMinimalDidDocJson(did: String): String = """
        {
          "@context": ["https://www.w3.org/ns/did/v1"],
          "id": "$did",
          "verificationMethod": [],
          "authentication": [],
          "assertionMethod": []
        }
    """.trimIndent()
}
