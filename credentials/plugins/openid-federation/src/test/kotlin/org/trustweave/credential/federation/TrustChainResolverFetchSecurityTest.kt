package org.trustweave.credential.federation

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.net.SsrfBlockingDns
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Trust-chain resolution follows URLs it was told about by the very documents it is verifying:
 * `authority_hints` comes from the leaf entity's own JWT, and each authority's
 * `federation_fetch_endpoint` is read out of a statement that was itself just fetched. That makes
 * every hop an attacker-influenced fetch, which is exactly the shape the rest of the library already
 * defends: OID4VP, OID4VCI, SIOP, did:web and EVM anchoring all default to
 * `ssrfGuardedOkHttpClient()`.
 *
 * These tests pin the three properties that were missing here: the default client refuses internal
 * addresses, responses are bounded, and the subject identifier cannot escape its query parameter.
 */
class TrustChainResolverFetchSecurityTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `the default client refuses to resolve an internal address`() {
        // MockWebServer listens on loopback, so a resolver with the guard installed cannot reach it.
        // That is the point: a federation peer must not be able to steer a fetch at 127.0.0.1 or a
        // cloud metadata endpoint.
        val resolver = TrustChainResolver()

        val error =
            assertFailsWith<Exception> {
                runBlocking { resolver.fetchEntityConfiguration(server.url("/").toString().trimEnd('/')) }
            }

        val chain = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
        assertTrue(
            chain.contains("SSRF", ignoreCase = true) || chain.contains("disallowed", ignoreCase = true),
            "expected the SSRF guard to refuse a loopback host, got: $chain",
        )
    }

    @Test
    fun `an oversized entity configuration is rejected rather than buffered`() {
        // A compromised authority can answer with an unbounded body; reading it whole is a
        // memory-exhaustion DoS. An explicitly unguarded client is injected so this test exercises
        // the size cap rather than the SSRF guard.
        val resolver = TrustChainResolver(httpClient = okhttp3.OkHttpClient(), maxResponseBytes = 1024)
        server.enqueue(MockResponse().setBody("x".repeat(64 * 1024)))

        val error =
            assertFailsWith<Exception> {
                runBlocking { resolver.fetchEntityConfiguration(server.url("/").toString().trimEnd('/')) }
            }

        val chain = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
        assertTrue(
            chain.contains("size", ignoreCase = true) || chain.contains("exceeds", ignoreCase = true),
            "expected a size-limit failure, got: $chain",
        )
    }

    @Test
    fun `the subject identifier cannot inject extra query parameters`() {
        // `sub` is an entity identifier taken from an untrusted statement. Concatenated into a URL
        // it could append its own parameters, or truncate the intended ones with a fragment.
        val url =
            TrustChainResolver.subordinateStatementUrl(
                fetchEndpoint = server.url("/fetch").toString(),
                subjectId = "https://evil.test/x?injected=1#frag",
            )

        assertTrue(url.queryParameterNames.contains("sub"), "sub must be present")
        assertTrue(url.queryParameter("injected") == null, "an injected parameter must not survive")
        assertTrue(
            url.queryParameter("sub") == "https://evil.test/x?injected=1#frag",
            "the whole identifier must stay inside the sub parameter, got: ${url.queryParameter("sub")}",
        )
    }

    @Test
    fun `the guarded default is what ships`() {
        // Structural, on purpose: the behavioural test above proves the guard works, this one proves
        // nobody silently swapped the default back to a bare OkHttpClient.
        assertTrue(
            TrustChainResolver.defaultHttpClient().dns === SsrfBlockingDns,
            "TrustChainResolver must default to the SSRF-guarded client",
        )
    }
}
