package org.trustweave.anchor.cardano

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.trustweave.anchor.AnchorRef
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Round-trip integration test against Blockfrost Preview.
 *
 * Gated on two env vars — the test is *skipped* when either is missing so the test
 * task still passes locally and in CI without secrets:
 *
 * - `CARDANO_BLOCKFROST_PROJECT_ID` — Blockfrost project id (must start with `preview`)
 * - `CARDANO_SUBMITTER_MNEMONIC` — 15- or 24-word BIP-39 mnemonic of a Preview wallet
 *   that holds at least 5 tADA. (Preview faucet: <https://docs.cardano.org/cardano-testnet/tools/faucet/>.)
 *
 * The test:
 * 1. Anchors a small JSON payload under CIP-20 (label 674) via [com.bloxbean.cardano.client.quicktx.QuickTxBuilder].
 * 2. Waits for confirmation (up to 5 min).
 * 3. Reads the metadata back via the Blockfrost `/txs/{hash}/metadata` endpoint and asserts equality.
 *
 * This test exercises the real Cardano wire protocol — no mocking.
 */
@EnabledIfEnvironmentVariable(named = "CARDANO_BLOCKFROST_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CARDANO_SUBMITTER_MNEMONIC", matches = ".+")
class CardanoBlockfrostIntegrationTest {

    @Test
    fun `anchor and read back round-trip on Preview`() = runBlocking {
        val projectId = System.getenv("CARDANO_BLOCKFROST_PROJECT_ID")!!
        val mnemonic = System.getenv("CARDANO_SUBMITTER_MNEMONIC")!!

        val cfg = CardanoAnchorConfig(
            blockfrostProjectId = projectId,
            network = CardanoNetwork.Preview,
            submitterMnemonic = mnemonic,
            confirmationTimeoutSeconds = 300,
        )

        CardanoBlockchainAnchorClient(CardanoBlockchainAnchorClient.PREVIEW, cfg).use { client ->
            val payload = buildJsonObject {
                put("test", JsonPrimitive("trustweave-cardano-anchor"))
                put("ts", JsonPrimitive(System.currentTimeMillis()))
            }

            val anchored = client.writePayload(payload)
            assertNotNull(anchored.ref.txHash, "txHash must be returned")

            val ref = AnchorRef(
                chainId = CardanoBlockchainAnchorClient.PREVIEW,
                txHash = anchored.ref.txHash,
            )

            // Blockfrost surfaces metadata once the tx is on-chain; completeAndWait already blocked
            // until confirmation, so a single read should succeed.
            val read = client.readPayload(ref)
            val obj = read.payload as JsonObject
            assertEquals("trustweave-cardano-anchor", obj["test"]!!.jsonPrimitive.content)
        }
    }
}
