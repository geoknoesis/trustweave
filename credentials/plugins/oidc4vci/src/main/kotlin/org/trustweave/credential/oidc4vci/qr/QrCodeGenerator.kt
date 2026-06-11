package org.trustweave.credential.oidc4vci.qr

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
     * Generates an OIDC4VCI credential offer URL (OID4VCI v1.0 §4.1).
     *
     * The offer is carried either by reference in a `credential_offer_uri` parameter or
     * by value as URL-encoded JSON in a single `credential_offer` parameter.
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
        val param = if (credentialOfferUri != null) {
            "credential_offer_uri=${encode(credentialOfferUri)}"
        } else {
            val offerJson = buildJsonObject {
                put("credential_issuer", credentialIssuer)
                if (credentialConfigurationIds.isNotEmpty()) {
                    put("credential_configuration_ids", JsonArray(credentialConfigurationIds.map { JsonPrimitive(it) }))
                }
            }
            "credential_offer=${encode(offerJson.toString())}"
        }

        return "openid-credential-offer://?$param"
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

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
