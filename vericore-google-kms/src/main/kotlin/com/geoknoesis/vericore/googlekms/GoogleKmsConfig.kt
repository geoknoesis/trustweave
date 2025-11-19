package com.geoknoesis.vericore.googlekms

/**
 * Configuration for Google Cloud KMS client.
 * 
 * Supports service account authentication (JSON file or string), Application Default Credentials (ADC),
 * and environment variable-based configuration.
 * 
 * **Example:**
 * ```kotlin
 * val config = GoogleKmsConfig.builder()
 *     .projectId("my-project")
 *     .location("us-east1")
 *     .keyRing("my-key-ring")
 *     .build()
 * ```
 */
data class GoogleKmsConfig(
    val projectId: String,
    val location: String,
    val keyRing: String? = null,
    val credentialsPath: String? = null,
    val credentialsJson: String? = null,
    val endpoint: String? = null
) {
    init {
        require(projectId.isNotBlank()) { "Google Cloud project ID must be specified" }
        require(location.isNotBlank()) { "Google Cloud location must be specified" }
    }
    
    companion object {
        /**
         * Creates a builder for GoogleKmsConfig.
         */
        fun builder(): Builder = Builder()
        
        /**
         * Creates configuration from environment variables.
         * 
         * Reads:
         * - GOOGLE_CLOUD_PROJECT or GCLOUD_PROJECT
         * - GOOGLE_APPLICATION_CREDENTIALS (path to service account JSON)
         * - GOOGLE_CLOUD_LOCATION (optional)
         * - GOOGLE_CLOUD_KEY_RING (optional)
         * 
         * @return GoogleKmsConfig instance, or null if project ID is not set
         */
        fun fromEnvironment(): GoogleKmsConfig? {
            val projectId = System.getenv("GOOGLE_CLOUD_PROJECT") 
                ?: System.getenv("GCLOUD_PROJECT")
                ?: return null
            
            return Builder()
                .projectId(projectId)
                .location(System.getenv("GOOGLE_CLOUD_LOCATION") ?: "us-east1")
                .keyRing(System.getenv("GOOGLE_CLOUD_KEY_RING"))
                .credentialsPath(System.getenv("GOOGLE_APPLICATION_CREDENTIALS"))
                .build()
        }
        
        /**
         * Creates configuration from a map (typically from provider options).
         * 
         * @param options Map containing configuration options
         * @return GoogleKmsConfig instance
         * @throws IllegalArgumentException if project ID or location is not provided
         */
        fun fromMap(options: Map<String, Any?>): GoogleKmsConfig {
            val projectId = options["projectId"] as? String
                ?: throw IllegalArgumentException("Google Cloud project ID must be specified in options")
            val location = options["location"] as? String
                ?: throw IllegalArgumentException("Google Cloud location must be specified in options")
            
            return Builder()
                .projectId(projectId)
                .location(location)
                .keyRing(options["keyRing"] as? String)
                .credentialsPath(options["credentialsPath"] as? String)
                .credentialsJson(options["credentialsJson"] as? String)
                .endpoint(options["endpoint"] as? String)
                .build()
        }
    }
    
    /**
     * Builder for GoogleKmsConfig.
     */
    class Builder {
        private var projectId: String? = null
        private var location: String? = null
        private var keyRing: String? = null
        private var credentialsPath: String? = null
        private var credentialsJson: String? = null
        private var endpoint: String? = null
        
        fun projectId(projectId: String): Builder {
            this.projectId = projectId
            return this
        }
        
        fun location(location: String): Builder {
            this.location = location
            return this
        }
        
        fun keyRing(keyRing: String?): Builder {
            this.keyRing = keyRing
            return this
        }
        
        fun credentialsPath(credentialsPath: String?): Builder {
            this.credentialsPath = credentialsPath
            return this
        }
        
        fun credentialsJson(credentialsJson: String?): Builder {
            this.credentialsJson = credentialsJson
            return this
        }
        
        fun endpoint(endpoint: String?): Builder {
            this.endpoint = endpoint
            return this
        }
        
        fun build(): GoogleKmsConfig {
            val projectId = this.projectId ?: throw IllegalArgumentException("Google Cloud project ID is required")
            val location = this.location ?: throw IllegalArgumentException("Google Cloud location is required")
            return GoogleKmsConfig(
                projectId = projectId,
                location = location,
                keyRing = keyRing,
                credentialsPath = credentialsPath,
                credentialsJson = credentialsJson,
                endpoint = endpoint
            )
        }
    }
}

