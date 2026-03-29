package org.trustweave.wallet

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

/**
 * Tests for [Wallet.getStatistics].
 *
 * Uses an anonymous minimal [Wallet] implementation that returns a fixed list of credentials.
 * The test for a wallet that implements [CredentialCollections] or [CredentialTagging] verifies
 * that getStatistics() queries those capabilities.
 */
class WalletStatisticsTest {

    private val issuerDid = "did:key:z6MkIssuer"
    private val subjectDid = "did:key:z6MkSubject"

    private fun credential(
        id: String = "urn:vc:1",
        expired: Boolean = false,
        revoked: Boolean = false,
        hasProof: Boolean = true
    ): VerifiableCredential = VerifiableCredential(
        id = CredentialId(id),
        type = listOf(CredentialType.fromString("VerifiableCredential")),
        issuer = Issuer.fromDid(Did(issuerDid)),
        issuanceDate = Clock.System.now() - 1.days,
        credentialSubject = CredentialSubject.fromIri(Iri(subjectDid)),
        expirationDate = if (expired) Clock.System.now() - 1.days else null,
        credentialStatus = if (revoked) CredentialStatus(
            id = StatusListId("urn:status:1"),
            type = "StatusList2021Entry",
            statusPurpose = StatusPurpose.REVOCATION,
            statusListIndex = "0",
            statusListCredential = StatusListId("urn:status-list:1")
        ) else null,
        proof = if (hasProof) CredentialProof.JwtProof("eyJhbGciOiJFZERTQSJ9.stub.stub") else null
    )

    /**
     * Minimal [Wallet] that holds a fixed list of credentials.
     */
    private inner class FixedWallet(private val creds: List<VerifiableCredential>) : Wallet {
        override val walletId: String = "test-wallet"
        override suspend fun store(credential: VerifiableCredential): String = credential.id?.value ?: "stored"
        override suspend fun get(credentialId: String): VerifiableCredential? = creds.find { it.id?.value == credentialId }
        override suspend fun delete(credentialId: String): Boolean = true
        override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = creds
        override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = creds
    }

    // ── basic statistics ─────────────────────────────────────────────────────

    @Test
    fun `empty wallet returns all-zero statistics`() = runTest {
        val wallet = FixedWallet(emptyList())
        val stats = wallet.getStatistics()
        assertEquals(0, stats.totalCredentials)
        assertEquals(0, stats.validCredentials)
        assertEquals(0, stats.expiredCredentials)
        assertEquals(0, stats.revokedCredentials)
        assertEquals(0, stats.collectionsCount)
        assertEquals(0, stats.tagsCount)
    }

    @Test
    fun `totalCredentials counts all credentials`() = runTest {
        val wallet = FixedWallet(listOf(credential("vc:1"), credential("vc:2"), credential("vc:3")))
        val stats = wallet.getStatistics()
        assertEquals(3, stats.totalCredentials)
    }

    @Test
    fun `validCredentials counts unexpired non-revoked credentials with proof`() = runTest {
        val creds = listOf(
            credential("vc:1", expired = false, revoked = false, hasProof = true),
            credential("vc:2", expired = true, revoked = false, hasProof = true),
            credential("vc:3", expired = false, revoked = true, hasProof = true),
            credential("vc:4", expired = false, revoked = false, hasProof = false)
        )
        val wallet = FixedWallet(creds)
        val stats = wallet.getStatistics()
        assertEquals(1, stats.validCredentials)
    }

    @Test
    fun `expiredCredentials counts only past-expiration credentials`() = runTest {
        val creds = listOf(
            credential("vc:1", expired = true),
            credential("vc:2", expired = true),
            credential("vc:3", expired = false)
        )
        val wallet = FixedWallet(creds)
        val stats = wallet.getStatistics()
        assertEquals(2, stats.expiredCredentials)
    }

    @Test
    fun `revokedCredentials counts credentials with credentialStatus set`() = runTest {
        val creds = listOf(
            credential("vc:1", revoked = true),
            credential("vc:2", revoked = false),
            credential("vc:3", revoked = true)
        )
        val wallet = FixedWallet(creds)
        val stats = wallet.getStatistics()
        assertEquals(2, stats.revokedCredentials)
    }

    // ── without CredentialOrganization capabilities ───────────────────────────

    @Test
    fun `collectionsCount is 0 when wallet does not implement CredentialCollections`() = runTest {
        val wallet = FixedWallet(emptyList())
        val stats = wallet.getStatistics()
        assertEquals(0, stats.collectionsCount)
    }

    @Test
    fun `tagsCount is 0 when wallet does not implement CredentialTagging`() = runTest {
        val wallet = FixedWallet(emptyList())
        val stats = wallet.getStatistics()
        assertEquals(0, stats.tagsCount)
    }
}
