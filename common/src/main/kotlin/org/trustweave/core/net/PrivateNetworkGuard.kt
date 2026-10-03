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
     * IPv6 unique-local (`fc00::/7`), IPv4 ranges that are not publicly routable (`0/8`, CGNAT
     * `100.64/10`, `192.0.0/24`, `198.18/15`, `240/4`), and IPv6 forms that embed an IPv4 address
     * (IPv4-compatible, NAT64 `64:ff9b::/96`, 6to4 `2002::/16`), judged by the embedded address.
     */
    public fun isDisallowed(address: InetAddress): Boolean =
        address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            isUniqueLocalIpv6(address) ||
            isReservedIpv4(address) ||
            embeddedIpv4(address)?.let { isDisallowed(it) } == true

    /**
     * Resolves [host] and returns a human-readable rejection reason if it is disallowed or cannot be
     * resolved, or `null` if every resolved address is a permitted public address.
     *
     * ALL resolved addresses are checked, so a host with several DNS records cannot smuggle one
     * internal address past the guard. IP-literal hosts are validated without a DNS lookup.
     */
    public fun rejectionReason(host: String): String? {
        if (host.isBlank()) return "host is blank"
        val addresses =
            try {
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

    private fun isReservedIpv4(address: InetAddress): Boolean {
        val b = address.address
        if (b.size != 4) return false
        val o0 = b[0].toInt() and 0xFF
        val o1 = b[1].toInt() and 0xFF
        val o2 = b[2].toInt() and 0xFF
        return o0 == 0 ||
            (o0 == 100 && o1 in 64..127) ||
            (o0 == 192 && o1 == 0 && o2 == 0) ||
            (o0 == 198 && (o1 == 18 || o1 == 19)) ||
            o0 >= 240
    }

    /** The IPv4 address embedded in an IPv6 address (compatible, NAT64, 6to4), or `null`. */
    private fun embeddedIpv4(address: InetAddress): InetAddress? {
        val b = address.address
        if (b.size != 16) return null
        val u = IntArray(16) { b[it].toInt() and 0xFF }
        val offset =
            when {
                (0..11).all { u[it] == 0 } -> 12 // ::a.b.c.d (IPv4-compatible)
                u[0] == 0x00 && u[1] == 0x64 && u[2] == 0xFF && u[3] == 0x9B && (4..11).all { u[it] == 0 } -> 12 // NAT64
                u[0] == 0x20 && u[1] == 0x02 -> 2 // 6to4
                else -> return null
            }
        return InetAddress.getByAddress(b.copyOfRange(offset, offset + 4))
    }
}
