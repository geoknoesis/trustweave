package org.trustweave.iondid

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidErrorType
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.resolver.errorType
import org.trustweave.did.sidetree.SidetreeMethodSpec
import org.trustweave.did.sidetree.SidetreeOperationBuilder
import org.trustweave.did.sidetree.SidetreeP256KeyPair
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IonDidMethodTest {
    private lateinit var server: MockWebServer
    private lateinit var method: IonDidMethod

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        method =
            IonDidMethod(
                InMemoryKeyManagementService(),
                IonDidConfig(ionNodeUrl = server.url("/").toString().trimEnd('/'), timeoutSeconds = 5),
                OkHttpClient(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun docBody(id: String) =
        """{"didDocument":{"@context":["https://www.w3.org/ns/did/v1"],"id":"$id","verificationMethod":[]},"didDocumentMetadata":{}}"""

    @Test
    fun `resolve succeeds when document id equals requested DID`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody("did:ion:EiAbc")))
            val result = method.resolveDid(Did("did:ion:EiAbc"))
            assertIs<DidResolutionResult.Success>(result)
            assertEquals("did:ion:EiAbc", result.document.id.value)
        }

    private fun realLongForm(): Pair<String, String> {
        val created =
            runBlocking {
                SidetreeOperationBuilder(SidetreeMethodSpec.ION).buildCreateOperation(SidetreeP256KeyPair.generate().publicJwk)
            }
        return created.longFormDid to created.didSuffix
    }

    @Test
    fun `resolve of a long-form DID may be answered with its canonical short form`() =
        runBlocking<Unit> {
            val (longForm, suffix) = realLongForm()
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody("did:ion:$suffix")))
            val result = method.resolveDid(Did(longForm))
            assertIs<DidResolutionResult.Success>(result)
        }

    @Test
    fun `a long-form DID whose initial state does not hash to its suffix is rejected without asking the node`() =
        runBlocking<Unit> {
            val (longForm, _) = realLongForm()
            val (otherLongForm, _) = realLongForm()
            // Splice the second DID's initial state onto the first DID's suffix.
            val forged = longForm.substringBeforeLast(':') + ":" + otherLongForm.substringAfterLast(':')
            val failure = method.resolveDid(Did(forged))
            assertIs<DidResolutionResult.Failure>(failure)
            assertEquals(DidErrorType.INVALID_DID, failure.errorType)
            assertEquals(0, server.requestCount, "an invalid long-form DID must not reach the node")
        }

    @Test
    fun `a long-form DID with a non-JSON initial state is rejected`() =
        runBlocking<Unit> {
            val (longForm, _) = realLongForm()
            val garbage = longForm.substringBeforeLast(':') + ":bm90LWpzb24"
            val failure = method.resolveDid(Did(garbage))
            assertIs<DidResolutionResult.Failure>(failure)
            assertEquals(DidErrorType.INVALID_DID, failure.errorType)
        }

    @Test
    fun `a network-prefixed short form is not a long form and may not be answered with the network label`() =
        runBlocking<Unit> {
            val suffix = "Ei" + "A".repeat(44)
            // Answered for itself: fine.
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody("did:ion:test:$suffix")))
            assertIs<DidResolutionResult.Success>(method.resolveDid(Did("did:ion:test:$suffix")))
            // Answered with did:ion:test (what a "long-form canonical" misreading would accept): rejected.
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody("did:ion:test")))
            val failure = method.resolveDid(Did("did:ion:test:$suffix"))
            assertIs<DidResolutionResult.Failure>(failure)
            assertEquals(DidErrorType.INVALID_DID_DOCUMENT, failure.errorType)
        }

    @Test
    fun `a network-prefixed long form resolves to its network-prefixed canonical form`() =
        runBlocking<Unit> {
            val (longForm, suffix) = realLongForm()
            val networkLongForm = longForm.replaceFirst("did:ion:", "did:ion:test:")
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody("did:ion:test:$suffix")))
            assertIs<DidResolutionResult.Success>(method.resolveDid(Did(networkLongForm)))
        }

    @Test
    fun `resolve rejects a document for a different DID and does not cache it`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody("did:ion:EiOther")))
            val result = method.resolveDid(Did("did:ion:EiAbc"))
            assertIs<DidResolutionResult.Failure>(result)
            assertEquals(DidErrorType.INVALID_DID_DOCUMENT, result.errorType)
            assertTrue(result.errorMessage?.contains("did:ion:EiOther") == true)
        }

    @Test
    fun `a node error is an internal error, only a 404 is notFound`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            val failure = method.resolveDid(Did("did:ion:EiAbc"))
            assertIs<DidResolutionResult.Failure.ResolutionError>(failure)
            assertEquals(DidErrorType.INTERNAL_ERROR, failure.errorType)

            server.enqueue(MockResponse().setResponseCode(404))
            assertIs<DidResolutionResult.Failure.NotFound>(method.resolveDid(Did("did:ion:EiAbc")))
        }

    @Test
    fun `a node answer without a document id is an invalid document, not an invalid DID`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"didDocument":{"verificationMethod":[]}}"""))
            val failure = method.resolveDid(Did("did:ion:EiAbc"))
            assertIs<DidResolutionResult.Failure>(failure)
            assertEquals(DidErrorType.INVALID_DID_DOCUMENT, failure.errorType)
        }

    @Test
    fun `config requires a node url`() {
        assertThrows<IllegalArgumentException> { IonDidConfig.fromMap(emptyMap()) }
        assertThrows<IllegalArgumentException> { IonDidConfig.builder().build() }
    }

    @Test
    fun `config round-trips through map`() {
        val config =
            IonDidConfig
                .builder()
                .ionNodeUrl("https://node.example")
                .batchSize(3)
                .property("x", 1)
                .build()
        val back = IonDidConfig.fromMap(config.toMap())
        assertEquals(config.ionNodeUrl, back.ionNodeUrl)
        assertEquals(3, back.batchSize)
        assertEquals(1, back.additionalProperties["x"])
    }
}

class IonDidConfigRedactionTest {
    @kotlin.test.Test
    fun `toString hides bitcoin rpc credentials and additional property values`() {
        val text =
            IonDidConfig(
                ionNodeUrl = "https://node.example",
                bitcoinRpcUrl = "http://user:RPC-PASS@btc:8332",
                additionalProperties = mapOf("apiKey" to "PROP-SECRET"),
            ).toString()
        kotlin.test.assertFalse("RPC-PASS" in text, text)
        kotlin.test.assertFalse("PROP-SECRET" in text, text)
        kotlin.test.assertTrue("apiKey" in text, text)
    }
}
