package org.trustweave.kms.cyberark

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

class ConjurTokenCachingTest {
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
        CyberArkKmsConfig(
            conjurUrl = server.url("/").toString().trimEnd('/'),
            account = "acct",
            apiKey = "api-key",
            hostId = "host/h",
        )

    private fun request(): Request {
        val builder = Request.Builder()
        return builder.url(server.url("/secrets/x")).get().build()
    }

    private fun call(client: okhttp3.OkHttpClient) {
        client.newCall(request()).execute().close()
    }

    @Test
    fun `the token is fetched once and reused until it nears expiry`() {
        var fetches = 0
        var now = 0L
        val client =
            ConjurClientFactory.createClient(config(), { fetches++.let { IssuedToken("tok-$it", lifetimeMillis = null) } }, { now })
        repeat(5) { server.enqueue(MockResponse().setBody("{}")) }

        repeat(3) { call(client) }
        assertEquals(1, fetches, "one token for three calls")
        assertEquals("Token token=\"tok-0\"", server.takeRequest().getHeader("Authorization"))

        // Default lifetime 8 min, margin 2 min: still cached at 5 min, refetched after 6 min.
        now = 5 * 60_000L
        call(client)
        assertEquals(1, fetches)
        now = 6 * 60_000L + 1
        call(client)
        assertEquals(2, fetches)
    }

    @Test
    fun `a rejected token is refreshed once and the request retried`() {
        var fetches = 0
        val client = ConjurClientFactory.createClient(config(), { IssuedToken("tok-${fetches++}", null) }, { 0L })
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("{}"))

        val response = client.newCall(request()).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(2, fetches)
        assertEquals("Token token=\"tok-0\"", server.takeRequest().getHeader("Authorization"))
        assertEquals("Token token=\"tok-1\"", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `an authentication failure does not echo the upstream body into the exception`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("INTERNAL-SECRET-DETAIL api-key=leaked"))
        val client = ConjurClientFactory.createClient(config())

        val e = assertThrows<IllegalStateException> { call(client) }

        assertFalse(e.toString().contains("INTERNAL-SECRET-DETAIL"), e.toString())
        assertFalse(e.toString().contains("leaked"), e.toString())
        assertTrue(e.message!!.contains("500"), e.message)
    }
}
