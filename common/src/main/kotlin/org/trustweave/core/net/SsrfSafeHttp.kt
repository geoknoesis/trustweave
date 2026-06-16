package org.trustweave.core.net

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration

/**
 * An OkHttp [Dns] that refuses to resolve any host mapping to an internal address (loopback,
 * private, link-local, cloud-metadata, IPv6-ULA — see [PrivateNetworkGuard]).
 *
 * Because OkHttp resolves DNS for the initial request AND every redirect hop through this [Dns],
 * installing it on a client blocks SSRF to internal endpoints across redirects, and OkHttp connects
 * only to the addresses returned here (closing the resolve-then-connect TOCTOU). If ANY resolved
 * address is disallowed the whole host is refused, so a multi-record host cannot smuggle one
 * internal address past the guard.
 */
public object SsrfBlockingDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.any { PrivateNetworkGuard.isDisallowed(it) }) {
            throw UnknownHostException(
                "SSRF guard: host '$hostname' resolves to a disallowed internal/loopback/link-local address",
            )
        }
        return addresses
    }
}

/**
 * A default OkHttpClient hardened for fetching attacker-influenced URLs: it blocks internal hosts
 * via [SsrfBlockingDns] and bounds every call with timeouts (defeating slow-loris / hung reads).
 * Callers that need different behaviour can inject their own client.
 */
public fun ssrfGuardedOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .dns(SsrfBlockingDns)
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .callTimeout(Duration.ofSeconds(60))
        .build()
