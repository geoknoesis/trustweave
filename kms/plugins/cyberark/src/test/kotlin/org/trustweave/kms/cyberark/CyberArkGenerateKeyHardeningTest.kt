package org.trustweave.kms.cyberark

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KmsOptionKeys
import org.trustweave.kms.results.GenerateKeyResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CyberArkGenerateKeyHardeningTest {
    private lateinit var server: MockWebServer
    private lateinit var kms: CyberArkKeyManagementService

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        kms =
            CyberArkKeyManagementService(
                CyberArkKmsConfig(conjurUrl = server.url("/").toString().trimEnd('/'), account = "acct", apiKey = "key"),
                OkHttpClient(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a failed metadata write fails generateKey and removes the orphan secret`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(200)) // private key stored
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom")) // metadata refused
            server.enqueue(MockResponse().setResponseCode(200)) // cleanup
            server.enqueue(MockResponse().setResponseCode(200)) // cleanup

            val result = kms.generateKey(Algorithm.P256, mapOf(KmsOptionKeys.NAME to "k1"))

            assertTrue(result is GenerateKeyResult.Failure.Error, "got $result")
            val methods = (1..server.requestCount).map { server.takeRequest() }.map { it.method to it.path }
            assertEquals("POST", methods[0].first)
            assertEquals("POST", methods[1].first)
            assertTrue(methods.drop(2).any { it.first == "DELETE" && it.second!!.contains("/acct/TrustWeave/keys/k1") }, methods.toString())
        }

    @Test
    fun `a successful generateKey still succeeds`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(200))

            val result = kms.generateKey(Algorithm.P256, mapOf(KmsOptionKeys.NAME to "k1"))

            assertTrue(result is GenerateKeyResult.Success, "got $result")
        }

    @Test
    fun `resolveKeyId rejects traversal and unexpected segments`() {
        for (bad in listOf(
            "../x",
            "a/../b",
            "/acct/../other/secret",
            "..",
            "a/./b",
            "a//b",
            "a\\b",
            "a?b",
            "a#b",
            "a%2e%2eb/c",
            "x y",
            "a\nb",
            "",
        )) {
            assertThrows<IllegalArgumentException>("'$bad'") { AlgorithmMapping.resolveKeyId(bad, "acct") }
        }
    }

    @Test
    fun `resolveKeyId keeps accepting normal ids and absolute paths`() {
        assertEquals("/acct/TrustWeave/keys/my-key_1.v2", AlgorithmMapping.resolveKeyId("my-key_1.v2", "acct"))
        assertEquals("/acct/TrustWeave/keys/k", AlgorithmMapping.resolveKeyId("/acct/TrustWeave/keys/k", "acct"))
    }
}
