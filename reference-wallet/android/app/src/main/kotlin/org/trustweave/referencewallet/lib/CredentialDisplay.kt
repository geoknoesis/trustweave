package org.trustweave.referencewallet.lib

import java.util.Base64
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Pure Kotlin presentation helpers for displaying credentials in plain consumer language
 * while remaining completely honest about issuer trust and expiration status.
 */
object CredentialDisplay {

    enum class BadgeTone { SUCCESS, INFO, WARNING, DANGER, NEUTRAL }

    data class StatusBadge(val label: String, val tone: BadgeTone)

    data class DisplayClaim(
        val key: String,
        val label: String,
        val value: String,
        val shareable: Boolean,
    )

    data class ShareableClaimDetail(
        val name: String,
        val label: String,
        val value: String?,
    )

    private val ENVELOPE_KEYS = setOf(
        "iss", "sub", "aud", "iat", "exp", "nbf", "jti", "cnf",
        "vct", "vc", "type", "@context", "id", "_sd", "_sd_alg", "status", "proof",
    )

    private val IMAGE_KEY_REGEX = Regex("portrait|photo|image|picture|avatar", RegexOption.IGNORE_CASE)

    /** Convert camelCase / snake_case claim names to readable Title Case labels. */
    fun humanClaimName(name: String): String =
        name
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

    /** Short scannable key fingerprint (e.g. `z6MkpT…vktH`) for trust prompts and cards. */
    fun shortDidFingerprint(did: String): String {
        if (did.isBlank()) return "Unknown"
        val tail = did.substringAfterLast(':')
        return if (tail.length <= 12) tail else "${tail.take(6)}…${tail.takeLast(4)}"
    }

    /**
     * Honest issuer label: never calls an arbitrary `did:key:` a "Verified issuer".
     * Uses an explicit organization name when known, a domain for `did:web:`,
     * or a short scannable fingerprint.
     */
    fun formatIssuerLabel(issuerDid: String, issuerName: String? = null): String {
        if (!issuerName.isNullOrBlank()) return issuerName.trim()
        if (issuerDid.startsWith("did:web:")) {
            val host = issuerDid.removePrefix("did:web:").substringBefore(':')
            if (host.isNotBlank()) return host
        }
        if (issuerDid.startsWith("did:")) {
            return "Issuer · ${shortDidFingerprint(issuerDid)}"
        }
        return if (issuerDid.length > 28) "${issuerDid.take(28)}…" else issuerDid
    }

    fun extractExpiryEpochSeconds(cred: Storage.StoredCredential): Long? =
        try {
            val payload = when (cred.format) {
                "vc+sd-jwt" -> SdJwt.decode(cred.credential).issuerPayload
                else -> {
                    val parts = cred.credential.split(".")
                    if (parts.size == 3) {
                        Json.parseToJsonElement(Crypto.b64uDecodeString(parts[1])).jsonObject
                    } else {
                        null
                    }
                }
            }
            (payload?.get("exp") as? JsonPrimitive)?.doubleOrNull?.toLong()
        } catch (_: Exception) {
            null
        }

    fun statusBadge(
        cred: Storage.StoredCredential,
        nowEpochSeconds: Long,
    ): StatusBadge {
        val exp = extractExpiryEpochSeconds(cred)
        if (exp != null && exp <= nowEpochSeconds) {
            return StatusBadge("Expired", BadgeTone.DANGER)
        }
        if (exp != null && exp - nowEpochSeconds < 30L * 24L * 3600L) {
            return StatusBadge("Expiring soon", BadgeTone.WARNING)
        }
        return when (cred.trustSource) {
            "configured", "backend" -> StatusBadge("Trusted issuer", BadgeTone.SUCCESS)
            "confirmed" -> StatusBadge("Confirmed by you", BadgeTone.INFO)
            else -> StatusBadge("Saved on device", BadgeTone.NEUTRAL)
        }
    }

    /** Extract human-readable claim labels and actual values from a stored credential. */
    fun extractClaimsForDisplay(cred: Storage.StoredCredential): List<DisplayClaim> {
        val rawMap = extractRawClaimMap(cred)
        val shareableSet = cred.selectivelyDisclosable.toSet()
        val out = mutableListOf<DisplayClaim>()
        for ((key, value) in rawMap) {
            if (key in ENVELOPE_KEYS) continue
            if (IMAGE_KEY_REGEX.containsMatchIn(key)) continue
            val text = renderValue(value) ?: continue
            if (text.startsWith("data:image/")) continue
            out.add(
                DisplayClaim(
                    key = key,
                    label = humanClaimName(key),
                    value = text,
                    shareable = key in shareableSet,
                ),
            )
        }
        return out
    }

    /** Map every selectively disclosable claim name to its human label and actual value. */
    fun extractShareableClaimDetails(cred: Storage.StoredCredential): List<ShareableClaimDetail> {
        val rawMap = extractRawClaimMap(cred)
        return cred.selectivelyDisclosable.map { name ->
            ShareableClaimDetail(
                name = name,
                label = humanClaimName(name),
                value = rawMap[name]?.let(::renderValue),
            )
        }
    }

    /** Format an ISO-8601 timestamp into concise relative consumer text (e.g. "Just now", "5m ago", "2h ago"). */
    fun formatRelativeTime(isoTimestamp: String, nowEpochSeconds: Long): String =
        try {
            val epoch = Instant.parse(isoTimestamp).epochSeconds
            val diff = (nowEpochSeconds - epoch).coerceAtLeast(0L)
            when {
                diff < 60L -> "Just now"
                diff < 3600L -> "${diff / 60L}m ago"
                diff < 86_400L -> "${diff / 3600L}h ago"
                diff < 7L * 86_400L -> "${diff / 86_400L}d ago"
                else -> isoTimestamp.take(10)
            }
        } catch (_: Exception) {
            isoTimestamp.take(10)
        }

    /** Decode embedded base64 / data-URI portrait bytes from credential claims, if present. */
    fun extractPortraitBytes(cred: Storage.StoredCredential): ByteArray? {
        val rawMap = extractRawClaimMap(cred)
        for ((key, value) in rawMap) {
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            val isPortraitKey = IMAGE_KEY_REGEX.containsMatchIn(key)
            val isDataImage = text.startsWith("data:image/", ignoreCase = true)
            if (!isPortraitKey && !isDataImage) continue
            val b64 = if (text.contains(",")) text.substringAfter(",") else text
            return try {
                Base64.getDecoder().decode(b64.trim())
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun extractRawClaimMap(cred: Storage.StoredCredential): Map<String, JsonElement> =
        try {
            if (cred.format == "vc+sd-jwt") {
                val decoded = SdJwt.decode(cred.credential)
                val map = LinkedHashMap<String, JsonElement>()
                decoded.issuerPayload.forEach { (k, v) -> map[k] = v }
                decoded.disclosures.forEach { d -> map[d.name] = d.value }
                map
            } else {
                val parts = cred.credential.split(".")
                if (parts.size != 3) {
                    emptyMap()
                } else {
                    val payload = Json.parseToJsonElement(Crypto.b64uDecodeString(parts[1])).jsonObject
                    val vc = payload["vc"] as? JsonObject
                    val subject = vc?.get("credentialSubject") as? JsonObject
                    subject ?: emptyMap()
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }

    private fun renderValue(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
            is JsonArray -> element.mapNotNull { renderValue(it) }.joinToString(", ").takeIf { it.isNotBlank() }
            is JsonObject -> element.values.mapNotNull { renderValue(it) }.joinToString(" · ").takeIf { it.isNotBlank() }
        }
}
