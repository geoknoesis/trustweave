package org.trustweave.credential.qr

import kotlin.test.*

/**
 * Tests for QR Code Parser.
 */
class QrCodeParserTest {

    @Test
    fun `test parse credential offer URL with credential_issuer`() {
        val url = "openid-credential-offer://?credential_issuer=https://issuer.example.com&credential_configuration_ids=PersonCredential,EducationCredential"
        
        val result = QrCodeParser.parse(url)
        
        assertTrue(result is QrCodeContent.CredentialOffer, "Expected CredentialOffer, got ${result::class.simpleName}")
        val offer: QrCodeContent.CredentialOffer = result
        assertEquals("https://issuer.example.com", offer.credentialIssuer)
        assertEquals(2, offer.credentialConfigurationIds.size)
        assertTrue(offer.credentialConfigurationIds.contains("PersonCredential"))
        assertTrue(offer.credentialConfigurationIds.contains("EducationCredential"))
        assertEquals(url, offer.rawUrl)
    }

    @Test
    fun `test parse credential offer URL with credential_offer_uri`() {
        val offerUri = "https://issuer.example.com/offers/123"
        val url = "openid-credential-offer://?credential_offer_uri=$offerUri"
        
        val result = QrCodeParser.parse(url)
        
        assertTrue(result is QrCodeContent.CredentialOffer)
        val offer: QrCodeContent.CredentialOffer = result
        assertEquals(offerUri, offer.credentialOfferUri)
    }

    @Test
    fun `test parse presentation request URL with client_id and request_uri`() {
        val requestUri = "https://verifier.example.com/request/123"
        val url = "openid4vp://authorize?client_id=test-client&request_uri=$requestUri"
        
        val result = QrCodeParser.parse(url)
        
        assertTrue(result is QrCodeContent.PresentationRequest)
        val request: QrCodeContent.PresentationRequest = result
        assertEquals("test-client", request.clientId)
        assertEquals(requestUri, request.requestUri)
        assertEquals(url, request.authorizationUrl)
    }

    @Test
    fun `test parse presentation request URL with only request_uri`() {
        val requestUri = "https://verifier.example.com/request/123"
        val url = "openid4vp://authorize?request_uri=$requestUri"
        
        val result = QrCodeParser.parse(url)
        
        assertTrue(result is QrCodeContent.PresentationRequest)
        val request: QrCodeContent.PresentationRequest = result
        assertEquals(requestUri, request.requestUri)
    }

    @Test
    fun `test parse HTTP URL with credential offer parameters`() {
        val url = "https://issuer.example.com/offer?credential_issuer=https://issuer.example.com&credential_configuration_ids=PersonCredential"
        
        val result = QrCodeParser.parse(url)
        
        assertTrue(result is QrCodeContent.CredentialOffer, "Expected CredentialOffer for HTTP URL with credential_issuer")
        val offer: QrCodeContent.CredentialOffer = result
        assertEquals("https://issuer.example.com", offer.credentialIssuer)
    }

    @Test
    fun `test parse HTTP URL with presentation request parameters`() {
        val requestUri = "https://verifier.example.com/request/123"
        val url = "https://verifier.example.com/auth?client_id=test&request_uri=$requestUri"
        
        val result = QrCodeParser.parse(url)
        
        assertTrue(result is QrCodeContent.PresentationRequest, "Expected PresentationRequest for HTTP URL with request_uri and client_id")
        val request: QrCodeContent.PresentationRequest = result
        assertEquals("test", request.clientId)
        assertEquals(requestUri, request.requestUri)
    }

    @Test
    fun `test parse unrecognized format throws exception`() {
        val invalidUrl = "not-a-valid-qr-code"
        
        assertFailsWith<IllegalArgumentException> {
            QrCodeParser.parse(invalidUrl)
        }
    }

    @Test
    fun `test parse URL with URL encoding`() {
        val encodedIssuer = "https%3A%2F%2Fissuer.example.com"
        val url = "openid-credential-offer://?credential_issuer=$encodedIssuer"
        
        val result = QrCodeParser.parse(url)
        
        assertTrue(result is QrCodeContent.CredentialOffer)
        val offer: QrCodeContent.CredentialOffer = result
        assertEquals("https://issuer.example.com", offer.credentialIssuer)
    }

    @Test
    fun `test parse URL with empty query string throws exception`() {
        val url = "openid4vp://authorize"
        
        // Should throw exception since both client_id and request_uri are missing
        assertFailsWith<IllegalArgumentException> {
            QrCodeParser.parse(url)
        }
    }
}
