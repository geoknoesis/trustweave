package com.trustweave.trust.dsl.did

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidCreationOptionsBuilder
import com.trustweave.did.DidMethod
import com.trustweave.trust.types.Did
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
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
    private val provider: DidDslProvider,
    /**
     * Coroutine dispatcher for I/O-bound operations.
     * Defaults to [Dispatchers.IO] if not provided.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var method: String? = null
    private val optionsBuilder = DidCreationOptionsBuilder()

    /**
     * Set DID method (e.g., "key", "web", "ion").
     * 
     * @param name Must be a non-blank string containing only alphanumeric characters and hyphens
     * @throws IllegalArgumentException if name is blank or contains invalid characters
     */
    fun method(name: String) {
        require(name.isNotBlank()) { "DID method name cannot be blank" }
        require(name.matches(Regex("^[a-z0-9-]+$"))) { 
            "DID method name must contain only lowercase letters, numbers, and hyphens. Got: $name" 
        }
        this.method = name
    }

    /**
     * Set key algorithm by string name (e.g., "Ed25519", "secp256k1").
     * 
     * For type safety, prefer using algorithm(value: DidCreationOptions.KeyAlgorithm).
     */
    fun algorithm(name: String) {
        val keyAlgorithm = DidCreationOptions.KeyAlgorithm.fromName(name)
            ?: throw IllegalArgumentException("Unsupported key algorithm: $name")
        optionsBuilder.algorithm = keyAlgorithm
    }

    /**
     * Set key algorithm using type-safe enum.
     * 
     * This is the preferred method for compile-time type safety.
     */
    fun algorithm(value: DidCreationOptions.KeyAlgorithm) {
        optionsBuilder.algorithm = value
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
     * This operation performs I/O-bound work (key generation, DID document creation)
     * and uses the configured dispatcher. It is non-blocking and can be cancelled.
     *
     * @return Type-safe Did (e.g., Did("did:key:z6Mk..."))
     */
    suspend fun build(): Did = withContext(ioDispatcher) {
        val methodName = method ?: throw IllegalStateException(
            "DID method is required. Use method(\"key\") or method(\"web\") etc."
        )

        val didMethod = provider.getDidMethod(methodName) as? DidMethod
            ?: throw IllegalStateException(
                "DID method '$methodName' is not configured. " +
                "Configure it in trustLayer { did { method(\"$methodName\") { ... } } }"
            )

        val document = didMethod.createDid(optionsBuilder.build())
        return@withContext Did(document.id)
    }
}

/**
 * Extension function to create a DID using a DID DSL provider.
 *
 * Returns a type-safe Did.
 * 
 * Uses the default dispatcher ([Dispatchers.IO]) unless the provider
 * is a [TrustWeaveContext] with a custom dispatcher configured.
 */
suspend fun DidDslProvider.createDid(block: DidBuilder.() -> Unit): Did {
    val dispatcher = (this as? com.trustweave.trust.dsl.TrustWeaveContext)?.getConfig()?.ioDispatcher
        ?: Dispatchers.IO
    val builder = DidBuilder(this, dispatcher)
    builder.block()
    return builder.build()
}

