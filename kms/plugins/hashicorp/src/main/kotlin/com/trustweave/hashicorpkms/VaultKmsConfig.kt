package com.trustweave.hashicorpkms

/**
 * Configuration for HashiCorp Vault client.
 * 
 * Supports token-based authentication (default) and AppRole authentication.
 * Can load configuration from environment variables or explicit parameters.
 * 
 * **Example:**
 * ```kotlin
 * val config = VaultKmsConfig.builder()
 *     .address("http://localhost:8200")
 *     .token("hvs.xxx")
 *     .transitPath("transit")
 *     .build()
 * ```
 */
data class VaultKmsConfig(
    val address: String,
    val token: String? = null,
    val transitPath: String = "transit",
    val namespace: String? = null,
    val appRolePath: String? = null,
    val roleId: String? = null,
    val secretId: String? = null,
    val engineVersion: Int = 2
) {
    init {
        require(address.isNotBlank()) { "Vault address must be specified" }
        require(transitPath.isNotBlank()) { "Transit path must be specified" }
    }
    
    companion object {
        /**
         * Creates a builder for VaultKmsConfig.
         */
        fun builder(): Builder = Builder()
        
        /**
         * Creates configuration from environment variables.
         * 
         * Reads:
         * - VAULT_ADDR
         * - VAULT_TOKEN
         * - VAULT_NAMESPACE
         * - VAULT_TRANSIT_PATH
         * 
         * @return VaultKmsConfig instance, or null if address is not set
         */
        fun fromEnvironment(): VaultKmsConfig? {
            val address = System.getenv("VAULT_ADDR") ?: return null
            
            return Builder()
                .address(address)
                .token(System.getenv("VAULT_TOKEN"))
                .namespace(System.getenv("VAULT_NAMESPACE"))
                .transitPath(System.getenv("VAULT_TRANSIT_PATH") ?: "transit")
                .build()
        }
        
        /**
         * Creates configuration from a map (typically from provider options).
         * 
         * @param options Map containing configuration options
         * @return VaultKmsConfig instance
         * @throws IllegalArgumentException if address is not provided
         */
        fun fromMap(options: Map<String, Any?>): VaultKmsConfig {
            val address = options["address"] as? String
                ?: throw IllegalArgumentException("Vault address must be specified in options")
            
            return Builder()
                .address(address)
                .token(options["token"] as? String)
                .transitPath(options["transitPath"] as? String ?: "transit")
                .namespace(options["namespace"] as? String)
                .appRolePath(options["appRolePath"] as? String)
                .roleId(options["roleId"] as? String)
                .secretId(options["secretId"] as? String)
                .engineVersion((options["engineVersion"] as? Number)?.toInt() ?: 2)
                .build()
        }
    }
    
    /**
     * Builder for VaultKmsConfig.
     */
    class Builder {
        private var address: String? = null
        private var token: String? = null
        private var transitPath: String = "transit"
        private var namespace: String? = null
        private var appRolePath: String? = null
        private var roleId: String? = null
        private var secretId: String? = null
        private var engineVersion: Int = 2
        
        fun address(address: String): Builder {
            this.address = address
            return this
        }
        
        fun token(token: String?): Builder {
            this.token = token
            return this
        }
        
        fun transitPath(transitPath: String): Builder {
            this.transitPath = transitPath
            return this
        }
        
        fun namespace(namespace: String?): Builder {
            this.namespace = namespace
            return this
        }
        
        fun appRolePath(appRolePath: String?): Builder {
            this.appRolePath = appRolePath
            return this
        }
        
        fun roleId(roleId: String?): Builder {
            this.roleId = roleId
            return this
        }
        
        fun secretId(secretId: String?): Builder {
            this.secretId = secretId
            return this
        }
        
        fun engineVersion(engineVersion: Int): Builder {
            this.engineVersion = engineVersion
            return this
        }
        
        fun build(): VaultKmsConfig {
            val address = this.address ?: throw IllegalArgumentException("Vault address is required")
            return VaultKmsConfig(
                address = address,
                token = token,
                transitPath = transitPath,
                namespace = namespace,
                appRolePath = appRolePath,
                roleId = roleId,
                secretId = secretId,
                engineVersion = engineVersion
            )
        }
    }
}

