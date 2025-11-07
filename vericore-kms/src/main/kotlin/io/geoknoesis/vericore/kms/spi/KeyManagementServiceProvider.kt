package io.geoknoesis.vericore.kms.spi

import io.geoknoesis.vericore.kms.KeyManagementService

/**
 * Service Provider Interface for KeyManagementService implementations.
 * Implementations of this interface will be discovered via Java ServiceLoader.
 */
interface KeyManagementServiceProvider {
    /**
     * Creates a KeyManagementService instance.
     *
     * @param options Configuration options for the service
     * @return A KeyManagementService instance
     */
    fun create(options: Map<String, Any?> = emptyMap()): KeyManagementService

    /**
     * The name/identifier of this provider (e.g., "waltid", "inmemory").
     */
    val name: String
}

