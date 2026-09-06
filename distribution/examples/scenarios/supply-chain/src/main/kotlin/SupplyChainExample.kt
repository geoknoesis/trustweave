package org.trustweave.examples.supplychain

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** A supplier signs a shipment batch and destination. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "supply-chain",
            credentialType = "ShipmentCredential",
            claims =
                linkedMapOf(
                    "batchId" to "batch-demo-17",
                    "destination" to "warehouse-demo-a",
                    "productName" to "Example component",
                ),
            tamperedClaim = "destination",
            tamperedValue = "warehouse-other",
        )
    }
