package org.trustweave.did.orb

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [OrbDidMethod] using [MockWebServer] to simulate an Orb node.
 *
 * Coverage:
 *  1. createDid: builds a Sidetree create request and POSTs it to /sidetree/v1/operations.
 *     The DID returned is a long-form did:orb DID.
 *  2. resolveDid: GETs /sidetree/v1/identifiers/{did} and parses the DID document.
 *  3. resolveDid falls back to the in-memory cache when Orb returns 404 for a DID
 *     created in this session.
 *  4. resolveDid returns notFound when there is no cache.
 *  5. updateDid builds a Sidetree update operation and POSTs it.
 *  6. deactivateDid builds a Sidetree deactivate operation and POSTs it.
 *  7. Method name is "orb".
 */
class OrbDidMethodTest {

    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var server: MockWebServer
    private lateinit var method: OrbDidMethod

    @BeforeEach
    fun setUp() {
        kms = InMemoryKeyManagementService()
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        val config = OrbDidConfig(
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 5,
        )
        method = OrbDidMethod(kms, config, client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1 — createDid
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `createDid posts a sidetree create operation and returns a did orb DID`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"queued"}"""))

        val document = method.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })

        // The DID must use the did:orb namespace
        assertTrue(
            document.id.value.startsWith("did:orb:"),
            "Expected did:orb prefix, got ${document.id.value}",
        )
        // At least one verification method is present
        assertTrue(document.verificationMethod.isNotEmpty())

        // The POST request must hit /sidetree/v1/operations with a create body
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sidetree/v1/operations", request.path)
        assertEquals("application/json", request.getHeader("Content-Type"))
        val bodyJson = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("create", bodyJson["type"]?.jsonPrimitive?.content)
        assertNotNull(bodyJson["suffixData"])
        assertNotNull(bodyJson["delta"])
    }

    @Test
    fun `createDid uses the DID returned by the Orb node when present`() = runBlocking {
        val canonicalDid = "did:orb:Eiabc123canonicalsuffix"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"didDocument":{"id":"$canonicalDid"}}""",
            ),
        )

        val document = method.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })

        assertEquals(canonicalDid, document.id.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2 — resolveDid (200 OK)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolveDid parses a successful Orb resolution result`() = runBlocking {
        val did = "did:orb:EiTest"
        val body = """
            {
              "didDocument": {
                "@context": ["https://www.w3.org/ns/did/v1"],
                "id": "$did",
                "verificationMethod": [],
                "authentication": [],
                "assertionMethod": []
              },
              "didDocumentMetadata": { "method": { "published": true } }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = method.resolveDid(Did(did))
        assertIs<DidResolutionResult.Success>(result)
        assertEquals(did, result.document.id.value)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sidetree/v1/identifiers/$did", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3 — resolveDid falls back to in-memory cache on 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolveDid falls back to local cache when Orb returns 404`() = runBlocking {
        // First: create the DID (Orb acks the operation)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"queued"}"""))
        val document = method.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })
        server.takeRequest() // consume the create POST

        // Then: Orb returns 404 for the not-yet-anchored long-form DID
        server.enqueue(MockResponse().setResponseCode(404))

        val result = method.resolveDid(document.id)
        assertIs<DidResolutionResult.Success>(result)
        assertEquals(document.id.value, result.document.id.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4 — resolveDid returns notFound when no cache and Orb 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolveDid returns notFound when Orb 404s a DID we never created`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = method.resolveDid(Did("did:orb:EiUnknownSuffix"))
        assertIs<DidResolutionResult.Failure>(result)
    }

    @Test
    fun `resolveDid rejects DIDs with the wrong method`() = runBlocking {
        val result = method.resolveDid(Did("did:web:example.com"))
        assertIs<DidResolutionResult.Failure>(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 5 — updateDid
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `updateDid posts a sidetree update operation`() = runBlocking {
        // create
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"queued"}"""))
        val document = method.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })
        server.takeRequest() // create POST

        // resolveDid called inside updateDid → return 404, will fall back to cache
        server.enqueue(MockResponse().setResponseCode(404))
        // update POST
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"queued"}"""))

        val updated = method.updateDid(document.id) { it }
        assertEquals(document.id.value, updated.id.value)

        // consume the resolve GET
        server.takeRequest()
        // verify the update POST
        val updateRequest = server.takeRequest()
        assertEquals("POST", updateRequest.method)
        assertEquals("/sidetree/v1/operations", updateRequest.path)
        val updateBody = Json.parseToJsonElement(updateRequest.body.readUtf8()).jsonObject
        assertEquals("update", updateBody["type"]?.jsonPrimitive?.content)
        assertNotNull(updateBody["didSuffix"])
        assertNotNull(updateBody["revealValue"])
        assertNotNull(updateBody["delta"])
        assertNotNull(updateBody["signedData"])
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 6 — deactivateDid
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `deactivateDid posts a sidetree deactivate operation`() = runBlocking {
        // create
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"queued"}"""))
        val document = method.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })
        server.takeRequest()

        // deactivate POST
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"queued"}"""))

        val result = method.deactivateDid(document.id)
        assertTrue(result, "deactivateDid should return true for a known DID")

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/sidetree/v1/operations", req.path)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("deactivate", body["type"]?.jsonPrimitive?.content)
        assertNotNull(body["didSuffix"])
        assertNotNull(body["revealValue"])
        assertNotNull(body["signedData"])
    }

    @Test
    fun `deactivateDid returns false when Orb rejects the operation`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        val result = method.deactivateDid(Did("did:orb:EiSomeSuffix"))
        assertFalse(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 7 — Method name
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `method name is orb`() {
        assertEquals("orb", method.method)
    }

}
