package org.trustweave.examples.blockchain

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.core.util.DigestUtils
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

/**
 * Local anchoring contract demonstration. No RPC connection or blockchain transaction occurs.
 * Run: `./gradlew :distribution:examples:runBlockchainAnchoring`
 */
fun main() =
    runBlocking {
        val document =
            buildJsonObject {
                put("id", "example-document")
                put("description", "Local anchoring demonstration")
            }
        val payload =
            buildJsonObject {
                put("digest", DigestUtils.sha256DigestMultibase(document))
            }
        for (chainId in listOf("ethereum:local-demo", "base:local-demo", "arbitrum:local-demo")) {
            val client = InMemoryBlockchainAnchorClient(chainId)
            val written = client.writePayload(payload)
            val restored = client.readPayload(written.ref)
            check(restored.payload == payload) { "Anchor payload did not round-trip for $chainId" }
            check(restored.ref == written.ref) { "Anchor reference changed for $chainId" }
            println("Verified in-memory write/read on $chainId: ${written.ref.txHash}")
            client.clear()
        }
        println("Local demonstration complete; this does not validate hosted blockchain providers.")
    }
