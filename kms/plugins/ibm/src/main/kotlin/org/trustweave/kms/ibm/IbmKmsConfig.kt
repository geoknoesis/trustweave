package org.trustweave.kms.ibm

/**
 * Configuration for IBM Key Protect / Hyper Protect Crypto Services client.
 *
 * Supports IAM API key authentication and service instance ID.
 * Can load configuration from environment variables or explicit parameters.
 *
 * **Example:**
 * ```kotlin
 * val config = IbmKmsConfig.builder()
 *     .apiKey("xxx")
 *     .instanceId("xxx")
 *     .region("us-south")
 *     .build()
 * ```
 */
data class IbmKmsConfig(
    val apiKey: String,
    val instanceId: String,
    val region: String = "us-south",
    val serviceUrl: String? = null,
    val endpointOverride: String? = null
) {
    init {
        require(apiKey.isNotBlank()) { "IBM API key must be specified" }
        require(instanceId.isNotBlank()) { "IBM service instance ID must be specified" }
        require(region.isNotBlank()) { "IBM region must be specified" }
    }

    companion object {
        /**
         * Creates a builder for IbmKmsConfig.
         */
        fun builder(): Builder = Builder()

        /**
         * Creates configuration from environment variables.
         *
         * Reads:
         * - IBM_API_KEY or IBMCLOUD_API_KEY
         * - IBM_INSTANCE_ID or KMS_INSTANCE_ID
         * - IBM_REGION or IBMCLOUD_REGION
         *
         * @return IbmKmsConfig instance, or null if required values are not set
         */
        fun fromEnvironment(): IbmKmsConfig? {
            val apiKey = System.getenv("IBM_API_KEY")
                ?: System.getenv("IBMCLOUD_API_KEY")
                ?: return null

            val instanceId = System.getenv("IBM_INSTANCE_ID")
                ?: System.getenv("KMS_INSTANCE_ID")
                ?: return null

            val region = System.getenv("IBM_REGION")
                ?: System.getenv("IBMCLOUD_REGION")
                ?: "us-south"

            return Builder()
                .apiKey(apiKey)
                .instanceId(instanceId)
                .region(region)
                .serviceUrl(System.getenv("IBM_SERVICE_URL"))
                .build()
        }

        /**
         * Creates configuration from a map (typically from provider options).
         *
         * @param options Map containing configuration options
         * @return IbmKmsConfig instance
         * @throws IllegalArgumentException if required values are not provided
         */
        fun fromMap(options: Map<String, Any?>): IbmKmsConfig {
            val apiKey = options["apiKey"] as? String
                ?: options["api_key"] as? String
                ?: throw IllegalArgumentException("IBM API key must be specified in options")

            val instanceId = options["instanceId"] as? String
                ?: options["instance_id"] as? String
                ?: throw IllegalArgumentException("IBM service instance ID must be specified in options")

            return Builder()
                .apiKey(apiKey)
                .instanceId(instanceId)
                .region(options["region"] as? String ?: "us-south")
                .serviceUrl(options["serviceUrl"] as? String ?: options["service_url"] as? String)
                .endpointOverride(options["endpointOverride"] as? String ?: options["endpoint_override"] as? String)
                .build()
        }
    }

    /**
     * Builder for IbmKmsConfig.
     */
    class Builder {
        private var apiKey: String? = null
        private var instanceId: String? = null
        private var region: String = "us-south"
        private var serviceUrl: String? = null
        private var endpointOverride: String? = null

        fun apiKey(apiKey: String): Builder {
            this.apiKey = apiKey
            return this
        }

        fun instanceId(instanceId: String): Builder {
            this.instanceId = instanceId
            return this
        }

        fun region(region: String): Builder {
            this.region = region
            return this
        }

        fun serviceUrl(serviceUrl: String?): Builder {
            this.serviceUrl = serviceUrl
            return this
        }

        fun endpointOverride(endpointOverride: String?): Builder {
            this.endpointOverride = endpointOverride
            return this
        }

        fun build(): IbmKmsConfig {
            val apiKey = this.apiKey ?: throw IllegalArgumentException("IBM API key is required")
            val instanceId = this.instanceId ?: throw IllegalArgumentException("IBM service instance ID is required")
            return IbmKmsConfig(
                apiKey = apiKey,
                instanceId = instanceId,
                region = region,
                serviceUrl = serviceUrl,
                endpointOverride = endpointOverride
            )
        }
    }
}

