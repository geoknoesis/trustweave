package org.trustweave.anchor.indy

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.trustweave.core.exception.TrustWeaveException
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** Exercises actual ATTRIB consensus and GET_ATTRIB using the disposable local test ledger. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndyVonNetworkIntegrationTest {
    private val container = VonNetworkContainer()
    private lateinit var httpClient: HttpClient

    @BeforeAll
    fun setUp() {
        val available = DockerClientFactory.instance().isDockerAvailable
        if (System.getProperty("trustweave.indy.integration") == "required") {
            check(available) { "Docker is required for live Indy validation" }
        } else {
            assumeTrue(available, "Docker unavailable; no live Indy evidence produced")
        }
        container.start()
        httpClient = HttpClient(CIO)
    }

    @AfterAll
    fun tearDown() {
        if (::httpClient.isInitialized) httpClient.close()
        if (container.isRunning) container.stop()
    }

    @Test
    fun `anchor digest round trip via ATTRIB and GET_ATTRIB`() =
        runBlocking<Unit> {
            // Public genesis trustee key, valid only in this newly created disposable ledger.
            val nym = RegisteredNym("V4SGRU86Z58d6TV7PBUe6f", encodeBase58("000000000000000000000000Trustee1".toByteArray()))
            val client =
                IndyBlockchainAnchorClient(
                    chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
                    options =
                        mapOf(
                            "poolEndpoint" to container.proxyUrl(),
                            "did" to nym.did,
                            "signingKeySeed" to nym.seedBase58,
                        ),
                    httpClient = httpClient,
                )

            val digestHex =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest("hello".toByteArray())
                    .joinToString(separator = "") { "%02x".format(it) }
            val payload =
                buildJsonObject {
                    put("vcId", JsonPrimitive("urn:uuid:integration-test"))
                    put("digest", JsonPrimitive(digestHex))
                }

            val written = client.writePayload(payload)
            assertNotNull(written.ref.txHash)

            val read = client.readPayload(written.ref)
            val readObj = read.payload.jsonObject
            assertEquals(payload["vcId"]!!.jsonPrimitive.content, readObj["vcId"]!!.jsonPrimitive.content)
            assertEquals(payload["digest"]!!.jsonPrimitive.content, readObj["digest"]!!.jsonPrimitive.content)

            val replacement = buildJsonObject { put("vcId", JsonPrimitive("urn:uuid:replacement")) }
            val newer = client.writePayload(replacement)
            assertEquals(replacement, client.readPayload(newer.ref).payload)
            assertFailsWith<TrustWeaveException.NotFound> { client.readPayload(written.ref) }
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

    private data class RegisteredNym(
        val did: String,
        val seedBase58: String,
    )
}
