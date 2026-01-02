package org.trustweave.kms.services

/**
 * Internal factory interface for creating KMS instances.
 * 
 * This internal interface provides a factory abstraction for creating KMS
 * instances from configuration. It's used internally by the SPI implementation
 * to create service instances from providers.
 * 
 * **Internal Use Only:**
 * This is an internal factory interface and should not be implemented or used
 * directly by KMS plugins or consumers. Use [KeyManagementServices] factory
 * or implement [KeyManagementServiceProvider] SPI instead.
 */

import org.trustweave.kms.KeyManagementService

/**
 * Factory interface for creating Key Management Service instances.
 *
 * Eliminates the need for reflection when instantiating KMS implementations.
 */
interface KmsFactory {
    /**
     * Creates an in-memory KMS instance (for testing).
     *
     * @return Pair of (KMS instance, signer function), where signer function may be null
     *         if the KMS provides its own signing mechanism
     */
    suspend fun createInMemory(): Pair<KeyManagementService, (suspend (ByteArray, String) -> ByteArray)?>

    /**
     * Creates a KMS instance from a provider name.
     *
     * @param providerName The provider name (e.g., "waltid", "inMemory")
     * @param algorithm The algorithm to use (e.g., "Ed25519")
     * @return Pair of (KMS instance, signer function), where signer function may be null
     *         if the KMS provides its own signing mechanism or if provider doesn't support direct signing
     * @throws IllegalStateException if the provider is not found or cannot be instantiated
     */
    suspend fun createFromProvider(
        providerName: String,
        algorithm: String
    ): Pair<KeyManagementService, (suspend (ByteArray, String) -> ByteArray)?>
}

