package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.spi.services.DidMethodService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * DID Builder DSL.
 * 
 * Provides a fluent API for creating DIDs using trust layer configuration.
 * Automatically uses configured DID methods and KMS from the trust layer.
 * 
 * **Example Usage**:
 * ```kotlin
 * val professionalDid = trustLayer.createDid {
 *     method("key")
 *     algorithm("Ed25519")
 * }
 * ```
 */
class DidBuilder(
    private val context: TrustLayerContext
) {
    private var method: String? = null
    private var algorithm: String? = null
    private val options = mutableMapOf<String, Any?>()
    
    /**
     * Set DID method (e.g., "key", "web", "ion").
     */
    fun method(name: String) {
        this.method = name
    }
    
    /**
     * Set key algorithm (e.g., "Ed25519", "secp256k1").
     */
    fun algorithm(name: String) {
        this.algorithm = name
        options["algorithm"] = name
    }
    
    /**
     * Add custom option for DID creation.
     */
    fun option(key: String, value: Any?) {
        options[key] = value
    }
    
    /**
     * Build and create the DID.
     * 
     * @return DID string (e.g., "did:key:z6Mk...")
     */
    suspend fun build(): String = withContext(Dispatchers.IO) {
        val methodName = method ?: throw IllegalStateException(
            "DID method is required. Use method(\"key\") or method(\"web\") etc."
        )
        
        // Get DID method from trust layer
        val didMethod = context.getDidMethod(methodName)
            ?: throw IllegalStateException(
                "DID method '$methodName' is not configured in trust layer. " +
                "Configure it in trustLayer { did { method(\"$methodName\") { ... } } }"
            )
        
        // Prepare options
        val createOptions = mutableMapOf<String, Any?>()
        createOptions.putAll(options)
        if (algorithm != null && !createOptions.containsKey("algorithm")) {
            createOptions["algorithm"] = algorithm
        }
        
        // Invoke the DID method directly via reflection
        val document = invokeSuspendFunction(didMethod, "createDid", createOptions)
            ?: throw IllegalStateException("createDid on '$methodName' returned null")

        val didId = extractDidId(document)
            ?: throw IllegalStateException("createDid on '$methodName' returned document without id")

        return@withContext didId
    }
}

private suspend fun invokeSuspendFunction(target: Any, methodName: String, vararg args: Any?): Any? {
    val function = target::class.memberFunctions.firstOrNull { fn ->
        fn.name == methodName && fn.parameters.size == args.size + 1
    } ?: throw NoSuchMethodException("Method $methodName with ${args.size} arguments not found on ${target::class.qualifiedName}")

    function.isAccessible = true
    return function.callSuspend(target, *args)
}

private fun extractDidId(document: Any): String? {
    return try {
        val method = document::class.java.methods.firstOrNull { it.name == "getId" && it.parameterCount == 0 }
        method?.let {
            it.isAccessible = true
            it.invoke(document) as? String
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Extension function to create a DID using trust layer configuration.
 */
suspend fun TrustLayerContext.createDid(block: DidBuilder.() -> Unit): String {
    val builder = DidBuilder(this)
    builder.block()
    return builder.build()
}

/**
 * Extension function for direct DID creation on trust layer config.
 */
suspend fun TrustLayerConfig.createDid(block: DidBuilder.() -> Unit): String {
    return dsl().createDid(block)
}

