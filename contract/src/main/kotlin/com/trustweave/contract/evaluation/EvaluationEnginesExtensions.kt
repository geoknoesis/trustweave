package com.trustweave.contract.evaluation

import com.trustweave.contract.models.ContractCondition
import com.trustweave.contract.models.ExecutionModel
import kotlinx.serialization.json.JsonElement

/**
 * Extension functions for better developer experience with evaluation engines.
 */

/**
 * Helper function to extract engine ID from execution model.
 */
private fun ExecutionModel.getEngineId(): String? = when (this) {
    is ExecutionModel.Parametric -> evaluationEngine
    is ExecutionModel.Conditional -> evaluationEngine
    is ExecutionModel.Scheduled -> evaluationEngine
    is ExecutionModel.EventDriven -> evaluationEngine
    is ExecutionModel.Manual -> null
}

/**
 * Get an engine or throw with a clear error message.
 *
 * @param engineId The engine identifier
 * @return The engine
 * @throws IllegalStateException if engine is not registered
 */
fun EvaluationEngines.require(engineId: String): ContractEvaluationEngine {
    return this[engineId] ?: throw IllegalStateException(
        "Evaluation engine '$engineId' is not registered. " +
        "Available engines: ${keys.joinToString()}"
    )
}

/**
 * Verify engine integrity or throw SecurityException.
 *
 * @param engineId The engine identifier
 * @param expectedHash The expected implementation hash
 * @throws IllegalStateException if engine is not registered
 * @throws SecurityException if integrity check fails (hash mismatch)
 */
fun EvaluationEngines.verifyOrThrow(engineId: String, expectedHash: String) {
    val engine = this[engineId]
    if (engine == null) {
        throw IllegalStateException(
            "Evaluation engine '$engineId' is not registered. " +
            "Available engines: ${keys.joinToString()}"
        )
    }
    val actualHash = engine.implementationHash
    if (actualHash != expectedHash) {
        throw SecurityException(
            "Evaluation engine '$engineId' integrity check failed. " +
            "Engine may have been tampered with. " +
            "Expected hash: $expectedHash, " +
            "Actual hash: $actualHash"
        )
    }
}

/**
 * Add engine hash and version to execution model from registered engines.
 *
 * @param engines The evaluation engines collection
 * @return Execution model with engine hash and version populated
 */
fun ExecutionModel.withEngineHash(engines: EvaluationEngines): ExecutionModel {
    val engineId = getEngineId() ?: return this
    val engine = engines[engineId]
    val engineVersion = engine?.version
    val engineHash = engine?.implementationHash

    return when (this) {
        is ExecutionModel.Parametric -> copy(
            engineVersion = engineVersion,
            engineHash = engineHash
        )
        is ExecutionModel.Conditional -> copy(
            engineVersion = engineVersion,
            engineHash = engineHash
        )
        is ExecutionModel.Scheduled -> copy(
            engineVersion = engineVersion,
            engineHash = engineHash
        )
        is ExecutionModel.EventDriven -> copy(
            engineVersion = engineVersion,
            engineHash = engineHash
        )
        is ExecutionModel.Manual -> this
    }
}

/**
 * Evaluate a condition using an engine with error handling.
 *
 * Catches expected exceptions (IllegalArgumentException, UnsupportedOperationException)
 * and converts them to error results. Re-throws unexpected errors (OutOfMemoryError, etc.).
 *
 * @param engine The evaluation engine to use
 * @param inputData Input data for evaluation
 * @param context Evaluation context
 * @return Condition result with error handling
 */
suspend fun ContractCondition.evaluateWith(
    engine: ContractEvaluationEngine,
    inputData: JsonElement,
    context: EvaluationContext
): com.trustweave.contract.models.ConditionResult {
    return try {
        com.trustweave.contract.models.ConditionResult(
            conditionId = id,
            satisfied = engine.evaluateCondition(this, inputData, context),
            evaluatedValue = inputData
        )
    } catch (e: IllegalArgumentException) {
        com.trustweave.contract.models.ConditionResult(
            conditionId = id,
            satisfied = false,
            evaluatedValue = inputData,
            error = "Invalid condition or input data: ${e.message}"
        )
    } catch (e: UnsupportedOperationException) {
        com.trustweave.contract.models.ConditionResult(
            conditionId = id,
            satisfied = false,
            evaluatedValue = inputData,
            error = "Condition type not supported: ${e.message}"
        )
    } catch (e: IllegalStateException) {
        com.trustweave.contract.models.ConditionResult(
            conditionId = id,
            satisfied = false,
            evaluatedValue = inputData,
            error = "Evaluation state error: ${e.message}"
        )
    } catch (e: Exception) {
        // Re-throw unexpected exceptions
        // Note: Error types (OutOfMemoryError, etc.) are not caught and will propagate
        throw e
    }
}

/**
 * Get an engine or return default value.
 *
 * @param engineId The engine identifier
 * @param defaultValue Function to compute default value
 * @return The engine or default value
 */
inline fun EvaluationEngines.getOrElse(
    engineId: String,
    defaultValue: () -> ContractEvaluationEngine
): ContractEvaluationEngine = this[engineId] ?: defaultValue()

/**
 * Filter engines by a predicate.
 *
 * @param predicate Function to test each engine
 * @return List of engines matching the predicate
 */
fun EvaluationEngines.filter(
    predicate: (ContractEvaluationEngine) -> Boolean
): List<ContractEvaluationEngine> = values.filter(predicate)

/**
 * Transform engines using a mapping function.
 *
 * @param transform Function to transform each engine
 * @return List of transformed values
 */
fun <T> EvaluationEngines.map(
    transform: (ContractEvaluationEngine) -> T
): List<T> = values.map(transform)

