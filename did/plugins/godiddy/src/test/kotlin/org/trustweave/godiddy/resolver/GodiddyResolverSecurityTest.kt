package org.trustweave.godiddy.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.godiddy.GodiddyClient
import org.trustweave.godiddy.GodiddyConfig
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GodiddyResolverSecurityTest {
    @Test
    fun `a DID that is not a valid DID never reaches the network`() =
        runBlocking<Unit> {
            val requested = CopyOnWriteArrayList<String>()
            val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
            server.createContext("/") { exchange ->
                requested.add(exchange.requestURI.toString())
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            server.start()
            val client = GodiddyClient(GodiddyConfig(baseUrl = "http://localhost:${server.address.port}"))
            try {
                val resolver = GodiddyResolver(client)
                for (bad in listOf("../../admin", "did:example:a/../../admin", "did:example:a?x=1", "not-a-did", "did:example:a\r\nX: y")) {
                    val result = resolver.resolveDid(bad)
                    assertTrue(result is DidResolutionResult.Failure.InvalidFormat, "$bad -> $result")
                }
                assertEquals(emptyList(), requested.toList())
            } finally {
                client.close()
                server.stop(0)
            }
        }

    @Test
    fun `the API key is not forwarded to another host on redirect`() =
        runBlocking<Unit> {
            val seenAuth = CopyOnWriteArrayList<String?>()
            val target = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            target.createContext("/") { exchange ->
                seenAuth.add(exchange.requestHeaders.getFirst("Authorization"))
                val body = """{"didDocument":null,"didDocumentMetadata":{"deactivated":true}}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            target.start()
            val origin = HttpServer.create(InetSocketAddress("localhost", 0), 0)
            origin.createContext("/") { exchange ->
                exchange.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}/elsewhere")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            origin.start()
            val client = GodiddyClient(GodiddyConfig(baseUrl = "http://localhost:${origin.address.port}", apiKey = "SECRET-KEY"))
            try {
                runCatching { GodiddyResolver(client).resolveDid("did:example:abc") }
                assertTrue(seenAuth.none { it != null }, "Authorization leaked to redirect target: $seenAuth")
            } finally {
                client.close()
                origin.stop(0)
                target.stop(0)
            }
        }
}
