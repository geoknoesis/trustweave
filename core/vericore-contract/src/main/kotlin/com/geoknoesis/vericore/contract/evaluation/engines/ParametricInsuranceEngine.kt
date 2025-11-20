package com.geoknoesis.vericore.contract.evaluation.engines

import com.geoknoesis.vericore.contract.evaluation.*
import com.geoknoesis.vericore.contract.models.*
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
 * val engine = ParametricInsuranceEngine()
 * registry.register(engine)
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
        
        val path = parts[0].trim().removePrefix("$.").trim()
        val thresholdStr = parts[1].trim()
        
        // Extract value from input data
        val inputObj = inputData as? JsonObject
            ?: throw IllegalArgumentException("Input data must be a JSON object")
        
        val value = inputObj[path]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw IllegalArgumentException("Cannot extract numeric value from path: $path")
        
        val threshold = thresholdStr.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid threshold value: $thresholdStr")
        
        // Evaluate comparison
        return when (operator) {
            ">=" -> value >= threshold
            "<=" -> value <= threshold
            "==" -> value == threshold
            ">" -> value > threshold
            "<" -> value < threshold
            "!=" -> value != threshold
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
        
        val path1 = parts[0].trim().removePrefix("$.").trim()
        val path2 = parts[1].trim().removePrefix("$.").trim()
        
        val inputObj = inputData as? JsonObject
            ?: throw IllegalArgumentException("Input data must be a JSON object")
        
        val value1 = inputObj[path1]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw IllegalArgumentException("Cannot extract numeric value from path: $path1")
        
        val value2 = inputObj[path2]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw IllegalArgumentException("Cannot extract numeric value from path: $path2")
        
        // Evaluate comparison
        return when (operator) {
            ">=" -> value1 >= value2
            "<=" -> value1 <= value2
            "==" -> value1 == value2
            ">" -> value1 > value2
            "<" -> value1 < value2
            "!=" -> value1 != value2
            else -> false
        }
    }
}

