package org.trustweave.did.base

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals

class AbstractDidMethodUpdateIdTest {
    private class M : AbstractDidMethod("test", InMemoryKeyManagementService()) {
        override suspend fun createDid(options: DidCreationOptions): DidDocument = throw UnsupportedOperationException()

        override suspend fun resolveDid(did: Did): DidResolutionResult = throw UnsupportedOperationException()

        suspend fun seed(id: String) = storeDocument(id, DidDocument(id = Did(id)))

        fun current(id: String) = getStoredDocument(id)
    }

    @Test
    fun `an update that changes the document id is refused and changes nothing`() =
        runBlocking<Unit> {
            val m = M()
            m.seed("did:test:a")
            assertThrows<TrustWeaveException> {
                m.updateDid(Did("did:test:a")) { DidDocument(id = Did("did:test:b")) }
            }
            assertEquals(Did("did:test:a"), m.current("did:test:a")?.id)
        }

    @Test
    fun `an update that keeps the id is applied`() =
        runBlocking<Unit> {
            val m = M()
            m.seed("did:test:a")
            m.updateDid(Did("did:test:a")) { it.copy(controller = listOf(Did("did:test:ctl"))) }
            assertEquals(listOf(Did("did:test:ctl")), m.current("did:test:a")?.controller)
        }
}
