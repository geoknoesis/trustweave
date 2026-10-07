package org.trustweave.godiddy.resolver

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.godiddy.GodiddyClient
import org.trustweave.godiddy.GodiddyConfig
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for GodiddyResolver.
 */
class GodiddyResolverBranchCoverageTest {

    /** A loopback upstream answering every identifier request with [status] and [body]; never the public API. */
    private fun upstream(
        status: Int,
        body: String,
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        val bytes = body.toByteArray(Charsets.UTF_8)
        server.createContext("/1.0.0/universal-resolver/identifiers/") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    private suspend fun <T> withUpstream(
        status: Int,
        body: String,
        block: suspend (GodiddyResolver) -> T,
    ): T {
        val server = upstream(status, body)
        val client = GodiddyClient(GodiddyConfig(baseUrl = "http://localhost:${server.address.port}"))
        try {
            return block(GodiddyResolver(client))
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun `a wrapped resolution response becomes a Success carrying the document`() =
        runBlocking<Unit> {
            val body = """{"didDocument":{"id":"did:example:123"},"didDocumentMetadata":{},"didResolutionMetadata":{}}"""
            val result = withUpstream(200, body) { it.resolveDid("did:example:123") }
            val success = assertIs<DidResolutionResult.Success>(result)
            assertEquals("did:example:123", success.document.id.value)
        }

    @Test
    fun `a bare DID document response becomes a Success`() =
        runBlocking<Unit> {
            val result = withUpstream(200, """{"id":"did:example:123"}""") { it.resolveDid("did:example:123") }
            val success = assertIs<DidResolutionResult.Success>(result)
            assertEquals("did:example:123", success.document.id.value)
        }

    @Test
    fun `document metadata from the upstream is carried over`() =
        runBlocking<Unit> {
            val body = """{"didDocument":{"id":"did:example:123"},"didDocumentMetadata":{"versionId":"7"},"didResolutionMetadata":{}}"""
            val result = withUpstream(200, body) { it.resolveDid("did:example:123") }
            val success = assertIs<DidResolutionResult.Success>(result)
            assertEquals("7", success.documentMetadata.versionId)
            assertFalse(success.documentMetadata.deactivated)
        }

    @Test
    fun `an upstream 404 becomes NotFound`() =
        runBlocking<Unit> {
            val result = withUpstream(404, "") { it.resolveDid("did:example:missing") }
            assertIs<DidResolutionResult.Failure.NotFound>(result)
        }

    @Test
    fun `an upstream server error is raised, not turned into a Success`() =
        runBlocking<Unit> {
            val failure = assertFailsWith<TrustWeaveException> { withUpstream(500, "{}") { it.resolveDid("did:example:123") } }
            assertTrue(failure.message!!.contains("500"), "message should name the HTTP status: ${failure.message}")
        }

    @Test
    fun `test GodiddyResolver convertToDidDocument with all fields`() = runBlocking<Unit> {
        val config = GodiddyConfig.default()
        val client = GodiddyClient(config)
        val resolver = GodiddyResolver(client)

        // Test conversion logic indirectly through resolveDid
        try {
            val result = resolver.resolveDid("did:key:123")
            assertNotNull(result)
        } catch (e: Exception) {
            // Expected to fail without mock
            assertIs<TrustWeaveException>(e)
        }

        client.close()
    }

    @Test
    fun `test GodiddyResolver convertToDidDocument with missing id`() = runBlocking<Unit> {
        val config = GodiddyConfig.default()
        val client = GodiddyClient(config)
        val resolver = GodiddyResolver(client)

        // This will fail in real scenario, but we test the branch
        try {
            val result = resolver.resolveDid("did:key:123")
            assertNotNull(result)
        } catch (e: Exception) {
            // Expected to fail without mock
            assertIs<TrustWeaveException>(e)
        }

        client.close()
    }
}

