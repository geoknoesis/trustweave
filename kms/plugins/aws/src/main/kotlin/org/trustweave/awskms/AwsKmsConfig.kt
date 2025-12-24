package org.trustweave.awskms

import org.trustweave.kms.KmsOptionKeys

/**
 * Configuration for AWS KMS client.
 *
 * Supports IAM role-based authentication (default) and access key authentication (fallback).
 * Can load configuration from environment variables or explicit parameters.
 *
 * **Example:**
 * ```kotlin
 * val config = AwsKmsConfig.builder()
 *     .region("us-east-1")
 *     .accessKeyId("AKIA...")
 *     .secretAccessKey("...")
 *     .build()
 * ```
 */
data class AwsKmsConfig(
    val region: String,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val sessionToken: String? = null,
    val endpointOverride: String? = null,
    val pendingWindowInDays: Int? = null,
    val cacheTtlSeconds: Long? = 300 // Default 5 minutes
) {
    init {
        require(region.isNotBlank()) { "AWS region must be specified" }
        require(pendingWindowInDays == null || pendingWindowInDays in 7..30) {
            "Pending window must be between 7 and 30 days, got: $pendingWindowInDays"
        }
        require(cacheTtlSeconds == null || cacheTtlSeconds > 0) {
            "Cache TTL must be positive, got: $cacheTtlSeconds"
        }
    }

    companion object {
        /**
         * Creates a builder for AwsKmsConfig.
         */
        fun builder(): Builder = Builder()

        /**
         * Creates configuration from environment variables.
         *
         * Reads:
         * - AWS_REGION or AWS_DEFAULT_REGION
         * - AWS_ACCESS_KEY_ID
         * - AWS_SECRET_ACCESS_KEY
         * - AWS_SESSION_TOKEN
         *
         * @return AwsKmsConfig instance, or null if region is not set
         */
        fun fromEnvironment(): AwsKmsConfig? {
            val region = System.getenv("AWS_REGION")
                ?: System.getenv("AWS_DEFAULT_REGION")
                ?: return null

            return Builder()
                .region(region)
                .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                .sessionToken(System.getenv("AWS_SESSION_TOKEN"))
                .pendingWindowInDays(System.getenv("AWS_KMS_PENDING_WINDOW_DAYS")?.toIntOrNull())
                .cacheTtlSeconds(System.getenv("AWS_KMS_CACHE_TTL_SECONDS")?.toLongOrNull())
                .build()
        }

        /**
         * Creates configuration from a map (typically from provider options).
         *
         * @param options Map containing configuration options
         * @return AwsKmsConfig instance
         * @throws IllegalArgumentException if region is not provided
         */
        fun fromMap(options: Map<String, Any?>): AwsKmsConfig {
            val region = options[KmsOptionKeys.REGION] as? String
                ?: throw IllegalArgumentException("AWS region must be specified in options")

            return Builder()
                .region(region)
                .accessKeyId(options[KmsOptionKeys.ACCESS_KEY_ID] as? String)
                .secretAccessKey(options[KmsOptionKeys.SECRET_ACCESS_KEY] as? String)
                .sessionToken(options[KmsOptionKeys.SESSION_TOKEN] as? String)
                .endpointOverride(options[KmsOptionKeys.ENDPOINT_OVERRIDE] as? String)
                .pendingWindowInDays(options[KmsOptionKeys.PENDING_WINDOW_IN_DAYS] as? Int)
                .cacheTtlSeconds((options["cacheTtlSeconds"] as? Number)?.toLong())
                .build()
        }
    }

    /**
     * Builder for AwsKmsConfig.
     */
    class Builder {
        private var region: String? = null
        private var accessKeyId: String? = null
        private var secretAccessKey: String? = null
        private var sessionToken: String? = null
        private var endpointOverride: String? = null
        private var pendingWindowInDays: Int? = null
        private var cacheTtlSeconds: Long? = 300

        fun region(region: String): Builder {
            this.region = region
            return this
        }

        fun accessKeyId(accessKeyId: String?): Builder {
            this.accessKeyId = accessKeyId
            return this
        }

        fun secretAccessKey(secretAccessKey: String?): Builder {
            this.secretAccessKey = secretAccessKey
            return this
        }

        fun sessionToken(sessionToken: String?): Builder {
            this.sessionToken = sessionToken
            return this
        }

        fun endpointOverride(endpointOverride: String?): Builder {
            this.endpointOverride = endpointOverride
            return this
        }

        fun pendingWindowInDays(pendingWindowInDays: Int?): Builder {
            this.pendingWindowInDays = pendingWindowInDays
            return this
        }

        fun cacheTtlSeconds(cacheTtlSeconds: Long?): Builder {
            this.cacheTtlSeconds = cacheTtlSeconds
            return this
        }

        fun build(): AwsKmsConfig {
            val region = this.region ?: throw IllegalArgumentException("AWS region is required")
            return AwsKmsConfig(
                region = region,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                sessionToken = sessionToken,
                endpointOverride = endpointOverride,
                pendingWindowInDays = pendingWindowInDays,
                cacheTtlSeconds = cacheTtlSeconds
            )
        }
    }
}

