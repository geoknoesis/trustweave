package org.trustweave.anchor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class AnchorCancellationTest {
    @Test
    fun `provider cancellation survives shared read and write handlers`() =
        runBlocking<Unit> {
            for (testMode in listOf(false, true)) {
                val options = mapOf("inMemoryTestMode" to testMode)
                val client =
                    object : AbstractBlockchainAnchorClient("test:chain", options) {
                        override fun canSubmitTransaction() = true

                        override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String =
                            throw CancellationException("cancelled")

                        override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult =
                            throw CancellationException("cancelled")

                        override fun generateTestTxHash() = "test"

                        override fun getBlockchainName() = "test"
                    }
                assertFailsWith<CancellationException> { client.writePayload(buildJsonObject {}) }
                assertFailsWith<CancellationException> { client.readPayload(AnchorRef("test:chain", "tx")) }
            }
        }
}
