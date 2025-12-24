package org.trustweave.plcdid

/**
 * Configuration for did:plc method implementation.
 *
 * Supports Personal Linked Container (PLC) DID method for AT Protocol.
 *
 * **Example Usage:**
 * ```kotlin
 * val config = PlcDidConfig.builder()
 *     .plcRegistryUrl("https://plc.directory")
 *     .timeoutSeconds(30)
 *     .build()
 * ```
 */
data class PlcDidConfig(
    /**
     * PLC registry URL (optional, uses default if not provided).
     */
    val plcRegistryUrl: String? = null,

    /**
     * HTTP timeout in seconds (default: 30).
     */
    val timeoutSeconds: Int = 30,

    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap()
) {

    companion object {
        /**
         * Default PLC registry URL.
         */
        const val DEFAULT_PLC_REGISTRY_URL = "https://plc.directory"

        /**
         * Creates default configuration.
         */
        fun default(): PlcDidConfig {
            return PlcDidConfig(
                plcRegistryUrl = DEFAULT_PLC_REGISTRY_URL
            )
        }

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): PlcDidConfig {
            return PlcDidConfig(
                plcRegistryUrl = map["plcRegistryUrl"] as? String ?: DEFAULT_PLC_REGISTRY_URL,
                timeoutSeconds = map["timeoutSeconds"] as? Int ?: 30,
                additionalProperties = map.filterKeys {
                    it !in setOf("plcRegistryUrl", "timeoutSeconds")
                }
            )
        }

        /**
         * Builder for PlcDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Builder for PlcDidConfig.
     */
    class Builder {
        private var plcRegistryUrl: String? = DEFAULT_PLC_REGISTRY_URL
        private var timeoutSeconds: Int = 30
        private val additionalProperties = mutableMapOf<String, Any?>()

        fun plcRegistryUrl(value: String?): Builder {
            this.plcRegistryUrl = value
            return this
        }

        fun timeoutSeconds(value: Int): Builder {
            this.timeoutSeconds = value
            return this
        }

        fun property(key: String, value: Any?): Builder {
            this.additionalProperties[key] = value
            return this
        }

        fun build(): PlcDidConfig {
            return PlcDidConfig(
                plcRegistryUrl = plcRegistryUrl,
                timeoutSeconds = timeoutSeconds,
                additionalProperties = additionalProperties.toMap()
            )
        }
    }

    /**
     * Converts to map format.
     */
    fun toMap(): Map<String, Any?> {
        return buildMap {
            if (plcRegistryUrl != null) {
                put("plcRegistryUrl", plcRegistryUrl)
            }
            put("timeoutSeconds", timeoutSeconds)
            putAll(additionalProperties)
        }
    }
}

