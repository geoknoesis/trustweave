package com.trustweave.googlekms

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

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
    
    /**
     * Google Cloud KMS required environment variables.
     * GOOGLE_CLOUD_PROJECT or GCLOUD_PROJECT is required.
     * GOOGLE_APPLICATION_CREDENTIALS is optional if using Application Default Credentials (ADC).
     */
    override val requiredEnvironmentVariables: List<String> = listOf(
        "GOOGLE_CLOUD_PROJECT",  // or GCLOUD_PROJECT
        "?GOOGLE_APPLICATION_CREDENTIALS"  // Optional if using ADC
    )
    
    override fun hasRequiredEnvironmentVariables(): Boolean {
        // Check if project ID is set OR if we're running on GCP (ADC available)
        return (System.getenv("GOOGLE_CLOUD_PROJECT") != null ||
                System.getenv("GCLOUD_PROJECT") != null ||
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null) ||
               // Running on GCP (Application Default Credentials available)
               System.getenv("GCE_METADATA_HOST") != null
    }
    
    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = GoogleKmsConfig.fromMap(options)
        return GoogleCloudKeyManagementService(config)
    }
}

