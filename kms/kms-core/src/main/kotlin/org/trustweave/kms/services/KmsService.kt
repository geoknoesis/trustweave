package org.trustweave.kms.services

/**
 * Internal service abstraction for KMS creation.
 * 
 * This internal interface provides an abstraction layer for KMS instance creation
 * that is used by the [KeyManagementServices] factory. It allows for different
 * creation strategies and is used internally for service provider integration.
 * 
 * **Internal Use Only:**
 * This is an internal service interface and should not be implemented or used
 * directly by KMS plugins or consumers. Use [KeyManagementServices] factory
 * or implement [KeyManagementServiceProvider] SPI instead.
 */

/**
 * Service interface for Key Management Service operations.
 *
 * Provides a way to call KMS methods without direct dependency or reflection.
 */
interface KmsService {
    /**
     * Generates a new cryptographic key.
     *
     * @param kms The KMS instance (as Any to avoid dependency)
     * @param algorithm The algorithm to use (e.g., "Ed25519", "secp256k1")
     * @param options Additional options for key generation
     * @return A KeyHandle (as Any to avoid dependency)
     */
    suspend fun generateKey(
        kms: Any, // KeyManagementService - using Any to avoid dependency
        algorithm: String,
        options: Map<String, Any?> = emptyMap()
    ): Any // KeyHandle - using Any to avoid dependency

    /**
     * Gets the ID from a KeyHandle.
     *
     * @param keyHandle The key handle (as Any to avoid dependency)
     * @return The key ID
     */
    fun getKeyId(keyHandle: Any): String

    /**
     * Gets the public key JWK from a KeyHandle.
     *
     * @param keyHandle The key handle (as Any to avoid dependency)
     * @return The public key JWK map, or null if not available
     */
    fun getPublicKeyJwk(keyHandle: Any): Map<String, Any?>?
}

