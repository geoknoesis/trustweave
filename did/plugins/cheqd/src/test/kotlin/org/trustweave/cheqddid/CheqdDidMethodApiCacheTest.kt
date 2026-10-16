package org.trustweave.cheqddid

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** An API-resolved document is revalidated on every resolve: the cache must not outlive the registry's answer. */
class CheqdDidMethodApiCacheTest {
    private var server: HttpServer? = null

    @Volatile private var status = 200

    @Volatile private var deactivated = false
    private val hits = AtomicInteger()
    private val did = "did:cheqd:mainnet:" + ByteArray(16) { 3 }.encodeBase58()

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun method(): CheqdDidMethod {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { exchange ->
            hits.incrementAndGet()
            val body =
                if (status == 200) {
                    """{"didDocument":{"@context":"https://www.w3.org/ns/did/v1","id":"$did"},"didDocumentMetadata":{"deactivated":$deactivated}}"""
                } else {
                    ""
                }.toByteArray()
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            exchange.responseBody.use { if (body.isNotEmpty()) it.write(body) }
        }
        s.start()
        server = s
        return CheqdDidMethod(
            InMemoryKeyManagementService(),
            InMemoryBlockchainAnchorClient(chainId = "cheqd:mainnet"),
            CheqdDidConfig(cheqdApiUrl = "http://127.0.0.1:${s.address.port}", network = "mainnet"),
        )
    }

    @Test
    fun `a second resolve asks the API again instead of serving the cached copy`() =
        runBlocking<Unit> {
            val m = method()
            assertIs<DidResolutionResult.Success>(m.resolveDid(Did(did)))
            assertIs<DidResolutionResult.Success>(m.resolveDid(Did(did)))
            assertEquals(2, hits.get())
        }

    @Test
    fun `a document removed from the registry stops resolving`() =
        runBlocking<Unit> {
            val m = method()
            assertIs<DidResolutionResult.Success>(m.resolveDid(Did(did)))
            status = 404
            assertIs<DidResolutionResult.Failure.NotFound>(m.resolveDid(Did(did)))
        }

    @Test
    fun `a registry outage after an API resolve is an error, not a stale success`() =
        runBlocking<Unit> {
            val m = method()
            assertIs<DidResolutionResult.Success>(m.resolveDid(Did(did)))
            status = 500
            assertIs<DidResolutionResult.Failure>(m.resolveDid(Did(did)))
        }

    @Test
    fun `a DID deactivated on the registry after being cached resolves as deactivated`() =
        runBlocking<Unit> {
            val m = method()
            assertIs<DidResolutionResult.Success>(m.resolveDid(Did(did)))
            deactivated = true
            assertIs<DidResolutionResult.Deactivated>(m.resolveDid(Did(did)))
        }
}
