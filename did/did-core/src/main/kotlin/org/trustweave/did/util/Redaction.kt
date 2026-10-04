package org.trustweave.did.util

import java.net.URI

/**
 * Helpers for keeping credentials out of `toString()` output and logs.
 */
object Redaction {
    /**
     * Reduces a URL to `scheme://host[:port]`. JSON-RPC endpoints routinely embed the API key in
     * the path (`/v2/<key>`) or query (`?apikey=...`) or userinfo; none of that survives.
     * An unparseable value yields `<redacted-url>` rather than being echoed back.
     */
    @JvmStatic
    fun url(value: String?): String {
        if (value == null) return "null"
        return try {
            val uri = URI(value.trim())
            val scheme = uri.scheme
            val host = uri.host
            if (scheme == null || host == null) {
                "<redacted-url>"
            } else {
                buildString {
                    append(scheme).append("://").append(host)
                    if (uri.port != -1) append(':').append(uri.port)
                }
            }
        } catch (_: Exception) {
            "<redacted-url>"
        }
    }

    /** Renders only the keys of [properties]; the values (which may be secrets) are never printed. */
    @JvmStatic
    fun keysOnly(properties: Map<String, Any?>): String = "${properties.keys} <redacted values>"

    /** `null` or `<redacted>`, for a presence-only rendering of a secret. */
    @JvmStatic
    fun secret(value: Any?): String = if (value == null) "null" else "<redacted>"
}
