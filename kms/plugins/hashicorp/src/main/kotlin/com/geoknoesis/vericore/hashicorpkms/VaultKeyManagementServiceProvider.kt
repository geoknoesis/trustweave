package com.geoknoesis.vericore.hashicorpkms

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for HashiCorp Vault KeyManagementService.
 * 
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 * 
 * **Example:**
 * ```kotlin
 * val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
 * val vaultProvider = providers.find { it.name == "vault" }
 * val kms = vaultProvider?.create(mapOf(
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

