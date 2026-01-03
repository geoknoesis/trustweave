package org.trustweave.did.registry

import org.trustweave.did.identifiers.Did
import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.exception.DidException
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import java.util.concurrent.ConcurrentHashMap
import java.util.ServiceLoader

/**
 * In-memory registry for managing DID method implementations.
 *
 * Methods can be registered manually or automatically discovered via SPI (Service Provider Interface).
 * When auto-registration is enabled, all DidMethodProvider implementations on the classpath
 * are scanned and their methods are registered.
 *
 * **Thread Safety:**
 * All operations are thread-safe using ConcurrentHashMap for concurrent access
 * without explicit synchronization. All operations are atomic and safe for concurrent use.
 *
 * **Performance:**
 * - O(1) lookup time for method retrieval
 * - O(n) for getAllMethodNames() and getAllMethods() where n is the number of methods
 *
 * **Example Usage:**
 * ```kotlin
 * // Manual registration
 * val registry = DidMethodRegistry()
 * registry.register(KeyDidMethod(kms))
 * 
 * // Auto-register from SPI providers
 * val registry = DidMethodRegistry.autoRegister(kms)
 * 
 * // Use operators
 * val method = registry["key"]
 * if ("key" in registry) {
 *     // Method is registered
 * }
 * val result = registry.resolve("did:key:z6Mk...")
 * ```
 */
class DidMethodRegistry {
    private val methods = ConcurrentHashMap<String, DidMethod>()

    /**
     * Registers a DID method.
     */
    fun register(method: DidMethod) {
        methods[method.method] = method
    }

    /**
     * Registers multiple DID methods.
     */
    fun registerAll(vararg methods: DidMethod) {
        methods.forEach { register(it) }
    }

    /**
     * Gets a DID method by name.
     */
    operator fun get(methodName: String): DidMethod? = methods[methodName]

    /**
     * Registers a method using bracket notation.
     *
     * **Example:**
     * ```kotlin
     * registry["key"] = KeyDidMethod(kms)
     * ```
     */
    operator fun set(methodName: String, method: DidMethod) {
        require(method.method == methodName) {
            "Method name mismatch: expected '$methodName', got '${method.method}'"
        }
        register(method)
    }

    /**
     * Checks if a method is registered using the `in` operator.
     *
     * **Example:**
     * ```kotlin
     * if ("key" in registry) {
     *     // Method is registered
     * }
     * ```
     */
    operator fun contains(methodName: String): Boolean = methods.containsKey(methodName)

    /**
     * Resolves a DID using a registered method.
     *
     * @param did The DID string to resolve
     * @return DidResolutionResult
     * @throws DidException.DidMethodNotRegistered if the method is not registered
     */
    suspend fun resolve(did: String): DidResolutionResult {
        val parsed = Did(did)
        val methodName = parsed.method
        val method = methods[methodName]
        return method?.resolveDid(parsed) ?: throw DidException.DidMethodNotRegistered(
            method = methodName,
            availableMethods = methods.keys.toList()
        )
    }

    /**
     * Unregisters a DID method.
     *
     * @return true if the method was registered and removed, false otherwise
     */
    fun unregister(methodName: String): Boolean = methods.remove(methodName) != null

    /**
     * Returns the number of registered methods.
     */
    fun size(): Int = methods.size

    /**
     * Gets all registered method names.
     */
    fun getAllMethodNames(): List<String> = methods.keys.toList()

    /**
     * Gets all registered methods as a map.
     */
    fun getAllMethods(): Map<String, DidMethod> = HashMap(methods)

    /**
     * Checks if a method is registered.
     * 
     * Consider using the `in` operator instead: `"key" in registry`
     */
    fun has(methodName: String): Boolean = methods.containsKey(methodName)

    // Internal/test utility
    /**
     * Clears all registered methods (for testing).
     */
    fun clear() {
        methods.clear()
    }

    companion object {
        /**
         * Creates a registry and auto-registers all methods discovered via SPI.
         *
         * Scans all DidMethodProvider implementations on the classpath and registers
         * their supported methods. Methods are created with default options and a
         * default KMS (if provided).
         *
         * **Note:** Only methods with all required environment variables available
         * will be registered. Methods that require environment variables but don't
         * have them available will be skipped.
         *
         * @param kms Optional KMS to use for methods that require it. If null, methods
         *   will attempt to discover KMS via SPI or fail if required.
         * @param options Optional creation options to pass to providers
         * @return A registry with all discovered methods registered
         *
         * **Example:**
         * ```kotlin
         * val kms = InMemoryKeyManagementService()
         * val registry = DidMethodRegistry.autoRegister(kms)
         * // All SPI-discovered methods are now registered
         * ```
         */
        suspend fun autoRegister(
            kms: KeyManagementService? = null,
            options: DidCreationOptions = DidCreationOptions()
        ): DidMethodRegistry {
            val registry = DidMethodRegistry()

            try {
                val providers = ServiceLoader.load(DidMethodProvider::class.java)
                val creationOptions = if (kms != null) {
                    options.copy(
                        additionalProperties = options.additionalProperties + ("kms" to kms)
                    )
                } else {
                    options
                }

                // Collect all unique method names from all providers
                val methodsToRegister = mutableSetOf<String>()
                providers.forEach { provider ->
                    methodsToRegister.addAll(provider.supportedMethods)
                }

                // Create and register each method
                for (methodName in methodsToRegister) {
                    // Find first provider that supports this method
                    val provider = providers.find { methodName in it.supportedMethods }
                    if (provider != null) {
                        // Check if environment variables are available (optional)
                        if (provider.hasRequiredEnvironmentVariables()) {
                            val method = provider.create(methodName, creationOptions)
                            if (method != null) {
                                registry.register(method)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // SPI classes not available or error during discovery
                // Continue with empty registry - user can register manually
            }

            return registry
        }

        /**
         * Creates a registry with the given methods.
         *
         * **Example:**
         * ```kotlin
         * val registry = DidMethodRegistry.of(
         *     KeyDidMethod(kms),
         *     WebDidMethod()
         * )
         * ```
         */
        fun of(vararg methods: DidMethod): DidMethodRegistry {
            val registry = DidMethodRegistry()
            methods.forEach { registry.register(it) }
            return registry
        }
    }
}
