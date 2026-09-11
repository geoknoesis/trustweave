package org.trustweave.hashicorpkms

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VaultTransitRoutingTest {
    @Test
    fun `default client sends Transit requests without KV-v2 rewriting`() {
        val received = java.util.concurrent.CopyOnWriteArrayList<String>()
        val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            received.add("${exchange.requestMethod} ${exchange.requestURI}")
            val body = exchange.requestBody.readAllBytes().decodeToString()
            if (exchange.requestMethod == "POST") bodies.add(body)
            val bytes = """{"data":{}}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val config = VaultKmsConfig("http://127.0.0.1:${server.address.port}", token = "test-fixture")
            val client = VaultKmsClientFactory.createClient(config)
            client.logical().write("transit/keys/example", mapOf("type" to "ed25519"))
            client.logical().read("transit/keys/example")
            assertEquals(listOf("POST /v1/transit/keys/example", "GET /v1/transit/keys/example"), received.toList())
            assertEquals(listOf("""{"type":"ed25519"}"""), bodies.toList())
            assertFailsWith<IllegalArgumentException> { config.copy(engineVersion = 2) }
        } finally { server.stop(0) }
    }
}
