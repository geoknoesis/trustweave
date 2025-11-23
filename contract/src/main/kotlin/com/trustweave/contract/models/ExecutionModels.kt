package com.trustweave.contract.models

import com.trustweave.anchor.AnchorRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Execution context for contract execution.
 */
@Serializable
data class ExecutionContext(
    val triggerData: JsonElement? = null,      // For parametric execution
    val eventData: JsonElement? = null,        // For event-driven execution
    val timeContext: String? = null,          // ISO 8601 timestamp for time-based execution
    val additionalContext: JsonElement? = null
)

/**
 * Execution result.
 */
@Serializable
data class ExecutionResult(
    val contractId: String,
    val executed: Boolean,
    val executionType: ExecutionType,
    val outcomes: List<ContractOutcome>,
    val evidence: List<String>? = null, // Verifiable Credential IDs
    val timestamp: String // ISO 8601 timestamp
)

/**
 * Execution types.
 */
@Serializable
enum class ExecutionType {
    PARAMETRIC_TRIGGER,
    CONDITIONAL_EVALUATION,
    SCHEDULED_ACTION,
    EVENT_RESPONSE,
    MANUAL_ACTION
}

/**
 * Contract outcome.
 */
@Serializable
data class ContractOutcome(
    val type: OutcomeType,
    val description: String,
    val monetaryImpact: MonetaryAmount? = null,
    val obligationTriggered: String? = null,
    val metadata: JsonElement? = null
)

/**
 * Outcome types.
 */
@Serializable
enum class OutcomeType {
    PAYOUT,
    PENALTY,
    NOTIFICATION,
    STATUS_CHANGE,
    CUSTOM
}

/**
 * Condition evaluation result.
 */
@Serializable
data class ConditionEvaluation(
    val contractId: String,
    val conditions: List<ConditionResult>,
    val overallResult: Boolean,
    val timestamp: String // ISO 8601 timestamp
)

/**
 * Individual condition result.
 */
@Serializable
data class ConditionResult(
    val conditionId: String,
    val satisfied: Boolean,
    val evaluatedValue: JsonElement? = null,
    val error: String? = null
)

/**
 * Bound contract result.
 */
@Serializable
data class BoundContract(
    val contract: SmartContract,
    val credentialId: String,
    val anchorRef: AnchorRefData
) {
    /**
     * Gets the AnchorRef (non-serializable) for API compatibility.
     */
    fun getAnchorRef(): AnchorRef = anchorRef.toAnchorRef()
}

