package org.trustweave.core.net

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.UnknownHostException
import kotlin.test.assertTrue

/**
 * [SsrfBlockingDns] is the OkHttp DNS used by the SSRF-safe default clients. It must refuse to
 * resolve any host that maps to an internal address, so an attacker-supplied URL (OID4VP
 * `request_uri`, OID4VCI `credential_offer_uri`, …) cannot reach loopback / private / metadata
 * endpoints — on the initial request and on every redirect hop. IP-literal hosts do no DNS.
 */
class SsrfBlockingDnsTest {

    @Test
    fun `loopback host is refused`() {
        assertThrows<UnknownHostException> { SsrfBlockingDns.lookup("127.0.0.1") }
    }

    @Test
    fun `private network host is refused`() {
        assertThrows<UnknownHostException> { SsrfBlockingDns.lookup("10.0.0.1") }
    }

    @Test
    fun `cloud metadata host is refused`() {
        assertThrows<UnknownHostException> { SsrfBlockingDns.lookup("169.254.169.254") }
    }

    @Test
    fun `public address literal resolves`() {
        assertTrue(SsrfBlockingDns.lookup("8.8.8.8").isNotEmpty())
    }

    @Test
    fun `the guarded client is wired to the blocking dns`() {
        assertTrue(ssrfGuardedOkHttpClient().dns === SsrfBlockingDns)
    }
}
