package org.trustweave.wallet

import kotlinx.coroutines.test.runTest
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class CredentialQueryPredicatesTest {
    private val future = Clock.System.now() + 30.days
    private val past = Clock.System.now() - 1.days

    private fun withStatus(base: VerifiableCredential): VerifiableCredential =
        base.copy(credentialStatus = CredentialStatus(id = StatusListId("urn:status:1"), type = "StatusList2021Entry"))

    private fun predicate(configure: CredentialQueryBuilder.() -> Unit) = CredentialQueryBuilder().apply(configure).toPredicate()

    @Test
    fun `byTypes matches a credential holding any of the listed types`() {
        val p = predicate { byTypes("Degree", "License") }

        assertTrue(p(sampleCredential("a", types = listOf("VerifiableCredential", "License"))))
        assertFalse(p(sampleCredential("b", types = listOf("VerifiableCredential", "Passport"))))
    }

    @Test
    fun `bySubject matches the subject identifier exactly`() {
        val p = predicate { bySubject("did:key:alice") }

        assertTrue(p(sampleCredential("a", subject = "did:key:alice")))
        assertFalse(p(sampleCredential("b", subject = "did:key:alice2")))
    }

    @Test
    fun `byIssuer and byType combine as a conjunction`() {
        val p =
            predicate {
                byIssuer("did:key:issuerA")
                byType("Degree")
            }

        assertTrue(p(sampleCredential("a", issuer = "did:key:issuerA", types = listOf("Degree"))))
        assertFalse(p(sampleCredential("b", issuer = "did:key:issuerB", types = listOf("Degree"))))
        assertFalse(p(sampleCredential("c", issuer = "did:key:issuerA", types = listOf("Other"))))
    }

    @Test
    fun `notExpired treats a missing expiration as not expired and the boundary by the clock`() {
        val p = predicate { notExpired() }

        assertTrue(p(sampleCredential("a", expiresAt = null)))
        assertTrue(p(sampleCredential("b", expiresAt = future)))
        assertFalse(p(sampleCredential("c", expiresAt = past)))
    }

    @Test
    fun `expired never matches a credential without an expiration`() {
        val p = predicate { expired() }

        assertFalse(p(sampleCredential("a", expiresAt = null)))
        assertFalse(p(sampleCredential("b", expiresAt = future)))
        assertTrue(p(sampleCredential("c", expiresAt = past)))
    }

    @Test
    fun `revoked cannot be determined offline and therefore matches nothing`() {
        val p = predicate { revoked() }

        assertFalse(p(sampleCredential("a")))
        assertFalse(p(withStatus(sampleCredential("b"))))
    }

    @Test
    fun `valid requires a proof, no expiry in the past and no status entry`() {
        val p = predicate { valid() }

        assertTrue(p(sampleCredential("ok", expiresAt = future)))
        assertTrue(p(sampleCredential("ok-no-expiry")))
        assertFalse(p(sampleCredential("unsigned", signed = false)))
        assertFalse(p(sampleCredential("expired", expiresAt = past)))
        assertFalse(p(withStatus(sampleCredential("status"))))
    }

    @Test
    fun `tag and collection requests accumulate as sets without touching the predicate`() {
        val builder =
            CredentialQueryBuilder().apply {
                byTag("a")
                byTag("b")
                byTag("a")
                byCollection("c1")
            }

        assertEquals(setOf("a", "b"), builder.requestedTags)
        assertEquals(setOf("c1"), builder.requestedCollections)
        assertTrue(builder.toPredicate()(sampleCredential("x")))
    }

    @Test
    fun `query on a wallet applies the predicate in storage order`() =
        runTest {
            val wallet = MapWallet("q")
            wallet.store(sampleCredential("1", types = listOf("Degree")))
            wallet.store(sampleCredential("2", types = listOf("License")))
            wallet.store(sampleCredential("3", types = listOf("Degree"), expiresAt = past))

            val ids =
                wallet
                    .query {
                        byType("Degree")
                        notExpired()
                    }.map { it.id?.value }

            assertEquals(listOf("1"), ids)
        }
}
