package org.trustweave.cheqddid

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.net.InetSocketAddress
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheqdDidMethodTest {
    private var server: HttpServer? = null

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun api(
        status: Int,
        body: String,
    ): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun method(
        apiUrl: String? = null,
        anchorClient: BlockchainAnchorClient = InMemoryBlockchainAnchorClient(chainId = "cheqd:mainnet"),
    ) = CheqdDidMethod(
        InMemoryKeyManagementService(),
        anchorClient,
        CheqdDidConfig(cheqdApiUrl = apiUrl, network = "mainnet"),
    )

    @Test
    fun `an Ed25519 DID uses base58 of the first 16 public key bytes`() =
        runBlocking<Unit> {
            val document = method().createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))

            val x = document.verificationMethod.single().publicKeyJwk!!["x"] as String
            val expected =
                Base64
                    .getUrlDecoder()
                    .decode(x)
                    .copyOfRange(0, 16)
                    .encodeBase58()
            assertEquals("did:cheqd:mainnet:$expected", document.id.value)
        }

    @Test
    fun `a non-Ed25519 DID uses a UUID`() =
        runBlocking<Unit> {
            val document = method().createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))

            val id = document.id.value.removePrefix("did:cheqd:mainnet:")
            assertEquals(id, UUID.fromString(id).toString())
        }

    @Test
    fun `a created DID resolves from its anchor`() =
        runBlocking<Unit> {
            val method = method()
            val document = method.createDid(DidCreationOptions())

            val result = assertIs<DidResolutionResult.Success>(method.resolveDid(document.id))
            assertEquals(document.id, result.document.id)
        }

    @Test
    fun `an unknown DID without an API is reported as not found, saying why`() =
        runBlocking<Unit> {
            val result = method().resolveDid(Did("did:cheqd:mainnet:${UUID.randomUUID()}"))

            val failure = assertIs<DidResolutionResult.Failure.NotFound>(result)
            assertTrue(failure.reason!!.contains("cheqdApiUrl"), failure.reason)
        }

    @Test
    fun `an API document for a different DID is rejected`() =
        runBlocking<Unit> {
            val other = UUID.randomUUID()
            val url = api(200, """{"@context":"https://www.w3.org/ns/did/v1","id":"did:cheqd:mainnet:$other"}""")

            val result = method(apiUrl = url).resolveDid(Did("did:cheqd:mainnet:${UUID.randomUUID()}"))

            assertIs<DidResolutionResult.Failure>(result)
            assertFalse(result is DidResolutionResult.Success)
        }

    @Test
    fun `an API error is a failure, not a not-found`() =
        runBlocking<Unit> {
            val result = method(apiUrl = api(500, "boom")).resolveDid(Did("did:cheqd:mainnet:${UUID.randomUUID()}"))
            assertIs<DidResolutionResult.Failure.ResolutionError>(result)
        }

    @Test
    fun `a malformed identifier is invalid`() =
        runBlocking<Unit> {
            assertIs<DidResolutionResult.Failure.InvalidFormat>(method().resolveDid(Did("did:cheqd:mainnet:not-an-id")))
        }

    @Test
    fun `a failed chain read for an anchored DID fails instead of serving the cache`() =
        runBlocking<Unit> {
            val chain = InMemoryBlockchainAnchorClient(chainId = "cheqd:mainnet")
            var readable = true
            val flaky =
                object : BlockchainAnchorClient {
                    override suspend fun writePayload(
                        payload: JsonElement,
                        mediaType: String,
                    ): AnchorResult = chain.writePayload(payload, mediaType)

                    override suspend fun readPayload(ref: AnchorRef): AnchorResult =
                        if (readable) chain.readPayload(ref) else throw IllegalStateException("node down")
                }
            val method = method(anchorClient = flaky)
            val document = method.createDid(DidCreationOptions())
            assertIs<DidResolutionResult.Success>(method.resolveDid(document.id))

            readable = false
            assertIs<DidResolutionResult.Failure>(method.resolveDid(document.id))
        }

    @Test
    fun `config toString redacts the private key`() {
        val text = CheqdDidConfig(network = "mainnet", privateKey = "super-secret-mnemonic words").toString()
        assertFalse("super-secret" in text, text)
        assertTrue("<redacted>" in text, text)
    }
}
