package com.trustweave.did

/**
 * Default in-memory implementation of [DidMethodRegistry].
 */
class DefaultDidMethodRegistry : DidMethodRegistry {
    private val methods = mutableMapOf<String, DidMethod>()

    @Synchronized
    override fun register(method: DidMethod) {
        methods[method.method] = method
    }

    @Synchronized
    override fun registerAll(vararg methods: DidMethod) {
        methods.forEach { register(it) }
    }

    @Synchronized
    override fun get(methodName: String): DidMethod? = methods[methodName]

    @Synchronized
    override fun has(methodName: String): Boolean = methods.containsKey(methodName)

    @Synchronized
    override fun getAllMethodNames(): List<String> = methods.keys.toList()

    @Synchronized
    override fun getAllMethods(): Map<String, DidMethod> = methods.toMap()

    override suspend fun resolve(did: String): DidResolutionResult {
        val parsed = Did.parse(did)
        val method = get(parsed.method)
            ?: throw IllegalArgumentException(
                "DID method '${parsed.method}' is not registered. " +
                    "Available methods: ${getAllMethodNames()}"
            )
        return method.resolveDid(did)
    }

    @Synchronized
    override fun unregister(methodName: String): Boolean = methods.remove(methodName) != null

    @Synchronized
    override fun clear() {
        methods.clear()
    }

    @Synchronized
    override fun size(): Int = methods.size

    @Synchronized
    override fun snapshot(): DidMethodRegistry {
        val copy = DefaultDidMethodRegistry()
        methods.values.forEach { copy.register(it) }
        return copy
    }

    companion object {
        fun empty(): DidMethodRegistry = DefaultDidMethodRegistry()
    }
}


