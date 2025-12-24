package org.trustweave.contract.evaluation

import org.trustweave.contract.models.ContractCondition
import org.trustweave.contract.models.ExecutionModel
import kotlinx.serialization.json.JsonElement

/**
 * Pluggable contract condition evaluation engine.
 *
 * Each engine implements domain-specific evaluation logic for contract conditions.
 * The engine ID must match the evaluationEngine string in the contract's ExecutionModel.
 *
 * **Example Usage:**
 * ```kotlin
 * class MyEngine : BaseEvaluationEngine() {
 *     override val engineId = "my-engine"
 *     override val version = "1.0.0"
 *     override val supportedConditionTypes = setOf(ConditionType.THRESHOLD)
 *
 *     override suspend fun evaluateCondition(
 *         condition: ContractCondition,
 *         inputData: JsonElement,
 *         context: EvaluationContext
 *     ): Boolean {
 *         // Implementation...
 *     }
 * }
 * ```
 */
interface ContractEvaluationEngine {
    /**
     * Unique identifier for this evaluation engine.
     * Must match the evaluationEngine value in ExecutionModel.
     * Examples: "parametric-insurance", "rule-engine", "smart-contract-vm"
     */
    val engineId: String

    /**
     * Version of the engine implementation.
     * Used for tamper detection and compatibility checks.
     */
    val version: String

    /**
     * Hash/digest of the engine implementation.
     * Used to verify the engine hasn't been tampered with.
     *
     * This should be computed once during engine initialization
     * and remain constant for the engine's lifetime.
     */
    val implementationHash: String

    /**
     * Supported condition types for this engine.
     */
    val supportedConditionTypes: Set<org.trustweave.contract.models.ConditionType>

    /**
     * Evaluate a single contract condition.
     *
     * @param condition The condition to evaluate
     * @param inputData Input data for evaluation (e.g., trigger data, event data)
     * @param context Additional evaluation context
     * @return True if condition is satisfied, false otherwise
     * @throws UnsupportedOperationException if condition type is not supported
     */
    suspend fun evaluateCondition(
        condition: ContractCondition,
        inputData: JsonElement,
        context: EvaluationContext
    ): Boolean

    /**
     * Evaluate multiple conditions (for composite conditions).
     *
     * @param conditions List of conditions to evaluate
     * @param inputData Input data for evaluation
     * @param context Additional evaluation context
     * @return Map of condition ID to evaluation result
     */
    suspend fun evaluateConditions(
        conditions: List<ContractCondition>,
        inputData: JsonElement,
        context: EvaluationContext
    ): Map<String, Boolean>
}

/**
 * Evaluation context providing additional information for condition evaluation.
 */
data class EvaluationContext(
    /**
     * Contract ID being evaluated.
     */
    val contractId: String,

    /**
     * Execution model of the contract.
     */
    val executionModel: ExecutionModel,

    /**
     * Contract-specific data (domain-specific).
     */
    val contractData: JsonElement? = null,

    /**
     * Additional metadata for evaluation.
     */
    val metadata: Map<String, Any?> = emptyMap()
)

