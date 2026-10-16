package org.trustweave.credential.oidc4vp

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException
import org.trustweave.credential.oidc4vp.models.AuthorizationRequest
import org.trustweave.credential.oidc4vp.models.PermissionRequest
import org.trustweave.credential.oidc4vp.models.PermissionResponse
import org.trustweave.credential.oidc4vp.session.InMemorySessionStore
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `request_uri` and `response_uri` share one https-or-real-loopback policy; bodies are bounded. */
class Oidc4VpUrlPolicyTest {
    private val store = InMemorySessionStore()
    private val service = Oidc4VpService(kms = InMemoryKeyManagementService(), httpClient = okhttp3.OkHttpClient(), sessionStore = store)

    private fun parse(requestUri: String) =
        runBlocking {
            service.parseAuthorizationUrl("openid4vp://authorize?client_id=c&request_uri=$requestUri")
        }

    @Test
    fun `plain http request_uri to a public host is refused`() {
        assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> { parse("http://verifier.example/req") }
    }

    @Test
    fun `127 prefixed DNS name is not loopback for request_uri`() {
        assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> { parse("http://127.evil.com/req") }
    }

    @Test
    fun `127 prefixed DNS name is not loopback for response_uri`() {
        runBlocking {
            store.put(
                "r1",
                PermissionRequest("r1", AuthorizationRequest(responseUri = "http://127.evil.com/x", clientId = "c")),
            )
            assertFailsWith<Oidc4VpException.PresentationSubmissionFailed> {
                service.submitPermissionResponse(PermissionResponse(responseId = "p", requestId = "r1", vpToken = "t"))
            }
        }
    }

    @Test
    fun `oversized request_uri body is refused`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{" + "a".repeat(2 * 1024 * 1024)))
        server.start()
        try {
            val e = assertFailsWith<Oidc4VpException.AuthorizationRequestFetchFailed> { parse(server.url("/req").toString()) }
            assertEquals(true, e.message.contains("too large"), e.message)
        } finally {
            server.shutdown()
        }
    }
}
