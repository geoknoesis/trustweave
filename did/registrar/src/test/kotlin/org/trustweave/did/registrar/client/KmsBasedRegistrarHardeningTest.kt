package org.trustweave.did.registrar.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmsBasedRegistrarHardeningTest {
    private class Exploding : DidMethod {
        override val method = "boom"

        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw IllegalStateException("jdbc:postgresql://db.internal:5432/keys password=hunter2\nINJECTED")

        override suspend fun resolveDid(did: Did): DidResolutionResult = throw UnsupportedOperationException()

        override suspend fun updateDid(
            did: Did,
            updater: (DidDocument) -> DidDocument,
        ): DidDocument = throw UnsupportedOperationException()

        override suspend fun deactivateDid(did: Did): Boolean = throw UnsupportedOperationException()
    }

    @Test
    fun `an internal failure is not echoed into the response or the stored job`() =
        runBlocking<Unit> {
            val jobs = InMemoryJobStorage()
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService(), jobs) { _, _ -> Exploding() }

            val response = registrar.createDid("boom", CreateDidOptions())

            assertEquals(OperationState.FAILED, response.didState.state)
            val everything = response.toString() + jobs.get(response.jobId!!).toString()
            assertFalse("hunter2" in everything, everything)
            assertFalse("db.internal" in everything, everything)
            assertFalse("INJECTED" in everything, everything)
        }

    @Test
    fun `secrets are returned to the caller once and never stored`() =
        runBlocking<Unit> {
            val jobs = InMemoryJobStorage()
            val registrar = KmsBasedRegistrar(InMemoryKeyManagementService(), jobs) { _, kms -> DidKeyMockMethod(kms) }

            val response = registrar.createDid("key", CreateDidOptions(returnSecrets = true))

            assertNotNull(response.didState.secret)
            assertNull(jobs.get(response.jobId!!)?.didState?.secret)
            assertTrue(jobs.exists(response.jobId!!))
        }
}
