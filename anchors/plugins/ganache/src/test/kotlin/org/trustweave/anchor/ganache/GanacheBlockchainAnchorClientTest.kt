package org.trustweave.anchor.ganache

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for Ganache chain-id resolution feeding EIP-155 transaction signing.
 * Ganache's chain-id parsing is deliberately permissive (`toLongOrNull() ?: 1337`);
 * these tests pin that the number it resolves is exactly the number every
 * transaction is signed with, fallback included. Chain mechanics are covered by
 * the evm-base tests; the Docker-backed scenario lives in [GanacheEoIntegrationTest].
 */
class GanacheBlockchainAnchorClientTest {

    /** Well-known throwaway test key (Hardhat/Anvil account #0). Never funded. */
    private val options = mapOf<String, Any?>(
        "privateKey" to "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    )

    @Test
    fun `explicit eip155 chain number drives EIP-155 signing`() {
        GanacheBlockchainAnchorClient("eip155:5777", options).use { client ->
            assertEquals(5777L, client.eip155ChainId)
        }
    }

    @Test
    fun `default local chain id resolves to 1337 for signing`() {
        GanacheBlockchainAnchorClient(GanacheBlockchainAnchorClient.LOCAL, options).use { client ->
            assertEquals(1337L, client.eip155ChainId)
        }
    }

    @Test
    fun `permissive non-numeric chain suffix falls back to 1337 for signing too`() {
        GanacheBlockchainAnchorClient("eip155:local", options).use { client ->
            assertEquals(1337L, client.eip155ChainId)
        }
    }
}
