package org.trustweave.revocation.bitstring

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.trustweave.revocation.bitstring.HttpsStatusListCredentialFetcher.Companion.awaitBody
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The fetcher's HTTP call must be aborted when the calling coroutine is cancelled. */
class FetcherCancellationTest {
    private val client = OkHttpClient.Builder().build()

    @Test
    fun `cancelling the coroutine closes the connection to a stalled endpoint`() =
        runBlocking<Unit> {
            ServerSocket(0).use { server ->
                val clientClosed = CountDownLatch(1)
                val accepted = CompletableDeferred<Unit>()
                val serverThread =
                    Thread {
                        server.accept().use { socket ->
                            accepted.complete(Unit)
                            // Never respond; a -1 read means the client tore the connection down.
                            val input = socket.getInputStream()
                            val buf = ByteArray(4096)
                            try {
                                while (input.read(buf) >= 0) {
                                    // swallow the request
                                }
                            } catch (_: java.io.IOException) {
                                // reset also means closed
                            }
                            clientClosed.countDown()
                        }
                    }.apply {
                        isDaemon = true
                        start()
                    }
                val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build())
                val job = async(Dispatchers.IO) { call.awaitBody(1024) }
                withTimeout(5_000) { accepted.await() }
                job.cancelAndJoin()
                assertTrue(call.isCanceled(), "the OkHttp call must be cancelled with the coroutine")
                assertTrue(clientClosed.await(5, TimeUnit.SECONDS), "the stalled connection must be closed")
                serverThread.join(1_000)
            }
        }

    @Test
    fun `a non-200 response fails the await`() =
        runBlocking<Unit> {
            ServerSocket(0).use { server ->
                Thread {
                    server.accept().use { socket ->
                        socket.getInputStream().read(ByteArray(4096))
                        socket.getOutputStream().write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        socket.getOutputStream().flush()
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
                val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build())
                val failure = assertFailsWith<IllegalStateException> { call.awaitBody(1024) }
                assertTrue(failure.message!!.contains("503"), failure.message)
            }
        }

    @Test
    fun `a 200 response is read and an oversized one is refused`() =
        runBlocking<Unit> {
            fun serve(body: String): Int {
                val server = ServerSocket(0)
                Thread {
                    server.use {
                        it.accept().use { socket ->
                            socket.getInputStream().read(ByteArray(4096))
                            socket.getOutputStream().write(
                                "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray(),
                            )
                            socket.getOutputStream().flush()
                        }
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
                return server.localPort
            }
            val ok = client.newCall(Request.Builder().url("http://127.0.0.1:${serve("hello")}/").build())
            assertEquals("hello", ok.awaitBody(1024))
            val big = client.newCall(Request.Builder().url("http://127.0.0.1:${serve("x".repeat(100))}/").build())
            assertFailsWith<IllegalStateException> { big.awaitBody(10) }
            assertFailsWith<CancellationException> { throw CancellationException("sanity") }
        }
}
