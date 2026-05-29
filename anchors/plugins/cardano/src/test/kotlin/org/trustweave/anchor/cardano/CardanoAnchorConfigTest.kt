package org.trustweave.anchor.cardano

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardanoAnchorConfigTest {

    @Test
    fun `defaults to CIP-20 label 674 on Preview`() {
        val cfg = CardanoAnchorConfig(blockfrostProjectId = "preview123")
        assertEquals(CardanoAnchorConfig.CIP20_MESSAGE_LABEL, cfg.metadataLabel)
        assertEquals(CardanoNetwork.Preview, cfg.network)
        assertEquals("https://cardano-preview.blockfrost.io/api/v0", cfg.blockfrostBaseUrl())
        assertFalse(cfg.canSubmit())
    }

    @Test
    fun `canSubmit is true when mnemonic provided`() {
        val cfg = CardanoAnchorConfig(
            blockfrostProjectId = "preview123",
            submitterMnemonic = "abandon abandon abandon",
        )
        assertTrue(cfg.canSubmit())
    }

    @Test
    fun `canSubmit is true when secret key provided`() {
        val cfg = CardanoAnchorConfig(
            blockfrostProjectId = "preview123",
            submitterSecretKey = "deadbeef",
        )
        assertTrue(cfg.canSubmit())
    }

    @Test
    fun `rejects blank project id`() {
        assertThrows<IllegalArgumentException> {
            CardanoAnchorConfig(blockfrostProjectId = "   ")
        }
    }

    @Test
    fun `rejects negative metadata label`() {
        assertThrows<IllegalArgumentException> {
            CardanoAnchorConfig(blockfrostProjectId = "preview123", metadataLabel = -5L)
        }
    }

    @Test
    fun `rejects non-positive confirmation timeout`() {
        assertThrows<IllegalArgumentException> {
            CardanoAnchorConfig(blockfrostProjectId = "preview123", confirmationTimeoutSeconds = 0)
        }
    }

    @Test
    fun `base URL override wins over network default`() {
        val cfg = CardanoAnchorConfig(
            blockfrostProjectId = "preview123",
            blockfrostBaseUrlOverride = "http://localhost:9999/api/v0",
        )
        assertEquals("http://localhost:9999/api/v0", cfg.blockfrostBaseUrl())
    }

    @Test
    fun `toMap and fromMap round-trip`() {
        val cfg = CardanoAnchorConfig(
            blockfrostProjectId = "preview123",
            network = CardanoNetwork.Preprod,
            submitterMnemonic = "test test test",
            metadataLabel = 1234L,
            blockfrostBaseUrlOverride = "http://localhost:8080/api/v0",
            confirmationTimeoutSeconds = 30L,
        )
        val map = cfg.toMap()
        val restored = CardanoAnchorConfig.fromMap("cardano:preprod", map)
        assertEquals(cfg, restored)
    }

    @Test
    fun `fromMap derives network from chainId when missing in map`() {
        val cfg = CardanoAnchorConfig.fromMap(
            "cardano:mainnet",
            mapOf(CardanoAnchorConfig.KEY_PROJECT_ID to "mainnet123"),
        )
        assertEquals(CardanoNetwork.Mainnet, cfg.network)
        assertEquals(CardanoAnchorConfig.CIP20_MESSAGE_LABEL, cfg.metadataLabel)
    }

    @Test
    fun `network fromChainId returns null for unknown`() {
        assertNull(CardanoNetwork.fromChainId("cardano:bogus"))
    }
}
