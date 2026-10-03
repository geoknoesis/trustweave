package org.trustweave.did.registrar.client

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.KeyManagementMode
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.model.UpdateDidOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmsBasedRegistrarTest {
    /** Counts KMS key generations so a test can prove no orphaned key is created. */
    private class CountingKms(
        private val delegate: InMemoryKeyManagementService = InMemoryKeyManagementService(),
    ) : KeyManagementService by delegate {
        var generated = 0

        override suspend fun generateKey(
            algorithm: org.trustweave.kms.Algorithm,
            options: Map<String, Any?>,
        ) = delegate.generateKey(algorithm, options).also { generated++ }

        override suspend fun generateKey(
            algorithmName: String,
            options: Map<String, Any?>,
        ) = delegate.generateKey(algorithmName, options).also { generated++ }
    }

    /** Records the options it was created with and refuses update/deactivate. */
    private class RecordingMethod : DidMethod {
        override val method = "rec"
        var lastOptions: DidCreationOptions? = null

        override suspend fun createDid(options: DidCreationOptions): DidDocument {
            lastOptions = options
            return DidDocument(id = Did("did:rec:123"))
        }

        override suspend fun resolveDid(did: Did): DidResolutionResult = throw UnsupportedOperationException()

        override suspend fun updateDid(
            did: Did,
            updater: (DidDocument) -> DidDocument,
        ): DidDocument = throw UnsupportedOperationException("updates need a signed operation")

        override suspend fun deactivateDid(did: Did): Boolean = throw UnsupportedOperationException()
    }

    @Test
    fun `create delegates to the DID method and generates exactly one key`() =
        runBlocking<Unit> {
            val kms = CountingKms()
            val registrar = KmsBasedRegistrar(kms) { _, k -> DidKeyMockMethod(k) }

            val response = registrar.createDid("key", CreateDidOptions())

            assertEquals(OperationState.FINISHED, response.didState.state)
            assertTrue(response.didState.did!!.startsWith("did:key:"))
            assertEquals(1, kms.generated, "the registrar must not generate a second, orphaned key")
        }

    @Test
    fun `create without a method factory fails instead of fabricating a DID`() =
        runBlocking<Unit> {
            val response = KmsBasedRegistrar(InMemoryKeyManagementService()).createDid("key", CreateDidOptions())

            assertEquals(OperationState.FAILED, response.didState.state)
            assertNull(response.didState.did)
            assertTrue(response.didState.reason!!.contains("didMethodFactory"), response.didState.reason)
        }

    @Test
    fun `external secret mode is refused`() =
        runBlocking<Unit> {
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService()) { _, k -> DidKeyMockMethod(k) }
            val response =
                registrar.createDid("key", CreateDidOptions(keyManagementMode = KeyManagementMode.EXTERNAL_SECRET))
            assertEquals(OperationState.FAILED, response.didState.state)
        }

    @Test
    fun `method-specific string options are passed without JSON quotes`() =
        runBlocking<Unit> {
            val method = RecordingMethod()
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService()) { _, _ -> method }

            registrar.createDid(
                "rec",
                CreateDidOptions(
                    methodSpecificOptions =
                        mapOf(
                            "domain" to JsonPrimitive("example.com"),
                            "flag" to JsonPrimitive(true),
                            "count" to JsonPrimitive(3),
                        ),
                ),
            )

            val props = assertNotNull(method.lastOptions).additionalProperties
            assertEquals("example.com", props["domain"])
            assertEquals(true, props["flag"])
            assertEquals(3, props["count"])
        }

    @Test
    fun `update and deactivate are delegated to the DID method`() =
        runBlocking<Unit> {
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService()) { _, k -> DidKeyMockMethod(k) }
            val created = registrar.createDid("key", CreateDidOptions())
            val did = created.didState.did!!
            val document = created.didState.didDocument!!
            val updatedDocument = document.copy(controller = listOf(Did("did:example:controller")))

            val updated = registrar.updateDid(did, updatedDocument, UpdateDidOptions())
            assertEquals(OperationState.FINISHED, updated.didState.state)
            assertEquals(updatedDocument, updated.didState.didDocument)

            val deactivated = registrar.deactivateDid(did, DeactivateDidOptions())
            assertEquals(OperationState.FINISHED, deactivated.didState.state)

            // The mock method really removed it, so a second deactivation reports failure.
            val again = registrar.deactivateDid(did, DeactivateDidOptions())
            assertEquals(OperationState.FAILED, again.didState.state)
        }

    @Test
    fun `update and deactivate fail when no method is configured`() =
        runBlocking<Unit> {
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService())
            val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

            val updated = registrar.updateDid(did, DidDocument(id = Did(did)), UpdateDidOptions())
            assertEquals(OperationState.FAILED, updated.didState.state)

            val deactivated = registrar.deactivateDid(did, DeactivateDidOptions())
            assertEquals(OperationState.FAILED, deactivated.didState.state)
        }

    @Test
    fun `an unsupported update is reported as failed with the method's reason`() =
        runBlocking<Unit> {
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService()) { _, _ -> RecordingMethod() }

            val response = registrar.updateDid("did:rec:123", DidDocument(id = Did("did:rec:123")), UpdateDidOptions())

            assertEquals(OperationState.FAILED, response.didState.state)
            assertTrue(response.didState.reason!!.contains("does not support update"), response.didState.reason)
        }

    @Test
    fun `an update whose document id differs from the DID is rejected`() =
        runBlocking<Unit> {
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService()) { _, k -> DidKeyMockMethod(k) }

            val response = registrar.updateDid("did:key:abc", DidDocument(id = Did("did:key:other")), UpdateDidOptions())

            assertEquals(OperationState.FAILED, response.didState.state)
        }
}
