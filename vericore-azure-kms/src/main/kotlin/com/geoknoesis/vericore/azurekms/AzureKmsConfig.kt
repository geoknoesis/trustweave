package com.geoknoesis.vericore.azurekms

/**
 * Configuration for Azure Key Vault client.
 * 
 * Supports Managed Identity authentication (default) and Service Principal authentication (fallback).
 * Can load configuration from environment variables or explicit parameters.
 * 
 * **Example:**
 * ```kotlin
 * val config = AzureKmsConfig.builder()
 *     .vaultUrl("https://myvault.vault.azure.net")
 *     .clientId("client-id")
 *     .clientSecret("client-secret")
 *     .tenantId("tenant-id")
 *     .build()
 * ```
 */
data class AzureKmsConfig(
    val vaultUrl: String,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val tenantId: String? = null,
    val endpointOverride: String? = null
) {
    init {
        require(vaultUrl.isNotBlank()) { "Azure Key Vault URL must be specified" }
        require(vaultUrl.startsWith("https://")) { "Azure Key Vault URL must use HTTPS" }
    }
    
    companion object {
        /**
         * Creates a builder for AzureKmsConfig.
         */
        fun builder(): Builder = Builder()
        
        /**
         * Creates configuration from environment variables.
         * 
         * Reads:
         * - AZURE_VAULT_URL
         * - AZURE_CLIENT_ID
         * - AZURE_CLIENT_SECRET
         * - AZURE_TENANT_ID
         * 
         * @return AzureKmsConfig instance, or null if vault URL is not set
         */
        fun fromEnvironment(): AzureKmsConfig? {
            val vaultUrl = System.getenv("AZURE_VAULT_URL")
                ?: return null
            
            return Builder()
                .vaultUrl(vaultUrl)
                .clientId(System.getenv("AZURE_CLIENT_ID"))
                .clientSecret(System.getenv("AZURE_CLIENT_SECRET"))
                .tenantId(System.getenv("AZURE_TENANT_ID"))
                .build()
        }
        
        /**
         * Creates configuration from a map (typically from provider options).
         * 
         * @param options Map containing configuration options
         * @return AzureKmsConfig instance
         * @throws IllegalArgumentException if vault URL is not provided
         */
        fun fromMap(options: Map<String, Any?>): AzureKmsConfig {
            val vaultUrl = options["vaultUrl"] as? String
                ?: throw IllegalArgumentException("Azure Key Vault URL must be specified in options")
            
            return Builder()
                .vaultUrl(vaultUrl)
                .clientId(options["clientId"] as? String)
                .clientSecret(options["clientSecret"] as? String)
                .tenantId(options["tenantId"] as? String)
                .endpointOverride(options["endpointOverride"] as? String)
                .build()
        }
    }
    
    /**
     * Builder for AzureKmsConfig.
     */
    class Builder {
        private var vaultUrl: String? = null
        private var clientId: String? = null
        private var clientSecret: String? = null
        private var tenantId: String? = null
        private var endpointOverride: String? = null
        
        fun vaultUrl(vaultUrl: String): Builder {
            this.vaultUrl = vaultUrl
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
        
        fun tenantId(tenantId: String?): Builder {
            this.tenantId = tenantId
            return this
        }
        
        fun endpointOverride(endpointOverride: String?): Builder {
            this.endpointOverride = endpointOverride
            return this
        }
        
        fun build(): AzureKmsConfig {
            val vaultUrl = this.vaultUrl ?: throw IllegalArgumentException("Azure Key Vault URL is required")
            return AzureKmsConfig(
                vaultUrl = vaultUrl,
                clientId = clientId,
                clientSecret = clientSecret,
                tenantId = tenantId,
                endpointOverride = endpointOverride
            )
        }
    }
}

