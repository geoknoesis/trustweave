package org.trustweave.soldid

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.net.InetSocketAddress
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SolDidMethodHardeningTest {
    private var server: HttpServer? = null

    @Volatile private var account: String? = null

    @Volatile private var owner: String = "11111111111111111111111111111111"

    @Volatile private var lastRequest: String = ""

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun rpc(): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { exchange ->
            lastRequest = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val current = account
            val value =
                if (current == null) {
                    "null"
                } else {
                    val data = Base64.getEncoder().encodeToString(current.toByteArray())
                    """{"data":["$data","base64"],"owner":"$owner"}"""
                }
            val body = """{"jsonrpc":"2.0","id":1,"result":{"context":{"slot":1},"value":$value}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun method(programId: String? = null) =
        SolDidMethod(
            InMemoryKeyManagementService(),
            InMemoryBlockchainAnchorClient(chainId = "solana:mainnet-beta"),
            SolDidConfig(rpcUrl = rpc(), network = SolDidConfig.MAINNET, programId = programId),
        )

    private fun docJson(address: String) = """{"@context":"https://www.w3.org/ns/did/v1","id":"did:sol:$address"}"""

    @Test
    fun `a locally deactivated DID stays deactivated although its account still holds a live document`() =
        runBlocking<Unit> {
            val method = method()
            val document = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            account = docJson(document.id.value.removePrefix("did:sol:"))
            assertIs<DidResolutionResult.Success>(method.resolveDid(document.id))
            assertTrue(method.deactivateDid(document.id))

            assertIs<DidResolutionResult.Deactivated>(method.resolveDid(document.id))
        }

    @Test
    fun `an account owned by another program is not a DID account when a program is configured`() =
        runBlocking<Unit> {
            val address = ByteArray(32) { 7 }.encodeBase58()
            val program = ByteArray(32) { 5 }.encodeBase58()
            account = docJson(address)
            owner = ByteArray(32) { 6 }.encodeBase58()
            assertIs<DidResolutionResult.Failure>(method(program).resolveDid(Did("did:sol:$address")))

            owner = program
            assertIs<DidResolutionResult.Success>(method(program).resolveDid(Did("did:sol:$address")))
        }

    @Test
    fun `oversized account data is refused`() =
        runBlocking<Unit> {
            val address = ByteArray(32) { 7 }.encodeBase58()
            account = docJson(address).dropLast(1) + ",\"pad\":\"" + "A".repeat(300_000) + "\"}"
            assertIs<DidResolutionResult.Failure>(method().resolveDid(Did("did:sol:$address")))
        }

    @Test
    fun `getAccountInfo asks for the configured commitment`() =
        runBlocking<Unit> {
            val address = ByteArray(32) { 7 }.encodeBase58()
            account = docJson(address)
            method().resolveDid(Did("did:sol:$address"))
            assertTrue("\"commitment\":\"finalized\"" in lastRequest.replace(" ", ""), lastRequest)
            assertEquals(true, "getAccountInfo" in lastRequest)
        }
}
