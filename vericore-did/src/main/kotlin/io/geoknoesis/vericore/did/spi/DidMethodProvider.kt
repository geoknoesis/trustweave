package io.geoknoesis.vericore.did.spi

import io.geoknoesis.vericore.did.DidMethod

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
    fun create(methodName: String, options: Map<String, Any?> = emptyMap()): DidMethod?

    /**
     * The name/identifier of this provider (e.g., "waltid", "mock").
     */
    val name: String

    /**
     * List of DID method names supported by this provider.
     */
    val supportedMethods: List<String>
}

