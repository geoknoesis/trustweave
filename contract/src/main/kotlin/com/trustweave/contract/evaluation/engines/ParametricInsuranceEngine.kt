package com.trustweave.contract.evaluation.engines

import com.trustweave.contract.evaluation.*
import com.trustweave.contract.models.*
import kotlinx.serialization.json.*

/**
 * Parametric insurance evaluation engine.
 *
 * Evaluates conditions for parametric insurance contracts based on
 * external data triggers (e.g., flood depth, temperature, rainfall).
 *
 * Supports condition types:
 * - THRESHOLD: Value exceeds or falls below a threshold
 * - RANGE: Value within a specified range
 * - COMPARISON: Compare two values
 *
 * Expression format examples:
 * - Threshold: "$.floodDepthCm >= 50"
 * - Range: "$.temperature >= 20 && $.temperature <= 30"
 * - Comparison: "$.value1 > $.value2"
 *
 * **Example Usage:**
 * ```kotlin
 * val engines = EvaluationEngines()
 * engines += ParametricInsuranceEngine()
 *
 * val contract = createContract(
 *     executionModel = ExecutionModel.Parametric(
 *         triggerType = TriggerType.EarthObservation,
 *         evaluationEngine = "parametric-insurance"
 *     )
 * )
 * ```
 */
class ParametricInsuranceEngine : BaseEvaluationEngine() {
    override val engineId: String = "parametric-insurance"
    override val version: String = "1.0.0"
    override val supportedConditionTypes: Set<ConditionType> = setOf(
        ConditionType.THRESHOLD,
        ConditionType.RANGE,
        ConditionType.COMPARISON
    )

    override suspend fun evaluateCondition(
        condition: ContractCondition,
        inputData: JsonElement,
        context: EvaluationContext
    ): Boolean {
        return when (condition.conditionType) {
            ConditionType.THRESHOLD -> evaluateThreshold(condition, inputData)
            ConditionType.RANGE -> evaluateRange(condition, inputData)
            ConditionType.COMPARISON -> evaluateComparison(condition, inputData)
            else -> throw UnsupportedOperationException(
                "Condition type ${condition.conditionType} not supported by $engineId"
            )
        }
    }

    /**
     * Evaluates a threshold condition.
     *
     * Expression format: "$.path >= value" or "$.path <= value" or "$.path == value"
     * Examples:
     * - "$.floodDepthCm >= 50"
     * - "$.temperature <= 30"
     * - "$.rainfallMm == 100"
     */
    private fun evaluateThreshold(
        condition: ContractCondition,
        inputData: JsonElement
    ): Boolean {
        val expression = condition.expression.trim()
        require(expression.isNotBlank()) {
            "Condition expression cannot be blank"
        }

        // Parse expression: $.path >= value
        val operators = listOf(">=", "<=", "==", ">", "<", "!=")
        val operator = operators.find { expression.contains(it) }
            ?: throw IllegalArgumentException(
                "Invalid threshold expression: $expression. " +
                "Expected format: \$.path >= value"
            )

        val parts = expression.split(operator, limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid threshold expression: $expression")
        }

        val leftPart = parts[0].trim()
        require(leftPart.startsWith("$.")) {
            "Invalid threshold expression: $expression. Path must start with \$."
        }
        val path = leftPart.removePrefix("$.").trim()
        require(path.isNotBlank()) {
            "Invalid threshold expression: $expression. Path cannot be empty"
        }

        val thresholdStr = parts[1].trim()
        require(thresholdStr.isNotBlank()) {
            "Invalid threshold expression: $expression. Threshold value cannot be empty"
        }

        // Extract value from input data
        val inputObj = inputData as? JsonObject
            ?: throw IllegalArgumentException("Input data must be a JSON object")

        val pathValue = inputObj[path]
        val value = pathValue?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw IllegalArgumentException(
                "Cannot extract numeric value from path: $path. " +
                "Found: ${pathValue?.let { it.toString() } ?: "null"}"
            )

        val threshold = thresholdStr.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid threshold value: $thresholdStr")

        // Evaluate comparison with epsilon for equality
        return when (operator) {
            ">=" -> value >= threshold
            "<=" -> value <= threshold
            "==" -> kotlin.math.abs(value - threshold) < 1e-9
            ">" -> value > threshold
            "<" -> value < threshold
            "!=" -> kotlin.math.abs(value - threshold) >= 1e-9
            else -> false
        }
    }

    /**
     * Evaluates a range condition.
     *
     * Expression format: "$.path >= min && $.path <= max"
     * Example: "$.temperature >= 20 && $.temperature <= 30"
     */
    private fun evaluateRange(
        condition: ContractCondition,
        inputData: JsonElement
    ): Boolean {
        val expression = condition.expression.trim()
        require(expression.isNotBlank()) {
            "Condition expression cannot be blank"
        }

        // Parse: $.path >= min && $.path <= max
        if (!expression.contains("&&")) {
            throw IllegalArgumentException(
                "Invalid range expression: $expression. " +
                "Expected format: \$.path >= min && \$.path <= max"
            )
        }

        val parts = expression.split("&&", limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid range expression: $expression")
        }

        val minCondition = parts[0].trim()
        val maxCondition = parts[1].trim()
        require(minCondition.isNotBlank() && maxCondition.isNotBlank()) {
            "Invalid range expression: $expression. Both min and max conditions must be present"
        }

        // Evaluate both conditions
        val minSatisfied = evaluateThreshold(
            condition.copy(expression = minCondition),
            inputData
        )
        val maxSatisfied = evaluateThreshold(
            condition.copy(expression = maxCondition),
            inputData
        )

        return minSatisfied && maxSatisfied
    }

    /**
     * Evaluates a comparison condition.
     *
     * Expression format: "$.path1 > $.path2" or "$.path1 == $.path2"
     * Example: "$.currentValue > $.previousValue"
     */
    private fun evaluateComparison(
        condition: ContractCondition,
        inputData: JsonElement
    ): Boolean {
        val expression = condition.expression.trim()
        require(expression.isNotBlank()) {
            "Condition expression cannot be blank"
        }

        val operators = listOf(">=", "<=", "==", ">", "<", "!=")
        val operator = operators.find { expression.contains(it) }
            ?: throw IllegalArgumentException(
                "Invalid comparison expression: $expression. " +
                "Expected format: \$.path1 >= \$.path2"
            )

        val parts = expression.split(operator, limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid comparison expression: $expression")
        }

        val leftPart = parts[0].trim()
        val rightPart = parts[1].trim()

        require(leftPart.startsWith("$.")) {
            "Invalid comparison expression: $expression. Left path must start with \$."
        }
        require(rightPart.startsWith("$.")) {
            "Invalid comparison expression: $expression. Right path must start with \$."
        }

        val path1 = leftPart.removePrefix("$.").trim()
        val path2 = rightPart.removePrefix("$.").trim()

        require(path1.isNotBlank()) {
            "Invalid comparison expression: $expression. Left path cannot be empty"
        }
        require(path2.isNotBlank()) {
            "Invalid comparison expression: $expression. Right path cannot be empty"
        }

        val inputObj = inputData as? JsonObject
            ?: throw IllegalArgumentException("Input data must be a JSON object")

        val path1Value = inputObj[path1]
        val value1 = path1Value?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw IllegalArgumentException(
                "Cannot extract numeric value from path: $path1. " +
                "Found: ${path1Value?.let { it.toString() } ?: "null"}"
            )

        val path2Value = inputObj[path2]
        val value2 = path2Value?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw IllegalArgumentException(
                "Cannot extract numeric value from path: $path2. " +
                "Found: ${path2Value?.let { it.toString() } ?: "null"}"
            )

        // Evaluate comparison with epsilon for equality
        return when (operator) {
            ">=" -> value1 >= value2
            "<=" -> value1 <= value2
            "==" -> kotlin.math.abs(value1 - value2) < 1e-9
            ">" -> value1 > value2
            "<" -> value1 < value2
            "!=" -> kotlin.math.abs(value1 - value2) >= 1e-9
            else -> false
        }
    }
}

