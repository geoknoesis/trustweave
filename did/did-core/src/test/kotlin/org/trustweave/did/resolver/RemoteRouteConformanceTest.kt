package org.trustweave.did.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolution.ResolutionOptions
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Conformance tests for the "remote route" — [DefaultUniversalResolver] wrapped by
 * [UniversalResolver.asDidResolver] — closing three DID Resolution 1.0 §4/§4.4 gaps that
 * [RegistryBasedResolver] (the local-registry route) already closes but the remote route never
 * received:
 *
 * - G1: §4 requires the resolved document's `id` to be string-equal to the resolved DID.
 * - G2: §4.4 requires `expandRelativeUrls` to actually expand relative service ids.
 * - G3: a malformed upstream document must degrade to a failure *result*, never an uncaught
 *   exception that gets misclassified as the caller's DID being invalid.
 *
 * Reuses the JDK `HttpServer` fixture shape established in [DefaultUniversalResolverTest] /
 * [DefaultUniversalResolverCrTest]: bind port 0, register the real `/1.0/identifiers/` context
 * path, stop the server in a `finally` block.
 */
class RemoteRouteConformanceTest {
    private fun startServer(body: String): HttpServer {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        val bytes = body.toByteArray(Charsets.UTF_8)
        server.createContext("/1.0/identifiers/") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    // ─── G1: id-equality guard ───
    //
    // A wrong — or compromised — upstream resolver can return another DID's document. Absent the
    // guard, this surfaces as Success; the caller has no way to know the document it received
    // does not belong to the DID it asked for.

    @Test
    fun `G1 a resolved document with a different id is rejected, not returned as Success`() =
        runBlocking {
            val requested = "did:example:requested"
            val body = """{"didDocument":{"id":"did:example:someone-else"},"didDocumentMetadata":{}}"""
            val server = startServer(body)
            try {
                val resolver =
                    DefaultUniversalResolver(
                        baseUrl = "http://localhost:${server.address.port}",
                        timeout = 5,
                    ).asDidResolver()

                val result = resolver.resolve(Did(requested))

                assertTrue(
                    result is DidResolutionResult.Failure.ResolutionError,
                    "expected a failure, got $result",
                )
                assertEquals(
                    DidErrorType.INVALID_DID_DOCUMENT,
                    (result as DidResolutionResult.Failure.ResolutionError).resolutionMetadata.error?.type,
                )
            } finally {
                server.stop(0)
            }
        }

    // ─── G2: expandRelativeUrls must actually expand ───
    //
    // A did:key-style fixture with no services proves nothing here (TC-14's known weakness) — this
    // fixture carries a relative service id so the assertion can genuinely fail pre-fix.

    @Test
    fun `G2 expandRelativeUrls rewrites a relative service id through the composed fallback stack`() =
        runBlocking {
            val did = "did:example:relsvc"
            val body =
                """{"didDocument":{"id":"$did","service":[""" +
                    """{"id":"#vcs","type":"VerifiableCredentialService","serviceEndpoint":"https://example.com/vc/"}]},""" +
                    """"didDocumentMetadata":{}}"""
            val server = startServer(body)
            try {
                val universal =
                    DefaultUniversalResolver(
                        baseUrl = "http://localhost:${server.address.port}",
                        timeout = 5,
                    ).asDidResolver()
                // Composed the way FallbackDidResolver's own kdoc documents: a primary that does
                // not know the method, falling through to the universal-resolver adapter.
                val primary =
                    object : DidResolver {
                        override suspend fun resolve(did: Did): DidResolutionResult =
                            DidResolutionResult.Failure.MethodNotRegistered(method = did.method)
                    }
                val resolver = FallbackDidResolver(primary, universal)

                val result = resolver.resolve(Did(did), ResolutionOptions(expandRelativeUrls = true))

                assertTrue(result is DidResolutionResult.Success, "expected Success, got $result")
                assertEquals(
                    "$did#vcs",
                    (result as DidResolutionResult.Success)
                        .document
                        .service
                        .first()
                        .id,
                )
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `G2 without the option a relative service id is left alone`() =
        runBlocking {
            val did = "did:example:relsvc-off"
            val body =
                """{"didDocument":{"id":"$did","service":[""" +
                    """{"id":"#vcs","type":"VerifiableCredentialService","serviceEndpoint":"https://example.com/vc/"}]},""" +
                    """"didDocumentMetadata":{}}"""
            val server = startServer(body)
            try {
                val resolver =
                    DefaultUniversalResolver(
                        baseUrl = "http://localhost:${server.address.port}",
                        timeout = 5,
                    ).asDidResolver()

                val result = resolver.resolve(Did(did), ResolutionOptions())

                assertTrue(result is DidResolutionResult.Success, "expected Success, got $result")
                assertEquals(
                    "#vcs",
                    (result as DidResolutionResult.Success)
                        .document
                        .service
                        .first()
                        .id,
                )
            } finally {
                server.stop(0)
            }
        }

    // ─── G3: a malformed upstream document must not throw past the caller's control ───
    //
    // DidDocumentJsonParser.parse throws DidException.InvalidDidFormat / IllegalArgumentException
    // for a malformed `id`. Left unguarded, this escapes resolveDid() and asDidResolver() maps the
    // exception to INVALID_DID (400) — blaming the caller's perfectly valid requested DID for an
    // upstream document defect.

    @Test
    fun `G3 a malformed document id degrades to a failure result, not INVALID_DID`() =
        runBlocking {
            val body = """{"didDocument":{"id":"not-a-valid-did"},"didDocumentMetadata":{}}"""
            val server = startServer(body)
            try {
                val resolver =
                    DefaultUniversalResolver(
                        baseUrl = "http://localhost:${server.address.port}",
                        timeout = 5,
                    ).asDidResolver()

                val result = resolver.resolve(Did("did:example:well-formed-request"))

                assertTrue(result is DidResolutionResult.Failure, "expected a failure result, got $result")
                assertFalse(
                    result.errorType == DidErrorType.INVALID_DID,
                    "must not blame the caller's valid DID for an upstream malformed document",
                )
                // The upstream *did* return a document — it just failed to parse. INVALID_DID_DOCUMENT
                // honestly names that, the same type GodiddyResolver already uses for its identical
                // document-conversion-failure guard; a bare INTERNAL_ERROR would still bury the real
                // cause behind a generic label.
                assertEquals(DidErrorType.INVALID_DID_DOCUMENT, result.errorType)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `G3 a document with no id field at all also degrades to INVALID_DID_DOCUMENT, not INVALID_DID`() =
        runBlocking {
            // §4.4 step 5: the upstream returned a non-null didDocument, just one missing the
            // required `id` field entirely — DidDocumentJsonParser.parse throws
            // DidException.InvalidDidFormat directly for this shape, which is the specific path
            // that (pre-fix) propagated uncaught and got mapped to INVALID_DID (400).
            val body = """{"didDocument":{"verificationMethod":[]},"didDocumentMetadata":{}}"""
            val server = startServer(body)
            try {
                val resolver =
                    DefaultUniversalResolver(
                        baseUrl = "http://localhost:${server.address.port}",
                        timeout = 5,
                    ).asDidResolver()

                val result = resolver.resolve(Did("did:example:well-formed-request-2"))

                assertTrue(result is DidResolutionResult.Failure, "expected a failure result, got $result")
                assertFalse(
                    result.errorType == DidErrorType.INVALID_DID,
                    "must not blame the caller's valid DID for an upstream document missing 'id'",
                )
                assertEquals(DidErrorType.INVALID_DID_DOCUMENT, result.errorType)
            } finally {
                server.stop(0)
            }
        }
}
