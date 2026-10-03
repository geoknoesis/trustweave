package org.trustweave.kms.thales

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Config validation, provider wiring and error mapping against a scripted server: every upstream
 * failure must surface as an explicit `Failure` result (never a thrown exception, never a fake
 * success).
 */
class ThalesServiceBehaviourTest {
    private lateinit var server: MockWebServer
    private lateinit var kms: ThalesKeyManagementService

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        kms = ThalesKeyManagementService(ThalesKmsConfig(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = "key"), OkHttpClient())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `config rejects blank endpoint`() {
        assertThrows<IllegalArgumentException> { ThalesKmsConfig(baseUrl = " ", apiKey = "key") }
    }

    @Test
    fun `fromMap reports missing required options`() {
        assertThrows<IllegalArgumentException> { ThalesKmsConfig.fromMap(emptyMap()) }
    }

    @Test
    fun `provider is named and creates a service from valid options`() {
        val provider = ThalesKeyManagementServiceProvider()
        assertEquals("thales", provider.name)
        assertTrue(provider.supportedAlgorithms.isNotEmpty())
        assertThrows<IllegalArgumentException> { provider.create(emptyMap()) }
    }

    @Test
    fun `an upstream error on generateKey is a Failure, not a success`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            val result = kms.generateKey(Algorithm.P256, emptyMap())
            assertTrue(result is GenerateKeyResult.Failure, "got $result")
        }

    @Test
    fun `an upstream error on sign is a Failure, not a signature`() =
        runBlocking<Unit> {
            repeat(3) { server.enqueue(MockResponse().setResponseCode(500).setBody("boom")) }
            val result = kms.sign(KeyId("missing-key"), "payload".toByteArray(), Algorithm.P256)
            assertTrue(result is SignResult.Failure, "got $result")
        }

    @Test
    fun `an upstream error on getPublicKey is a Failure`() =
        runBlocking<Unit> {
            repeat(3) { server.enqueue(MockResponse().setResponseCode(500).setBody("boom")) }
            assertTrue(kms.getPublicKey(KeyId("missing-key")) is GetPublicKeyResult.Failure)
        }

    @Test
    fun `a dead endpoint yields Failure results without throwing`() =
        runBlocking<Unit> {
            server.shutdown()
            assertTrue(kms.sign(KeyId("k"), ByteArray(1), Algorithm.P256) is SignResult.Failure)
            assertTrue(kms.generateKey(Algorithm.P256, emptyMap()) is GenerateKeyResult.Failure)
            assertIs<DeleteKeyResult>(kms.deleteKey(KeyId("k")))
        }

    @Test
    fun `unsupported algorithm is reported as such`() =
        runBlocking<Unit> {
            val result = kms.generateKey(Algorithm.Custom("NOPE"), emptyMap())
            assertTrue(result is GenerateKeyResult.Failure, "got $result")
        }
}
