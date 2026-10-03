package org.trustweave.credential.revocation

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.model.StatusPurpose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `releaseStatusListIndex`: only a never-issued, never-touched index may be handed back. */
class InMemoryReleaseStatusListIndexTest {
    private val issuer = "did:example:issuer"

    @Test
    fun `an unused index is released and handed out again`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            val list = manager.createStatusList(issuer, StatusPurpose.REVOCATION)
            val first = manager.assignCredentialIndex("cred-a", list)

            assertTrue(manager.releaseStatusListIndex(list, first))
            assertEquals(first, manager.assignCredentialIndex("cred-b", list))
        }

    @Test
    fun `a second release is a no-op and unknown inputs are refused`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            val list = manager.createStatusList(issuer, StatusPurpose.REVOCATION)
            val index = manager.assignCredentialIndex("cred-a", list)

            assertTrue(manager.releaseStatusListIndex(list, index))
            assertFalse(manager.releaseStatusListIndex(list, index))
            assertFalse(manager.releaseStatusListIndex(list, 999))
            assertFalse(manager.releaseStatusListIndex(list, -1))
        }

    @Test
    fun `an index whose status was ever written is never released`() =
        runBlocking<Unit> {
            val manager = RevocationManagers.default()
            val list = manager.createStatusList(issuer, StatusPurpose.REVOCATION)
            val index = manager.assignCredentialIndex("cred-a", list)
            manager.revokeCredential("cred-a", list)
            assertFalse(manager.releaseStatusListIndex(list, index), "revoked")

            // Revoke then clear: the bit is back to 0 but the credential was clearly in use.
            manager.unrevokeCredential("cred-a", list)
            assertFalse(manager.releaseStatusListIndex(list, index), "revoked then cleared")
        }
}
