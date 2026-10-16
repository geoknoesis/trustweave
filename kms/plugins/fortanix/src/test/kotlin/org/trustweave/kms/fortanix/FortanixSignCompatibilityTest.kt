package org.trustweave.kms.fortanix

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.SignResult
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Sign-time algorithm compatibility, key-id hardening and transport policy against a scripted Fortanix DSM. */
class FortanixSignCompatibilityTest {
    private lateinit var server: MockWebServer
    private lateinit var kms: FortanixKeyManagementService
    private var stored: KeyPair? = null
    private var storedAlgorithm: Algorithm = Algorithm.P256
    private var signCalls = 0
    private val paths = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    paths += path
                    val pair = stored ?: return MockResponse().setResponseCode(404)
                    return when {
                        path.endsWith("/export") ->
                            MockResponse().setBody("""{"pub_key":"${Base64.getEncoder().encodeToString(pair.public.encoded)}"}""")
                        path.endsWith("/sign") -> {
                            signCalls++
                            MockResponse().setBody("""{"signature":"${Base64.getEncoder().encodeToString(ByteArray(64) { 1 })}"}""")
                        }
                        else ->
                            MockResponse().setBody(
                                """{"obj_type":"EC","curve":"${AlgorithmMapping.toFortanixCurve(storedAlgorithm)}"}""",
                            )
                    }
                }
            }
        server.start()
        kms =
            FortanixKeyManagementService(
                FortanixKmsConfig(apiEndpoint = server.url("/").toString().trimEnd('/'), apiKey = "key"),
                OkHttpClient(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun storeP256() {
        storedAlgorithm = Algorithm.P256
        stored =
            KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    }

    @Test
    fun `a P-256 key refuses secp256k1 and P-384 sign requests without calling sign`() =
        runBlocking<Unit> {
            storeP256()
            for (requested in listOf(Algorithm.Secp256k1, Algorithm.P384)) {
                val result = kms.sign(KeyId("k"), "data".toByteArray(), requested)
                assertIs<SignResult.Failure.UnsupportedAlgorithm>(result, "requested $requested got $result")
                assertTrue(result.keyAlgorithm == Algorithm.P256)
            }
            assertTrue(signCalls == 0)
        }

    @Test
    fun `a matching or null algorithm still signs`() =
        runBlocking<Unit> {
            storeP256()
            assertIs<SignResult.Success>(kms.sign(KeyId("k"), "data".toByteArray(), Algorithm.P256))
            assertIs<SignResult.Success>(kms.sign(KeyId("k"), "data".toByteArray(), null as Algorithm?))
        }

    @Test
    fun `key ids that could alter the URL path are rejected before any request`() =
        runBlocking<Unit> {
            storeP256()
            for (bad in listOf("../x", "a/b", "a?b", "a#b", "a\u0001b", "a%2fb", "..")) {
                assertIs<SignResult.Failure.Error>(kms.sign(KeyId(bad), "data".toByteArray(), Algorithm.P256), "'$bad'")
            }
            assertTrue(paths.isEmpty(), "no request may be sent: $paths")
        }

    @Test
    fun `plaintext http to a public host is refused`() {
        assertThrows<IllegalArgumentException> { FortanixKmsConfig(apiEndpoint = "http://8.8.8.8", apiKey = "key") }
    }
}
