package org.trustweave.polygondid

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolygonDidConfigRedactionTest {
    @Test
    fun `toString prints scheme and host only for URLs that embed API keys`() {
        val url = "https://user:SECRETKEY@rpc.example.org:8545/v2/SECRETKEY?apikey=SECRETKEY"
        val text =
            PolygonDidConfig(
                rpcUrl = url,
                chainId = "eip155:137",
                privateKey = "SECRETKEY",
            ).toString()
        assertFalse(text.contains("SECRETKEY"), text)
        assertFalse(text.contains("/v2/"), text)
        assertTrue(text.contains("https://rpc.example.org:8545"), text)
    }
}
