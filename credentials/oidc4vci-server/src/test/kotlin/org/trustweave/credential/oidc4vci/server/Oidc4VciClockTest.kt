package org.trustweave.credential.oidc4vci.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Offer, token and deferred-credential lifetimes follow the injected [Clock], at the exact boundary. */
class Oidc4VciClockTest {
    private class FakeClock(
        private var instant: Instant = Instant.fromEpochMilliseconds(1_000_000_000_000),
    ) : Clock {
        override fun now(): Instant = instant

        fun advance(by: Duration) {
            instant += by
        }
    }

    private fun issuer(clock: Clock) =
        Oidc4VciIssuerService(
            baseUrl = "https://issuer.example",
            issuerDid = "did:key:z6MkTestIssuer",
            supportedConfigurations = TEST_CONFIGURATIONS,
            offerTtlSeconds = 300,
            tokenTtlSeconds = 3600,
            deferredTtlSeconds = 100,
            clock = clock,
        )

    @Test
    fun `an offer is redeemable just before its ttl and refused at it`() {
        val clock = FakeClock()
        val service = issuer(clock)
        val early = service.createOffer(listOf("T"))
        val late = service.createOffer(listOf("T"))

        clock.advance(299.seconds)
        assertNotNull(service.exchangePreAuthCode(early.preAuthCode, null))

        clock.advance(1.seconds)
        assertFailsWith<IllegalArgumentException> { service.exchangePreAuthCode(late.preAuthCode, null) }
    }

    @Test
    fun `an access token expires with the clock`() {
        val clock = FakeClock()
        val service = issuer(clock)
        val token = service.exchangePreAuthCode(service.createOffer(listOf("T")).preAuthCode, null)

        clock.advance(3599.seconds)
        service.requireAccessToken(token.accessToken)

        clock.advance(1.seconds)
        assertFailsWith<InvalidTokenException> { service.requireAccessToken(token.accessToken) }
    }

    @Test
    fun `a deferred credential is dropped once its ttl passes`() {
        val clock = FakeClock()
        val service = issuer(clock)
        val token = service.exchangePreAuthCode(service.createOffer(listOf("T")).preAuthCode, null)
        service.registerDeferredCredential("tx-1", """{"vc":1}""")
        service.registerDeferredCredential("tx-2", """{"vc":2}""")

        clock.advance(99.seconds)
        assertNotNull(service.getDeferredCredential("tx-1", token.accessToken))

        clock.advance(1.seconds)
        assertNull(service.getDeferredCredential("tx-2", token.accessToken))
        assertEquals(0, service.retainedDeferredCount())
    }
}
