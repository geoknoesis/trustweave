package io.geoknoesis.vericore.spi.services

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
    suspend fun createInMemory(): Pair<Any, (suspend (ByteArray, String) -> ByteArray)?> // Pair<KeyManagementService, SignerFunction?>

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
    ): Pair<Any, (suspend (ByteArray, String) -> ByteArray)?> // Pair<KeyManagementService, SignerFunction?>
}


