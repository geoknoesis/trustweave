package com.trustweave.trust.dsl.did

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidCreationOptionsBuilder
import com.trustweave.did.KeyAlgorithm
import com.trustweave.did.DidMethod
import com.trustweave.did.exception.DidException
import com.trustweave.did.identifiers.Did
import com.trustweave.trust.dsl.TrustWeaveContext
import com.trustweave.trust.types.DidCreationResult
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
        val keyAlgorithm = KeyAlgorithm.fromName(name)
            ?: throw IllegalArgumentException("Unsupported key algorithm: $name")
        optionsBuilder.algorithm = keyAlgorithm
    }

    /**
     * Set key algorithm using type-safe enum.
     * 
     * This is the preferred method for compile-time type safety.
     */
    fun algorithm(value: KeyAlgorithm) {
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
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun build(): DidCreationResult = withContext(ioDispatcher) {
        // Use explicit method, or config's default, or first registered method
        val methodName = method 
            ?: (provider as? TrustWeaveContext)?.getConfig()?.defaultDidMethod
            ?: return@withContext DidCreationResult.Failure.InvalidConfiguration(
                reason = "DID method is required. Use method(\"key\") or configure a default in did { method(\"key\") { ... } }"
            )

        val didMethod = provider.getDidMethod(methodName) as? DidMethod
            ?: run {
                // Try to get available methods from registry if provider is TrustWeaveContext
                val availableMethods = try {
                    (provider as? TrustWeaveContext)
                        ?.getConfig()
                        ?.registries
                        ?.didRegistry
                        ?.getAllMethodNames()
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                return@withContext DidCreationResult.Failure.MethodNotRegistered(
                    method = methodName,
                    availableMethods = availableMethods
                )
            }

        try {
            val document = didMethod.createDid(optionsBuilder.build())
            DidCreationResult.Success(
                did = Did(document.id.value),
                document = document
            )
        } catch (e: TrustWeaveException) {
            when (e) {
                is DidException.DidMethodNotRegistered -> {
                    DidCreationResult.Failure.MethodNotRegistered(
                        method = methodName,
                        availableMethods = e.availableMethods
                    )
                }
                else -> {
                    DidCreationResult.Failure.Other(
                        reason = e.message ?: "DID creation failed",
                        cause = e
                    )
                }
            }
        } catch (e: IllegalArgumentException) {
            DidCreationResult.Failure.InvalidConfiguration(
                reason = e.message ?: "Invalid configuration",
                details = emptyMap()
            )
        } catch (e: Exception) {
            DidCreationResult.Failure.Other(
                reason = e.message ?: "Unknown error during DID creation",
                cause = e
            )
        }
    }
}

/**
 * Extension function to create a DID using a DID DSL provider.
 *
 * Returns a sealed result type for exhaustive error handling.
 * 
 * Uses the default dispatcher ([Dispatchers.IO]) unless the provider
 * is a [TrustWeaveContext] with a custom dispatcher configured.
 */
suspend fun DidDslProvider.createDid(block: DidBuilder.() -> Unit): DidCreationResult {
    val dispatcher = (this as? TrustWeaveContext)?.getConfig()?.ioDispatcher
        ?: Dispatchers.IO
    val builder = DidBuilder(this, dispatcher)
    builder.block()
    return builder.build()
}

