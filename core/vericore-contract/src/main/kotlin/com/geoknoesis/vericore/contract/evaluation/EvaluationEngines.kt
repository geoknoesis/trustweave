package com.geoknoesis.vericore.contract.evaluation

import java.util.concurrent.ConcurrentHashMap

/**
 * Collection of contract evaluation engines.
 * 
 * Provides secure registration and lookup of evaluation engines,
 * with tamper detection capabilities.
 * 
 * **Thread Safety**: This collection is thread-safe for concurrent access.
 * - All operations (register, unregister, lookup) are thread-safe
 * - Iterators are weakly consistent (may not reflect all concurrent modifications)
 * - Engine instances themselves should be thread-safe if accessed concurrently
 * 
 * **Example Usage:**
 * ```kotlin
 * val engines = EvaluationEngines()
 * 
 * // Register an engine (using += operator)
 * engines += ParametricInsuranceEngine()
 * 
 * // Get an engine (using [] operator)
 * val engine = engines["parametric-insurance"]
 * 
 * // Check if registered (using 'in' operator)
 * if ("parametric-insurance" in engines) { ... }
 * 
 * // Verify engine integrity
 * engines.verify("parametric-insurance", expectedHash)
 * 
 * // Iterate over engines
 * for (engine in engines) { ... }
 * ```
 */
interface EvaluationEngines : Iterable<ContractEvaluationEngine> {
    /**
     * Register an evaluation engine.
     * 
     * @param engine The evaluation engine to register
     * @throws IllegalArgumentException if engine ID is blank or already registered
     */
    operator fun plusAssign(engine: ContractEvaluationEngine)
    
    /**
     * Unregister an evaluation engine.
     * 
     * @param engineId The engine identifier
     * Note: Use `engineId in engines` before/after to check if removal succeeded.
     * For a method that returns a boolean, use [remove].
     */
    operator fun minusAssign(engineId: String)
    
    /**
     * Remove an engine and return whether it was removed.
     * 
     * @param engineId The engine identifier
     * @return True if engine was removed, false if not found
     */
    fun remove(engineId: String): Boolean
    
    /**
     * Get an evaluation engine by ID.
     * 
     * @param engineId The engine identifier
     * @return The engine, or null if not found
     */
    operator fun get(engineId: String): ContractEvaluationEngine?
    
    /**
     * Check if an engine is registered.
     * 
     * @param engineId The engine identifier
     * @return True if registered, false otherwise
     */
    operator fun contains(engineId: String): Boolean
    
    /**
     * Verify an engine matches the expected implementation hash.
     * Used to detect tampering.
     * 
     * @param engineId The engine identifier
     * @param expectedHash The expected implementation hash from the contract (must not be blank)
     * @return True if hash matches, false otherwise (including if engine is not registered)
     * @throws IllegalArgumentException if expectedHash is blank
     */
    fun verify(engineId: String, expectedHash: String): Boolean
    
    /**
     * Get all registered engine IDs.
     * Returns a view of the keys (not a copy).
     */
    val keys: Set<String>
    
    /**
     * Get all registered engines.
     * Returns a view of the values (not a copy).
     */
    val values: Collection<ContractEvaluationEngine>
    
    /**
     * Get the number of registered engines.
     */
    val size: Int
    
    /**
     * Check if the collection is empty.
     */
    val isEmpty: Boolean
    
    /**
     * Clear all registered engines.
     */
    fun clear()
}

/**
 * Factory function for creating an in-memory evaluation engines collection.
 * 
 * **Example:**
 * ```kotlin
 * val engines = EvaluationEngines()
 * engines += ParametricInsuranceEngine()
 * ```
 */
fun EvaluationEngines(): EvaluationEngines = InMemoryEvaluationEngines()

/**
 * In-memory thread-safe implementation of EvaluationEngines.
 */
internal class InMemoryEvaluationEngines : EvaluationEngines {
    private val engines = ConcurrentHashMap<String, ContractEvaluationEngine>()
    private val engineHashes = ConcurrentHashMap<String, String>()
    
    override operator fun plusAssign(engine: ContractEvaluationEngine) {
        require(engine.engineId.isNotBlank()) {
            "Evaluation engine ID cannot be blank"
        }
        if (engines.containsKey(engine.engineId)) {
            throw IllegalArgumentException(
                "Evaluation engine '${engine.engineId}' is already registered"
            )
        }
        engines[engine.engineId] = engine
        engineHashes[engine.engineId] = engine.implementationHash
    }
    
    override operator fun minusAssign(engineId: String) {
        engines.remove(engineId)
        engineHashes.remove(engineId)
    }
    
    override operator fun get(engineId: String): ContractEvaluationEngine? = engines[engineId]
    
    override operator fun contains(engineId: String): Boolean = engines.containsKey(engineId)
    
    override fun remove(engineId: String): Boolean {
        val wasPresent = engines.containsKey(engineId)
        if (wasPresent) {
            engines.remove(engineId)
            engineHashes.remove(engineId)
        }
        return wasPresent
    }
    
    override fun verify(engineId: String, expectedHash: String): Boolean {
        require(expectedHash.isNotBlank()) { "Expected hash cannot be blank" }
        val actualHash = engineHashes[engineId] ?: return false
        return actualHash == expectedHash
    }
    
    override val keys: Set<String> get() = engines.keys
    
    override val values: Collection<ContractEvaluationEngine> get() = engines.values
    
    override val size: Int get() = engines.size
    
    override val isEmpty: Boolean get() = engines.isEmpty()
    
    override fun iterator(): Iterator<ContractEvaluationEngine> = engines.values.iterator()
    
    override fun clear() {
        engines.clear()
        engineHashes.clear()
    }
}

