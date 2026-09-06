package org.trustweave.examples.workflow

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** A reviewer signs the approval of one workflow revision. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "digital-workflow",
            credentialType = "WorkflowApprovalCredential",
            claims =
                linkedMapOf(
                    "workflowId" to "workflow-demo-17",
                    "revision" to "7",
                    "decision" to "approved",
                ),
            tamperedClaim = "revision",
            tamperedValue = "8",
        )
    }
