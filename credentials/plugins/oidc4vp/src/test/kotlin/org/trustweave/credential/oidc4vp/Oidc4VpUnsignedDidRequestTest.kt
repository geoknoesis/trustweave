package org.trustweave.credential.oidc4vp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException
import org.trustweave.credential.oidc4vp.models.AuthorizationRequest
import org.trustweave.credential.oidc4vp.models.PermissionRequest
import org.trustweave.credential.oidc4vp.models.PermissionResponse
import org.trustweave.credential.oidc4vp.session.InMemorySessionStore
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * A DID client_id (or client_id_scheme=did) is only meaningful for a signed request object. An
 * unsigned JSON document or bare URL parameters must be refused so a forger cannot claim a DID
 * and pick the response_uri, and the response_uri itself must be https (or loopback).
 */
class Oidc4VpUnsignedDidRequestTest {
    private lateinit var server: MockWebServer
    private lateinit var service: Oidc4VpService
    private val store = InMemorySessionStore()

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = Oidc4VpService(kms = InMemoryKeyManagementService(), httpClient = okhttp3.OkHttpClient(), sessionStore = store)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun body(
        clientId: String,
        scheme: String? = null,
    ) = buildJsonObject {
        put("client_id", clientId)
        scheme?.let { put("client_id_scheme", it) }
        put("response_uri", "https://attacker.example/steal")
        put("nonce", "n")
    }.toString()

    private fun url(clientId: String) = "openid4vp://authorize?client_id=$clientId&request_uri=${server.url("/request")}"

    @Test
    fun `unsigned JSON request for a DID client_id in the URL is refused`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setBody(body("did:web:bank.example")))
            assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
                service.parseAuthorizationUrl(url("did:web:bank.example"))
            }
        }

    @Test
    fun `unsigned JSON request declaring a DID client_id or scheme is refused`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setBody(body("did:web:bank.example")))
            assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
                service.parseAuthorizationUrl(url("https://x.example"))
            }
            server.enqueue(MockResponse().setBody(body("https://x.example", scheme = "did")))
            assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> {
                service.parseAuthorizationUrl(url("https://x.example"))
            }
        }

    @Test
    fun `URL-only request for a DID client_id is refused`() =
        runBlocking<Unit> {
            assertFailsWith<Oidc4VpException.UrlParseFailed> {
                service.parseAuthorizationUrl(
                    "openid4vp://authorize?client_id=did:web:bank.example&response_uri=https://attacker.example/steal&nonce=n",
                )
            }
        }

    @Test
    fun `submission to a non-https non-loopback response endpoint is refused`() =
        runBlocking<Unit> {
            store.put(
                "r1",
                PermissionRequest(
                    requestId = "r1",
                    authorizationRequest = AuthorizationRequest(responseUri = "http://169.254.169.254/x", clientId = "c"),
                ),
            )
            assertFailsWith<Oidc4VpException.PresentationSubmissionFailed> {
                service.submitPermissionResponse(PermissionResponse(responseId = "p", requestId = "r1", vpToken = "t"))
            }
        }
}
