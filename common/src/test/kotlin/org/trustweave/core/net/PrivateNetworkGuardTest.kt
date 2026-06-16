package org.trustweave.core.net

import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The SSRF guard is security-critical: outbound requests to hosts derived from untrusted input
 * (did:web identifiers, OID4VP `request_uri`, …) must never reach loopback, private, link-local,
 * or cloud-metadata addresses. IP-literal hosts are used throughout so the tests do no DNS.
 */
class PrivateNetworkGuardTest {

    private fun addr(ip: String): InetAddress = InetAddress.getByName(ip)

    @Test
    fun `loopback link-local private wildcard and IPv6 ULA addresses are disallowed`() {
        val disallowed = listOf(
            "127.0.0.1", // IPv4 loopback
            "10.0.0.1", // private 10/8
            "172.16.5.4", // private 172.16/12
            "192.168.1.1", // private 192.168/16
            "169.254.169.254", // link-local / cloud metadata (IMDS)
            "0.0.0.0", // wildcard / any-local
            "::1", // IPv6 loopback
            "fe80::1", // IPv6 link-local
            "fc00::1", // IPv6 unique-local (ULA)
        )
        for (ip in disallowed) {
            assertTrue(PrivateNetworkGuard.isDisallowed(addr(ip)), "$ip must be disallowed")
        }
    }

    @Test
    fun `public addresses are allowed`() {
        for (ip in listOf("8.8.8.8", "1.1.1.1", "93.184.216.34", "2606:4700:4700::1111")) {
            assertFalse(PrivateNetworkGuard.isDisallowed(addr(ip)), "$ip must be allowed")
        }
    }

    @Test
    fun `rejectionReason flags internal IP-literal hosts and allows public ones`() {
        assertNotNull(PrivateNetworkGuard.rejectionReason("127.0.0.1"))
        assertNotNull(PrivateNetworkGuard.rejectionReason("169.254.169.254"))
        assertNull(PrivateNetworkGuard.rejectionReason("8.8.8.8"))
    }
}
