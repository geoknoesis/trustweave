package org.trustweave.did.resolver

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals

class DefaultUniversalResolverMethodsListTest {
    private fun methodsFor(body: String): List<String>? {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/1.0/methods") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val resolver = DefaultUniversalResolver("http://localhost:${server.address.port}", timeout = 5)
            return runBlocking { resolver.getSupportedMethods() }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `non-primitive elements do not throw and are dropped`() {
        assertEquals(listOf("web", "key"), methodsFor("""["web", {"a":1}, ["x"], "key"]"""))
    }

    @Test
    fun `json null elements are dropped rather than reported as the method null`() {
        assertEquals(listOf("web"), methodsFor("""[null, "web", null]"""))
    }

    @Test
    fun `non-string primitives are dropped`() {
        assertEquals(listOf("web"), methodsFor("""[1, true, "web"]"""))
    }

    @Test
    fun `a plain list is unchanged`() {
        assertEquals(listOf("web", "key"), methodsFor("""["web","key"]"""))
    }
}
