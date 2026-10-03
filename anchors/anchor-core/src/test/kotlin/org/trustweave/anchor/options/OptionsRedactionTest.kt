package org.trustweave.anchor.options

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Options classes are logged and put in exception messages; their toString must not leak secrets. */
class OptionsRedactionTest {
    private val secret = "s3cr3t-value-0123456789abcdef"

    private fun assertNoSecret(text: String) {
        assertFalse(text.contains(secret), "secret leaked into: $text")
        assertFalse(text.contains("API_KEY_IN_PATH"), "URL-embedded key leaked into: $text")
    }

    @Test
    fun `AlgorandOptions redacts private key, tokens and URL paths`() {
        val text =
            AlgorandOptions(
                algodUrl = "https://algod.example.com/API_KEY_IN_PATH",
                algodToken = secret,
                privateKey = secret,
                appId = "123",
                indexerUrl = "https://indexer.example.com:8443?token=API_KEY_IN_PATH",
                indexerToken = secret,
            ).toString()

        assertNoSecret(text)
        assertTrue(text.contains("appId=123"))
        assertTrue(text.contains("https://algod.example.com/***"))
        assertTrue(text.contains("https://indexer.example.com:8443/***"))
    }

    @Test
    fun `EVM-style options redact private keys and RPC API keys`() {
        assertNoSecret(PolygonOptions("https://polygon.example.com/v2/API_KEY_IN_PATH", secret, "0xabc").toString())
        assertNoSecret(GanacheOptions("http://localhost:8545", secret).toString())
        assertTrue(GanacheOptions("http://localhost:8545", secret).toString().contains("rpcUrl=http://localhost:8545,"))
    }

    @Test
    fun `IndyOptions redacts the wallet key`() {
        val text = IndyOptions("https://pool.example.com", "wallet", secret, "did:sov:abc").toString()
        assertNoSecret(text)
        assertTrue(text.contains("walletName=wallet"))
    }

    @Test
    fun `absent secrets stay visibly absent and toMap is unchanged`() {
        assertTrue(AlgorandOptions().toString().contains("privateKey=null"))
        assertEquals(secret, PolygonOptions(privateKey = secret).toMap()["privateKey"])
    }
}
