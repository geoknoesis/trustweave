package org.trustweave.hashicorpkms

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Sign parameters, missing-key mapping, no silent key reuse and error-code extraction against a scripted Transit. */
class VaultSignContractTest {
    private lateinit var server: HttpServer
    private lateinit var kms: VaultKeyManagementService
    private val requests = CopyOnWriteArrayList<Triple<String, String, String>>()
    private var keyType: String? = null
    private var publicPem: String = ""
    private var signStatus = 200
    private var signErrorBody = """{"errors":[]}"""

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val sig = Base64.getEncoder().encodeToString(ByteArray(64))
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val path = exchange.requestURI.path
            requests.add(Triple(exchange.requestMethod, path, body))
            val (status, out) =
                when {
                    path == "/v1/transit/keys/k1" && exchange.requestMethod == "GET" ->
                        if (keyType == null) {
                            404 to """{"errors":[]}"""
                        } else {
                            200 to
                                """{"data":{"type":"$keyType","latest_version":1,"keys":{"1":{"public_key":"${publicPem.replace(
                                    "\n",
                                    "\\n",
                                )}"}}}}"""
                        }
                    path == "/v1/transit/keys/k1" -> {
                        if (keyType == null) keyType = "ecdsa-p256"
                        204 to ""
                    }
                    path == "/v1/transit/sign/k1" ->
                        if (signStatus == 200) 200 to """{"data":{"signature":"vault:v1:$sig"}}""" else signStatus to signErrorBody
                    else -> 404 to """{"errors":[]}"""
                }
            val bytes = out.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) } else exchange.close()
        }
        server.start()
        kms = VaultKeyManagementService(VaultKmsConfig("http://127.0.0.1:${server.address.port}", token = "test-fixture"))
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun signBodies() = requests.filter { it.second == "/v1/transit/sign/k1" }.map { it.third }

    @Test
    fun `RSA signs with pkcs1v15 and a hash sized to the key`() =
        runBlocking<Unit> {
            for ((type, bits, hash) in listOf(
                Triple("rsa-2048", 2048, "sha2-256"),
                Triple("rsa-3072", 3072, "sha2-384"),
                Triple("rsa-4096", 4096, "sha2-512"),
            )) {
                requests.clear()
                keyType = type
                assertIs<SignResult.Success>(kms.sign(KeyId("k1"), "x".toByteArray(), Algorithm.RSA(bits)))
                val body = signBodies().single()
                assertTrue("\"signature_algorithm\":\"pkcs1v15\"" in body.replace(" ", ""), body)
                assertTrue("\"hash_algorithm\":\"$hash\"" in body.replace(" ", ""), body)
            }
        }

    @Test
    fun `EC signing does not send an RSA signature algorithm`() =
        runBlocking<Unit> {
            keyType = "ecdsa-p256"
            assertIs<SignResult.Success>(kms.sign(KeyId("k1"), "x".toByteArray(), Algorithm.P256))
            assertTrue("signature_algorithm" !in signBodies().single())
        }

    @Test
    fun `signing with a missing key is KeyNotFound`() =
        runBlocking<Unit> {
            keyType = null
            assertIs<SignResult.Failure.KeyNotFound>(kms.sign(KeyId("k1"), "x".toByteArray(), Algorithm.P256))
            assertIs<SignResult.Failure.KeyNotFound>(kms.sign(KeyId("k1"), "x".toByteArray(), null as Algorithm?))
            assertTrue(signBodies().isEmpty())
        }

    @Test
    fun `the HTTP status code comes from the driver not from words in the body`() =
        runBlocking<Unit> {
            keyType = "ecdsa-p256"
            signStatus = 500
            signErrorBody = """{"errors":["upstream not found, backend unavailable"]}"""
            assertIs<SignResult.Failure.Error>(kms.sign(KeyId("k1"), "x".toByteArray(), Algorithm.P256))
        }

    @Test
    fun `generateKey refuses to reuse an existing key name`() =
        runBlocking<Unit> {
            keyType = "ecdsa-p256"
            val result = kms.generateKey(Algorithm.P256, mapOf(org.trustweave.kms.KmsOptionKeys.KEY_NAME to "k1"))
            assertIs<GenerateKeyResult.Failure.DuplicateKeyId>(result)
            assertTrue(requests.none { it.first != "GET" }, "no create request may be issued: $requests")
        }

    @Test
    fun `generateKey creates a new key when the name is free`() =
        runBlocking<Unit> {
            keyType = null
            val pair =
                KeyPairGenerator
                    .getInstance(
                        "EC",
                    ).apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }
                    .generateKeyPair()
            publicPem =
                "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.public.encoded) +
                "\n-----END PUBLIC KEY-----\n"
            val result = kms.generateKey(Algorithm.P256, mapOf(org.trustweave.kms.KmsOptionKeys.KEY_NAME to "k1"))
            assertIs<GenerateKeyResult.Success>(result)
            assertEquals(1, requests.count { it.first != "GET" })
        }

    @Test
    fun `key ids that could alter the URL path are rejected before any request`() =
        runBlocking<Unit> {
            keyType = "ecdsa-p256"
            for (bad in listOf("../x", "a/b", "a?b", "a#b", "a\u0001b", "a%2fb", "..")) {
                assertIs<SignResult.Failure.Error>(kms.sign(KeyId(bad), "x".toByteArray(), Algorithm.P256), "'$bad'")
                assertIs<org.trustweave.kms.results.DeleteKeyResult.Failure.Error>(kms.deleteKey(KeyId(bad)), "'$bad'")
            }
            assertTrue(requests.isEmpty(), "no request may be sent: $requests")
        }
}
