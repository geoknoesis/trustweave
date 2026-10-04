package org.trustweave.plcdid

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlcDidMethodTest {
    private companion object {
        const val DID = "did:plc:ewvi7nxzyoun6zhxrhs64oiz"
    }

    private var server: HttpServer? = null
    private val lastPath = AtomicReference<String>()

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun directory(
        status: Int,
        body: String = "",
    ): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { exchange ->
            lastPath.set(exchange.requestURI.path)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun method(url: String?) = PlcDidMethod(InMemoryKeyManagementService(), PlcDidConfig(plcRegistryUrl = url))

    @Test
    fun `resolution reads the document from the directory root path`() =
        runBlocking<Unit> {
            val url = directory(200, """{"@context":["https://www.w3.org/ns/did/v1"],"id":"$DID"}""")

            val result = assertIs<DidResolutionResult.Success>(method(url).resolveDid(Did(DID)))

            assertEquals(DID, result.document.id.value)
            assertEquals("/$DID", lastPath.get())
        }

    @Test
    fun `a document for another DID is rejected, not rewritten`() =
        runBlocking<Unit> {
            val url = directory(200, """{"id":"did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"}""")

            val result = method(url).resolveDid(Did(DID))

            val failure = assertIs<DidResolutionResult.Failure.ResolutionError>(result)
            assertTrue(failure.reason.contains("mismatch"), failure.reason)
        }

    @Test
    fun `404 is not found and 410 resolves as deactivated`() =
        runBlocking<Unit> {
            assertIs<DidResolutionResult.Failure.NotFound>(method(directory(404)).resolveDid(Did(DID)))
            stop()
            val tombstoned = assertIs<DidResolutionResult.Deactivated>(method(directory(410)).resolveDid(Did(DID)))
            assertTrue(tombstoned.documentMetadata.deactivated)
        }

    @Test
    fun `an oversized directory response is refused`() =
        runBlocking<Unit> {
            val huge = """{"id":"$DID","pad":"${"a".repeat(1_100_000)}"}"""
            val result = method(directory(200, huge)).resolveDid(Did(DID))
            val failure = assertIs<DidResolutionResult.Failure.ResolutionError>(result)
            assertTrue(failure.reason.contains("maximum allowed size"), failure.reason)
        }

    @Test
    fun `a redirect from the directory is not followed`() =
        runBlocking<Unit> {
            val target = directory(200, """{"id":"$DID"}""")
            stop()
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            s.createContext("/") { exchange ->
                exchange.responseHeaders.add("Location", "$target/$DID")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            s.start()
            server = s
            val result = method("http://127.0.0.1:${s.address.port}").resolveDid(Did(DID))
            val failure = assertIs<DidResolutionResult.Failure.ResolutionError>(result)
            assertTrue(failure.reason.contains("redirect", ignoreCase = true), failure.reason)
        }

    @Test
    fun `a directory error is a resolution error`() =
        runBlocking<Unit> {
            assertIs<DidResolutionResult.Failure.ResolutionError>(method(directory(500)).resolveDid(Did(DID)))
        }

    @Test
    fun `a malformed identifier is invalid`() =
        runBlocking<Unit> {
            assertIs<DidResolutionResult.Failure.InvalidFormat>(method("http://127.0.0.1:1").resolveDid(Did("did:plc:NOT-base32")))
        }

    @Test
    fun `create, update and deactivate fail loudly instead of faking registration`() =
        runBlocking<Unit> {
            val method = method(null)
            val create = assertFailsWith<TrustWeaveException> { method.createDid(DidCreationOptions()) }
            assertEquals(PlcDidMethod.NOT_IMPLEMENTED, create.code)
            assertFailsWith<TrustWeaveException> { method.updateDid(Did(DID)) { it } }
            assertFailsWith<TrustWeaveException> { method.deactivateDid(Did(DID)) }
        }
}
