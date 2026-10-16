package org.trustweave.signatures.trustlists

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Hostile XML shapes (absurd nesting, absurd size) must be refused as malformed, never crash the caller. */
class TrustListXmlLimitsTest {
    private val ca = TrustListFixtures.generateCaAndSigner()

    /** [xml] with [depth] nested elements appended inside its root element. */
    private fun deepen(
        xml: ByteArray,
        depth: Int,
    ): ByteArray {
        val text = String(xml, Charsets.UTF_8)
        val rootStart = Regex("<(?![?!])[^>]*>").find(text)!!.range.last + 1
        val nest = "<x>".repeat(depth) + "</x>".repeat(depth)
        return (text.substring(0, rootStart) + nest + text.substring(rootStart)).toByteArray(Charsets.UTF_8)
    }

    private fun signed(xml: ByteArray) = SignedXmlTestSupport.signEnveloped(xml, ca.signerKey, listOf(ca.signerCert))

    @Test
    fun `a pathologically nested document is Malformed for the signature verifier`() {
        val deep = deepen(signed(TrustListFixtures.renderLotlXml()), 60_000)
        val result = DefaultLotlSignatureVerifier().verify(deep, listOf(ca.signerCert))
        assertTrue(result is LotlSignatureValidationResult.Invalid.Malformed, "got: $result")
        val tsl = DefaultTslSignatureVerifier().verify(deep, listOf(ca.signerCert))
        assertTrue(tsl is LotlSignatureValidationResult.Invalid.Malformed, "got: $tsl")
    }

    @Test
    fun `a pathologically nested document is a parse exception for the parser`() {
        val deep = deepen(TrustListFixtures.renderLotlXml(), 60_000)
        assertThrows<TrustListParseException> { EtsiTrustListParser().parse(deep, emptyMap()) }
    }

    @Test
    fun `an oversize document is refused before parsing`() {
        val valid = String(TrustListFixtures.renderLotlXml(), Charsets.UTF_8)
        val rootStart = Regex("<(?![?!])[^>]*>").find(valid)!!.range.last + 1
        val padding = "<!--" + " ".repeat(TrustListXml.MAX_INPUT_BYTES) + "-->"
        val big = (valid.substring(0, rootStart) + padding + valid.substring(rootStart)).toByteArray(Charsets.UTF_8)
        val result = DefaultLotlSignatureVerifier().verify(big, listOf(ca.signerCert))
        assertTrue(result is LotlSignatureValidationResult.Invalid.Malformed, "got: $result")
        assertThrows<TrustListParseException> { EtsiTrustListParser().parse(big, emptyMap()) }
    }

    @Test
    fun `ordinary nesting depth still parses`() {
        val ok = deepen(TrustListFixtures.renderLotlXml(), 50)
        EtsiTrustListParser().parse(ok, emptyMap())
    }
}
