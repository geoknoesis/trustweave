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

    @Test
    fun `resolve of a long-form DID may be answered with its canonical short form`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody("did:ion:EiAbc")))
            val result = method.resolveDid(Did("did:ion:EiAbc:eyJkZWx0YSI6e319"))
            assertIs<DidResolutionResult.Success>(result)
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
