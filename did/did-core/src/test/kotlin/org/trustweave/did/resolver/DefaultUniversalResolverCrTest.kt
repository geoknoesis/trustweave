package org.trustweave.did.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [DefaultUniversalResolver]'s conformance to the DID Resolution 1.0 §12.1 HTTP
 * binding: the `Accept: application/did-resolution` request header, the HTTP 410 → `Deactivated`
 * mapping, the §12.1 status → error-type table, and `nextVersionId` propagation.
 *
 * Reuses the JDK `HttpServer` fixture shape already established in
 * [DefaultUniversalResolverTest] (`resolveDid surfaces a bare errorMessage as the NotFound
 * reason`): bind port 0, register the real `/1.0/identifiers/` context path that
 * [StandardUniversalResolverAdapter.buildResolveUrl] produces, stop the server in a `finally`
 * block, and drive the real `HttpClient.sendAsync` path. Kept in a separate file — per this
 * task's plan — rather than growing the existing (already large) test class further.
 */
class DefaultUniversalResolverCrTest {
    /**
     * Starts a fixture server that answers any request under the `/1.0/identifiers/` path with
     * [status] and [body]. An empty [body] sends no response body at all (JDK
     * `sendResponseHeaders(status, -1)`), exercising the "absent body" case. When
     * [capturedAccept] is supplied, the inbound `Accept` header is recorded into it.
     */
    private fun startServer(
        status: Int,
        body: String,
        capturedAccept: AtomicReference<String?>? = null,
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        val bytes = body.toByteArray(Charsets.UTF_8)
        server.createContext("/1.0/identifiers/") { exchange ->
            capturedAccept?.set(exchange.requestHeaders.getFirst("Accept"))
            exchange.responseHeaders.add("Content-Type", "application/json")
            if (bytes.isEmpty()) {
                exchange.sendResponseHeaders(status, -1)
                exchange.responseBody.close()
            } else {
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        return server
    }

    @Test
    fun `resolve request asks for application did-resolution`() =
        runBlocking<Unit> {
            val capturedAccept = AtomicReference<String?>(null)
            val body = """{"didDocument":{"id":"did:example:123"},"didDocumentMetadata":{}}"""
            val server = startServer(200, body, capturedAccept)
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                resolver.resolveDid("did:example:123")

                assertEquals("application/did-resolution", capturedAccept.get())
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `HTTP 410 maps to Deactivated`() =
        runBlocking<Unit> {
            val body = """{"didDocument":null,"didDocumentMetadata":{"deactivated":true}}"""
            val server = startServer(410, body)
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:deactivated")

                assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
                assertTrue(result.documentMetadata.deactivated)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `HTTP 410 with absent body still produces Deactivated`() =
        runBlocking<Unit> {
            val server = startServer(410, "")
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:deactivated-empty")

                assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
                assertTrue(result.documentMetadata.deactivated)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `HTTP 410 with malformed body still produces Deactivated`() =
        runBlocking<Unit> {
            val server = startServer(410, "not-json{{{")
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:deactivated-malformed")

                assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
                assertTrue(result.documentMetadata.deactivated)
            } finally {
                server.stop(0)
            }
        }

    // ─── Critical fix: an HTTP 200 body carrying BOTH a non-null didDocument AND
    // didDocumentMetadata.deactivated: true must still yield Deactivated, never Success. Every
    // pre-existing deactivation test in this class uses "didDocument": null, which is exactly why
    // this composition — Success checked before deactivated — survived until now: a revoked DID's
    // document would otherwise flow into a caller as a plain Success and could go on to verify a
    // credential and be cached. ───

    @Test
    fun `HTTP 200 with a non-null document and deactivated true still produces Deactivated`() =
        runBlocking<Unit> {
            val body = """{"didDocument":{"id":"did:example:still-live"},"didDocumentMetadata":{"deactivated":true}}"""
            val server = startServer(200, body)
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:still-live")

                assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
                assertTrue(result.documentMetadata.deactivated)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `HTTP 501 maps to METHOD_NOT_SUPPORTED`() =
        runBlocking<Unit> {
            val server = startServer(501, "")
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:unsupported-method")

                assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, result.errorType)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `HTTP 400 maps to INVALID_DID`() =
        runBlocking<Unit> {
            val server = startServer(400, "")
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:bad")

                assertEquals(DidErrorType.INVALID_DID, result.errorType)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `legacy string error in 200 body is upgraded to NOT_FOUND`() =
        runBlocking<Unit> {
            val body = """{"didResolutionMetadata":{"error":"notFound"},"didDocumentMetadata":{}}"""
            val server = startServer(200, body)
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:legacy-notfound")

                assertEquals(DidErrorType.NOT_FOUND, result.errorType)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `upstream nextVersionId lands in document metadata`() =
        runBlocking<Unit> {
            val body = """{"didDocument":{"id":"did:example:versioned"},"didDocumentMetadata":{"versionId":"3","nextVersionId":"4"}}"""
            val server = startServer(200, body)
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:versioned")

                assertTrue(result is DidResolutionResult.Success, "expected Success, got $result")
                assertEquals("4", result.documentMetadata.nextVersionId)
            } finally {
                server.stop(0)
            }
        }

    // ─── Fix round 1: valid JSON with an explicit `null` field (JsonNull, not absent) must not
    // crash resolution. `JsonNull` is a real JsonElement, so `?.jsonObject`-style extraction
    // invokes on it (the safe call does not short-circuit) and throws IllegalArgumentException —
    // a different failure mode than the malformed-JSON-syntax case above, which throws
    // SerializationException and was already handled. Both StandardUniversalResolverAdapter and
    // GodiddyProtocolAdapter now use `as? JsonObject` so this degrades to null instead. ───

    @Test
    fun `HTTP 410 with explicit null metadata field still produces Deactivated`() =
        runBlocking<Unit> {
            val server = startServer(410, """{"didDocumentMetadata":null}""")
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:deactivated-null-metadata")

                assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
                assertTrue(result.documentMetadata.deactivated)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `HTTP 200 with explicit null metadata field does not crash resolution`() =
        runBlocking<Unit> {
            val server = startServer(200, """{"didDocumentMetadata":null}""")
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                // No "didDocument" key is present either, so this resolves to NotFound rather than
                // throwing — the point of this test is that it returns a DidResolutionResult at all
                // instead of DidException.DidResolutionFailed escaping from the JsonNull cast.
                val result = resolver.resolveDid("did:example:no-document-null-metadata")

                assertTrue(result is DidResolutionResult.Failure.NotFound, "expected NotFound, got $result")
            } finally {
                server.stop(0)
            }
        }

    // ─── Fix round 2: the same JsonNull problem one JSON level deeper. `didDocumentMetadata`
    // itself is now guarded (round 1), but its *sub-fields* were not: `parseDidDocumentMetadata`
    // used `?.jsonPrimitive`/`?.jsonArray` per field. `deactivated: null` alone does not actually
    // crash (JsonNull is itself a JsonPrimitive, so `.jsonPrimitive` succeeds on it and
    // `.booleanOrNull` degrades gracefully) — verified empirically before writing this test. The
    // two fields that do crash pre-fix are `canonicalId` (JsonNull's `.content` is the literal
    // string "null", which `Did("null")` then rejects) and `equivalentId` (`.jsonArray` throws on
    // JsonNull the same way `.jsonObject` did). This test includes `deactivated: null` — the
    // literal shape requested in review — alongside those two, so it both matches what was asked
    // and gives genuine RED coverage. ───

    @Test
    fun `HTTP 410 with null-valued metadata sub-fields still produces Deactivated`() =
        runBlocking<Unit> {
            val body = """{"didDocumentMetadata":{"deactivated":null,"canonicalId":null,"equivalentId":null}}"""
            val server = startServer(410, body)
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:deactivated-null-subfields")

                assertTrue(result is DidResolutionResult.Deactivated, "expected Deactivated, got $result")
                assertTrue(result.documentMetadata.deactivated)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `HTTP 200 with null-valued metadata sub-fields does not crash resolution`() =
        runBlocking<Unit> {
            val body =
                """{"didDocument":{"id":"did:example:200-null-subfields"},""" +
                    """"didDocumentMetadata":{"deactivated":null,"canonicalId":null,"equivalentId":null,"versionId":null}}"""
            val server = startServer(200, body)
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:200-null-subfields")

                assertTrue(result is DidResolutionResult.Success, "expected Success, got $result")
                assertEquals(null, result.documentMetadata.canonicalId)
                assertEquals(null, result.documentMetadata.versionId)
            } finally {
                server.stop(0)
            }
        }

    // ─── F4: DidDocumentMetadata.proof (§4.3) round-trip. toJson() emits `proof`, but
    // parseDidDocumentMetadata previously never read it back, silently dropping controller/VDR
    // proofs on a resolved document. ───

    @Test
    fun `upstream documentMetadata proof is parsed back`() =
        runBlocking<Unit> {
            val body =
                """{"didDocument":{"id":"did:example:proofed"},""" +
                    """"didDocumentMetadata":{"proof":[{"type":"DataIntegrityProof","proofValue":"z123"}]}}"""
            val server = startServer(200, body)
            try {
                val resolver = DefaultUniversalResolver(baseUrl = "http://localhost:${server.address.port}", timeout = 5)

                val result = resolver.resolveDid("did:example:proofed")

                assertTrue(result is DidResolutionResult.Success, "expected Success, got $result")
                assertEquals(1, result.documentMetadata.proof.size)
                assertEquals(
                    "DataIntegrityProof",
                    result.documentMetadata.proof
                        .single()["type"]
                        ?.jsonPrimitive
                        ?.content,
                )
            } finally {
                server.stop(0)
            }
        }
}
