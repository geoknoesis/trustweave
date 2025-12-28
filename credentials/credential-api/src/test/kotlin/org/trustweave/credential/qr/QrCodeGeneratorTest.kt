package org.trustweave.credential.qr

import kotlin.test.*

/**
 * Tests for QR Code Generator.
 */
class QrCodeGeneratorTest {

    @Test
    fun `test generateCredentialOfferUrl with credential_issuer`() {
        val url = QrCodeGenerator.generateCredentialOfferUrl(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("PersonCredential", "EducationCredential")
        )
        
        assertTrue(url.startsWith("openid-credential-offer://"))
        assertTrue(url.contains("credential_issuer="))
        assertTrue(url.contains("credential_configuration_ids="))
        assertTrue(url.contains("PersonCredential"))
        assertTrue(url.contains("EducationCredential"))
    }

    @Test
    fun `test generateCredentialOfferUrl with credential_offer_uri`() {
        val offerUri = "https://issuer.example.com/offers/123"
        val url = QrCodeGenerator.generateCredentialOfferUrl(
            credentialIssuer = "https://issuer.example.com",
            credentialOfferUri = offerUri
        )
        
        assertTrue(url.startsWith("openid-credential-offer://"))
        assertTrue(url.contains("credential_offer_uri="))
        // URL is encoded, so check for encoded version
        val encodedUri = java.net.URLEncoder.encode(offerUri, "UTF-8")
        assertTrue(url.contains(encodedUri), "URL should contain encoded offerUri")
    }

    @Test
    fun `test generateCredentialOfferUrl with empty credential types`() {
        val url = QrCodeGenerator.generateCredentialOfferUrl(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = emptyList()
        )
        
        assertTrue(url.startsWith("openid-credential-offer://"))
        assertTrue(url.contains("credential_issuer="))
        assertFalse(url.contains("credential_configuration_ids="))
    }

    @Test
    fun `test generatePresentationRequestUrl with client_id and request_uri`() {
        val url = QrCodeGenerator.generatePresentationRequestUrl(
            clientId = "test-client",
            requestUri = "https://verifier.example.com/request/123"
        )
        
        assertTrue(url.startsWith("openid4vp://authorize"))
        assertTrue(url.contains("client_id=test-client"))
        assertTrue(url.contains("request_uri="))
    }

    @Test
    fun `test generatePresentationRequestUrl with only client_id`() {
        val url = QrCodeGenerator.generatePresentationRequestUrl(
            clientId = "test-client"
        )
        
        assertTrue(url.startsWith("openid4vp://authorize"))
        assertTrue(url.contains("client_id=test-client"))
    }

    @Test
    fun `test generatePresentationRequestUrl with additional parameters`() {
        val url = QrCodeGenerator.generatePresentationRequestUrl(
            clientId = "test-client",
            requestUri = "https://verifier.example.com/request/123",
            additionalParams = mapOf("scope" to "openid", "response_type" to "vp_token")
        )
        
        assertTrue(url.contains("scope="))
        assertTrue(url.contains("response_type="))
    }

    @Test
    fun `test generatePresentationRequestUrl with neither client_id nor request_uri throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            QrCodeGenerator.generatePresentationRequestUrl()
        }
    }

    @Test
    fun `test generated URLs are properly encoded`() {
        val url = QrCodeGenerator.generateCredentialOfferUrl(
            credentialIssuer = "https://issuer.example.com/path with spaces",
            credentialConfigurationIds = listOf("Credential Type")
        )
        
        // Check that special characters are encoded
        assertFalse(url.contains(" "))
        assertTrue(url.contains("%20") || url.contains("+"))
    }
}

