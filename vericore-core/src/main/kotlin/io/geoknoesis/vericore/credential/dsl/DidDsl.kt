package io.geoknoesis.vericore.credential.dsl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

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
        
        // Create DID using reflection to avoid dependency on vericore-did
        try {
            val createDidMethod = didMethod.javaClass.getMethod(
                "createDid",
                Map::class.java,
                Continuation::class.java
            )
            
            suspendCoroutineUninterceptedOrReturn<String> { cont ->
                try {
                    val result = createDidMethod.invoke(didMethod, createOptions, cont)
                    if (result === COROUTINE_SUSPENDED) {
                        COROUTINE_SUSPENDED
                    } else {
                        // Extract DID string from DidDocument
                        val document = result as? Any
                        if (document != null) {
                            try {
                                val getIdMethod = document.javaClass.getMethod("getId")
                                val didString = getIdMethod.invoke(document) as? String
                                if (didString != null) {
                                    cont.resumeWith(Result.success(didString))
                                    COROUTINE_SUSPENDED
                                } else {
                                    cont.resumeWith(Result.failure(
                                        IllegalStateException("DID document has no ID")
                                    ))
                                    COROUTINE_SUSPENDED
                                }
                            } catch (e: Exception) {
                                // Try field access
                                try {
                                    val idField = document.javaClass.getDeclaredField("id")
                                    idField.isAccessible = true
                                    val didString = idField.get(document) as? String
                                    if (didString != null) {
                                        cont.resumeWith(Result.success(didString))
                                        COROUTINE_SUSPENDED
                                    } else {
                                        cont.resumeWith(Result.failure(
                                            IllegalStateException("DID document has no ID")
                                        ))
                                        COROUTINE_SUSPENDED
                                    }
                                } catch (e2: Exception) {
                                    cont.resumeWith(Result.failure(
                                        IllegalStateException(
                                            "Failed to extract DID from document: ${e2.message}"
                                        )
                                    ))
                                    COROUTINE_SUSPENDED
                                }
                            }
                        } else {
                            cont.resumeWith(Result.failure(
                                IllegalStateException("createDid returned null")
                            ))
                            COROUTINE_SUSPENDED
                        }
                    }
                } catch (e: Exception) {
                    cont.resumeWith(Result.failure(
                        IllegalStateException(
                            "Failed to create DID with method '$methodName': ${e.message}",
                            e
                        )
                    ))
                    COROUTINE_SUSPENDED
                }
            } ?: throw IllegalStateException("DID creation was suspended")
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException(
                "DID method '$methodName' does not implement createDid method. " +
                "Ensure it implements DidMethod interface.",
                e
            )
        }
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

