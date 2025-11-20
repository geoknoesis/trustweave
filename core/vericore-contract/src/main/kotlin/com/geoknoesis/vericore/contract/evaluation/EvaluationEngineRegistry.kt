package com.geoknoesis.vericore.contract.evaluation

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for contract evaluation engines.
 * 
 * Provides secure registration and lookup of evaluation engines,
 * with tamper detection capabilities.
 * 
 * **Thread Safety**: This registry is thread-safe for concurrent access.
 * 
 * **Example Usage:**
 * ```kotlin
 * val registry = DefaultEvaluationEngineRegistry()
 * 
 * // Register an engine
 * val engine = ParametricInsuranceEngine()
 * registry.register(engine)
 * 
 * // Get an engine
 * val retrieved = registry.get("parametric-insurance")
 * 
 * // Verify engine integrity
 * val isValid = registry.verifyEngineIntegrity("parametric-insurance", expectedHash)
 * ```
 */
interface EvaluationEngineRegistry {
    /**
     * Register an evaluation engine.
     * 
     * @param engine The evaluation engine to register
     * @throws IllegalArgumentException if engine ID is already registered
     */
    fun register(engine: ContractEvaluationEngine)
    
    /**
     * Unregister an evaluation engine.
     * 
     * @param engineId The engine identifier
     * @return True if engine was removed, false if not found
     */
    fun unregister(engineId: String): Boolean
    
    /**
     * Get an evaluation engine by ID.
     * 
     * @param engineId The engine identifier
     * @return The engine, or null if not found
     */
    fun get(engineId: String): ContractEvaluationEngine?
    
    /**
     * Verify an engine matches the expected implementation hash.
     * Used to detect tampering.
     * 
     * @param engineId The engine identifier
     * @param expectedHash The expected implementation hash from the contract
     * @return True if hash matches, false otherwise
     */
    fun verifyEngineIntegrity(engineId: String, expectedHash: String): Boolean
    
    /**
     * Get all registered engine IDs.
     * 
     * @return List of registered engine identifiers
     */
    fun getRegisteredEngineIds(): List<String>
    
    /**
     * Check if an engine is registered.
     * 
     * @param engineId The engine identifier
     * @return True if registered, false otherwise
     */
    fun isRegistered(engineId: String): Boolean
    
    /**
     * Get the number of registered engines.
     * 
     * @return Number of registered engines
     */
    fun size(): Int
    
    /**
     * Clear all registered engines.
     */
    fun clear()
}

/**
 * Default thread-safe implementation of EvaluationEngineRegistry.
 */
class DefaultEvaluationEngineRegistry : EvaluationEngineRegistry {
    private val engines = ConcurrentHashMap<String, ContractEvaluationEngine>()
    private val engineHashes = ConcurrentHashMap<String, String>()
    
    override fun register(engine: ContractEvaluationEngine) {
        if (engines.containsKey(engine.engineId)) {
            throw IllegalArgumentException(
                "Evaluation engine '${engine.engineId}' is already registered"
            )
        }
        engines[engine.engineId] = engine
        engineHashes[engine.engineId] = engine.implementationHash
    }
    
    override fun unregister(engineId: String): Boolean {
        val removed = engines.remove(engineId) != null
        engineHashes.remove(engineId)
        return removed
    }
    
    override fun get(engineId: String): ContractEvaluationEngine? = engines[engineId]
    
    override fun verifyEngineIntegrity(engineId: String, expectedHash: String): Boolean {
        val actualHash = engineHashes[engineId] ?: return false
        return actualHash == expectedHash
    }
    
    override fun getRegisteredEngineIds(): List<String> = engines.keys.toList()
    
    override fun isRegistered(engineId: String): Boolean = engines.containsKey(engineId)
    
    override fun size(): Int = engines.size
    
    override fun clear() {
        engines.clear()
        engineHashes.clear()
    }
}

