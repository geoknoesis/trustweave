package org.trustweave.examples.location

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** An observer signs a location claim for a subject. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "proof-of-location",
            credentialType = "LocationAttestationCredential",
            claims =
                linkedMapOf(
                    "latitude" to "45.5017",
                    "longitude" to "-73.5673",
                    "observedAt" to "2026-09-05T12:00:00Z",
                ),
            tamperedClaim = "latitude",
            tamperedValue = "45.5019",
        )
    }
