package org.trustweave.anchor.starknet

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StarknetSafetyTest {
    @Test
    fun `stub cannot be discovered or fabricate a ledger write`() =
        runBlocking<Unit> {
            assertFalse(ServiceLoader.load(BlockchainAnchorClientProvider::class.java).any { it.name == "starknet" })
            StarkNetBlockchainAnchorClient(StarkNetBlockchainAnchorClient.MAINNET).use { client ->
                assertFailsWith<BlockchainException.ConfigurationFailed> { client.writePayload(buildJsonObject {}) }
            }
        }
}
