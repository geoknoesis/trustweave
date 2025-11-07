package io.geoknoesis.vericore.godiddy

/**
 * Configuration for godiddy services.
 */
data class GodiddyConfig(
    /**
     * Base URL for godiddy services.
     * Defaults to public godiddy API service.
     * Can be overridden for self-hosted instances.
     */
    val baseUrl: String = "https://api.godiddy.com",
    
    /**
     * HTTP request timeout in milliseconds.
     */
    val timeout: Long = 30000,
    
    /**
     * API key for authentication (if required).
     */
    val apiKey: String? = null
) {
    companion object {
        /**
         * Default configuration using public godiddy service.
         */
        fun default(): GodiddyConfig = GodiddyConfig()
        
        /**
         * Creates configuration from options map.
         */
        fun fromOptions(options: Map<String, Any?>): GodiddyConfig {
            return GodiddyConfig(
                baseUrl = options["baseUrl"] as? String ?: default().baseUrl,
                timeout = (options["timeout"] as? Number)?.toLong() ?: default().timeout,
                apiKey = options["apiKey"] as? String
            )
        }
    }
}

