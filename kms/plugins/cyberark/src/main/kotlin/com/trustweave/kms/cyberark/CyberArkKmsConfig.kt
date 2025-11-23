package com.trustweave.kms.cyberark

/**
 * Configuration for CyberArk Conjur client.
 * 
 * Supports API key and host-based authentication.
 * Can load configuration from environment variables or explicit parameters.
 */
data class CyberArkKmsConfig(
    val conjurUrl: String,
    val account: String,
    val apiKey: String? = null,
    val hostId: String? = null,
    val username: String? = null,
    val password: String? = null
) {
    init {
        require(conjurUrl.isNotBlank()) { "CyberArk Conjur URL must be specified" }
        require(account.isNotBlank()) { "CyberArk Conjur account must be specified" }
        require(
            apiKey != null || 
            (username != null && password != null)
        ) { "CyberArk Conjur authentication credentials must be provided (apiKey or username/password)" }
    }
    
    companion object {
        fun builder(): Builder = Builder()
        
        fun fromEnvironment(): CyberArkKmsConfig? {
            val conjurUrl = System.getenv("CONJUR_URL") ?: return null
            val account = System.getenv("CONJUR_ACCOUNT") ?: return null
            val apiKey = System.getenv("CONJUR_API_KEY")
            val hostId = System.getenv("CONJUR_HOST_ID")
            
            return Builder()
                .conjurUrl(conjurUrl)
                .account(account)
                .apiKey(apiKey)
                .hostId(hostId)
                .build()
        }
        
        fun fromMap(options: Map<String, Any?>): CyberArkKmsConfig {
            val conjurUrl = options["conjurUrl"] as? String
                ?: throw IllegalArgumentException("CyberArk Conjur URL must be specified")
            val account = options["account"] as? String
                ?: throw IllegalArgumentException("CyberArk Conjur account must be specified")
            
            return Builder()
                .conjurUrl(conjurUrl)
                .account(account)
                .apiKey(options["apiKey"] as? String)
                .hostId(options["hostId"] as? String)
                .username(options["username"] as? String)
                .password(options["password"] as? String)
                .build()
        }
    }
    
    class Builder {
        private var conjurUrl: String? = null
        private var account: String? = null
        private var apiKey: String? = null
        private var hostId: String? = null
        private var username: String? = null
        private var password: String? = null
        
        fun conjurUrl(conjurUrl: String): Builder {
            this.conjurUrl = conjurUrl
            return this
        }
        
        fun account(account: String): Builder {
            this.account = account
            return this
        }
        
        fun apiKey(apiKey: String?): Builder {
            this.apiKey = apiKey
            return this
        }
        
        fun hostId(hostId: String?): Builder {
            this.hostId = hostId
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
        
        fun build(): CyberArkKmsConfig {
            val conjurUrl = this.conjurUrl ?: throw IllegalArgumentException("Conjur URL is required")
            val account = this.account ?: throw IllegalArgumentException("Account is required")
            return CyberArkKmsConfig(
                conjurUrl = conjurUrl,
                account = account,
                apiKey = apiKey,
                hostId = hostId,
                username = username,
                password = password
            )
        }
    }
}

