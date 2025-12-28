package org.trustweave.credential.qr

import java.net.URLDecoder

/**
 * QR code parser for credential and presentation URLs.
 *
 * Supports parsing:
 * - OIDC4VCI credential offer URLs: `openid-credential-offer://...`
 * - OIDC4VP authorization URLs: `openid4vp://authorize?...`
 * - Generic HTTP/HTTPS URLs
 *
 * **Example Usage:**
 * ```kotlin
 * val qrCode = "openid-credential-offer://?credential_issuer=https://issuer.example.com&..."
 * val parsed = QrCodeParser.parse(qrCode)
 * when (parsed) {
 *     is QrCodeContent.CredentialOffer -> {
 *         println("Credential offer from: ${parsed.credentialIssuer}")
 *     }
 *     is QrCodeContent.PresentationRequest -> {
 *         println("Presentation request: ${parsed.authorizationUrl}")
 *     }
 * }
 * ```
 */
object QrCodeParser {

    /**
     * Parses a QR code string and returns typed content.
     *
     * @param qrCodeString The QR code content (URL or URI)
     * @return Parsed QR code content
     * @throws IllegalArgumentException if the QR code format is not recognized
     */
    fun parse(qrCodeString: String): QrCodeContent {
        val trimmed = qrCodeString.trim()
        
        return when {
            trimmed.startsWith("openid-credential-offer://", ignoreCase = true) -> {
                parseCredentialOfferUrl(trimmed)
            }
            trimmed.startsWith("openid4vp://", ignoreCase = true) -> {
                parsePresentationRequestUrl(trimmed)
            }
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
                // Could be a credential offer or presentation request URL served over HTTP
                parseHttpUrl(trimmed)
            }
            else -> {
                throw IllegalArgumentException("Unrecognized QR code format: $trimmed")
            }
        }
    }

    /**
     * Parses an OIDC4VCI credential offer URL.
     */
    private fun parseCredentialOfferUrl(url: String): QrCodeContent.CredentialOffer {
        // Remove the scheme prefix
        val queryString = url.substringAfter("?", missingDelimiterValue = "")
        val params = parseQueryParameters(queryString)
        
        val credentialOfferUri = params["credential_offer_uri"]
        val credentialIssuer = params["credential_issuer"]
        
        // Either credential_offer_uri OR credential_issuer must be present
        if (credentialOfferUri == null && credentialIssuer == null) {
            throw IllegalArgumentException("Missing both 'credential_offer_uri' and 'credential_issuer' in credential offer URL. At least one must be present.")
        }
        
        val credentialConfigurationIds = params["credential_configuration_ids"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        
        return QrCodeContent.CredentialOffer(
            credentialIssuer = credentialIssuer ?: "",  // Empty if using credential_offer_uri
            credentialConfigurationIds = credentialConfigurationIds,
            credentialOfferUri = credentialOfferUri,
            rawUrl = url
        )
    }

    /**
     * Parses an OIDC4VP authorization URL.
     */
    private fun parsePresentationRequestUrl(url: String): QrCodeContent.PresentationRequest {
        // Format: openid4vp://authorize?client_id=...&request_uri=...
        val queryString = url.substringAfter("?", missingDelimiterValue = "")
        val params = parseQueryParameters(queryString)
        
        val clientId = params["client_id"]
        val requestUri = params["request_uri"]
        
        if (clientId == null && requestUri == null) {
            throw IllegalArgumentException("Missing both 'client_id' and 'request_uri' in presentation request URL")
        }
        
        return QrCodeContent.PresentationRequest(
            authorizationUrl = url,
            clientId = clientId,
            requestUri = requestUri
        )
    }

    /**
     * Parses an HTTP/HTTPS URL (could be either type).
     */
    private fun parseHttpUrl(url: String): QrCodeContent {
        // Try to determine the type based on path or query parameters
        // Extract query string manually to avoid deprecated URL constructor
        val queryString = if (url.contains("?")) {
            url.substringAfter("?")
        } else {
            ""
        }
        val queryParams = parseQueryParameters(queryString)
        
        return when {
            // Check for credential offer indicators
            queryParams.containsKey("credential_issuer") || queryParams.containsKey("credential_offer_uri") -> {
                parseCredentialOfferUrl(url.replaceFirst("https?://", "openid-credential-offer://", ignoreCase = true))
            }
            // Check for presentation request indicators
            queryParams.containsKey("request_uri") && queryParams.containsKey("client_id") -> {
                parsePresentationRequestUrl(url.replaceFirst("https?://", "openid4vp://", ignoreCase = true))
            }
            else -> {
                QrCodeContent.GenericUrl(url)
            }
        }
    }

    /**
     * Parses query parameters from a query string.
     */
    private fun parseQueryParameters(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        
        return query.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8")
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            key to value
        }
    }
}

/**
 * Sealed class representing parsed QR code content.
 */
sealed class QrCodeContent {
    /**
     * OIDC4VCI credential offer.
     */
    data class CredentialOffer(
        val credentialIssuer: String,  // Can be empty if credentialOfferUri is present
        val credentialConfigurationIds: List<String>,
        val credentialOfferUri: String? = null,
        val rawUrl: String
    ) : QrCodeContent()

    /**
     * OIDC4VP presentation request.
     */
    data class PresentationRequest(
        val authorizationUrl: String,
        val clientId: String?,
        val requestUri: String?
    ) : QrCodeContent()

    /**
     * Generic HTTP/HTTPS URL that couldn't be classified.
     */
    data class GenericUrl(val url: String) : QrCodeContent()
}

