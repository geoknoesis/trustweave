package org.trustweave.core.net

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpUrlPolicyTest {
    @Test
    fun `only real loopback literals and localhost are loopback`() {
        listOf("localhost", "127.0.0.1", "127.1.2.3", "[::1]", "::1", "[::ffff:127.0.0.1]").forEach {
            assertTrue(HttpUrlPolicy.isLoopbackHost(it), it)
        }
        listOf("127.evil.com", "127.0.0.1.evil.com", "10.0.0.1", "example.com", "", "999.0.0.1", "[2001:db8::1]").forEach {
            assertFalse(HttpUrlPolicy.isLoopbackHost(it), it)
        }
        assertFalse(HttpUrlPolicy.isLoopbackHost(null))
    }

    @Test
    fun `http is accepted only for loopback`() {
        assertTrue(HttpUrlPolicy.isHttpsOrLoopbackHttp("https://example.com/x"))
        assertTrue(HttpUrlPolicy.isHttpsOrLoopbackHttp("http://127.0.0.1:8080/x"))
        assertFalse(HttpUrlPolicy.isHttpsOrLoopbackHttp("http://127.evil.com/x"))
        assertFalse(HttpUrlPolicy.isHttpsOrLoopbackHttp("ftp://localhost/x"))
        assertFalse(HttpUrlPolicy.isHttpsOrLoopbackHttp("not a uri"))
    }

    @Test
    fun `oversized bodies are rejected and small ones read`() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/big") { ex ->
            val bytes = ByteArray(5000) { 'a'.code.toByte() }
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/small") { ex ->
            val bytes = "ok".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = OkHttpClient()
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals("ok", client.newCall(Request.Builder().url("$base/small").build()).execute().readBodyBounded(100))
            assertThrows<IOException> { client.newCall(Request.Builder().url("$base/big").build()).execute().readBodyBounded(100) }
        } finally {
            server.stop(0)
        }
    }
}
