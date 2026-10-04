package org.trustweave.soldid

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolDidConfigRedactionTest {
    @Test
    fun `toString prints scheme and host only for URLs that embed API keys`() {
        val url = "https://user:SECRETKEY@rpc.example.org:8545/v2/SECRETKEY?apikey=SECRETKEY"
        val text =
            SolDidConfig(
                rpcUrl = url,
                privateKey = "SECRETKEY",
            ).toString()
        assertFalse(text.contains("SECRETKEY"), text)
        assertFalse(text.contains("/v2/"), text)
        assertTrue(text.contains("https://rpc.example.org:8545"), text)
    }
}
