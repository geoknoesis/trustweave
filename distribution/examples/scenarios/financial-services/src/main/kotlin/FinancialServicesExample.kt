package org.trustweave.examples.financial

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** An example reviewer signs a synthetic identity-review outcome. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "financial-services",
            credentialType = "IdentityReviewCredential",
            claims =
                linkedMapOf(
                    "reviewId" to "review-demo-21",
                    "reviewOutcome" to "approved",
                    "reviewPolicy" to "demo-policy-v1",
                ),
            tamperedClaim = "reviewOutcome",
            tamperedValue = "rejected",
        )
    }
