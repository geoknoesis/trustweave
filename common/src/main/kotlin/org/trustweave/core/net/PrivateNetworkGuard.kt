package org.trustweave.core.net

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * SSRF guard for outbound HTTP requests whose target host is derived from untrusted input — e.g. a
 * `did:web` identifier (`did:web:example.com` → `https://example.com/...`) or an OID4VP/OID4VCI
 * `request_uri` / `credential_offer_uri`.
 *
 * Such hosts are attacker-controlled by design, so before connecting the caller must reject any host
 * that resolves to an address used to reach the local machine, the internal network, or a cloud
 * metadata endpoint (e.g. `169.254.169.254`). This guard is the deny-list of those ranges.
 *
 * It does NOT defend against DNS rebinding or redirect-to-internal on its own — for those the host
 * must be re-checked on each connection hop (e.g. via an HTTP-client DNS interceptor). It is the
 * mandatory pre-flight check on the initial, untrusted host.
 */
public object PrivateNetworkGuard {

    /**
     * True if [address] falls in a range that an outbound request to a public endpoint must never
     * target: loopback, wildcard/any-local, link-local (incl. the `169.254.169.254` cloud-metadata
     * IP and IPv6 `fe80::/10`), private/site-local (`10/8`, `172.16/12`, `192.168/16`), multicast,
     * or IPv6 unique-local (`fc00::/7`).
     */
    public fun isDisallowed(address: InetAddress): Boolean =
        address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            isUniqueLocalIpv6(address)

    /**
     * Resolves [host] and returns a human-readable rejection reason if it is disallowed or cannot be
     * resolved, or `null` if every resolved address is a permitted public address.
     *
     * ALL resolved addresses are checked, so a host with several DNS records cannot smuggle one
     * internal address past the guard. IP-literal hosts are validated without a DNS lookup.
     */
    public fun rejectionReason(host: String): String? {
        if (host.isBlank()) return "host is blank"
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            return "host '$host' could not be resolved (${e.message})"
        }
        if (addresses.isEmpty()) return "host '$host' resolved to no addresses"
        addresses.firstOrNull { isDisallowed(it) }?.let {
            return "host '$host' resolves to a disallowed internal/loopback/link-local address (${it.hostAddress})"
        }
        return null
    }

    /**
     * IPv6 unique-local addresses (`fc00::/7`). The JVM's [InetAddress.isSiteLocalAddress] only
     * recognises the deprecated `fec0::/10` range for IPv6, so ULA must be detected explicitly.
     */
    private fun isUniqueLocalIpv6(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
