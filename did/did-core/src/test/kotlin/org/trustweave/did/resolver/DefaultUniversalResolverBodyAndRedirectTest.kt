package org.trustweave.did.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.http.HttpClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Body-read deadline and redirect refusal of [DefaultUniversalResolver]. */
class DefaultUniversalResolverBodyAndRedirectTest {
    private val doc = """{"didDocument":{"id":"did:example:123"},"didDocumentMetadata":{}}"""
    private val noRetry = RetryConfig(maxRetries = 0)

    @Test
    fun `a body that stalls after the headers is cut off by the deadline`() {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/1.0/identifiers/") { exchange ->
            exchange.sendResponseHeaders(200, 100_000)
            exchange.responseBody.write("{\"didDoc".toByteArray())
            exchange.responseBody.flush()
            Thread.sleep(8_000) // never completes within the 1s deadline
            runCatching { exchange.close() }
        }
        server.start()
        try {
            val resolver = DefaultUniversalResolver("http://localhost:${server.address.port}", timeout = 1, retryConfig = noRetry)
            val started = System.nanoTime()
            val outcome = runCatching { runBlocking { resolver.resolveDid("did:example:123") } }
            val seconds = (System.nanoTime() - started) / 1e9

            assertTrue(seconds < 5, "stalled body must be bounded by the deadline, took ${seconds}s")
            assertTrue(outcome.isFailure || outcome.getOrNull() is DidResolutionResult.Failure, "got $outcome")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `an unfollowed 3xx is refused with an explicit redirect error`() =
        runBlocking<Unit> {
            val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
            server.createContext("/1.0/identifiers/") { exchange ->
                exchange.responseHeaders.add("Location", "http://localhost:1/x")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.start()
            try {
                val resolver = DefaultUniversalResolver("http://localhost:${server.address.port}", timeout = 5, retryConfig = noRetry)
                val result = resolver.resolveDid("did:example:123")
                assertTrue(result is DidResolutionResult.Failure.ResolutionError, "got $result")
                assertTrue(result.reason.contains("redirect", ignoreCase = true), result.reason)
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `an injected redirect-following client does not get a redirected document accepted`() =
        runBlocking<Unit> {
            val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
            server.createContext("/1.0/identifiers/") { exchange ->
                exchange.responseHeaders.add("Location", "/final")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/final") { exchange ->
                val bytes = doc.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            try {
                val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
                val resolver =
                    DefaultUniversalResolver(
                        "http://localhost:${server.address.port}",
                        timeout = 5,
                        retryConfig = noRetry,
                        httpClient = client,
                    )
                val result = resolver.resolveDid("did:example:123")
                assertFalse(result is DidResolutionResult.Success, "a followed redirect must not yield a Success")
                assertEquals(true, result is DidResolutionResult.Failure)
            } finally {
                server.stop(0)
            }
        }
}
