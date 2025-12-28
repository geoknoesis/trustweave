package org.trustweave.credential.qr

/**
 * QR code generator for credential and presentation URLs.
 *
 * Generates QR code content for:
 * - OIDC4VCI credential offer URLs
 * - OIDC4VP authorization URLs
 *
 * **Note:** This generates the URL string content. Actual QR code image generation
 * should be done by a QR code library (e.g., ZXing, QRCode.js) in the application layer.
 *
 * **Example Usage:**
 * ```kotlin
 * // Generate credential offer URL
 * val offerUrl = QrCodeGenerator.generateCredentialOfferUrl(
 *     credentialIssuer = "https://issuer.example.com",
 *     credentialConfigurationIds = listOf("PersonCredential", "EducationCredential")
 * )
 * // Use offerUrl with QR code library to generate image
 * ```
 */
object QrCodeGenerator {

    /**
     * Generates an OIDC4VCI credential offer URL.
     *
     * @param credentialIssuer The credential issuer URL
     * @param credentialConfigurationIds List of credential configuration IDs
     * @param credentialOfferUri Optional direct credential offer URI
     * @return Credential offer URL string ready for QR code generation
     */
    fun generateCredentialOfferUrl(
        credentialIssuer: String,
        credentialConfigurationIds: List<String> = emptyList(),
        credentialOfferUri: String? = null
    ): String {
        val params = mutableListOf<String>()
        
        if (credentialOfferUri != null) {
            params.add("credential_offer_uri=${encode(credentialOfferUri)}")
        } else {
            params.add("credential_issuer=${encode(credentialIssuer)}")
            if (credentialConfigurationIds.isNotEmpty()) {
                params.add("credential_configuration_ids=${encode(credentialConfigurationIds.joinToString(","))}")
            }
        }
        
        return "openid-credential-offer://?${params.joinToString("&")}"
    }

    /**
     * Generates an OIDC4VP authorization URL.
     *
     * @param clientId The client ID (verifier identifier)
     * @param requestUri The request URI where the authorization request can be fetched
     * @param additionalParams Additional query parameters
     * @return Authorization URL string ready for QR code generation
     */
    fun generatePresentationRequestUrl(
        clientId: String? = null,
        requestUri: String? = null,
        additionalParams: Map<String, String> = emptyMap()
    ): String {
        val params = mutableListOf<String>()
        
        clientId?.let { params.add("client_id=${encode(it)}") }
        requestUri?.let { params.add("request_uri=${encode(it)}") }
        
        additionalParams.forEach { (key, value) ->
            params.add("${encode(key)}=${encode(value)}")
        }
        
        if (params.isEmpty()) {
            throw IllegalArgumentException("Either 'clientId' or 'requestUri' must be provided")
        }
        
        return "openid4vp://authorize?${params.joinToString("&")}"
    }

    /**
     * URL-encodes a string for use in query parameters.
     */
    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }
}

