package org.trustweave.credential.extensions

import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.TemporalValidity
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class TemporalValidityExtensionsTest {
    private val now: Instant = Clock.System.now()

    private fun vc(
        context: String = "https://www.w3.org/2018/credentials/v1",
        issuanceDate: Instant? = null,
        validFrom: Instant? = null,
        expirationDate: Instant? = null,
        validUntil: Instant? = null,
    ) = VerifiableCredential(
        context = listOf(context),
        type = listOf(CredentialType.fromString("VerifiableCredential")),
        issuer = Issuer.IriIssuer(Iri("did:key:test")),
        issuanceDate = issuanceDate,
        validFrom = validFrom,
        expirationDate = expirationDate,
        validUntil = validUntil,
        credentialSubject = CredentialSubject(id = Iri("did:key:holder"), claims = emptyMap()),
    )

    private val v2 = "https://www.w3.org/ns/credentials/v2"

    @Test
    fun `VC 2_0 credential with a past validUntil is expired`() {
        val c = vc(context = v2, validFrom = now - 10.days, validUntil = now - 1.days)
        assertTrue(c.isExpired())
        assertTrue(c.isExpiredAt(now))
        assertFalse(c.isValid())
        assertFalse(c.isValidAt(now))
        assertTrue(c.isValidAt(now - 5.days))
    }

    @Test
    fun `VC 1_1 expirationDate still works`() {
        val c = vc(issuanceDate = now - 10.days, expirationDate = now - 1.days)
        assertTrue(c.isExpired())
        assertFalse(c.isValid())
        assertFalse(vc(issuanceDate = now - 10.days, expirationDate = now + 1.days).isExpired())
        assertTrue(vc(issuanceDate = now - 10.days, expirationDate = now + 1.days).isValid())
    }

    @Test
    fun `earliest of expirationDate and validUntil wins`() {
        val c = vc(context = v2, expirationDate = now + 10.days, validUntil = now - 1.days)
        assertTrue(c.isExpired())
        val d = vc(context = v2, expirationDate = now - 1.days, validUntil = now + 10.days)
        assertTrue(d.isExpired())
    }

    @Test
    fun `not yet valid credentials are invalid but not expired`() {
        val c = vc(context = v2, validFrom = now + 1.days)
        assertFalse(c.isValid())
        assertFalse(c.isExpired())
        assertEquals(TemporalValidity.NOT_YET_VALID, c.temporalValidity(now))
        assertTrue(vc(issuanceDate = now + 1.days).isValid(now + 2.days))
        assertFalse(vc(issuanceDate = now + 1.days).isValidAt(now))
    }

    @Test
    fun `no dates means always valid`() {
        val c = vc()
        assertTrue(c.isValid())
        assertEquals(TemporalValidity.VALID, c.temporalValidity(now))
    }

    @Test
    fun `member and extension agree`() {
        val c = vc(context = v2, validUntil = now - 1.days)
        assertEquals(c.isValid(now), c.isValidAt(now))
        assertEquals(c.isValid(), c.isValid(Clock.System.now()))
    }
}
