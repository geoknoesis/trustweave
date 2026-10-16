package org.trustweave.core.net

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.net.UnknownHostException
import java.time.Duration
import javax.net.SocketFactory

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
 * A [SocketFactory] whose sockets refuse, at connect time, any destination [isDisallowed] rejects.
 *
 * OkHttp 4 never consults [Dns] when the host is an IP literal (`http://127.0.0.1/`,
 * `http://[::1]/`, `http://169.254.169.254/`, `http://[::ffff:10.0.0.1]/`), so a [Dns]-only guard
 * is bypassed by a literal in the initial URL or in any redirect `Location`. Every outbound TCP
 * connection OkHttp opens — initial request and every redirect hop, literal or resolved — goes
 * through [Socket.connect], so checking the concrete [InetSocketAddress] here covers all of them
 * and also closes the resolve-then-connect gap.
 */
internal class SsrfGuardedSocketFactory(
    private val delegate: SocketFactory = SocketFactory.getDefault(),
    private val isDisallowed: (InetAddress) -> Boolean = PrivateNetworkGuard::isDisallowed,
) : SocketFactory() {
    private fun guarded(): Socket =
        object : Socket() {
            override fun connect(
                endpoint: SocketAddress,
                timeout: Int,
            ) {
                check(endpoint)
                super.connect(endpoint, timeout)
            }

            override fun connect(endpoint: SocketAddress) {
                check(endpoint)
                super.connect(endpoint)
            }
        }

    private fun check(endpoint: SocketAddress) {
        val inet = endpoint as? InetSocketAddress ?: throw java.io.IOException("SSRF guard: unsupported socket address")
        val address = inet.address ?: throw UnknownHostException("SSRF guard: unresolved destination '${inet.hostString}'")
        if (isDisallowed(address)) {
            throw UnknownHostException(
                "SSRF guard: destination '${inet.hostString}' is a disallowed internal/loopback/link-local address",
            )
        }
    }

    // OkHttp uses only the unconnected createSocket(); the connecting overloads are refused so the
    // factory cannot be used to sidestep the check.
    override fun createSocket(): Socket = guarded()

    override fun createSocket(
        host: String?,
        port: Int,
    ): Socket = guarded().also { it.connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket =
        guarded().also {
            it.bind(InetSocketAddress(localHost, localPort))
            it.connect(InetSocketAddress(host, port))
        }

    override fun createSocket(
        host: InetAddress?,
        port: Int,
    ): Socket = guarded().also { it.connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket =
        guarded().also {
            it.bind(InetSocketAddress(localAddress, localPort))
            it.connect(InetSocketAddress(address, port))
        }
}

internal fun ssrfGuardedClientBuilder(
    dns: Dns = SsrfBlockingDns,
    isDisallowed: (InetAddress) -> Boolean = PrivateNetworkGuard::isDisallowed,
): OkHttpClient.Builder =
    OkHttpClient
        .Builder()
        .dns(dns)
        .socketFactory(SsrfGuardedSocketFactory(isDisallowed = isDisallowed))
        // A proxy resolves the target itself, so neither the Dns nor the connect-time guard would
        // see the real destination.
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .callTimeout(Duration.ofSeconds(60))

/**
 * A default OkHttpClient hardened for fetching attacker-influenced URLs: it blocks internal hosts
 * via [SsrfBlockingDns] (names) and a connect-time [PrivateNetworkGuard] check (IP literals, and
 * every redirect hop), ignores system proxies, and bounds every call with timeouts (defeating
 * slow-loris / hung reads). Callers that need different behaviour can inject their own client.
 */
public fun ssrfGuardedOkHttpClient(): OkHttpClient = ssrfGuardedClientBuilder().build()
