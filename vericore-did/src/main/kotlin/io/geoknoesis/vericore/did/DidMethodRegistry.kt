package io.geoknoesis.vericore.did

/**
 * Contract for registries that manage [DidMethod] implementations.
 *
 * The default in-memory implementation is provided by
 * [DefaultDidMethodRegistry], but alternate implementations can be supplied
 * for testing or advanced scenarios.
 */
interface DidMethodRegistry {
    fun register(method: DidMethod)
    fun registerAll(vararg methods: DidMethod)
    fun get(methodName: String): DidMethod?
    fun has(methodName: String): Boolean
    fun getAllMethodNames(): List<String>
    fun getAllMethods(): Map<String, DidMethod>
    suspend fun resolve(did: String): DidResolutionResult
    fun unregister(methodName: String): Boolean
    fun clear()
    fun size(): Int
    fun snapshot(): DidMethodRegistry
}

fun DidMethodRegistry(): DidMethodRegistry = DefaultDidMethodRegistry()

