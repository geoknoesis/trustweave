package org.trustweave.examples.healthcare

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** A clinic signs a synthetic encounter record for a patient DID. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "healthcare",
            credentialType = "MedicalRecordCredential",
            claims =
                linkedMapOf(
                    "recordId" to "encounter-demo-001",
                    "recordType" to "synthetic-visit-summary",
                    "clinicName" to "Example Clinic",
                ),
            tamperedClaim = "recordId",
            tamperedValue = "encounter-other",
        )
    }
