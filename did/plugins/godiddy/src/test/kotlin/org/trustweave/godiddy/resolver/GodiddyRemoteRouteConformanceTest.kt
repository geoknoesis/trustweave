package org.trustweave.godiddy.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidErrorType
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.asDidResolver
import org.trustweave.godiddy.GodiddyClient
import org.trustweave.godiddy.GodiddyConfig
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G1 coverage for the GoDiddy leg of the "remote route": [GodiddyResolver] wrapped by
 * [org.trustweave.did.resolver.UniversalResolver.asDidResolver], the same choke point
 * [org.trustweave.did.resolver.DefaultUniversalResolver] flows through.
 *
 * The DID Resolution 1.0 §4 id-equality guard was hoisted into
 * `UniversalResolver.asDidResolver()`'s shared `resolveViaUniversal` in `did-core`, so a single
 * fix protects both [org.trustweave.did.resolver.DefaultUniversalResolver] and [GodiddyResolver]
 * without duplicating the check. This test proves the GoDiddy leg is actually covered by that
 * shared fix, rather than assuming it.
 */
class GodiddyRemoteRouteConformanceTest {
    private fun startServer(body: String): HttpServer {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        val bytes = body.toByteArray(Charsets.UTF_8)
        server.createContext("/1.0.0/universal-resolver/identifiers/") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    @Test
    fun `G1 a resolved document with a different id is rejected via the shared asDidResolver guard`() =
        runBlocking {
            val requested = "did:example:requested"
            val body = """{"didDocument":{"id":"did:example:someone-else"},"didDocumentMetadata":{}}"""
            val server = startServer(body)
            val client = GodiddyClient(GodiddyConfig(baseUrl = "http://localhost:${server.address.port}"))
            try {
                val resolver = GodiddyResolver(client).asDidResolver()

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
                client.close()
                server.stop(0)
            }
        }
}
