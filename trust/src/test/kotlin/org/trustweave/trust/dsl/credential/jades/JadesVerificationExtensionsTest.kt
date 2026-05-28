package org.trustweave.trust.dsl.credential.jades

import org.trustweave.signatures.jades.JadesProfile
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.trust.dsl.createTestCredentialService
import org.trustweave.trust.dsl.credential.VerificationBuilder
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies that [requireJadesProfile] and [withJadesTrustAnchorResolver] populate
 * [VerificationBuilder]'s `additionalOptions` map with the exact wire keys/values the JAdES
 * proof engine expects (`"requiredProfile"`, `"trustAnchorResolver"`, `"acceptedAlgorithms"`).
 *
 * No actual verification is performed — the engine-level test in `credentials:plugins:jades`
 * already covers the sign+verify round-trip.
 */
class JadesVerificationExtensionsTest {

    private fun newBuilder(): VerificationBuilder =
        VerificationBuilder(credentialService = createTestCredentialService())

    private val stubResolver = object : TrustAnchorResolver {
        override fun resolve(
            signerCert: X509Certificate,
            chain: List<X509Certificate>,
        ): TrustAnchorMatch = TrustAnchorMatch.NotTrusted
    }

    @Test
    fun `requireJadesProfile B_B populates profile and resolver`() {
        val builder = newBuilder()

        builder.requireJadesProfile(
            profile = JadesProfile.B_B,
            trustAnchorResolver = stubResolver,
        )

        val opts = builder.additionalOptions
        assertEquals(JadesOptionKeys.PROFILE_B_B, opts[JadesVerificationOptionKeys.REQUIRED_PROFILE])
        assertSame(stubResolver, opts[JadesVerificationOptionKeys.TRUST_ANCHOR_RESOLVER])
        assertFalse(
            opts.containsKey(JadesVerificationOptionKeys.ACCEPTED_ALGORITHMS),
            "Default algorithm list should not be materialised when caller did not override",
        )
    }

    @Test
    fun `requireJadesProfile B_T populates the B-T wire value`() {
        val builder = newBuilder()

        builder.requireJadesProfile(
            profile = JadesProfile.B_T,
            trustAnchorResolver = stubResolver,
        )

        assertEquals(
            JadesOptionKeys.PROFILE_B_T,
            builder.additionalOptions[JadesVerificationOptionKeys.REQUIRED_PROFILE],
        )
    }

    @Test
    fun `requireJadesProfile forwards a non-null acceptedAlgorithms set`() {
        val builder = newBuilder()
        val algs = setOf("ES256", "EdDSA")

        builder.requireJadesProfile(
            profile = JadesProfile.B_B,
            trustAnchorResolver = stubResolver,
            acceptedAlgorithms = algs,
        )

        @Suppress("UNCHECKED_CAST")
        val stored = builder.additionalOptions[JadesVerificationOptionKeys.ACCEPTED_ALGORITHMS] as Set<String>
        assertEquals(setOf("ES256", "EdDSA"), stored)
    }

    @Test
    fun `requireJadesProfile rejects an empty acceptedAlgorithms set`() {
        val builder = newBuilder()
        val ex = assertFailsWith<IllegalArgumentException> {
            builder.requireJadesProfile(
                profile = JadesProfile.B_B,
                trustAnchorResolver = stubResolver,
                acceptedAlgorithms = emptySet(),
            )
        }
        assertTrue(ex.message!!.contains("acceptedAlgorithms"))
    }

    @Test
    fun `withJadesTrustAnchorResolver populates only the resolver entry`() {
        val builder = newBuilder()

        builder.withJadesTrustAnchorResolver(stubResolver)

        val opts = builder.additionalOptions
        assertEquals(1, opts.size, "Resolver-only convenience must not write a profile floor")
        assertSame(stubResolver, opts[JadesVerificationOptionKeys.TRUST_ANCHOR_RESOLVER])
    }
}
