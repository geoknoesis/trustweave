package org.trustweave.kms.thales

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThalesTokenCachingTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config() =
        ThalesKmsConfig(
            baseUrl = server.url("/").toString().trimEnd('/'),
            clientId = "cid",
            clientSecret = "csecret",
        )

    private fun request(): Request {
        val builder = Request.Builder()
        return builder.url(server.url("/api/x")).get().build()
    }

    private fun call(client: okhttp3.OkHttpClient) {
        client.newCall(request()).execute().close()
    }

    @Test
    fun `the oauth token is fetched once and reused until it nears the issuer's expiry`() {
        var fetches = 0
        var now = 0L
        val client =
            ThalesKmsClientFactory.createClient(
                config(),
                { IssuedToken("tok-${fetches++}", lifetimeMillis = 600_000L) },
                { now },
            )
        repeat(5) { server.enqueue(MockResponse().setBody("{}")) }

        repeat(3) { call(client) }
        assertEquals(1, fetches)

        // lifetime 10 min, margin 2.5 min: cached at 7 min, refetched after 7.5 min.
        now = 7 * 60_000L
        call(client)
        assertEquals(1, fetches)
        now = 7 * 60_000L + 31_000L
        call(client)
        assertEquals(2, fetches)
    }

    @Test
    fun `an oauth failure does not echo the upstream body into the exception`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("INTERNAL-SECRET-DETAIL client_secret=leaked"))
        val client = ThalesKmsClientFactory.createClient(config())

        val e = assertThrows<IllegalStateException> { call(client) }

        assertFalse(e.toString().contains("INTERNAL-SECRET-DETAIL"), e.toString())
        assertFalse(e.toString().contains("leaked"), e.toString())
        assertTrue(e.message!!.contains("400"), e.message)
    }

    @Test
    fun `a service error result does not carry the upstream body`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("INTERNAL-SECRET-DETAIL"))
        val kms =
            ThalesKeyManagementService(
                ThalesKmsConfig(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = "k"),
                okhttp3.OkHttpClient(),
            )
        val result =
            kotlinx.coroutines.runBlocking {
                kms.generateKey(org.trustweave.kms.Algorithm.Ed25519, emptyMap())
            }
        assertFalse(result.toString().contains("INTERNAL-SECRET-DETAIL"), result.toString())
    }
}
