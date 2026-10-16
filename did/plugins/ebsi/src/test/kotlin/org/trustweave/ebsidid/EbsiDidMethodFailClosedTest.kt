package org.trustweave.ebsidid

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertIs

/** The registry is the authority: a cached copy never answers for it, and a failed write is never a success. */
class EbsiDidMethodFailClosedTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun method(token: String? = "tok") =
        EbsiDidMethod(
            InMemoryKeyManagementService(),
            EbsiDidConfig(
                apiBaseUrl = server.url("").toString().trimEnd('/'),
                network = EbsiNetwork.PILOT,
                bearerToken = token,
                timeoutSeconds = 5,
            ),
            OkHttpClient(),
        )

    private fun docJson(did: String) = """{"@context":["https://www.w3.org/ns/did/v1"],"id":"$did","verificationMethod":[]}"""

    @Test
    fun `a 404 from the registry is notFound even when this instance cached the document`() =
        runBlocking<Unit> {
            val m = method()
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"ok"}"""))
            val doc = m.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })
            server.enqueue(MockResponse().setResponseCode(404))
            assertIs<DidResolutionResult.Failure.NotFound>(m.resolveDid(doc.id))
        }

    @Test
    fun `a registry outage is a resolution error, not a stale cached success`() =
        runBlocking<Unit> {
            val m = method()
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"ok"}"""))
            val doc = m.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })
            server.enqueue(MockResponse().setResponseCode(503))
            assertIs<DidResolutionResult.Failure.ResolutionError>(m.resolveDid(doc.id))
        }

    @Test
    fun `deactivation without a bearer token fails instead of only evicting the cache`() =
        runBlocking<Unit> {
            val m = method(token = null)
            val doc = m.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })
            assertThrows<EbsiException> { m.deactivateDid(doc.id) }
        }

    @Test
    fun `a JSON-RPC error on HTTP 200 fails the registration`() =
        runBlocking<Unit> {
            val m = method()
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("""{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"nope"}}"""),
            )
            assertThrows<EbsiException> { m.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 }) }
        }

    @Test
    fun `a deactivated DID resolves as Deactivated even though the registry still serves a document`() =
        runBlocking<Unit> {
            val m = method()
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"ok"}"""))
            val doc = m.createDid(didCreationOptions { algorithm = KeyAlgorithm.ED25519 })
            server.enqueue(MockResponse().setResponseCode(200).setBody(docJson(doc.id.value))) // resolve inside deactivate
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // PATCH
            kotlin.test.assertTrue(m.deactivateDid(doc.id))
            server.enqueue(MockResponse().setResponseCode(200).setBody(docJson(doc.id.value)))
            assertIs<DidResolutionResult.Deactivated>(m.resolveDid(doc.id))
        }

    @Test
    fun `an oversized registry response is refused`() =
        runBlocking<Unit> {
            val m = method()
            val pad = "A".repeat(3 * 1024 * 1024)
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"did:ebsi:zAbc","pad":"$pad"}"""))
            assertIs<DidResolutionResult.Failure>(
                m.resolveDid(
                    org.trustweave.did.identifiers
                        .Did("did:ebsi:zAbc"),
                ),
            )
        }
}
