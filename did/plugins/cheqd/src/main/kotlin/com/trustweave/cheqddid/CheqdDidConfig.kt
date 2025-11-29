package com.trustweave.cheqddid

/**
 * Configuration for did:cheqd method implementation.
 *
 * Supports Cheqd network DID method with payment features.
 *
 * **Example Usage:**
 * ```kotlin
 * val config = CheqdDidConfig.builder()
 *     .cheqdApiUrl("https://api.cheqd.net")
 *     .network("mainnet")
 *     .accountAddress("cheqd1...")
 *     .privateKey("...")
 *     .build()
 * ```
 */
data class CheqdDidConfig(
    /**
     * Cheqd API URL (optional, uses default if not provided).
     */
    val cheqdApiUrl: String? = null,

    /**
     * Cheqd network (mainnet, testnet, etc.).
     */
    val network: String = "mainnet",

    /**
     * Account address for transactions (optional).
     */
    val accountAddress: String? = null,

    /**
     * Private key for signing transactions (optional).
     */
    val privateKey: String? = null,

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
         * Cheqd network constants.
         */
        const val MAINNET = "mainnet"
        const val TESTNET = "testnet"

        /**
         * Default Cheqd API URLs.
         */
        const val MAINNET_API_URL = "https://api.cheqd.net"
        const val TESTNET_API_URL = "https://testnet-api.cheqd.net"

        /**
         * Creates configuration for Cheqd mainnet.
         */
        fun mainnet(cheqdApiUrl: String = MAINNET_API_URL): CheqdDidConfig {
            return CheqdDidConfig(
                cheqdApiUrl = cheqdApiUrl,
                network = MAINNET
            )
        }

        /**
         * Creates configuration for Cheqd testnet.
         */
        fun testnet(cheqdApiUrl: String = TESTNET_API_URL): CheqdDidConfig {
            return CheqdDidConfig(
                cheqdApiUrl = cheqdApiUrl,
                network = TESTNET
            )
        }

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): CheqdDidConfig {
            return CheqdDidConfig(
                cheqdApiUrl = map["cheqdApiUrl"] as? String,
                network = map["network"] as? String ?: MAINNET,
                accountAddress = map["accountAddress"] as? String,
                privateKey = map["privateKey"] as? String,
                timeoutSeconds = map["timeoutSeconds"] as? Int ?: 30,
                additionalProperties = map.filterKeys {
                    it !in setOf("cheqdApiUrl", "network", "accountAddress", "privateKey", "timeoutSeconds")
                }
            )
        }

        /**
         * Builder for CheqdDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Builder for CheqdDidConfig.
     */
    class Builder {
        private var cheqdApiUrl: String? = null
        private var network: String = MAINNET
        private var accountAddress: String? = null
        private var privateKey: String? = null
        private var timeoutSeconds: Int = 30
        private val additionalProperties = mutableMapOf<String, Any?>()

        fun cheqdApiUrl(value: String?): Builder {
            this.cheqdApiUrl = value
            return this
        }

        fun network(value: String): Builder {
            this.network = value
            return this
        }

        fun accountAddress(value: String?): Builder {
            this.accountAddress = value
            return this
        }

        fun privateKey(value: String?): Builder {
            this.privateKey = value
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

        fun build(): CheqdDidConfig {
            return CheqdDidConfig(
                cheqdApiUrl = cheqdApiUrl,
                network = network,
                accountAddress = accountAddress,
                privateKey = privateKey,
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
            if (cheqdApiUrl != null) {
                put("cheqdApiUrl", cheqdApiUrl)
            }
            put("network", network)
            if (accountAddress != null) {
                put("accountAddress", accountAddress)
            }
            if (privateKey != null) {
                put("privateKey", privateKey)
            }
            put("timeoutSeconds", timeoutSeconds)
            putAll(additionalProperties)
        }
    }
}

