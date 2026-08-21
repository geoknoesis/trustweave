package org.trustweave.did.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod

/**
 * Service Provider Interface for DidMethod implementations.
 * Implementations of this interface will be discovered via Java ServiceLoader.
 */
interface DidMethodProvider {
    /**
     * Creates a DidMethod instance for the specified method name.
     *
     * @param methodName The DID method name (e.g., "key", "web")
     * @param options Configuration options for the method
     * @return A DidMethod instance, or null if this provider doesn't support the method
     */
    fun create(
        methodName: String,
        options: DidCreationOptions = DidCreationOptions(),
    ): DidMethod?

    /**
     * The name/identifier of this provider (e.g., "waltid", "mock").
     */
    val name: String

    /**
     * List of DID method names supported by this provider.
     */
    val supportedMethods: List<String>

    /**
     * Preference when more than one provider claims the same DID method; higher wins.
     *
     * ServiceLoader returns providers in classpath order, so without an explicit preference the
     * winner moved with jar ordering. Production plugins leave this at the default; a provider
     * that exists only to stand in during tests should declare a negative value so it never
     * displaces a real implementation by accident.
     */
    val priority: Int get() = 0

    /**
     * Returns the list of environment variables required for this DID method provider.
     *
     * **Example:**
     * ```kotlin
     * override val requiredEnvironmentVariables: List<String> = listOf(
     *     "ETHEREUM_RPC_URL",
     *     "?ETHEREUM_PRIVATE_KEY"  // Optional if only resolving
     * )
     * ```
     *
     * **Note:** Optional env vars should be prefixed with "?" (e.g., "?ETHEREUM_PRIVATE_KEY")
     *
     * @return List of required environment variable names (empty by default)
     */
    val requiredEnvironmentVariables: List<String>
        get() = emptyList()

    /**
     * Checks if all required environment variables are available for this provider.
     *
     * @return true if all required env vars are set, false otherwise
     */
    fun hasRequiredEnvironmentVariables(): Boolean =
        requiredEnvironmentVariables.all { envVar ->
            val isOptional = envVar.startsWith("?")
            val actualVar = if (isOptional) envVar.substring(1) else envVar
            if (isOptional) true else System.getenv(actualVar) != null
        }
}
