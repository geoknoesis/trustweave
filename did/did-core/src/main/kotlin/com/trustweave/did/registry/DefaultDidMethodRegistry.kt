package com.trustweave.did.registry

import com.trustweave.did.identifiers.Did
import com.trustweave.did.DidMethod
import com.trustweave.did.exception.DidException
import com.trustweave.did.resolver.DidResolutionResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Default in-memory implementation of [DidMethodRegistry].
 *
 * This implementation is thread-safe using [ConcurrentHashMap] for concurrent access
 * without explicit synchronization.
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
        val method = get(parsed.method)
        if (method == null) {
            throw DidException.DidMethodNotRegistered(
                method = parsed.method,
                availableMethods = getAllMethodNames()
            )
        }
        return method.resolveDid(parsed)
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

    companion object {
        fun empty(): DidMethodRegistry = DefaultDidMethodRegistry()
    }
}

