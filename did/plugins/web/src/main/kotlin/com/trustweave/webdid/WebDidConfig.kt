package com.trustweave.webdid

/**
 * Configuration for did:web method implementation.
 *
 * Follows W3C did:web specification for HTTP/HTTPS-based DID resolution.
 *
 * **Example Usage:**
 * ```kotlin
 * val config = WebDidConfig.builder()
 *     .requireHttps(true)
 *     .documentPath("/.well-known/did.json")
 *     .build()
 * ```
 */
data class WebDidConfig(
    /**
     * Whether HTTPS is required for document hosting (default: true).
     * Per W3C spec, did:web requires HTTPS.
     */
    val requireHttps: Boolean = true,

    /**
     * Path to DID document (default: "/.well-known/did.json").
     * Per W3C spec, documents should be hosted at /.well-known/did.json
     */
    val documentPath: String = "/.well-known/did.json",

    /**
     * HTTP client timeout in seconds (default: 30).
     */
    val timeoutSeconds: Int = 30,

    /**
     * Whether to follow redirects (default: true).
     */
    val followRedirects: Boolean = true,

    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap()
) {

    companion object {
        /**
         * Creates a default configuration.
         */
        fun default(): WebDidConfig {
            return WebDidConfig()
        }

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): WebDidConfig {
            return WebDidConfig(
                requireHttps = map["requireHttps"] as? Boolean ?: true,
                documentPath = map["documentPath"] as? String ?: "/.well-known/did.json",
                timeoutSeconds = map["timeoutSeconds"] as? Int ?: 30,
                followRedirects = map["followRedirects"] as? Boolean ?: true,
                additionalProperties = map.filterKeys {
                    it !in setOf("requireHttps", "documentPath", "timeoutSeconds", "followRedirects")
                }
            )
        }

        /**
         * Builder for WebDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Builder for WebDidConfig.
     */
    class Builder {
        private var requireHttps: Boolean = true
        private var documentPath: String = "/.well-known/did.json"
        private var timeoutSeconds: Int = 30
        private var followRedirects: Boolean = true
        private val additionalProperties = mutableMapOf<String, Any?>()

        fun requireHttps(value: Boolean): Builder {
            this.requireHttps = value
            return this
        }

        fun documentPath(value: String): Builder {
            this.documentPath = value
            return this
        }

        fun timeoutSeconds(value: Int): Builder {
            this.timeoutSeconds = value
            return this
        }

        fun followRedirects(value: Boolean): Builder {
            this.followRedirects = value
            return this
        }

        fun property(key: String, value: Any?): Builder {
            this.additionalProperties[key] = value
            return this
        }

        fun build(): WebDidConfig {
            return WebDidConfig(
                requireHttps = requireHttps,
                documentPath = documentPath,
                timeoutSeconds = timeoutSeconds,
                followRedirects = followRedirects,
                additionalProperties = additionalProperties.toMap()
            )
        }
    }
}

