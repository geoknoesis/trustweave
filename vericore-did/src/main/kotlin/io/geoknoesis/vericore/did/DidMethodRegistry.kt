package io.geoknoesis.vericore.did

/**
 * Instance-based registry for DID method implementations.
 * 
 * This class provides a thread-safe, testable alternative to the global
 * `DidRegistry` singleton. Multiple registries can coexist, enabling
 * isolation between different contexts.
 * 
 * **Example Usage:**
 * ```kotlin
 * // Create a registry
 * val registry = DidMethodRegistry()
 * 
 * // Register methods
 * registry.register(DidKeyMethod(kms))
 * registry.register(DidWebMethod(kms))
 * 
 * // Resolve DIDs
 * val result = registry.resolve("did:key:...")
 * ```
 * 
 * **Benefits over global singleton:**
 * - Thread-safe without global state
 * - Testable in isolation
 * - Multiple contexts can coexist
 * - No hidden dependencies
 * 
 * @see DidRegistry for backward-compatible global registry
 */
class DidMethodRegistry {
    private val methods = mutableMapOf<String, DidMethod>()
    
    /**
     * Registers a DID method implementation.
     * 
     * If a method with the same name is already registered, it will be replaced.
     * 
     * **Example:**
     * ```kotlin
     * val registry = DidMethodRegistry()
     * registry.register(DidKeyMethod(kms))
     * ```
     * 
     * @param method The DID method to register
     */
    @Synchronized
    fun register(method: DidMethod) {
        methods[method.method] = method
    }
    
    /**
     * Registers multiple DID methods at once.
     * 
     * **Example:**
     * ```kotlin
     * registry.registerAll(
     *     DidKeyMethod(kms),
     *     DidWebMethod(kms),
     *     DidIonMethod()
     * )
     * ```
     * 
     * @param methods DID methods to register
     */
    @Synchronized
    fun registerAll(vararg methods: DidMethod) {
        methods.forEach { register(it) }
    }
    
    /**
     * Gets a DID method by name.
     * 
     * **Example:**
     * ```kotlin
     * val keyMethod = registry.get("key")
     * if (keyMethod != null) {
     *     val doc = keyMethod.createDid()
     * }
     * ```
     * 
     * @param methodName The name of the DID method (e.g., "key", "web", "ion")
     * @return The DidMethod implementation, or null if not found
     */
    @Synchronized
    fun get(methodName: String): DidMethod? {
        return methods[methodName]
    }
    
    /**
     * Checks if a DID method is registered.
     * 
     * @param methodName The name of the DID method
     * @return true if the method is registered, false otherwise
     */
    @Synchronized
    fun has(methodName: String): Boolean {
        return methods.containsKey(methodName)
    }
    
    /**
     * Gets all registered DID method names.
     * 
     * **Example:**
     * ```kotlin
     * val availableMethods = registry.getAllMethodNames()
     * println("Available DID methods: $availableMethods")
     * ```
     * 
     * @return List of registered method names
     */
    @Synchronized
    fun getAllMethodNames(): List<String> {
        return methods.keys.toList()
    }
    
    /**
     * Gets all registered DID methods.
     * 
     * @return Map of method name to DidMethod instance
     */
    @Synchronized
    fun getAllMethods(): Map<String, DidMethod> {
        return methods.toMap()
    }
    
    /**
     * Resolves a DID using the appropriate registered method.
     * 
     * The DID string is parsed to extract the method name,
     * which is then used to find the appropriate DID method implementation.
     * 
     * **Example:**
     * ```kotlin
     * val result = registry.resolve("did:key:z6Mkfriq...")
     * if (result.document != null) {
     *     println("Resolved: ${result.document.id}")
     * }
     * ```
     * 
     * @param did The DID string to resolve
     * @return A DidResolutionResult containing the document and metadata
     * @throws IllegalArgumentException if the DID method is not registered
     */
    suspend fun resolve(did: String): DidResolutionResult {
        val parsed = Did.parse(did)
        val method = get(parsed.method)
            ?: throw IllegalArgumentException(
                "DID method '${parsed.method}' is not registered. " +
                "Available methods: ${getAllMethodNames()}"
            )
        return method.resolveDid(did)
    }
    
    /**
     * Unregisters a DID method.
     * 
     * @param methodName The name of the method to unregister
     * @return true if the method was removed, false if it wasn't registered
     */
    @Synchronized
    fun unregister(methodName: String): Boolean {
        return methods.remove(methodName) != null
    }
    
    /**
     * Clears all registered methods.
     * 
     * Useful for testing or resetting the registry state.
     */
    @Synchronized
    fun clear() {
        methods.clear()
    }
    
    /**
     * Gets the number of registered DID methods.
     * 
     * @return Count of registered methods
     */
    @Synchronized
    fun size(): Int = methods.size

    /**
     * Creates a shallow copy of this registry.
     */
    @Synchronized
    fun snapshot(): DidMethodRegistry {
        val copy = DidMethodRegistry()
        methods.values.forEach { copy.register(it) }
        return copy
    }
    
    companion object {
        /**
         * Creates an empty registry.
         * 
         * DID methods must be registered manually using register().
         * 
         * **Example:**
         * ```kotlin
         * val registry = DidMethodRegistry.create()
         * registry.register(myDidMethod)
         * ```
         * 
         * @return Empty registry
         */
        fun create(): DidMethodRegistry {
            return DidMethodRegistry()
        }
    }
}

