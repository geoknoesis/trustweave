package com.trustweave.godiddy

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

        fun fromOptions(options: com.trustweave.did.DidCreationOptions): GodiddyConfig {
            val props = options.additionalProperties
            return GodiddyConfig(
                baseUrl = props["baseUrl"] as? String ?: default().baseUrl,
                timeout = (props["timeout"] as? Number)?.toLong() ?: default().timeout,
                apiKey = props["apiKey"] as? String
            )
        }
    }
}

