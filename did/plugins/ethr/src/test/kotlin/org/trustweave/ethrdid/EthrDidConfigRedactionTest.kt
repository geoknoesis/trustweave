package org.trustweave.ethrdid

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EthrDidConfigRedactionTest {
    @Test
    fun `toString prints scheme and host only for URLs that embed API keys`() {
        val url = "https://user:SECRETKEY@rpc.example.org:8545/v2/SECRETKEY?apikey=SECRETKEY"
        val text =
            EthrDidConfig(
                rpcUrl = url,
                chainId = "eip155:1",
                privateKey = "SECRETKEY",
            ).toString()
        assertFalse(text.contains("SECRETKEY"), text)
        assertFalse(text.contains("/v2/"), text)
        assertTrue(text.contains("https://rpc.example.org:8545"), text)
    }
}
