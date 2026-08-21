package org.trustweave.credential.bbs

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [BbsCryptoSuite] is not BBS+. It is an HMAC emulation that keeps the wire sizes of the real
 * cryptosuite, and its own documentation says a production deployment should swap it out.
 *
 * Nothing enforced that. The provider is registered in `META-INF/services`, so the engine was
 * discovered from the classpath automatically, and `distribution/bom` exports the module to every
 * consumer of the BOM. A `BBS_2023` proof therefore verified successfully by default.
 *
 * The scheme provides no authenticity whatsoever — see the forgery test below — so the emulation
 * now has to be switched on deliberately, and is off unless it is.
 */
class BbsInsecureEmulationGateTest {
    @BeforeTest
    fun disable() {
        BbsCryptoSuite.allowInsecureEmulation = false
    }

    @AfterTest
    fun reset() {
        BbsCryptoSuite.allowInsecureEmulation = false
    }

    @Test
    fun `the proof engine is not offered unless the emulation is enabled`() {
        assertNull(
            Bbs2023ProofEngineProvider().create(emptyMap()),
            "A ServiceLoader-discovered BBS engine must not appear by default",
        )
    }

    @Test
    fun `the proof engine is available once the emulation is enabled`() {
        BbsCryptoSuite.allowInsecureEmulation = true

        assertNotNull(
            Bbs2023ProofEngineProvider().create(emptyMap()),
            "Enabling the emulation must make the engine available for tests and development",
        )
    }

    @Test
    fun `signing refuses while the emulation is disabled`() {
        BbsCryptoSuite.allowInsecureEmulation = true
        val keyPair = BbsCryptoSuite.generateKeyPair("gate-key")
        BbsCryptoSuite.allowInsecureEmulation = false

        assertFailsWith<IllegalStateException> {
            BbsCryptoSuite.sign(keyPair.secretKeyBytes!!, keyPair.publicKeyBytes, listOf("m".toByteArray()))
        }
    }

    @Test
    fun `verification refuses while the emulation is disabled`() {
        BbsCryptoSuite.allowInsecureEmulation = true
        val keyPair = BbsCryptoSuite.generateKeyPair("gate-key")
        val messages = listOf("m".toByteArray())
        val signature = BbsCryptoSuite.sign(keyPair.secretKeyBytes!!, keyPair.publicKeyBytes, messages)
        BbsCryptoSuite.allowInsecureEmulation = false

        assertFailsWith<IllegalStateException> {
            BbsCryptoSuite.verify(keyPair.publicKeyBytes, signature, messages)
        }
    }

    @Test
    fun `the public key alone is enough to forge a signature`() {
        // Why the gate exists. sign() keys its HMAC on publicKey[0..31] and never touches the
        // secret key beyond a length check, so a forger who knows only the public key — which is
        // published in the issuer's DID document — produces signatures that verify. Any 32 bytes
        // stand in for the secret key.
        BbsCryptoSuite.allowInsecureEmulation = true

        val issuer = BbsCryptoSuite.generateKeyPair("victim-issuer")
        val forgedClaims = listOf("attacker chose this".toByteArray())

        val forgery =
            BbsCryptoSuite.sign(
                secretKey = ByteArray(32), // not the issuer's secret key
                publicKey = issuer.publicKeyBytes, // public, from the DID document
                messages = forgedClaims,
            )

        assertTrue(
            BbsCryptoSuite.verify(issuer.publicKeyBytes, forgery, forgedClaims),
            "Demonstrates the emulation has no authenticity: the public key is the signing key",
        )
    }
}
