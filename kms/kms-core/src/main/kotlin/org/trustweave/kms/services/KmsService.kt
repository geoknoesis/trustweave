package org.trustweave.kms.services

import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult

/**
 * Service interface for Key Management Service operations.
 *
 * Provides an abstraction layer around [KeyManagementService] for use by components
 * that need to generate and inspect keys without depending on the full KMS API.
 * Typically used by builders and DSL helpers in higher-level modules.
 *
 * **Internal Use:**
 * This is an internal service interface. Prefer using [KeyManagementService] directly
 * where possible.
 */
interface KmsService {
    /**
     * Generates a new cryptographic key.
     *
     * @param kms The KMS instance to use for key generation
     * @param algorithm The algorithm name (e.g., "Ed25519", "secp256k1")
     * @param options Additional provider-specific options for key generation
     * @return [GenerateKeyResult] — inspect for [GenerateKeyResult.Success] or failure subtypes
     */
    suspend fun generateKey(
        kms: KeyManagementService,
        algorithm: String,
        options: Map<String, Any?> = emptyMap()
    ): GenerateKeyResult

    /**
     * Extracts the key identifier from a [KeyHandle].
     *
     * @param keyHandle The key handle returned by a successful [generateKey] call
     * @return The key ID string
     */
    fun getKeyId(keyHandle: KeyHandle): String

    /**
     * Extracts the public key JWK from a [KeyHandle].
     *
     * @param keyHandle The key handle returned by a successful [generateKey] call
     * @return The public key as a JWK map, or null if not available
     */
    fun getPublicKeyJwk(keyHandle: KeyHandle): Map<String, Any?>?
}
