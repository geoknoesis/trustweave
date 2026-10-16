package org.trustweave.kms.thales

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.SignResult
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Sign-time algorithm compatibility, explicit hash selection and key-id hardening against a scripted CipherTrust. */
class ThalesSignCompatibilityTest {
    private lateinit var server: MockWebServer
    private lateinit var kms: ThalesKeyManagementService
    private var stored: Pair<Algorithm, KeyPair>? = null
    private val signBodies = mutableListOf<String>()
    private val paths = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    paths += path
                    val (alg, pair) = stored ?: return MockResponse().setResponseCode(404)
                    return when {
                        path.endsWith("/publickey") ->
                            MockResponse().setBody("""{"publicKey":"${Base64.getEncoder().encodeToString(pair.public.encoded)}"}""")
                        path.endsWith("/sign") -> {
                            signBodies += request.body.readUtf8()
                            val size = if (alg is Algorithm.RSA) alg.keySize / 8 else 2 * ((alg.curveBits() + 7) / 8)
                            MockResponse().setBody("""{"signature":"${Base64.getEncoder().encodeToString(ByteArray(size) { 1 })}"}""")
                        }
                        else -> MockResponse().setBody("""{"algorithm":"${AlgorithmMapping.toThalesKeyAlgorithm(alg)}"}""")
                    }
                }
            }
        server.start()
        kms =
            ThalesKeyManagementService(
                ThalesKmsConfig(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = "key"),
                OkHttpClient(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun Algorithm.curveBits(): Int =
        when (this) {
            Algorithm.P384 -> 384
            Algorithm.P521 -> 521
            else -> 256
        }

    private fun ecKey(curve: String): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec(curve)) }.generateKeyPair()

    private fun sentHash(): String =
        Json
            .parseToJsonElement(signBodies.single())
            .jsonObject["hashAlgorithm"]!!
            .jsonPrimitive.content

    @Test
    fun `a P-256 key refuses secp256k1 and P-384 sign requests without calling sign`() =
        runBlocking<Unit> {
            stored = Algorithm.P256 to ecKey("secp256r1")
            for (requested in listOf(Algorithm.Secp256k1, Algorithm.P384)) {
                val result = kms.sign(KeyId("k"), "data".toByteArray(), requested)
                assertIs<SignResult.Failure.UnsupportedAlgorithm>(result)
            }
            assertTrue(signBodies.isEmpty(), "no sign request may be issued for an incompatible algorithm")
        }

    @Test
    fun `a matching or null algorithm signs with the key's hash`() =
        runBlocking<Unit> {
            stored = Algorithm.P384 to ecKey("secp384r1")
            assertIs<SignResult.Success>(kms.sign(KeyId("k"), "data".toByteArray(), Algorithm.P384))
            assertIs<SignResult.Success>(kms.sign(KeyId("k"), "data".toByteArray(), null as Algorithm?))
            assertEquals(
                listOf("sha384", "sha384"),
                signBodies.map {
                    Json
                        .parseToJsonElement(it)
                        .jsonObject["hashAlgorithm"]!!
                        .jsonPrimitive.content
                },
            )
        }

    @Test
    fun `RSA hash follows key size`() =
        runBlocking<Unit> {
            for ((bits, hash) in listOf(2048 to "sha256", 3072 to "sha384", 4096 to "sha512")) {
                signBodies.clear()
                stored = Algorithm.RSA(bits) to KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
                assertIs<SignResult.Success>(kms.sign(KeyId("k"), "data".toByteArray(), Algorithm.RSA(bits)))
                assertEquals(hash, sentHash(), "RSA-$bits")
            }
        }

    @Test
    fun `key ids that could alter the URL path are rejected before any request`() =
        runBlocking<Unit> {
            stored = Algorithm.P256 to ecKey("secp256r1")
            for (bad in listOf("../x", "a/b", "a?b", "a#b", "a\u0001b", "a%2fb", "..")) {
                val result = kms.sign(KeyId(bad), "data".toByteArray(), Algorithm.P256)
                assertIs<SignResult.Failure.Error>(result, "'$bad'")
            }
            assertTrue(paths.isEmpty(), "no request may be sent: $paths")
        }
}
