package org.trustweave.hashicorpkms

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.SignResult
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VaultEmptyDataSignTest {
    private fun withTransit(
        signStatus: Int = 200,
        signBody: String,
        block: (VaultKmsConfig, List<String>) -> Unit,
    ) {
        val signBodies = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.requestBody.readAllBytes().decodeToString()
            val (status, body) =
                when {
                    exchange.requestURI.path == "/v1/transit/keys/k1" -> 200 to """{"data":{"type":"ed25519"}}"""
                    exchange.requestURI.path == "/v1/transit/sign/k1" -> {
                        signBodies.add(request)
                        signStatus to signBody
                    }
                    else -> 404 to """{"errors":[]}"""
                }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block(VaultKmsConfig("http://127.0.0.1:${server.address.port}", token = "test-fixture"), signBodies)
        } finally {
            server.stop(0)
        }
    }

    private val signature = ByteArray(64) { it.toByte() }
    private val signatureResponse = """{"data":{"signature":"vault:v1:${Base64.getEncoder().encodeToString(signature)}"}}"""

    @Test
    fun `empty data is sent to Transit as an empty base64 input`() {
        withTransit(signBody = signatureResponse) { config, signBodies ->
            val result = runBlocking { VaultKeyManagementService(config).sign(KeyId("k1"), ByteArray(0), Algorithm.Ed25519) }

            assertContentEquals(signature, assertIs<SignResult.Success>(result).signature)
            assertEquals(1, signBodies.size)
            assertTrue(""""input":""""" in signBodies.single(), signBodies.single())
        }
    }

    @Test
    fun `a refusal from Vault for an empty input is surfaced, not reported as success`() {
        withTransit(signStatus = 400, signBody = """{"errors":["missing input"]}""") { config, _ ->
            val result = runBlocking { VaultKeyManagementService(config).sign(KeyId("k1"), ByteArray(0), Algorithm.Ed25519) }

            assertIs<SignResult.Failure.Error>(result)
        }
    }

    @Test
    fun `non-empty data keeps its base64 input`() {
        withTransit(signBody = signatureResponse) { config, signBodies ->
            runBlocking { VaultKeyManagementService(config).sign(KeyId("k1"), "abc".toByteArray(), Algorithm.Ed25519) }

            assertTrue(""""input":"YWJj"""" in signBodies.single(), signBodies.single())
        }
    }
}
