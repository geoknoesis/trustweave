package org.trustweave.wallet

import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.vc.CredentialStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredRecordHelpersTest {
    private val plain = sampleCredential("plain")
    private val withStatus =
        plain.copy(credentialStatus = CredentialStatus(id = StatusListId("urn:status:1"), type = "StatusList2021Entry"))

    @Test
    fun `an empty filter matches everything`() {
        assertTrue(matchesOfflineStatusFilter(plain, CredentialFilter()))
        assertTrue(matchesOfflineStatusFilter(withStatus, CredentialFilter()))
    }

    @Test
    fun `hasStatusEntry filters on the presence of a status reference only`() {
        assertTrue(matchesOfflineStatusFilter(withStatus, CredentialFilter(hasStatusEntry = true)))
        assertFalse(matchesOfflineStatusFilter(plain, CredentialFilter(hasStatusEntry = true)))
        assertTrue(matchesOfflineStatusFilter(plain, CredentialFilter(hasStatusEntry = false)))
        assertFalse(matchesOfflineStatusFilter(withStatus, CredentialFilter(hasStatusEntry = false)))
    }

    @Test
    fun `revoked filter treats a status entry as unknown and matches neither value`() {
        assertFalse(matchesOfflineStatusFilter(withStatus, CredentialFilter(revoked = true)))
        assertFalse(matchesOfflineStatusFilter(withStatus, CredentialFilter(revoked = false)))
    }

    @Test
    fun `revoked filter on a credential without status can only match not-revoked`() {
        assertTrue(matchesOfflineStatusFilter(plain, CredentialFilter(revoked = false)))
        assertFalse(matchesOfflineStatusFilter(plain, CredentialFilter(revoked = true)))
    }

    @Test
    fun `recovery result is complete only when nothing failed to read`() {
        val record = StoredCredentialRecord("h1", plain)
        assertTrue(CredentialRecoveryResult(listOf(record), emptyList()).complete)

        val partial = CredentialRecoveryResult(listOf(record), listOf(CredentialReadFailure("h2", "corrupt")))
        assertFalse(partial.complete)
        assertEquals("corrupt", partial.failures.single().reason)
    }

    @Test
    fun `a page without a next cursor marks the end of the listing`() {
        assertNull(CredentialPage(emptyList(), null).nextCursor)
        assertEquals("c2", CredentialPage(emptyList(), "c2").nextCursor)
    }

    @Test
    fun `model defaults are empty and non-null`() {
        val collection = CredentialCollection("c", "Name")
        assertEquals(0, collection.credentialCount)
        assertNull(collection.description)

        val metadata = CredentialMetadata("cred")
        assertTrue(metadata.tags.isEmpty() && metadata.metadata.isEmpty())
        assertNull(metadata.notes)

        assertEquals(WalletStatistics(), WalletStatistics(0, 0, 0, 0, 0, 0, 0, 0))
        assertNull(KeyInfo("k", "Ed25519").publicKeyJwk)
    }
}
