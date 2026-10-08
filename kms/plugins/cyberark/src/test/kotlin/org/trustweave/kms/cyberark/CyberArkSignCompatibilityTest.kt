package org.trustweave.kms.cyberark

import kotlinx.coroutines.runBlocking
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
import java.security.Signature
import java.util.Base64
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Sign-time algorithm compatibility and RSA hash selection against a scripted Conjur. */
class CyberArkSignCompatibilityTest {
    private lateinit var server: MockWebServer
    private lateinit var kms: CyberArkKeyManagementService
    private var stored: Pair<Algorithm, KeyPair>? = null

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val (alg, pair) = stored ?: return MockResponse().setResponseCode(404)
                    val path = request.path.orEmpty()
                    val body =
                        when {
                            path.endsWith("/private") ->
                                """{"value":"${Base64.getEncoder().encodeToString(pair.private.encoded)}"}"""
                            path.endsWith("/metadata") ->
                                """{"algorithm":"${AlgorithmMapping.toConjurAlgorithm(alg)}",""" +
                                    """"publicKey":"${Base64.getEncoder().encodeToString(pair.public.encoded)}"}"""
                            else -> return MockResponse().setResponseCode(404)
                        }
                    return MockResponse().setResponseCode(200).setBody(body)
                }
            }
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

    private fun store(
        algorithm: Algorithm,
        pair: KeyPair,
    ) {
        stored = algorithm to pair
    }

    private fun ecKey(curve: String): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec(curve)) }.generateKeyPair()

    private fun rsaKey(bits: Int): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()

    @Test
    fun `a P-256 key refuses secp256k1 and P-384 sign requests`() =
        runBlocking<Unit> {
            store(Algorithm.P256, ecKey("secp256r1"))
            for (requested in listOf(Algorithm.Secp256k1, Algorithm.P384)) {
                val result = kms.sign(KeyId("k"), "data".toByteArray(), requested)
                assertIs<SignResult.Failure.UnsupportedAlgorithm>(result, "requested $requested got $result")
                assertTrue(result.keyAlgorithm == Algorithm.P256)
            }
        }

    @Test
    fun `a matching algorithm still signs`() =
        runBlocking<Unit> {
            store(Algorithm.P256, ecKey("secp256r1"))
            assertIs<SignResult.Success>(kms.sign(KeyId("k"), "data".toByteArray(), Algorithm.P256))
            assertIs<SignResult.Success>(kms.sign(KeyId("k"), "data".toByteArray(), null as Algorithm?))
        }

    private fun assertRsaHash(
        bits: Int,
        jcaName: String,
    ) = runBlocking<Unit> {
        val pair = rsaKey(bits)
        val alg = Algorithm.RSA(bits)
        store(alg, pair)
        val data = "payload".toByteArray()
        val result = kms.sign(KeyId("k"), data, alg)
        assertIs<SignResult.Success>(result)
        val verifier = Signature.getInstance(jcaName).apply { initVerify(pair.public) }
        verifier.update(data)
        assertTrue(verifier.verify(result.signature), "$bits-bit key must sign with $jcaName")
    }

    @Test
    fun `RSA-3072 signs with SHA-384`() = assertRsaHash(3072, "SHA384withRSA")

    @Test
    fun `RSA-4096 signs with SHA-512`() = assertRsaHash(4096, "SHA512withRSA")

    @Test
    fun `RSA-2048 signs with SHA-256`() = assertRsaHash(2048, "SHA256withRSA")
}
