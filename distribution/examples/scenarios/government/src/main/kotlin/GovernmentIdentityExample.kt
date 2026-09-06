package org.trustweave.examples.government

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** An example municipal issuer signs a residence claim. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "government",
            credentialType = "ResidenceCredential",
            claims =
                linkedMapOf(
                    "municipality" to "Example Town",
                    "residenceStatus" to "resident",
                    "documentId" to "residence-demo-11",
                ),
            tamperedClaim = "municipality",
            tamperedValue = "Other Town",
        )
    }
