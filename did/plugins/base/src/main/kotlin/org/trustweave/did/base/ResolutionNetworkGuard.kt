package org.trustweave.did.base

import okhttp3.Dns
import org.trustweave.core.net.PrivateNetworkGuard
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * SSRF deny-list for outbound DID-resolution requests whose host comes from an untrusted DID
 * (for example `did:web`).
 *
 * It extends [PrivateNetworkGuard] (loopback, any-local, link-local, site-local, multicast,
 * IPv6 unique-local) with ranges that guard misses:
 * - `0.0.0.0/8` ("this network"; `0.0.0.0` reaches localhost on Linux)
 * - `100.64.0.0/10` (RFC 6598 carrier-grade NAT, used for internal cloud networks)
 * - `192.0.0.0/24` (IETF protocol assignments), `198.18.0.0/15` (benchmarking)
 * - `240.0.0.0/4` (reserved) and the `255.255.255.255` broadcast address
 * - TEST-NET documentation ranges `192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24` and
 *   IPv6 documentation `2001:db8::/32`, Teredo `2001::/32`, and the local-use NAT64 `64:ff9b:1::/48`
 * - `fc00::/7` and `fe80::/10` checked explicitly from the raw bytes
 * - IPv6 addresses that embed an IPv4 address (IPv4-mapped `::ffff:0:0/96`, IPv4-compatible
 *   `::/96`, NAT64 `64:ff9b::/96`, 6to4 `2002::/16`): the embedded IPv4 address is checked
 *   against the same list.
 */
public object ResolutionNetworkGuard {
    /** True if [address] must never be the target of a resolution request. */
    public fun isDisallowed(address: InetAddress): Boolean {
        if (PrivateNetworkGuard.isDisallowed(address)) return true
        val bytes = address.address
        return when (address) {
            is Inet4Address -> isDisallowedIpv4(bytes)
            is Inet6Address -> isDisallowedIpv6(bytes)
            else -> true
        }
    }

    /**
     * Resolves [host] and returns why it is disallowed, or `null` when every resolved address
     * is a permitted public address. An unresolvable host is reported as a rejection.
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

    private fun isDisallowedIpv4(b: ByteArray): Boolean {
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        val b2 = b[2].toInt() and 0xFF
        return b0 == 0 ||
            b0 == 10 ||
            b0 == 127 ||
            (b0 == 100 && b1 in 64..127) ||
            (b0 == 169 && b1 == 254) ||
            (b0 == 172 && b1 in 16..31) ||
            (b0 == 192 && b1 == 0 && b2 == 0) ||
            (b0 == 192 && b1 == 168) ||
            (b0 == 192 && b1 == 0 && b2 == 2) ||
            (b0 == 198 && b1 == 51 && b2 == 100) ||
            (b0 == 203 && b1 == 0 && b2 == 113) ||
            (b0 == 198 && (b1 == 18 || b1 == 19)) ||
            b0 >= 224
    }

    private fun isDisallowedIpv6(b: ByteArray): Boolean {
        if (b.size != 16) return true
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        // fc00::/7 unique-local, fe80::/10 link-local, fec0::/10 site-local, ff00::/8 multicast.
        if ((b0 and 0xFE) == 0xFC) return true
        if (b0 == 0xFE && (b1 and 0xC0) in setOf(0x80, 0xC0)) return true
        if (b0 == 0xFF) return true
        // ::/96 (IPv4-compatible, includes :: and ::1) and ::ffff:0:0/96 (IPv4-mapped).
        val firstTenZero = (0 until 10).all { b[it].toInt() == 0 }
        if (firstTenZero) {
            val b10 = b[10].toInt() and 0xFF
            val b11 = b[11].toInt() and 0xFF
            if ((b10 == 0 && b11 == 0) || (b10 == 0xFF && b11 == 0xFF)) {
                return isDisallowedIpv4(b.copyOfRange(12, 16))
            }
        }
        // 2001:db8::/32 documentation, 2001::/32 Teredo.
        if (b0 == 0x20 && b1 == 0x01) {
            val b2 = b[2].toInt() and 0xFF
            val b3 = b[3].toInt() and 0xFF
            if ((b2 == 0x0D && b3 == 0xB8) || (b2 == 0 && b3 == 0)) return true
        }
        // 64:ff9b:1::/48 local-use NAT64 (RFC 8215).
        if (b0 == 0x00 &&
            b1 == 0x64 &&
            (b[2].toInt() and 0xFF) == 0xFF &&
            (b[3].toInt() and 0xFF) == 0x9B &&
            b[4].toInt() == 0 &&
            (b[5].toInt() and 0xFF) == 0x01
        ) {
            return true
        }
        // 64:ff9b::/96 NAT64.
        val nat64Prefix = byteArrayOf(0, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        if (b.copyOfRange(0, 12).contentEquals(nat64Prefix)) return isDisallowedIpv4(b.copyOfRange(12, 16))
        // 2002::/16 6to4: the IPv4 address is in bytes 2..5.
        if (b0 == 0x20 && b1 == 0x02) return isDisallowedIpv4(b.copyOfRange(2, 6))
        return false
    }
}

/**
 * OkHttp [Dns] that refuses to hand out any address [ResolutionNetworkGuard] disallows.
 *
 * The pre-flight host check runs before the request; this wrapper re-checks the addresses OkHttp
 * will actually connect to, so a DNS answer that changes between the check and the connection
 * (DNS rebinding) cannot reach an internal address.
 */
public class ResolutionGuardedDns(
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        addresses.firstOrNull { ResolutionNetworkGuard.isDisallowed(it) }?.let {
            throw UnknownHostException(
                "SSRF guard: host '$hostname' resolves to a disallowed internal address (${it.hostAddress})",
            )
        }
        return addresses
    }
}
