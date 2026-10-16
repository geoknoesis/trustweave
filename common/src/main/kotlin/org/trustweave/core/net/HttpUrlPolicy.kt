package org.trustweave.core.net

import okhttp3.Response
import java.io.IOException
import java.net.InetAddress

/**
 * Small, dependency-free URL policy helpers shared by the protocol plugins.
 */
public object HttpUrlPolicy {
    private val ipv4Literal = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /**
     * True only when [host] is the name `localhost` or an IP *literal* that is a loopback address.
     *
     * A host such as `127.evil.com` is a DNS name, not a literal, and is never loopback here; no DNS
     * lookup is ever performed. IPv6 literals may be bracketed (`[::1]`) and IPv4-mapped IPv6
     * loopback (`::ffff:127.0.0.1`) is recognised.
     */
    public fun isLoopbackHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h =
            host
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
                .lowercase()
        if (h == "localhost") return true
        val literal =
            when {
                ipv4Literal.matches(h) ->
                    ipv4Literal
                        .matchEntire(h)!!
                        .groupValues
                        .drop(1)
                        .all { it.toInt() in 0..255 }
                ':' in h -> h.all { it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' }
                else -> false
            }
        if (!literal) return false
        return runCatching { InetAddress.getByName(h).isLoopbackAddress }.getOrDefault(false)
    }

    /** True when [url] is `https`, or `http` to a loopback host per [isLoopbackHost]. */
    public fun isHttpsOrLoopbackHttp(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return scheme == "https" || (scheme == "http" && isLoopbackHost(uri.host))
    }
}

/** Default cap for protocol response bodies (1 MiB). */
public const val DEFAULT_MAX_RESPONSE_BODY_BYTES: Long = 1L * 1024 * 1024

/**
 * Reads the response body as UTF-8 text, failing with [IOException] if it exceeds [maxBytes] rather
 * than buffering an unbounded body. Returns `null` when the response has no body. The response is
 * always closed.
 */
public fun Response.readBodyBounded(maxBytes: Long = DEFAULT_MAX_RESPONSE_BODY_BYTES): String? =
    use { response ->
        val body = response.body ?: return@use null
        val declared = body.contentLength()
        if (declared > maxBytes) throw IOException("Response body exceeds $maxBytes bytes")
        val source = body.source()
        source.request(maxBytes + 1)
        if (source.buffer.size > maxBytes) throw IOException("Response body exceeds $maxBytes bytes")
        source.buffer.readUtf8()
    }
