package com.trustweave.kms.ibm

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for IBM Key Protect KeyManagementService.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 *
 * **Example:**
 * ```kotlin
 * val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
 * val ibmProvider = providers.find { it.name == "ibm" }
 * val kms = ibmProvider?.create(mapOf(
 *     "apiKey" to "xxx",
 *     "instanceId" to "xxx",
 *     "region" to "us-south"
 * ))
 * ```
 */
class IbmKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "ibm"

    override val supportedAlgorithms: Set<Algorithm> = IbmKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = try {
            IbmKmsConfig.fromMap(options)
        } catch (e: Exception) {
            // Try environment variables as fallback
            IbmKmsConfig.fromEnvironment()
                ?: throw IllegalArgumentException(
                    "IBM Key Protect configuration requires 'apiKey' and 'instanceId' in options or environment variables. " +
                    "Error: ${e.message}",
                    e
                )
        }

        return IbmKeyManagementService(config)
    }
}

