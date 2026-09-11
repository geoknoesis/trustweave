package org.trustweave.hashicorpkms

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for HashiCorp Vault KeyManagementService.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 *
 * **Example:**
 * ```kotlin
 * import org.trustweave.kms.*
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
        val algorithm = options["algorithm"]
        require(algorithm == null || (algorithm is String && supportsAlgorithm(algorithm))) {
            "Unsupported Vault signing algorithm"
        }
        // The SDK supplies algorithm as a selection hint, not a Vault connection setting.
        val connectionOptions = options - "algorithm"
        val config = if (connectionOptions.isEmpty()) {
            VaultKmsConfig.fromEnvironment()
                ?: throw IllegalArgumentException(
                    "Vault configuration requires explicit options or VAULT_ADDR"
                )
        } else VaultKmsConfig.fromMap(connectionOptions)

        return VaultKeyManagementService(config)
    }
}

