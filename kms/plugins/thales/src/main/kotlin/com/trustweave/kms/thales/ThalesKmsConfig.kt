package com.trustweave.kms.thales

/**
 * Configuration for Thales CipherTrust Manager client.
 * 
 * Supports API key and OAuth2 authentication.
 * Can load configuration from environment variables or explicit parameters.
 * 
 * **Example:**
 * ```kotlin
 * val config = ThalesKmsConfig.builder()
 *     .baseUrl("https://ciphertrust.example.com")
 *     .apiKey("xxx")
 *     .build()
 * ```
 */
data class ThalesKmsConfig(
    val baseUrl: String,
    val apiKey: String? = null,
    val username: String? = null,
    val password: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val accessToken: String? = null,
    val scope: String? = null
) {
    init {
        require(baseUrl.isNotBlank()) { "Thales CipherTrust base URL must be specified" }
        require(
            apiKey != null || 
            (username != null && password != null) || 
            (clientId != null && clientSecret != null) ||
            accessToken != null
        ) { "Thales CipherTrust authentication credentials must be provided" }
    }
    
    companion object {
        fun builder(): Builder = Builder()
        
        fun fromEnvironment(): ThalesKmsConfig? {
            val baseUrl = System.getenv("THALES_BASE_URL") ?: return null
            val apiKey = System.getenv("THALES_API_KEY")
            val username = System.getenv("THALES_USERNAME")
            val password = System.getenv("THALES_PASSWORD")
            
            return Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .username(username)
                .password(password)
                .build()
        }
        
        fun fromMap(options: Map<String, Any?>): ThalesKmsConfig {
            val baseUrl = options["baseUrl"] as? String
                ?: throw IllegalArgumentException("Thales CipherTrust baseUrl must be specified")
            
            return Builder()
                .baseUrl(baseUrl)
                .apiKey(options["apiKey"] as? String)
                .username(options["username"] as? String)
                .password(options["password"] as? String)
                .clientId(options["clientId"] as? String)
                .clientSecret(options["clientSecret"] as? String)
                .accessToken(options["accessToken"] as? String)
                .build()
        }
    }
    
    class Builder {
        private var baseUrl: String? = null
        private var apiKey: String? = null
        private var username: String? = null
        private var password: String? = null
        private var clientId: String? = null
        private var clientSecret: String? = null
        private var accessToken: String? = null
        private var scope: String? = null
        
        fun baseUrl(baseUrl: String): Builder {
            this.baseUrl = baseUrl
            return this
        }
        
        fun apiKey(apiKey: String?): Builder {
            this.apiKey = apiKey
            return this
        }
        
        fun username(username: String?): Builder {
            this.username = username
            return this
        }
        
        fun password(password: String?): Builder {
            this.password = password
            return this
        }
        
        fun clientId(clientId: String?): Builder {
            this.clientId = clientId
            return this
        }
        
        fun clientSecret(clientSecret: String?): Builder {
            this.clientSecret = clientSecret
            return this
        }
        
        fun accessToken(accessToken: String?): Builder {
            this.accessToken = accessToken
            return this
        }
        
        fun scope(scope: String?): Builder {
            this.scope = scope
            return this
        }
        
        fun build(): ThalesKmsConfig {
            val baseUrl = this.baseUrl ?: throw IllegalArgumentException("Base URL is required")
            return ThalesKmsConfig(
                baseUrl = baseUrl,
                apiKey = apiKey,
                username = username,
                password = password,
                clientId = clientId,
                clientSecret = clientSecret,
                accessToken = accessToken,
                scope = scope
            )
        }
    }
}

