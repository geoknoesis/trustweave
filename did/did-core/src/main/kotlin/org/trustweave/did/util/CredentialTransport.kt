package org.trustweave.did.util

import java.net.URI

/**
 * Refuses to send credentials (API keys, bearer tokens) over cleartext HTTP.
 */
object CredentialTransport {
    /**
     * Throws [IllegalArgumentException] when [baseUrl] is `http://` and [hasCredentials] is true
     * and the host is not a loopback address. `https://` is always accepted, and so is `http://`
     * when no credentials are configured (the caller then has nothing to leak). The host is
     * judged by its literal form (`localhost`, `127.0.0.0/8`, `::1`) and never resolved, so a
     * hostname that merely resolves to a local address does not qualify.
     */
    @JvmStatic
    fun requireSecure(
        baseUrl: String,
        hasCredentials: Boolean,
        what: String = "an API key",
    ) {
        if (!hasCredentials) return
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return // format is validated elsewhere
        if (!uri.scheme.equals("http", ignoreCase = true)) return
        val host =
            uri.host
                ?.removePrefix("[")
                ?.removeSuffix("]")
                ?.lowercase()
                .orEmpty()
        require(isLoopbackLiteral(host)) {
            "Refusing to send $what over cleartext http:// to non-loopback host '${Redaction.url(baseUrl)}'. Use https."
        }
    }

    private fun isLoopbackLiteral(host: String): Boolean =
        host == "localhost" ||
            host == "::1" ||
            host == "0:0:0:0:0:0:0:1" ||
            Regex("^127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$").matches(host)
}
