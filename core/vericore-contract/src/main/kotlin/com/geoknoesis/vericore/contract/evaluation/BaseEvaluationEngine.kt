package com.geoknoesis.vericore.contract.evaluation

import com.geoknoesis.vericore.contract.models.ConditionType
import com.geoknoesis.vericore.contract.models.ContractCondition
import kotlinx.serialization.json.JsonElement

/**
 * Base class for evaluation engines that provides hash calculation.
 * 
 * Extend this class to create custom evaluation engines. The implementation
 * hash is computed automatically from the class file bytecode.
 * 
 * **Example:**
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
 *         // Your implementation
 *     }
 * }
 * ```
 */
abstract class BaseEvaluationEngine : ContractEvaluationEngine {
    
    /**
     * Computes implementation hash from class file.
     * Override this if you need a different hash calculation method.
     */
    protected open fun computeImplementationHash(): String {
        return EngineHashCalculator.computeFromClassFile(this::class.java)
    }
    
    /**
     * Lazy initialization of hash to avoid computation during object construction.
     */
    final override val implementationHash: String by lazy {
        computeImplementationHash()
    }
    
    /**
     * Default implementation of evaluateConditions that evaluates each condition individually.
     * Override if you need custom batch evaluation logic.
     */
    override suspend fun evaluateConditions(
        conditions: List<ContractCondition>,
        inputData: JsonElement,
        context: EvaluationContext
    ): Map<String, Boolean> {
        return conditions.associate { condition ->
            condition.id to evaluateCondition(condition, inputData, context)
        }
    }
}

