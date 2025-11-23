package com.trustweave.awskms

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for AWS KMS KeyManagementService.
 * 
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 * 
 * **Example:**
 * ```kotlin
 * val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
 * val awsProvider = providers.find { it.name == "aws" }
 * val kms = awsProvider?.create(mapOf("region" to "us-east-1"))
 * ```
 */
class AwsKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "aws"
    
    override val supportedAlgorithms: Set<Algorithm> = AwsKeyManagementService.SUPPORTED_ALGORITHMS
    
    /**
     * AWS KMS required environment variables.
     * At minimum, AWS_REGION or AWS_DEFAULT_REGION is required.
     * AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY are optional if using IAM roles.
     */
    override val requiredEnvironmentVariables: List<String> = listOf(
        "AWS_REGION",  // or AWS_DEFAULT_REGION
        "?AWS_ACCESS_KEY_ID",  // Optional if using IAM roles
        "?AWS_SECRET_ACCESS_KEY"  // Optional if using IAM roles
    )
    
    override fun hasRequiredEnvironmentVariables(): Boolean {
        // AWS_REGION or AWS_DEFAULT_REGION must be set
        // Credentials can come from env vars OR IAM roles OR credential files
        val hasRegion = System.getenv("AWS_REGION") != null || 
                       System.getenv("AWS_DEFAULT_REGION") != null
        
        if (hasRegion) {
            return true
        }
        
        // Check if we're running on AWS (IAM role available via default credential provider)
        return try {
            Class.forName("software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = try {
            AwsKmsConfig.fromMap(options)
        } catch (e: Exception) {
            // Try environment variables as fallback
            AwsKmsConfig.fromEnvironment()
                ?: throw IllegalArgumentException(
                    "AWS KMS configuration requires 'region' in options or AWS_REGION environment variable. " +
                    "Error: ${e.message}",
                    e
                )
        }
        
        return AwsKeyManagementService(config)
    }
}

