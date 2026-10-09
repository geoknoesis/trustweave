package org.trustweave.credential.siop

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.trustweave.credential.siop.models.SiopV2AuthorizationRequest
import org.trustweave.credential.siop.models.SiopV2AuthorizationResponse
import org.trustweave.credential.siop.models.SiopV2Session
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A DID client_id (or client_id_scheme=did) promises a pinned verifier identity, which only a
 * signed request object can deliver. An unsigned document must never be accepted for it, and
 * the response_uri the wallet POSTs to must pass the https-or-loopback policy.
 */
class SiopV2UnsignedDidRequestTest {
    private lateinit var server: MockWebServer
    private lateinit var service: SiopV2Service

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = SiopV2Service(kms = InMemoryKeyManagementService(), httpClient = okhttp3.OkHttpClient())
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun json(
        clientId: String,
        scheme: String? = null,
    ): String =
        buildJsonObject {
            put("response_type", "vp_token")
            put("client_id", clientId)
            scheme?.let { put("client_id_scheme", it) }
            put("response_uri", "https://attacker.example/steal")
            put("nonce", "n")
        }.toString()

    private fun url(extra: String) = "siopv2://authorize?request_uri=${server.url("/request")}&$extra"

    @Test
    fun `unsigned JSON request for a DID client_id in the URL is refused`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setBody(json("did:web:bank.example")))
            val e = assertFailsWith<SiopV2Exception> { service.parseAuthorizationRequest(url("client_id=did:web:bank.example")) }
            assertEquals("UNSIGNED_REQUEST_OBJECT", e.code)
        }

    @Test
    fun `unsigned JSON request that declares a DID client_id itself is refused`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setBody(json("did:web:bank.example")))
            val e = assertFailsWith<SiopV2Exception> { service.parseAuthorizationRequest(url("client_id=https://x.example")) }
            assertEquals("UNSIGNED_REQUEST_OBJECT", e.code)
        }

    @Test
    fun `unsigned JSON request with client_id_scheme did is refused`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setBody(json("https://x.example", scheme = "did")))
            val e = assertFailsWith<SiopV2Exception> { service.parseAuthorizationRequest(url("client_id=https://x.example")) }
            assertEquals("UNSIGNED_REQUEST_OBJECT", e.code)
        }

    @Test
    fun `URL-only request for a DID client_id is refused`() =
        runBlocking<Unit> {
            val e =
                assertFailsWith<SiopV2Exception> {
                    service.parseAuthorizationRequest(
                        "siopv2://authorize?client_id=did:web:bank.example&response_type=vp_token&response_uri=https://attacker.example/steal&nonce=n",
                    )
                }
            assertEquals("UNSIGNED_REQUEST_OBJECT", e.code)
        }

    @Test
    fun `submitResponse refuses a non-https non-loopback response_uri`() =
        runBlocking<Unit> {
            val session =
                SiopV2Session(
                    sessionId = "s",
                    request = SiopV2AuthorizationRequest(responseType = "vp_token", responseUri = "http://169.254.169.254/latest", clientId = "https://x.example", nonce = "n"),
                )
            val e = assertFailsWith<SiopV2Exception> { service.submitResponse(session, SiopV2AuthorizationResponse(state = "x")) }
            assertEquals("INSECURE_RESPONSE_URI", e.code)
        }
}
