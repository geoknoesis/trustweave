package org.trustweave.anchor.algorand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AlgorandTransportTest {
    @Test fun `SDK clients retain HTTPS rather than falling back to HTTP`() {
        val client =
            AlgorandBlockchainAnchorClient(
                AlgorandBlockchainAnchorClient.TESTNET,
                mapOf(
                    "algodUrl" to "https://algod.example",
                    "indexerUrl" to "https://indexer.example",
                ),
            )
        for ((field, expected) in listOf("algodClient" to "https://algod.example", "indexerClient" to "https://indexer.example")) {
            val sdk =
                client.javaClass
                    .getDeclaredField(field)
                    .apply { isAccessible = true }
                    .get(client)
            assertEquals(expected, sdk.javaClass.getMethod("getHost").invoke(sdk))
        }
    }

    @Test fun `public HTTP rejects credentials before client use`() {
        assertFailsWith<IllegalArgumentException> {
            AlgorandBlockchainAnchorClient(AlgorandBlockchainAnchorClient.TESTNET, mapOf("algodUrl" to "http://8.8.8.8"))
        }
    }
}
