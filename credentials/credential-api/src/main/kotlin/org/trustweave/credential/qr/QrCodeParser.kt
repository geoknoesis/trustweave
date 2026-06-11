package org.trustweave.credential.qr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLDecoder

/**
 * QR code parser for credential and presentation URLs.
 *
 * Supports parsing:
 * - OIDC4VCI credential offer URLs: `openid-credential-offer://...`
 *   - Offer-by-value, OID4VCI v1.0 §4.1: a single `credential_offer` query parameter
 *     carrying URL-encoded JSON (`credential_issuer`, `credential_configuration_ids`, `grants`)
 *   - Offer-by-reference: a `credential_offer_uri` query parameter pointing at the offer JSON
 *   - Legacy flat parameters: `credential_issuer=...&credential_configuration_ids=a,b`
 * - OIDC4VP authorization URLs: `openid4vp://authorize?...`
 * - Generic HTTP/HTTPS URLs
 *
 * **Example Usage:**
 * ```kotlin
 * val qrCode = "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22...%7D"
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
     *
     * Supports three formats:
     * 1. **Offer-by-value (OID4VCI v1.0 §4.1)**: a single `credential_offer` query parameter
     *    carrying the URL-encoded offer JSON. `credential_issuer`,
     *    `credential_configuration_ids` and `grants` are extracted from the JSON.
     * 2. **Offer-by-reference**: a `credential_offer_uri` query parameter; the URI is surfaced
     *    via [QrCodeContent.CredentialOffer.credentialOfferUri] for the caller to fetch.
     * 3. **Legacy flat parameters**: top-level `credential_issuer` /
     *    `credential_configuration_ids` query parameters (kept for backward compatibility).
     */
    private fun parseCredentialOfferUrl(url: String): QrCodeContent.CredentialOffer {
        // Remove the scheme prefix
        val queryString = url.substringAfter("?", missingDelimiterValue = "")
        val params = parseQueryParameters(queryString)

        // Offer-by-value: single credential_offer parameter with URL-encoded JSON
        params["credential_offer"]?.let { offerJson ->
            return parseCredentialOfferJson(offerJson, url)
        }

        val credentialOfferUri = params["credential_offer_uri"]
        val credentialIssuer = params["credential_issuer"]

        // Either credential_offer / credential_offer_uri OR legacy credential_issuer must be present
        if (credentialOfferUri == null && credentialIssuer == null) {
            throw IllegalArgumentException(
                "Missing 'credential_offer', 'credential_offer_uri' and 'credential_issuer' " +
                    "in credential offer URL. At least one must be present."
            )
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
     * Parses the JSON document carried in a `credential_offer` query parameter
     * (offer-by-value, OID4VCI v1.0 §4.1).
     */
    private fun parseCredentialOfferJson(offerJson: String, rawUrl: String): QrCodeContent.CredentialOffer {
        val offer = try {
            Json.parseToJsonElement(offerJson) as? JsonObject
                ?: throw IllegalArgumentException("'credential_offer' is not a JSON object")
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "'credential_offer' parameter is not valid JSON: ${e.message}", e
            )
        }

        val credentialIssuer = (offer["credential_issuer"] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("Missing 'credential_issuer' in credential_offer JSON")

        val credentialConfigurationIds = (offer["credential_configuration_ids"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

        return QrCodeContent.CredentialOffer(
            credentialIssuer = credentialIssuer,
            credentialConfigurationIds = credentialConfigurationIds,
            credentialOfferUri = null,
            rawUrl = rawUrl,
            grantsJson = (offer["grants"] as? JsonObject)?.toString()
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
            queryParams.containsKey("credential_offer") ||
                queryParams.containsKey("credential_issuer") ||
                queryParams.containsKey("credential_offer_uri") -> {
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
     *
     * @property credentialIssuer The credential issuer URL. Can be empty when the offer is
     *   by-reference ([credentialOfferUri] is set) and the offer JSON has not been fetched yet.
     * @property credentialConfigurationIds The offered credential configuration ids.
     * @property credentialOfferUri Offer-by-reference URI; the caller fetches the offer JSON from it.
     * @property rawUrl The original QR code URL.
     * @property grantsJson Raw JSON of the offer's `grants` object (OID4VCI v1.0 §4.1.1), when the
     *   offer was carried by value in a `credential_offer` parameter. `null` for legacy/by-reference
     *   offers. Carries e.g. the pre-authorized code grant and `tx_code` requirements.
     */
    data class CredentialOffer(
        val credentialIssuer: String,  // Can be empty if credentialOfferUri is present
        val credentialConfigurationIds: List<String>,
        val credentialOfferUri: String? = null,
        val rawUrl: String,
        val grantsJson: String? = null
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

