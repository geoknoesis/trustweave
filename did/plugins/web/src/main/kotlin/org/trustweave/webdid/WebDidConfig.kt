package org.trustweave.webdid

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
     * Upper bound, in seconds, on each resolution request (applied by [WebDidMethod] as an
     * OkHttp call timeout; the SPI provider also uses it for connect/read/write timeouts when it
     * builds the client). Default: 30. `0` or less leaves the client's own timeouts alone.
     */
    val timeoutSeconds: Int = 30,
    /**
     * Whether resolution follows HTTP redirects (default: false). When enabled, [WebDidMethod]
     * follows at most [WebDidMethod.MAX_REDIRECTS] hops manually, re-checking HTTPS and the SSRF
     * guard on every hop; the HTTP client's own redirect handling is never used.
     */
    val followRedirects: Boolean = false,
    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap(),
) {
    /** Prints only the keys of [additionalProperties]: its values may carry credentials. */
    override fun toString(): String =
        "WebDidConfig(requireHttps=$requireHttps, documentPath=$documentPath, timeoutSeconds=$timeoutSeconds, " +
            "followRedirects=$followRedirects, additionalProperties=${additionalProperties.keys})"

    companion object {
        /**
         * Creates a default configuration.
         */
        fun default(): WebDidConfig = WebDidConfig()

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): WebDidConfig =
            WebDidConfig(
                requireHttps = map["requireHttps"] as? Boolean ?: true,
                documentPath = map["documentPath"] as? String ?: "/.well-known/did.json",
                timeoutSeconds = map["timeoutSeconds"] as? Int ?: 30,
                followRedirects = map["followRedirects"] as? Boolean ?: false,
                additionalProperties =
                    map.filterKeys {
                        it !in setOf("requireHttps", "documentPath", "timeoutSeconds", "followRedirects")
                    },
            )

        /**
         * Builder for WebDidConfig.
         */
        fun builder(): Builder = Builder()
    }

    /**
     * Builder for WebDidConfig.
     */
    class Builder {
        private var requireHttps: Boolean = true
        private var documentPath: String = "/.well-known/did.json"
        private var timeoutSeconds: Int = 30
        private var followRedirects: Boolean = false
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

        fun property(
            key: String,
            value: Any?,
        ): Builder {
            this.additionalProperties[key] = value
            return this
        }

        fun build(): WebDidConfig =
            WebDidConfig(
                requireHttps = requireHttps,
                documentPath = documentPath,
                timeoutSeconds = timeoutSeconds,
                followRedirects = followRedirects,
                additionalProperties = additionalProperties.toMap(),
            )
    }
}
