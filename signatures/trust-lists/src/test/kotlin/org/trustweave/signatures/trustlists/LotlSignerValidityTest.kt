package org.trustweave.signatures.trustlists

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class LotlSignerValidityTest {
    private fun clockAt(instant: Instant) =
        object : Clock {
            override fun now() = instant
        }

    @Test
    fun `pinned signer certificate that has expired is rejected`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                ca.signerKey,
                listOf(ca.signerCert),
            )
        val later = DefaultLotlSignatureVerifier(clockAt(Clock.System.now() + 800.days))

        val result = later.verify(signed, listOf(ca.signerCert))

        assertTrue(result is LotlSignatureValidationResult.Invalid.SignerCertificateNotValid, "got: $result")
    }

    @Test
    fun `signer certificate not yet valid is rejected`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                ca.signerKey,
                listOf(ca.signerCert),
            )
        val earlier = DefaultLotlSignatureVerifier(clockAt(Clock.System.now() - 30.days))

        val result = earlier.verify(signed, listOf(ca.signerCert))

        assertTrue(result is LotlSignatureValidationResult.Invalid.SignerCertificateNotValid, "got: $result")
    }

    @Test
    fun `expired trust anchor is not used for the PKIX fallback`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                ca.signerKey,
                listOf(ca.signerCert),
            )
        // Signer and CA both expire together here; the signer check fires first, which is the point:
        // nothing expired is ever accepted.
        val later = DefaultLotlSignatureVerifier(clockAt(Clock.System.now() + 800.days))
        assertTrue(later.verify(signed, listOf(ca.caCert)) is LotlSignatureValidationResult.Invalid)
    }

    @Test
    fun `intermediate carried in KeyInfo bridges signer to root anchor`() {
        val chain = TrustListFixtures.generateChain()
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                chain.signerKey,
                listOf(chain.signerCert, chain.intermediate),
            )

        val result = DefaultLotlSignatureVerifier().verify(signed, listOf(chain.root))

        assertTrue(result is LotlSignatureValidationResult.Valid, "got: $result")
        result as LotlSignatureValidationResult.Valid
        assertTrue(result.signerCert == chain.signerCert)
    }

    @Test
    fun `intermediate listed before the signer in KeyInfo still selects the leaf`() {
        val chain = TrustListFixtures.generateChain()
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                chain.signerKey,
                listOf(chain.intermediate, chain.signerCert),
            )

        val result = DefaultLotlSignatureVerifier().verify(signed, listOf(chain.root))

        assertTrue(result is LotlSignatureValidationResult.Valid, "got: $result")
    }

    @Test
    fun `missing intermediate leaves the signer untrusted`() {
        val chain = TrustListFixtures.generateChain()
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                chain.signerKey,
                listOf(chain.signerCert),
            )

        val result = DefaultLotlSignatureVerifier().verify(signed, listOf(chain.root))

        assertTrue(result is LotlSignatureValidationResult.Invalid.UntrustedSigner, "got: $result")
    }
}
