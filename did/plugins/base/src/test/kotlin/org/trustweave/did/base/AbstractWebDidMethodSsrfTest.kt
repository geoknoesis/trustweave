package org.trustweave.did.base

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertTrue

/**
 * Regression test for the did:web SSRF guard: a did:web host is attacker-controlled, so resolution
 * must refuse hosts that resolve to loopback / private / link-local / cloud-metadata addresses
 * before any connection is attempted.
 */
class AbstractWebDidMethodSsrfTest {
    /** Minimal concrete did:web method mapping `did:web:<host>` to `https://<host>/.well-known/did.json`. */
    private class TestWebDidMethod(
        kms: KeyManagementService,
    ) : AbstractWebDidMethod("web", kms, OkHttpClient()) {
        override fun getDocumentUrl(did: String): String = "https://${did.substringAfter("did:web:")}/.well-known/did.json"

        override suspend fun publishDocument(
            url: String,
            document: DidDocument,
        ): Boolean = true

        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("not needed for this test")

        override suspend fun resolveDid(did: Did): DidResolutionResult = resolveFromHttp(did.value)

        suspend fun publishViaGuard(url: String) {
            executeRequest(
                okhttp3.Request
                    .Builder()
                    .url(url)
                    .get()
                    .build(),
            ).close()
        }
    }

    private fun assertSsrfRejected(did: String) =
        runBlocking {
            val method = TestWebDidMethod(InMemoryKeyManagementService())
            val ex = runCatching { method.resolveDid(Did(did)) }.exceptionOrNull()
            assertTrue(ex is TrustWeaveException, "expected a TrustWeaveException for $did, got $ex")
            assertTrue(
                ex.message.contains("SSRF guard"),
                "expected SSRF-guard rejection for $did, got: ${ex.message}",
            )
        }

    @Test
    fun `loopback host is rejected before connecting`() = assertSsrfRejected("did:web:127.0.0.1")

    @Test
    fun `private network host is rejected before connecting`() = assertSsrfRejected("did:web:10.0.0.1")

    @Test
    fun `cloud metadata host is rejected before connecting`() = assertSsrfRejected("did:web:169.254.169.254")

    @Test
    fun `documentation ranges are rejected before connecting`() {
        assertSsrfRejected("did:web:192.0.2.1")
        assertSsrfRejected("did:web:198.51.100.1")
        assertSsrfRejected("did:web:203.0.113.1")
    }

    @Test
    fun `resolution and publish clients drop any configured proxy`() {
        val proxied =
            OkHttpClient
                .Builder()
                .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress.createUnresolved("proxy.internal", 3128)))
                .build()
        val method =
            object : AbstractWebDidMethod("web", InMemoryKeyManagementService(), proxied) {
                override fun getDocumentUrl(did: String) = "https://example.com/did.json"

                override suspend fun publishDocument(
                    url: String,
                    document: DidDocument,
                ) = true

                override suspend fun createDid(options: DidCreationOptions): DidDocument = throw UnsupportedOperationException()

                override suspend fun resolveDid(did: Did): DidResolutionResult = resolveFromHttp(did.value)
            }
        assertTrue(method.resolutionClient.proxy == java.net.Proxy.NO_PROXY)
        assertTrue(method.publishClient.proxy == java.net.Proxy.NO_PROXY)
        assertTrue(method.resolutionClient.dns is ResolutionGuardedDns)
        assertTrue(method.publishClient.dns is ResolutionGuardedDns)
        assertTrue(!method.publishClient.followRedirects)
    }

    @Test
    fun `backslash host cannot smuggle a different authority`() {
        // java.net.URL and OkHttp disagree on backslashes; the check now uses OkHttp's parse.
        val method = TestWebDidMethod(InMemoryKeyManagementService())
        val ex = runCatching { runBlocking { method.resolveDid(Did("did:web:127.0.0.1\\@example.com")) } }.exceptionOrNull()
        assertTrue(ex != null, "backslash host must not resolve")
    }

    @Test
    fun `publish request to a loopback host is refused by the guard`() {
        val method = TestWebDidMethod(InMemoryKeyManagementService())
        val ex = runCatching { runBlocking { method.publishViaGuard("https://127.0.0.1/did.json") } }.exceptionOrNull()
        assertTrue(ex is TrustWeaveException && ex.message.contains("SSRF guard"), "got $ex")
    }
}
