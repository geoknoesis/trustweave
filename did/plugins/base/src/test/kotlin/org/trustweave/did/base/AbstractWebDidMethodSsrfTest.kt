package org.trustweave.did.base

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Regression test for the did:web SSRF guard: a did:web host is attacker-controlled, so resolution
 * must refuse hosts that resolve to loopback / private / link-local / cloud-metadata addresses
 * before any connection is attempted.
 */
class AbstractWebDidMethodSsrfTest {

    /** Minimal concrete did:web method mapping `did:web:<host>` to `https://<host>/.well-known/did.json`. */
    private class TestWebDidMethod(kms: KeyManagementService) :
        AbstractWebDidMethod("web", kms, OkHttpClient()) {

        override fun getDocumentUrl(did: String): String =
            "https://${did.substringAfter("did:web:")}/.well-known/did.json"

        override suspend fun publishDocument(url: String, document: DidDocument): Boolean = true

        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("not needed for this test")

        override suspend fun resolveDid(did: Did): DidResolutionResult = resolveFromHttp(did.value)
    }

    private fun assertSsrfRejected(did: String) = runBlocking {
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
}
