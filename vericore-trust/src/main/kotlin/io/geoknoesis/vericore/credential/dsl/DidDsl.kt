package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.did.DidCreationOptions
import io.geoknoesis.vericore.did.DidCreationOptionsBuilder
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.spi.services.DidMethodService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val optionsBuilder = DidCreationOptionsBuilder()
    
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
        val keyAlgorithm = DidCreationOptions.KeyAlgorithm.fromName(name)
            ?: throw IllegalArgumentException("Unsupported key algorithm: $name")
        optionsBuilder.algorithm = keyAlgorithm
    }
    
    /**
     * Add custom option for DID creation.
     */
    fun option(key: String, value: Any?) {
        optionsBuilder.property(key, value)
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
        
        val didMethod = context.getDidMethod(methodName) as? DidMethod
            ?: throw IllegalStateException(
                "DID method '$methodName' is not configured in trust layer. " +
                "Configure it in trustLayer { did { method(\"$methodName\") { ... } } }"
            )
        
        val document = didMethod.createDid(optionsBuilder.build())
        return@withContext document.id
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

