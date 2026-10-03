package org.trustweave.soldid

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.util.decodeBase58
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyHandle
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.net.InetSocketAddress
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SolDidMethodTest {
    private var server: HttpServer? = null

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    /** A fake Solana JSON-RPC endpoint whose getAccountInfo returns [accountJson] as the account data. */
    private fun rpcServing(accountJson: String?): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { exchange ->
            val value =
                if (accountJson == null) {
                    "null"
                } else {
                    val data = Base64.getEncoder().encodeToString(accountJson.toByteArray())
                    """{"data":["$data","base64"],"owner":"11111111111111111111111111111111"}"""
                }
            val body = """{"jsonrpc":"2.0","id":1,"result":{"context":{"slot":1},"value":$value}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun method(rpcUrl: String = "http://127.0.0.1:1") =
        SolDidMethod(
            InMemoryKeyManagementService(),
            InMemoryBlockchainAnchorClient(chainId = "solana:mainnet-beta"),
            SolDidConfig.mainnet(rpcUrl),
        )

    @Test
    fun `address of the all-zero key is the system program address`() {
        val handle =
            KeyHandle(
                id = KeyId("k"),
                algorithm = "Ed25519",
                publicKeyJwk =
                    mapOf(
                        "kty" to "OKP",
                        "crv" to "Ed25519",
                        "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32)),
                    ),
            )
        assertEquals("11111111111111111111111111111111", solanaAddressFromKeyHandle(handle))
    }

    @Test
    fun `address is read from a multikey publicKeyMultibase when no JWK is present`() {
        val key = ByteArray(32) { it.toByte() }
        val multibase = "z" + (byteArrayOf(0xed.toByte(), 0x01) + key).encodeBase58()
        val handle = KeyHandle(id = KeyId("k"), algorithm = "Ed25519", publicKeyMultibase = multibase)
        assertEquals(key.encodeBase58(), solanaAddressFromKeyHandle(handle))
    }

    @Test
    fun `non-Ed25519 keys are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            solanaAddressFromKeyHandle(KeyHandle(id = KeyId("k"), algorithm = "secp256k1"))
        }
    }

    @Test
    fun `a key handle without a public key fails loudly`() {
        val ex = assertFailsWith<Exception> { solanaAddressFromKeyHandle(KeyHandle(id = KeyId("k"), algorithm = "Ed25519")) }
        assertTrue(ex.message!!.contains("no Ed25519 public key"), ex.message)
    }

    @Test
    fun `created DIDs embed the base58 public key and differ per key`() =
        runBlocking<Unit> {
            val method = method()
            val first = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            val second = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))

            assertTrue(first.id.value.startsWith("did:sol:"))
            assertNotEquals(first.id.value, second.id.value)
            val address = first.id.value.removePrefix("did:sol:")
            assertEquals(32, address.decodeBase58().size)
            val vmKey =
                first.verificationMethod
                    .single()
                    .publicKeyJwk!!["x"] as String
            assertEquals(address, Base64.getUrlDecoder().decode(vmKey).encodeBase58())
        }

    @Test
    fun `createDid rejects non-Ed25519 algorithms`() =
        runBlocking<Unit> {
            assertFailsWith<IllegalArgumentException> {
                method().createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))
            }
        }

    @Test
    fun `an on-chain document with a different id is rejected, not rewritten`() =
        runBlocking<Unit> {
            val address = ByteArray(32) { 7 }.encodeBase58()
            val other = ByteArray(32) { 9 }.encodeBase58()
            val rpc = rpcServing("""{"@context":"https://www.w3.org/ns/did/v1","id":"did:sol:$other"}""")

            val result = method(rpc).resolveDid(Did("did:sol:$address"))

            val failure = assertIs<DidResolutionResult.Failure>(result)
            assertTrue(failure.toString().contains("mismatch"), failure.toString())
        }

    @Test
    fun `an on-chain document for the requested DID resolves`() =
        runBlocking<Unit> {
            val address = ByteArray(32) { 7 }.encodeBase58()
            val rpc = rpcServing("""{"@context":"https://www.w3.org/ns/did/v1","id":"did:sol:$address"}""")

            val result = method(rpc).resolveDid(Did("did:sol:$address"))

            assertEquals("did:sol:$address", assertIs<DidResolutionResult.Success>(result).document.id.value)
        }

    @Test
    fun `a missing account resolves to notFound`() =
        runBlocking<Unit> {
            val address = ByteArray(32) { 7 }.encodeBase58()
            val result = method(rpcServing(null)).resolveDid(Did("did:sol:$address"))
            assertIs<DidResolutionResult.Failure.NotFound>(result)
        }

    @Test
    fun `an identifier that is not a 32-byte base58 address is invalid`() =
        runBlocking<Unit> {
            assertIs<DidResolutionResult.Failure.InvalidFormat>(method().resolveDid(Did("did:sol:not-base58-0OIl")))
        }
}

class SolDidRemovedAccountAndNetworkTest {
    private var server: HttpServer? = null

    @Volatile
    private var account: String? = null

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun rpc(): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { exchange ->
            val current = account
            val value =
                if (current == null) {
                    "null"
                } else {
                    val data = Base64.getEncoder().encodeToString(current.toByteArray())
                    """{"data":["$data","base64"],"owner":"11111111111111111111111111111111"}"""
                }
            val body = """{"jsonrpc":"2.0","id":1,"result":{"context":{"slot":1},"value":$value}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun method(config: (String) -> SolDidConfig = { SolDidConfig.mainnet(it) }) =
        SolDidMethod(
            InMemoryKeyManagementService(),
            InMemoryBlockchainAnchorClient(chainId = "solana:mainnet-beta"),
            config(rpc()),
        )

    private fun json(document: org.trustweave.did.model.DidDocument): String =
        kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonElement
                .serializer(),
            org.trustweave.did.representation.DidDocumentJsonProducer
                .toJsonObject(document, useV1_1Context = true),
        )

    @Test
    fun `a cached document is not served once the on-chain account is gone`() =
        runBlocking<Unit> {
            val method = method()
            val document = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            account = json(document)
            assertIs<DidResolutionResult.Success>(method.resolveDid(document.id))

            account = null // the account is removed on chain

            assertIs<DidResolutionResult.Failure.NotFound>(method.resolveDid(document.id))
        }

    @Test
    fun `a locally recorded deactivation is still reported when the account is gone`() =
        runBlocking<Unit> {
            val method = method()
            val document = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            account = json(document)
            assertTrue(method.deactivateDid(document.id))

            account = null

            assertIs<DidResolutionResult.Deactivated>(method.resolveDid(document.id))
        }

    @Test
    fun `a DID naming another network is refused`() =
        runBlocking<Unit> {
            val address = ByteArray(32) { 7 }.encodeBase58()
            val mainnet = method()
            assertIs<DidResolutionResult.Failure.InvalidFormat>(mainnet.resolveDid(Did("did:sol:devnet:$address")))
            assertIs<DidResolutionResult.Failure.InvalidFormat>(mainnet.resolveDid(Did("did:sol:testnet:$address")))

            val devnet = method { SolDidConfig.devnet(it) }
            // No network segment means mainnet, which a devnet instance must not answer.
            assertIs<DidResolutionResult.Failure.InvalidFormat>(devnet.resolveDid(Did("did:sol:$address")))
            account = null
            assertIs<DidResolutionResult.Failure.NotFound>(devnet.resolveDid(Did("did:sol:devnet:$address")))
        }
}

class SolDidConfigTest {
    @Test
    fun `toString redacts the private key`() {
        val config = SolDidConfig.mainnet(privateKey = "5Kb8kLf9zgWQnogidDA76MzPL6TsZZY36hWXMssSzNydYXYB9KF")
        val text = config.toString()
        assertTrue("5Kb8kLf9" !in text, text)
        assertTrue("<redacted>" in text, text)
    }
}
