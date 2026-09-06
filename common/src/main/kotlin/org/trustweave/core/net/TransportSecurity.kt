package org.trustweave.core.net

/**
 * Transport-security policy for **operator-configured** endpoints: blockchain RPC nodes, resolver
 * services, and anything else whose URL comes from deployment configuration rather than from an
 * untrusted document.
 *
 * This is the mirror image of [SsrfBlockingDns], and the distinction matters. For URLs discovered
 * inside untrusted input — an entity statement's `federation_fetch_endpoint`, a did:web host — a
 * private address is the *attack*, and [SsrfBlockingDns] refuses it. For an endpoint the operator
 * configured, a private address is the *normal case*: node software is routinely reached at
 * `http://localhost:8332` or across a cluster network, and blocking that would break the documented
 * deployment while preventing nothing.
 *
 * What is worth refusing there is plaintext to a *public* host, because the credentials and signed
 * payloads on that connection are readable and rewritable in transit.
 */
public object TransportSecurity {
    /**
     * Returns [url] unchanged when its transport is acceptable, and throws [IllegalArgumentException]
     * when it is plaintext `http://` to a public host.
     *
     * @param url the configured endpoint.
     * @param what what travels over it, named in the error so the operator can weigh the risk
     *   (for example "RPC credentials and signed transactions").
     */
    public fun requireSecureForPublicHosts(
        url: String,
        what: String,
    ): String {
        val endpoint =
            runCatching { java.net.URI(url).toURL() }.getOrNull()
                ?: throw IllegalArgumentException("Invalid configured HTTP endpoint")
        require(endpoint.protocol.lowercase() in setOf("http", "https") && endpoint.host.isNotBlank()) {
            "Configured endpoint must use HTTP or HTTPS with a host"
        }
        require(endpoint.userInfo == null) { "Credentials must not be embedded in endpoint URLs" }
        if (endpoint.protocol.equals("https", ignoreCase = true)) return url
        // URL.host accepts DNS labels containing underscores, including container service names.
        val host = endpoint.host

        // Plaintext is allowed only where the host is *positively established* as local. Note this
        // deliberately does NOT use PrivateNetworkGuard.rejectionReason: that returns a reason both
        // for a private host and for one that cannot be resolved, so treating "has a reason" as
        // "is local" would quietly permit plaintext to an unresolvable public host — failing open
        // on exactly the DNS hiccup an attacker can induce.
        val addresses =
            try {
                java.net.InetAddress
                    .getAllByName(host)
                    .toList()
            } catch (e: java.net.UnknownHostException) {
                emptyList()
            }

        // `all`, not `any`: a name resolving to both a private and a public address must not get
        // plaintext on the strength of the private one.
        val definitelyLocal = addresses.isNotEmpty() && addresses.all { PrivateNetworkGuard.isDisallowed(it) }

        if (!definitelyLocal) {
            val why =
                if (addresses.isEmpty()) {
                    "host '$host' could not be resolved, so it cannot be confirmed local"
                } else {
                    "host '$host' is public"
                }
            throw IllegalArgumentException(
                "Refusing a plaintext http:// endpoint: $why. " +
                    "$what cross this connection in the clear. " +
                    "Use https, or point it at a loopback or private-range node.",
            )
        }
        return url
    }
}
