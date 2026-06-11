package org.trustweave.credential.qr

import java.net.URLEncoder
import kotlin.test.*

/**
 * Tests for QR Code Parser.
 */
class QrCodeParserTest {

    private val offerByValueJson =
        """
        {
          "credential_issuer": "https://issuer.example.com",
          "credential_configuration_ids": ["PersonCredential", "EducationCredential"],
          "grants": {
            "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
              "pre-authorized_code": "code-123",
              "tx_code": { "input_mode": "numeric", "length": 4 }
            }
          }
        }
        """.trimIndent()

    @Test
    fun `test parse credential offer URL with single credential_offer JSON parameter`() {
        val url = "openid-credential-offer://?credential_offer=" + URLEncoder.encode(offerByValueJson, "UTF-8")

        val result = QrCodeParser.parse(url)

        assertTrue(result is QrCodeContent.CredentialOffer, "Expected CredentialOffer, got ${result::class.simpleName}")
        val offer: QrCodeContent.CredentialOffer = result
        assertEquals("https://issuer.example.com", offer.credentialIssuer)
        assertEquals(listOf("PersonCredential", "EducationCredential"), offer.credentialConfigurationIds)
        assertNull(offer.credentialOfferUri, "Offer-by-value has no by-reference URI")
        assertEquals(url, offer.rawUrl)

        val grantsJson = offer.grantsJson
        assertNotNull(grantsJson, "grants must be extracted from the credential_offer JSON")
        assertTrue(grantsJson.contains("urn:ietf:params:oauth:grant-type:pre-authorized_code"))
        assertTrue(grantsJson.contains("code-123"))
    }

    @Test
    fun `test parse HTTP URL with credential_offer parameter`() {
        val url = "https://wallet.example.com/offer?credential_offer=" +
            URLEncoder.encode(offerByValueJson, "UTF-8")

        val result = QrCodeParser.parse(url)

        assertTrue(result is QrCodeContent.CredentialOffer, "Expected CredentialOffer for HTTP URL with credential_offer")
        val offer: QrCodeContent.CredentialOffer = result
        assertEquals("https://issuer.example.com", offer.credentialIssuer)
        assertEquals(listOf("PersonCredential", "EducationCredential"), offer.credentialConfigurationIds)
    }

    @Test
    fun `test parse credential_offer with malformed JSON throws exception`() {
        val url = "openid-credential-offer://?credential_offer=not-a-json-object"

        assertFailsWith<IllegalArgumentException> {
            QrCodeParser.parse(url)
        }
    }

    @Test
    fun `test parse credential_offer JSON without credential_issuer throws exception`() {
        val url = "openid-credential-offer://?credential_offer=" +
            URLEncoder.encode("""{"credential_configuration_ids":["PersonCredential"]}""", "UTF-8")

        assertFailsWith<IllegalArgumentException> {
            QrCodeParser.parse(url)
        }
    }

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
