package org.trustweave.peerdid

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PeerDidMethodSelfCertifyingTest {
    private fun method() = PeerDidMethod(InMemoryKeyManagementService())

    @Test
    fun `an update cannot swap the keys of a self-certifying peer DID`() =
        runBlocking<Unit> {
            val m = method()
            val first = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            val second = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            assertThrows<Exception> {
                m.updateDid(first.id) { it.copy(verificationMethod = second.verificationMethod) }
            }
            val after = assertIs<DidResolutionResult.Success>(m.resolveDid(first.id))
            assertEquals(first.verificationMethod, after.document.verificationMethod)
        }

    @Test
    fun `an update cannot change the document id`() =
        runBlocking<Unit> {
            val m = method()
            val first = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            val second = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            assertThrows<Exception> { m.updateDid(first.id) { second } }
        }

    @Test
    fun `a deactivated peer DID does not resolve live again from its own identifier`() =
        runBlocking<Unit> {
            val m = method()
            val doc = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            assertTrue(m.deactivateDid(doc.id))
            assertIs<DidResolutionResult.Deactivated>(m.resolveDid(doc.id))
        }
}
