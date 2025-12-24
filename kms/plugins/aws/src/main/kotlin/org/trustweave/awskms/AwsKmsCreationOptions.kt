package org.trustweave.awskms

import org.trustweave.kms.KmsCreationOptions
import org.trustweave.kms.KmsOptionKeys

/**
 * Type-safe configuration builder for AWS KMS provider.
 *
 * Provides compile-time type safety and IDE autocomplete for AWS-specific options.
 *
 * **Example:**
 * ```kotlin
 * val options = awsKmsOptions {
 *     region = "us-east-1"
 *     accessKeyId = "AKIA..."
 *     secretAccessKey = "..."
 * }
 * ```
 */
class AwsKmsOptionsBuilder {
    var region: String? = null
    var accessKeyId: String? = null
    var secretAccessKey: String? = null
    var sessionToken: String? = null
    var endpointOverride: String? = null
    var pendingWindowInDays: Int? = null
    var cacheTtlSeconds: Long? = null
    var enabled: Boolean = true
    var priority: Int? = null

    fun build(): KmsCreationOptions {
        require(region != null) { "region is required for AWS KMS" }
        
        val properties = mutableMapOf<String, Any?>(
            KmsOptionKeys.REGION to region
        )
        
        accessKeyId?.let { properties[KmsOptionKeys.ACCESS_KEY_ID] = it }
        secretAccessKey?.let { properties[KmsOptionKeys.SECRET_ACCESS_KEY] = it }
        sessionToken?.let { properties[KmsOptionKeys.SESSION_TOKEN] = it }
        endpointOverride?.let { properties[KmsOptionKeys.ENDPOINT_OVERRIDE] = it }
        pendingWindowInDays?.let { properties[KmsOptionKeys.PENDING_WINDOW_IN_DAYS] = it }
        cacheTtlSeconds?.let { properties["cacheTtlSeconds"] = it }
        
        return KmsCreationOptions(
            enabled = enabled,
            priority = priority,
            additionalProperties = properties
        )
    }
}

/**
 * DSL builder function for AWS KMS options.
 *
 * **Example:**
 * ```kotlin
 * import org.trustweave.kms.*
 * 
 * val kms = KeyManagementServices.create("aws", awsKmsOptions {
 *     region = "us-east-1"
 * })
 * ```
 */
fun awsKmsOptions(
    block: AwsKmsOptionsBuilder.() -> Unit
): KmsCreationOptions {
    val builder = AwsKmsOptionsBuilder()
    builder.block()
    return builder.build()
}

