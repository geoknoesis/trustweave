package org.trustweave.credential.oidc4vci.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A pre-authorized code is a bearer secret with a deadline, and the deadline has to exist.
 *
 * It travels in a `credential_offer` URI — a QR image, an email, a link that ends up in server
 * logs and browser history — so OID4VCI treats it as short-lived and single-use. This server had
 * no issue time on an offer at all, which made every code valid for the life of the process while
 * the rejection message claimed it could be "expired".
 */
class Oidc4VciOfferExpiryTest {
    private fun issuer(offerTtlSeconds: Long = 300) =
        Oidc4VciIssuerService(
            baseUrl = "https://issuer.example",
            issuerDid = "did:key:z6MkTestIssuer",
            offerTtlSeconds = offerTtlSeconds,
        )

    private fun redeem(
        service: Oidc4VciIssuerService,
        code: String,
    ) = service.exchangePreAuthCode(code, txCodeValue = null)

    @Test
    fun `a fresh offer is redeemable`() {
        val service = issuer()
        val offer = service.createOffer(listOf("UniversityDegree"))
        val token = redeem(service, offer.preAuthCode)
        assertTrue(token.accessToken.isNotBlank())
    }

    @Test
    fun `an offer past its lifetime is refused`() {
        // A zero-second TTL makes every offer already expired the moment it is minted, which is
        // the boundary condition: elapsed >= ttl must refuse.
        val service = issuer(offerTtlSeconds = 0)
        val offer = service.createOffer(listOf("UniversityDegree"))
        val failure = assertFailsWith<IllegalArgumentException> { redeem(service, offer.preAuthCode) }
        assertTrue("expired" in (failure.message ?: ""), failure.message ?: "")
    }

    @Test
    fun `a code is single-use even when the first attempt fails`() {
        // The offer is consumed before it is judged, so a wrong tx_code cannot leave the code
        // sitting there for another guess.
        val service = issuer()
        val offer = service.createOffer(listOf("UniversityDegree"), txCode = null, txCodeValue = null)
        redeem(service, offer.preAuthCode)
        assertFailsWith<IllegalArgumentException> { redeem(service, offer.preAuthCode) }
    }

    @Test
    fun `an unknown code is refused`() {
        assertFailsWith<IllegalArgumentException> { redeem(issuer(), "never-issued") }
    }

    @Test
    fun `expired offers do not accumulate for the life of the process`() {
        val service = issuer(offerTtlSeconds = 0)
        repeat(50) { service.createOffer(listOf("UniversityDegree")) }

        // Every one of these is already past its TTL, so the next mint sweeps them. Before this,
        // an offer nobody redeemed stayed until the process ended.
        service.createOffer(listOf("UniversityDegree"))
        val (offers, _) = service.retainedState()
        assertTrue(offers <= 1, "expected expired offers to be swept, $offers retained")
    }

    @Test
    fun `live offers are never swept`() {
        val service = issuer(offerTtlSeconds = 3600)
        repeat(5) { service.createOffer(listOf("UniversityDegree")) }
        assertEquals(0, service.purgeExpired(), "nothing has expired yet")
        assertEquals(5, service.retainedState().first)
    }

    @Test
    fun `a token outliving its lifetime is swept rather than kept forever`() {
        val service =
            Oidc4VciIssuerService(
                baseUrl = "https://issuer.example",
                issuerDid = "did:key:z6MkTestIssuer",
                tokenTtlSeconds = 0,
            )
        val offer = service.createOffer(listOf("UniversityDegree"))
        redeem(service, offer.preAuthCode)
        assertEquals(1, service.retainedState().second, "the token exists before the sweep")

        // A token issued and then never used again used to stay for the life of the process,
        // because it was only evicted when someone happened to present it after expiry.
        assertTrue(service.purgeExpired() >= 1)
        assertEquals(0, service.retainedState().second)
    }

    @Test
    fun `purge reports what it removed and is safe to run repeatedly`() {
        val service = issuer(offerTtlSeconds = 0)
        // Minting sweeps first, so each create already drops the previous already-expired offer
        // and only the newest is left to find. That is the bound working, not a missed sweep.
        repeat(3) { service.createOffer(listOf("UniversityDegree")) }
        assertEquals(1, service.retainedState().first)
        assertEquals(1, service.purgeExpired())
        assertEquals(0, service.purgeExpired(), "a second sweep has nothing left to do")
    }
}
