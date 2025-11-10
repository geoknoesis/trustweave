package io.geoknoesis.vericore.spi.services

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


