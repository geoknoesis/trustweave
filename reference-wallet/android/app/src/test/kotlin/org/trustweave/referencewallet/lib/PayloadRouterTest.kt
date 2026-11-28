package org.trustweave.referencewallet.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for Android unified QR/NFC/deep-link payload routing and NDEF decoding. */
class PayloadRouterTest {

    @Test
    fun `classifies OpenID4VCI, direct offers, verifier URLs, openid4vp, public verify, and holder DIDs`() {
        val oidOffer = PayloadRouter.classify(
            "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.example%22%2C%22credential_configuration_ids%22%3A%5B%22Degree%22%5D%2C%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22code-99%22%7D%7D%7D",
        )
        assertTrue(oidOffer is PayloadRouter.RoutedPayload.Offer)
        val preAuth = (oidOffer as PayloadRouter.RoutedPayload.Offer).offer as PayloadRouter.IncomingOffer.Oid4VciPreAuthorized
        assertEquals("https://issuer.example", preAuth.issuerBase)
        assertEquals("code-99", preAuth.preAuthorizedCode)

        val verifier = PayloadRouter.classify("https://demo.example/verifier")
        assertTrue(verifier is PayloadRouter.RoutedPayload.Presentation)
        assertEquals("https://demo.example", (verifier as PayloadRouter.RoutedPayload.Presentation).request.verifierUrl)

        val vpUri = PayloadRouter.classify("openid4vp://?request_uri=https%3A%2F%2Fver.example%2Fapi%2Fdemo-verifier%2Frequest")
        assertTrue(vpUri is PayloadRouter.RoutedPayload.Presentation)

        val wrapped = PayloadRouter.classify("trustweavewallet://scan?url=https%3A%2F%2Fdemo.example%2Fverifier")
        assertTrue(wrapped is PayloadRouter.RoutedPayload.Presentation)

        val publicVerify = PayloadRouter.classify("https://demo.example/verify/abc123")
        assertTrue(publicVerify is PayloadRouter.RoutedPayload.PublicVerify)

        val holder = PayloadRouter.classify("""{"v":1,"type":"trustweave-holder","did":"did:key:z6MkABC"}""")
        assertTrue(holder is PayloadRouter.RoutedPayload.HolderDid)
    }

    @Test
    fun `decodes NFC Forum NDEF URI and Text records`() {
        val uriPayload = byteArrayOf(0x04) + "demo.example/verifier".toByteArray(Charsets.UTF_8)
        assertEquals("https://demo.example/verifier", NdefParser.decodeUriPayload(uriPayload))

        val textPayload = byteArrayOf(0x02) + "en".toByteArray(Charsets.US_ASCII) + "http://192.168.1.10:3000/verifier".toByteArray(Charsets.UTF_8)
        assertEquals("http://192.168.1.10:3000/verifier", NdefParser.decodeTextPayload(textPayload))
    }

    @Test
    fun `formats relative activity timestamps cleanly`() {
        val base = 1_700_000_000L // 2023-11-14T22:13:20Z
        assertEquals("Just now", CredentialDisplay.formatRelativeTime("2023-11-14T22:13:00Z", base))
        assertEquals("5m ago", CredentialDisplay.formatRelativeTime("2023-11-14T22:08:20Z", base))
        assertEquals("2h ago", CredentialDisplay.formatRelativeTime("2023-11-14T20:13:20Z", base))
        assertEquals("3d ago", CredentialDisplay.formatRelativeTime("2023-11-11T22:13:20Z", base))
    }
}
