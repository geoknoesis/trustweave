package org.trustweave.examples.dcat

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** A publisher signs dataset catalog metadata. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "data-catalog",
            credentialType = "DatasetMetadataCredential",
            claims =
                linkedMapOf(
                    "datasetId" to "dataset-demo-weather",
                    "datasetVersion" to "2026.09",
                    "licenseName" to "Example evaluation license",
                ),
            tamperedClaim = "datasetVersion",
            tamperedValue = "2026.10",
        )
    }
