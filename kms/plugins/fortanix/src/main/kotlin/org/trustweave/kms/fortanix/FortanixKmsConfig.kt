package org.trustweave.kms.fortanix

/**
 * Configuration for Fortanix Data Security Manager (DSM) client.
 *
 * Supports API key authentication.
 * Can load configuration from environment variables or explicit parameters.
 */
data class FortanixKmsConfig(
    val apiEndpoint: String,
    val apiKey: String
) {
    init {
        require(apiEndpoint.isNotBlank()) { "Fortanix DSM API endpoint must be specified" }
        require(apiKey.isNotBlank()) { "Fortanix DSM API key must be specified" }
    }

    companion object {
        fun builder(): Builder = Builder()

        fun fromEnvironment(): FortanixKmsConfig? {
            val apiEndpoint = System.getenv("FORTANIX_API_ENDPOINT") ?: return null
            val apiKey = System.getenv("FORTANIX_API_KEY") ?: return null

            return Builder()
                .apiEndpoint(apiEndpoint)
                .apiKey(apiKey)
                .build()
        }

        fun fromMap(options: Map<String, Any?>): FortanixKmsConfig {
            val apiEndpoint = options["apiEndpoint"] as? String
                ?: options["api_endpoint"] as? String
                ?: throw IllegalArgumentException("Fortanix DSM API endpoint must be specified")
            val apiKey = options["apiKey"] as? String
                ?: options["api_key"] as? String
                ?: throw IllegalArgumentException("Fortanix DSM API key must be specified")

            return Builder()
                .apiEndpoint(apiEndpoint)
                .apiKey(apiKey)
                .build()
        }
    }

    class Builder {
        private var apiEndpoint: String? = null
        private var apiKey: String? = null

        fun apiEndpoint(apiEndpoint: String): Builder {
            this.apiEndpoint = apiEndpoint
            return this
        }

        fun apiKey(apiKey: String): Builder {
            this.apiKey = apiKey
            return this
        }

        fun build(): FortanixKmsConfig {
            val apiEndpoint = this.apiEndpoint ?: throw IllegalArgumentException("API endpoint is required")
            val apiKey = this.apiKey ?: throw IllegalArgumentException("API key is required")
            return FortanixKmsConfig(apiEndpoint, apiKey)
        }
    }
}

