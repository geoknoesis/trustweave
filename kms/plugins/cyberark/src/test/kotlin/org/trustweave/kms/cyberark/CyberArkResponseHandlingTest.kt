package org.trustweave.kms.cyberark

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ForwardingSource
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every vendor HTTP response must be closed, and an empty success body must fail clearly. */
class CyberArkResponseHandlingTest {
    private lateinit var server: MockWebServer
    private lateinit var kms: CyberArkKeyManagementService
    private val opened = AtomicInteger()
    private val closed = AtomicInteger()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val tracking =
            OkHttpClient
                .Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val response = chain.proceed(chain.request())
                        val body = response.body ?: return@Interceptor response
                        opened.incrementAndGet()
                        val source =
                            object : ForwardingSource(body.source()) {
                                override fun close() {
                                    closed.incrementAndGet()
                                    super.close()
                                }
                            }.buffer()
                        response
                            .newBuilder()
                            .body(
                                source.asResponseBody(body.contentType() ?: "text/plain".toMediaTypeOrNull(), body.contentLength()),
                            ).build()
                    },
                ).build()
        val url = server.url("/").toString().trimEnd('/')
        kms = CyberArkKeyManagementService(CyberArkKmsConfig(conjurUrl = url, account = "acct", apiKey = "key"), tracking)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a failed delete releases every response`() =
        runBlocking<Unit> {
            repeat(6) { server.enqueue(MockResponse().setResponseCode(500).setBody("boom")) }
            val result = kms.deleteKey(KeyId("some-key"))
            assertTrue(result !is DeleteKeyResult.Deleted, "got $result")
            assertTrue(opened.get() > 0)
            assertEquals(opened.get(), closed.get(), "every response body must be closed")
        }

    @Test
    fun `a successful empty body is a failure and is closed`() =
        runBlocking<Unit> {
            repeat(6) { server.enqueue(MockResponse().setResponseCode(200)) }
            val result = kms.getPublicKey(KeyId("k"))
            assertTrue(result is GetPublicKeyResult.Failure, "got $result")
            assertTrue(result.toString().contains("empty response body"), "got $result")
            assertEquals(opened.get(), closed.get())
        }
}
