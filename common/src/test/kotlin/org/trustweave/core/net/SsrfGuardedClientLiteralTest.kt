package org.trustweave.core.net

import com.sun.net.httpserver.HttpServer
import okhttp3.Dns
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** IP-literal hosts never reach OkHttp's [Dns]; the connect-time guard must still refuse them. */
class SsrfGuardedClientLiteralTest {
    private val servers = mutableListOf<HttpServer>()
    private val hits = AtomicInteger()
    private val fixedDns =
        object : Dns {
            override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.2"))
        }

    private fun server(
        bind: String,
        handler: (com.sun.net.httpserver.HttpExchange) -> Unit,
    ): HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getByName(bind), 0), 0).also {
            it.createContext("/") { ex ->
                handler(ex)
                ex.close()
            }
            it.start()
            servers += it
        }

    @AfterEach
    fun stop() = servers.forEach { it.stop(0) }

    private fun ok(ex: com.sun.net.httpserver.HttpExchange) {
        hits.incrementAndGet()
        ex.sendResponseHeaders(200, -1)
    }

    private fun get(
        client: okhttp3.OkHttpClient,
        url: String,
    ) = client.newCall(Request.Builder().url(url).build()).execute().use { it.code }

    @Test
    fun `loopback IPv4 literal is refused and never reaches the server`() {
        val s = server("127.0.0.1", ::ok)
        assertThrows<IOException> { get(ssrfGuardedOkHttpClient(), "http://127.0.0.1:${s.address.port}/") }
        assertEquals(0, hits.get())
    }

    @Test
    fun `other internal literals are refused`() {
        val client = ssrfGuardedOkHttpClient()
        listOf(
            "http://169.254.169.254/",
            "http://10.1.2.3/",
            "http://192.168.0.1/",
            "http://[::1]:8080/",
            "http://[::ffff:127.0.0.1]:8080/",
            "http://[::ffff:10.0.0.1]/",
            "http://[fd00::1]/",
        ).forEach { url ->
            val e = assertThrows<IOException>(url) { get(client, url) }
            assertFalse(e.message.orEmpty().isEmpty())
        }
    }

    @Test
    fun `redirect from an allowed name to an IP literal is refused`() {
        // 127.0.0.2 plays the "public" host; everything else on loopback stays disallowed.
        val target = server("127.0.0.1", ::ok)
        val front =
            server("127.0.0.2") { ex ->
                ex.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}/secret")
                ex.sendResponseHeaders(302, -1)
            }
        val client =
            ssrfGuardedClientBuilder(
                dns = fixedDns,
                isDisallowed = { it.hostAddress != "127.0.0.2" && PrivateNetworkGuard.isDisallowed(it) },
            ).build()
        assertThrows<IOException> { get(client, "http://front.test:${front.address.port}/") }
        assertEquals(0, hits.get())
    }

    @Test
    fun `an allowed destination still works`() {
        val front = server("127.0.0.2", ::ok)
        val client =
            ssrfGuardedClientBuilder(
                dns = fixedDns,
                isDisallowed = { it.hostAddress != "127.0.0.2" && PrivateNetworkGuard.isDisallowed(it) },
            ).build()
        assertEquals(200, get(client, "http://front.test:${front.address.port}/"))
        assertEquals(1, hits.get())
    }
}
