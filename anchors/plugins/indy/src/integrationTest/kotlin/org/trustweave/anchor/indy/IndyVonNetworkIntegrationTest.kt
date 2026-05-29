package org.trustweave.anchor.indy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration test that round-trips an ATTRIB through a live Hyperledger Indy pool
 * (von-network running in a Docker container) and asserts the digest stored on-ledger
 * matches what the client wrote.
 *
 * Skips cleanly when Docker is unavailable so the test compiles and runs in CI
 * without bringing down the build. Run explicitly with:
 *
 * ```
 * ./gradlew :anchors:plugins:indy:integrationTest
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndyVonNetworkIntegrationTest {

    private val container = VonNetworkContainer()
    private lateinit var httpClient: HttpClient

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker not available; skipping")
        container.start()
        httpClient = HttpClient(CIO)
    }

    @AfterAll
    fun tearDown() {
        if (::httpClient.isInitialized) httpClient.close()
        if (container.isRunning) container.stop()
    }

    @Test
    fun `anchor digest round trip via ATTRIB and GET_ATTRIB`() = runBlocking {
        val nym = registerNym()
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = mapOf(
                "poolEndpoint" to container.browserUrl(),
                "did" to nym.did,
                "signingKeySeed" to nym.seedBase58
            ),
            httpClient = httpClient
        )

        val digestHex = MessageDigest.getInstance("SHA-256")
            .digest("hello".toByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
        val payload = buildJsonObject {
            put("vcId", JsonPrimitive("urn:uuid:integration-test"))
            put("digest", JsonPrimitive(digestHex))
        }

        val written = client.writePayload(payload)
        assertNotNull(written.ref.txHash)

        val read = client.readPayload(written.ref)
        val readObj = read.payload.jsonObject
        assertEquals(payload["vcId"]!!.jsonPrimitive.content, readObj["vcId"]!!.jsonPrimitive.content)
        assertEquals(payload["digest"]!!.jsonPrimitive.content, readObj["digest"]!!.jsonPrimitive.content)
    }

    /**
     * Calls von-network's self-serve `/register` endpoint to bootstrap a Steward DID
     * with write permissions on the ledger. Returns the DID and its raw Ed25519 seed.
     */
    private suspend fun registerNym(): RegisteredNym {
        val seed = "0".repeat(32) // deterministic test seed
        val response = httpClient.post("${container.browserUrl()}/register") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("seed", JsonPrimitive(seed))
                    put("role", JsonPrimitive("ENDORSER"))
                }.toString()
            )
        }
        val body = response.bodyAsText()
        val parsed = Json.parseToJsonElement(body).jsonObject
        val did = parsed["did"]?.jsonPrimitive?.content
            ?: error("von-network /register did not return a DID: $body")
        // The Indy ledger expects a raw 32-byte seed. We re-use the same string the
        // /register call consumed; the client encodes it to Base58 before signing.
        val seedBytes = seed.toByteArray()
        val seedBase58 = encodeBase58(seedBytes)
        return RegisteredNym(did = did, seedBase58 = seedBase58)
    }

    /** Tiny inlined Base58 encoder so this test does not reach into the plugin's internal helper. */
    private fun encodeBase58(input: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        if (input.isEmpty()) return ""
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) zeros++
        val temp = input.copyOf()
        val out = StringBuilder()
        var start = zeros
        while (start < temp.size) {
            var remainder = 0
            for (i in start until temp.size) {
                val digit = temp[i].toInt() and 0xFF
                val v = remainder * 256 + digit
                temp[i] = (v / 58).toByte()
                remainder = v % 58
            }
            if (temp[start].toInt() == 0) start++
            out.append(alphabet[remainder])
        }
        repeat(zeros) { out.append(alphabet[0]) }
        return out.reverse().toString()
    }

    private data class RegisteredNym(val did: String, val seedBase58: String)
}
