package com.geoknoesis.vericore.awskms

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

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

