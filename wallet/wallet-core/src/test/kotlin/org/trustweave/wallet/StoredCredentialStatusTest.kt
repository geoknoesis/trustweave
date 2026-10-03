package org.trustweave.wallet

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredCredentialStatusTest {
    private fun credential(withStatus: Boolean) =
        VerifiableCredential(
            id = CredentialId("urn:vc:status"),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.fromDid(Did("did:key:z6MkIssuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromIri(Iri("did:key:z6MkSubject")),
            credentialStatus =
                if (withStatus) {
                    CredentialStatus(
                        id = StatusListId("urn:status:1"),
                        type = "StatusList2021Entry",
                        statusPurpose = StatusPurpose.REVOCATION,
                        statusListIndex = "0",
                        statusListCredential = StatusListId("urn:status-list:1"),
                    )
                } else {
                    null
                },
        )

    @Test
    fun credentialWithoutStatusEntryIsActiveWithoutAskingTheResolver() =
        runTest {
            val resolver = WalletStatusResolver { error("must not be consulted") }
            assertEquals(StoredCredentialStatus.ACTIVE, resolveStoredStatus(credential(withStatus = false), resolver))
        }

    @Test
    fun statusEntryWithoutResolverIsUnknownNotRevoked() =
        runTest { assertEquals(StoredCredentialStatus.UNKNOWN, resolveStoredStatus(credential(withStatus = true), null)) }

    @Test
    fun statusEntryUsesTheResolverAnswer() =
        runTest {
            val revoked = WalletStatusResolver { StoredCredentialStatus.REVOKED }
            assertEquals(StoredCredentialStatus.REVOKED, resolveStoredStatus(credential(withStatus = true), revoked))
        }

    @Test
    fun offlineFilterTreatsUnresolvedStatusAsMatchingNeitherRevokedValue() {
        val withEntry = credential(withStatus = true)
        val without = credential(withStatus = false)

        assertFalse(matchesOfflineStatusFilter(withEntry, CredentialFilter(revoked = true)))
        assertFalse(matchesOfflineStatusFilter(withEntry, CredentialFilter(revoked = false)))
        assertTrue(matchesOfflineStatusFilter(without, CredentialFilter(revoked = false)))
        assertFalse(matchesOfflineStatusFilter(without, CredentialFilter(revoked = true)))
        assertTrue(matchesOfflineStatusFilter(withEntry, CredentialFilter()))
    }

    @Test
    fun hasStatusEntryFilterIsMetadataOnly() {
        assertTrue(matchesOfflineStatusFilter(credential(true), CredentialFilter(hasStatusEntry = true)))
        assertFalse(matchesOfflineStatusFilter(credential(false), CredentialFilter(hasStatusEntry = true)))
        assertTrue(matchesOfflineStatusFilter(credential(false), CredentialFilter(hasStatusEntry = false)))
    }

    @Test
    fun partialRecoveryIsNeverReportedComplete() {
        val record = StoredCredentialRecord("h1", credential(false))
        assertTrue(CredentialRecoveryResult(listOf(record), emptyList()).complete)
        val partial = CredentialRecoveryResult(listOf(record), listOf(CredentialReadFailure("h2", "corrupt")))
        assertFalse(partial.complete)
        assertNull(partial.failures.firstOrNull { it.storageHandle == "h1" })
    }
}
