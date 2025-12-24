package org.trustweave.did.registry

import org.trustweave.did.DidMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.RegistryBasedResolver

/**
 * Contract for registries that manage [DidMethod] implementations.
 *
 * The default in-memory implementation is provided by
 * [DefaultDidMethodRegistry], but alternate implementations can be supplied
 * for testing or advanced scenarios.
 *
 * **Note:** The [resolve] method is a convenience method that combines
 * registry lookup with resolution. For better separation of concerns,
 * consider using [RegistryBasedResolver] which wraps a registry.
 */
interface DidMethodRegistry {
    fun register(method: DidMethod)
    fun registerAll(vararg methods: DidMethod)
    fun get(methodName: String): DidMethod?
    fun has(methodName: String): Boolean
    fun getAllMethodNames(): List<String>
    fun getAllMethods(): Map<String, DidMethod>

    /**
     * Convenience method to resolve a DID using a registered method.
     *
     * This method combines registry lookup with resolution. For better
     * separation of concerns, use [RegistryBasedResolver] instead.
     *
     * @param did The DID string to resolve
     * @return DidResolutionResult
     * @throws DidException.DidMethodNotRegistered if the method is not registered
     */
    suspend fun resolve(did: String): DidResolutionResult

    fun unregister(methodName: String): Boolean
    fun clear()
    fun size(): Int
    fun snapshot(): DidMethodRegistry
}

fun DidMethodRegistry(): DidMethodRegistry = DefaultDidMethodRegistry()

