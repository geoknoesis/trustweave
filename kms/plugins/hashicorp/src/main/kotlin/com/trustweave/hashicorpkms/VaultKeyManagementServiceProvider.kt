package com.trustweave.hashicorpkms

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for HashiCorp Vault KeyManagementService.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 *
 * **Example:**
 * ```kotlin
 * import com.trustweave.kms.*
 * 
 * val kms = KeyManagementServices.create("vault", mapOf(
 *     "address" to "http://localhost:8200",
 *     "token" to "hvs.xxx"
 * ))
 * ```
 */
class VaultKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "vault"

    override val supportedAlgorithms: Set<Algorithm> = VaultKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = try {
            VaultKmsConfig.fromMap(options)
        } catch (e: Exception) {
            // Try environment variables as fallback
            VaultKmsConfig.fromEnvironment()
                ?: throw IllegalArgumentException(
                    "Vault configuration requires 'address' in options or VAULT_ADDR environment variable. " +
                    "Error: ${e.message}",
                    e
                )
        }

        return VaultKeyManagementService(config)
    }
}

