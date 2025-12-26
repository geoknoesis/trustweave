package org.trustweave.did.registry

import org.trustweave.did.identifiers.Did
import org.trustweave.did.DidMethod
import org.trustweave.did.exception.DidException
import org.trustweave.did.resolver.DidResolutionResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Default in-memory implementation of [DidMethodRegistry].
 *
 * This implementation is thread-safe using [ConcurrentHashMap] for concurrent access
 * without explicit synchronization. All operations are atomic and safe for concurrent use.
 *
 * **Thread Safety:**
 * - Registration and unregistration are thread-safe
 * - Concurrent reads and writes are supported
 * - Snapshot operations create a consistent view
 *
 * **Performance:**
 * - O(1) lookup time for method retrieval
 * - O(n) for getAllMethodNames() and getAllMethods() where n is the number of methods
 * - Snapshot creates a shallow copy for isolation
 *
 * **Example Usage:**
 * ```kotlin
 * val registry = DefaultDidMethodRegistry()
 * registry.register(KeyDidMethod(kms))
 * registry.register(WebDidMethod())
 *
 * val method = registry.get("key")
 * val allMethods = registry.getAllMethodNames()
 *
 * // Resolve a DID
 * val result = registry.resolve("did:key:z6Mk...")
 * ```
 *
 * @see DidMethodRegistry for the interface contract
 */
class DefaultDidMethodRegistry : DidMethodRegistry {
    private val methods = ConcurrentHashMap<String, DidMethod>()

    override fun register(method: DidMethod) {
        methods[method.method] = method
    }

    override fun registerAll(vararg methods: DidMethod) {
        methods.forEach { register(it) }
    }

    override fun get(methodName: String): DidMethod? = methods[methodName]

    override fun has(methodName: String): Boolean = methods.containsKey(methodName)

    override fun getAllMethodNames(): List<String> = methods.keys.toList()

    override fun getAllMethods(): Map<String, DidMethod> = HashMap(methods)

    override suspend fun resolve(did: String): DidResolutionResult {
        val parsed = Did(did)
        val methodName = parsed.method
        val method = methods[methodName]  // Use map directly to avoid operator overload confusion
        return method?.resolveDid(parsed) ?: throw DidException.DidMethodNotRegistered(
            method = methodName,
            availableMethods = getAllMethodNames()
        )
    }

    override fun unregister(methodName: String): Boolean = methods.remove(methodName) != null

    override fun clear() {
        methods.clear()
    }

    override fun size(): Int = methods.size

    override fun snapshot(): DidMethodRegistry {
        val copy = DefaultDidMethodRegistry()
        // ConcurrentHashMap.values iteration is thread-safe
        methods.values.forEach { copy.register(it) }
        return copy
    }


    /**
     * Operator overload for checking method existence using `in` operator.
     *
     * **Example:**
     * ```kotlin
     * if ("key" in registry) {
     *     // Method is registered
     * }
     * ```
     */
    operator fun contains(methodName: String): Boolean = has(methodName)

    /**
     * Operator overload for registering methods using bracket notation.
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

    companion object {
        /**
         * Creates an empty registry.
         */
        fun empty(): DidMethodRegistry = DefaultDidMethodRegistry()

        /**
         * Creates a registry with the given methods.
         *
         * **Example:**
         * ```kotlin
         * val registry = DefaultDidMethodRegistry.of(
         *     KeyDidMethod(kms),
         *     WebDidMethod()
         * )
         * ```
         */
        fun of(vararg methods: DidMethod): DefaultDidMethodRegistry {
            val registry = DefaultDidMethodRegistry()
            methods.forEach { registry.register(it) }
            return registry
        }
    }
}

