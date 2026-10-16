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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VaultSignKeyTypeTest {
    private fun withTransit(
        keyType: String,
        block: (VaultKmsConfig, List<String>) -> Unit,
    ) {
        val paths = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val sig = Base64.getEncoder().encodeToString(ByteArray(64))
        server.createContext("/") { exchange ->
            exchange.requestBody.readAllBytes()
            paths.add(exchange.requestURI.path)
            val (status, body) =
                when (exchange.requestURI.path) {
                    "/v1/transit/keys/k1" -> 200 to """{"data":{"type":"$keyType"}}"""
                    "/v1/transit/sign/k1" -> 200 to """{"data":{"signature":"vault:v1:$sig"}}"""
                    else -> 404 to """{"errors":[]}"""
                }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block(VaultKmsConfig("http://127.0.0.1:${server.address.port}", token = "test-fixture"), paths)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a key of an unmapped type fails closed with UnsupportedAlgorithm and is never signed with`() {
        withTransit("aes256-gcm96") { config, paths ->
            val result = runBlocking { VaultKeyManagementService(config).sign(KeyId("k1"), "x".toByteArray(), Algorithm.Ed25519) }

            assertIs<SignResult.Failure.UnsupportedAlgorithm>(result)
            assertTrue("/v1/transit/sign/k1" !in paths, paths.toString())
        }
    }

    @Test
    fun `an unmapped key type with no requested algorithm also fails closed`() {
        withTransit("aes256-gcm96") { config, paths ->
            val result = runBlocking { VaultKeyManagementService(config).sign(KeyId("k1"), "x".toByteArray(), null as Algorithm?) }

            assertIs<SignResult.Failure.UnsupportedAlgorithm>(result)
            assertTrue("/v1/transit/sign/k1" !in paths, paths.toString())
        }
    }

    @Test
    fun `key info is read once per sign`() {
        withTransit("ed25519") { config, paths ->
            val result = runBlocking { VaultKeyManagementService(config).sign(KeyId("k1"), "x".toByteArray(), Algorithm.Ed25519) }

            assertIs<SignResult.Success>(result)
            assertEquals(1, paths.count { it == "/v1/transit/keys/k1" }, paths.toString())
        }
    }

    @Test
    fun `an incompatible requested algorithm is still rejected`() {
        withTransit("ed25519") { config, _ ->
            val result = runBlocking { VaultKeyManagementService(config).sign(KeyId("k1"), "x".toByteArray(), Algorithm.P256) }

            assertIs<SignResult.Failure.UnsupportedAlgorithm>(result)
        }
    }
}
