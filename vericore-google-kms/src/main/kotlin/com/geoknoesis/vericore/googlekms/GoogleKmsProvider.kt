package com.geoknoesis.vericore.googlekms

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

/**
 * Service Provider Interface implementation for Google Cloud KMS.
 * 
 * Enables auto-discovery of Google Cloud KMS provider via Java ServiceLoader.
 * 
 * **Example:**
 * ```kotlin
 * val kms = KeyManagementServiceProvider.create("google-cloud-kms", mapOf(
 *     "projectId" to "my-project",
 *     "location" to "us-east1",
 *     "keyRing" to "my-key-ring"
 * ))
 * ```
 */
class GoogleKmsProvider : KeyManagementServiceProvider {
    override val name: String = "google-cloud-kms"
    
    override val supportedAlgorithms: Set<Algorithm> = GoogleCloudKeyManagementService.SUPPORTED_ALGORITHMS
    
    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = GoogleKmsConfig.fromMap(options)
        return GoogleCloudKeyManagementService(config)
    }
}

