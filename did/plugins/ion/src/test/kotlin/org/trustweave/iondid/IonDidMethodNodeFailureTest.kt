package org.trustweave.iondid

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.sidetree.InMemorySidetreeKeyStore
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** A node that rejects an operation must not cost the caller their keys or make the call look successful. */
class IonDidMethodNodeFailureTest {
    private lateinit var server: MockWebServer
    private lateinit var keyStore: InMemorySidetreeKeyStore
    private lateinit var method: IonDidMethod

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        keyStore = InMemorySidetreeKeyStore()
        method =
            IonDidMethod(
                InMemoryKeyManagementService(),
                IonDidConfig(ionNodeUrl = server.url("/").toString().trimEnd('/'), timeoutSeconds = 5),
                OkHttpClient(),
                keyStore,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun docBody(
        id: String,
        deactivated: Boolean = false,
    ) = """{"didDocument":{"@context":["https://www.w3.org/ns/did/v1"],"id":"$id","verificationMethod":[]},""" +
        """"didDocumentMetadata":{"deactivated":$deactivated}}"""

    private suspend fun created(): DidDocument {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        return method.createDid(DidCreationOptions())
    }

    private fun suffix(doc: DidDocument) =
        doc.id.value
            .removePrefix("did:ion:")
            .substringBefore(':')

    @Test
    fun `a rejected update keeps the previous update key and fails`() =
        runBlocking<Unit> {
            val doc = created()
            val before = assertNotNull(keyStore.get(suffix(doc)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody(doc.id.value)))
            server.enqueue(MockResponse().setResponseCode(400).setBody("bad"))
            assertThrows<TrustWeaveException> { method.updateDid(doc.id) { it } }
            assertEquals(before, keyStore.get(suffix(doc)))
        }

    @Test
    fun `a rejected recover keeps the previous keys and fails`() =
        runBlocking<Unit> {
            val doc = created()
            val before = assertNotNull(keyStore.get(suffix(doc)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody(doc.id.value)))
            server.enqueue(MockResponse().setResponseCode(500).setBody("bad"))
            assertThrows<TrustWeaveException> { method.recoverDid(doc.id) { it } }
            assertEquals(before, keyStore.get(suffix(doc)))
        }

    @Test
    fun `a rejected deactivate keeps the keys and does not report success`() =
        runBlocking<Unit> {
            val doc = created()
            val before = assertNotNull(keyStore.get(suffix(doc)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody(doc.id.value)))
            server.enqueue(MockResponse().setResponseCode(503).setBody("bad"))
            val outcome = runCatching { method.deactivateDid(doc.id) }
            assert(outcome.isFailure || outcome.getOrNull() == false) { "deactivate must not report success" }
            assertEquals(before, keyStore.get(suffix(doc)))
        }

    @Test
    fun `a node that reports the DID deactivated resolves as Deactivated`() =
        runBlocking<Unit> {
            server.enqueue(MockResponse().setResponseCode(200).setBody(docBody("did:ion:EiAbc", deactivated = true)))
            val result = method.resolveDid(Did("did:ion:EiAbc"))
            assertIs<DidResolutionResult.Deactivated>(result)
        }
}
