package org.trustweave.azurekms

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
    val endpointOverride: String? = null,
) {
    /** Redacts credentials so they never reach logs or exception messages. */
    override fun toString(): String =
        "AzureKmsConfig(vaultUrl=$vaultUrl, clientId=$clientId, clientSecret=${redactSecret(clientSecret)}, " +
            "tenantId=$tenantId, endpointOverride=$endpointOverride)"

    private fun redactSecret(value: Any?): String = if (value == null) "null" else "<redacted>"

    init {
        require(vaultUrl.isNotBlank()) { "Azure Key Vault URL must be specified" }
        require(vaultUrl.startsWith("https://")) { "Azure Key Vault URL must use HTTPS" }
        endpointOverride?.let { requireSecureEndpointOverride(it) }
    }

    companion object {
        /**
         * Validates an [endpointOverride]: an absolute URL with a host and no embedded
         * credentials, using `https`, or plaintext `http` **only for a loopback host**
         * (`localhost`, `127.0.0.0/8`, `::1`; typically a local Key Vault emulator). The client
         * sends real Azure AD tokens to this endpoint, so a cleartext connection to anything that
         * is not on this machine is refused.
         *
         * @throws IllegalArgumentException when the override is not acceptable
         */
        @JvmStatic
        fun requireSecureEndpointOverride(override: String) {
            val uri =
                try {
                    java.net.URI(override)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid Azure Key Vault endpointOverride: $override", e)
                }
            val scheme = uri.scheme?.lowercase()
            require(uri.isAbsolute && scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
                "Azure Key Vault endpointOverride must be an absolute http(s) URL, got: $override"
            }
            require(uri.userInfo == null) { "Azure Key Vault endpointOverride must not embed credentials" }
            if (scheme == "https") return
            require(isLoopbackHost(uri.host)) {
                "Azure Key Vault endpointOverride must use https unless the host is loopback " +
                    "(localhost, 127.0.0.0/8, ::1); refusing to send credentials over cleartext http to '${uri.host}'"
            }
        }

        private fun isLoopbackHost(rawHost: String): Boolean {
            val host = rawHost.removePrefix("[").removeSuffix("]").lowercase()
            if (host == "localhost" || host.endsWith(".localhost")) return true
            // Only IP literals are inspected: no DNS lookup decides whether a name is "local".
            val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
            if (!ipv4.matches(host) && !host.contains(':')) return false
            return runCatching {
                java.net.InetAddress
                    .getByName(host)
                    .isLoopbackAddress
            }.getOrDefault(false)
        }

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
            val vaultUrl =
                System.getenv("AZURE_VAULT_URL")
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
            val vaultUrl =
                options["vaultUrl"] as? String
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
                endpointOverride = endpointOverride,
            )
        }
    }
}
