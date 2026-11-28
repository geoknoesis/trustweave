package org.trustweave.referencewallet.lib

import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure Kotlin unified QR / NFC / Deep-Link classifier for Android.
 * Mirrors Expo's `qrRouter.ts` + `credentialOfferIngress.ts` + `presentationRequestQr.ts`.
 */
object PayloadRouter {

    sealed interface IncomingOffer {
        data class TrustWeaveDirect(
            val issuerUrl: String,
            val credentialEndpoint: String = "/api/demo-issuer/credential",
            val studentId: String? = null,
            val offerPath: String? = null,
        ) : IncomingOffer

        data class Oid4VciPreAuthorized(
            val issuerBase: String,
            val preAuthorizedCode: String,
            val credentialConfigurationIds: List<String> = emptyList(),
        ) : IncomingOffer
    }

    data class PresentationRequestPayload(
        val verifierUrl: String,
        val requestEndpoint: String = "/api/demo-verifier/request",
        val verifyEndpoint: String = "/api/demo-verifier/verify",
    )

    sealed interface RoutedPayload {
        data class Offer(val offer: IncomingOffer, val raw: String) : RoutedPayload
        data class Presentation(val request: PresentationRequestPayload, val raw: String) : RoutedPayload
        data class PublicVerify(val url: String, val raw: String) : RoutedPayload
        data class HolderDid(val did: String, val raw: String) : RoutedPayload
        data class Unknown(val reason: String, val raw: String) : RoutedPayload
    }

    fun unwrapDeepLink(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("trustweavewallet://", ignoreCase = true)) return trimmed
        return try {
            val query = parseQueryParams(URI(trimmed).rawQuery)
            query["data"] ?: query["url"] ?: query["offer"] ?: query["request"] ?: trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    fun classify(input: String): RoutedPayload {
        val raw = unwrapDeepLink(input)
        if (raw.isBlank()) {
            return RoutedPayload.Unknown("No code or link was detected. Try scanning again.", raw)
        }

        parseIncomingOffer(raw)?.let { return RoutedPayload.Offer(it, raw) }
        parsePublicVerifyUrl(raw)?.let { return RoutedPayload.PublicVerify(it, raw) }
        parsePresentationRequest(raw)?.let { return RoutedPayload.Presentation(it, raw) }

        try {
            val obj = Json.parseToJsonElement(raw).jsonObject
            if (obj.str("type") == "trustweave-holder" && obj.str("did") != null) {
                return RoutedPayload.HolderDid(obj.str("did")!!, raw)
            }
        } catch (_: Exception) {
            // not JSON
        }

        if (raw.startsWith("did:")) {
            return RoutedPayload.HolderDid(raw, raw)
        }

        return RoutedPayload.Unknown(
            "This QR code is not a supported credential offer or verification request.",
            raw,
        )
    }

    fun parseIncomingOffer(raw: String): IncomingOffer? {
        parseOid4VciOffer(raw)?.let { return it }
        parseDirectOffer(raw)?.let { return it }
        return null
    }

    private fun parseOid4VciOffer(raw: String): IncomingOffer.Oid4VciPreAuthorized? {
        if (!raw.startsWith("openid-credential-offer://", ignoreCase = true)) return null
        return try {
            val normalized = raw.replaceFirst(Regex("^openid-credential-offer://", RegexOption.IGNORE_CASE), "https://oid4vci.local/")
            val params = parseQueryParams(URI(normalized).rawQuery)
            val offerJson = params["credential_offer"] ?: return null
            val obj = Json.parseToJsonElement(offerJson).jsonObject
            val issuer = obj.str("credential_issuer")?.trimEnd('/') ?: return null
            val grants = obj["grants"] as? JsonObject ?: return null
            val preAuth = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] as? JsonObject ?: return null
            val code = preAuth.str("pre-authorized_code") ?: return null
            val ids = (obj["credential_configuration_ids"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?: emptyList()
            IncomingOffer.Oid4VciPreAuthorized(
                issuerBase = issuer,
                preAuthorizedCode = code,
                credentialConfigurationIds = ids,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDirectOffer(raw: String): IncomingOffer.TrustWeaveDirect? {
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return try {
                val uri = URI(raw)
                val path = uri.path ?: ""
                if (path.contains("/issuer/")) {
                    val base = "${uri.scheme}://${uri.authority}"
                    val params = parseQueryParams(uri.rawQuery)
                    IncomingOffer.TrustWeaveDirect(
                        issuerUrl = base,
                        studentId = params["studentId"] ?: params["id"],
                        offerPath = path,
                    )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
        return try {
            val obj = Json.parseToJsonElement(raw).jsonObject
            if (obj.str("type") != "trustweave-credential-offer") return null
            val issuerUrl = obj.str("issuerUrl")?.trimEnd('/') ?: return null
            IncomingOffer.TrustWeaveDirect(
                issuerUrl = issuerUrl,
                credentialEndpoint = obj.str("credentialEndpoint") ?: "/api/demo-issuer/credential",
                studentId = obj.str("studentId"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePublicVerifyUrl(raw: String): String? {
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) return null
        return try {
            val uri = URI(raw)
            val path = uri.path ?: ""
            if (path == "/verify" || path.startsWith("/verify/")) raw else null
        } catch (_: Exception) {
            null
        }
    }

    fun parsePresentationRequest(raw: String): PresentationRequestPayload? {
        if (raw.startsWith("openid4vp://", ignoreCase = true)) {
            return try {
                val normalized = raw.replaceFirst(Regex("^openid4vp://", RegexOption.IGNORE_CASE), "https://openid4vp.local/")
                val params = parseQueryParams(URI(normalized).rawQuery)
                val reqUri = params["request_uri"]?.let { URI(it) }
                if (reqUri != null && (reqUri.scheme == "http" || reqUri.scheme == "https")) {
                    val endpoint = buildString {
                        append(reqUri.path ?: "/api/demo-verifier/request")
                        if (!reqUri.rawQuery.isNullOrBlank()) append("?${reqUri.rawQuery}")
                    }
                    return PresentationRequestPayload(
                        verifierUrl = "${reqUri.scheme}://${reqUri.authority}",
                        requestEndpoint = endpoint,
                    )
                }
                val respUri = params["response_uri"]?.let { URI(it) }
                if (respUri != null && (respUri.scheme == "http" || respUri.scheme == "https")) {
                    return PresentationRequestPayload(
                        verifierUrl = "${respUri.scheme}://${respUri.authority}",
                        verifyEndpoint = respUri.path ?: "/api/demo-verifier/verify",
                    )
                }
                null
            } catch (_: Exception) {
                null
            }
        }

        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return try {
                val uri = URI(raw)
                val path = uri.path ?: ""
                if (path == "/verifier" || path.startsWith("/verifier/")) {
                    PresentationRequestPayload(verifierUrl = "${uri.scheme}://${uri.authority}")
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        return try {
            val obj = Json.parseToJsonElement(raw).jsonObject
            if (obj.str("type") != "trustweave-presentation-request") return null
            val verifierUrl = obj.str("verifierUrl")?.trimEnd('/') ?: return null
            PresentationRequestPayload(
                verifierUrl = verifierUrl,
                requestEndpoint = obj.str("requestEndpoint") ?: "/api/demo-verifier/request",
                verifyEndpoint = obj.str("verifyEndpoint") ?: "/api/demo-verifier/verify",
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Rewrite localhost/127.0.0.1 hostnames to the configured backend host when running on an emulator/phone. */
    fun resolveHostUrl(url: String, fallbackBaseUrl: String): String {
        val clean = url.trimEnd('/')
        return try {
            val uri = URI(clean)
            val isLocal = uri.host in setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")
            if (isLocal && fallbackBaseUrl.isNotBlank()) {
                val fb = URI(fallbackBaseUrl.trimEnd('/'))
                val portPart = if (uri.port != -1) ":${uri.port}" else if (fb.port != -1) ":${fb.port}" else ""
                "${uri.scheme}://${fb.host}$portPart"
            } else {
                clean
            }
        } catch (_: Exception) {
            clean
        }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            val key = URLDecoder.decode(if (idx >= 0) pair.substring(0, idx) else pair, "UTF-8")
            val value = if (idx >= 0) URLDecoder.decode(pair.substring(idx + 1), "UTF-8") else ""
            out[key] = value
        }
        return out
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
