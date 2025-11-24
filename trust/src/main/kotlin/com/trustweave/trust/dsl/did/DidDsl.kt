package com.trustweave.trust.dsl.did

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidCreationOptionsBuilder
import com.trustweave.did.DidMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DID Builder DSL.
 * 
 * Provides a fluent API for creating DIDs using a DID DSL provider.
 * 
 * **Example Usage**:
 * ```kotlin
 * val professionalDid = didProvider.createDid {
 *     method("key")
 *     algorithm("Ed25519")
 * }
 * ```
 */
class DidBuilder(
    private val provider: DidDslProvider
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
        
        val didMethod = provider.getDidMethod(methodName) as? DidMethod
            ?: throw IllegalStateException(
                "DID method '$methodName' is not configured. " +
                "Configure it in trustLayer { did { method(\"$methodName\") { ... } } }"
            )
        
        val document = didMethod.createDid(optionsBuilder.build())
        return@withContext document.id
    }
}

/**
 * Extension function to create a DID using a DID DSL provider.
 */
suspend fun DidDslProvider.createDid(block: DidBuilder.() -> Unit): String {
    val builder = DidBuilder(this)
    builder.block()
    return builder.build()
}

