package com.trustweave.iondid

/**
 * Configuration for did:ion method implementation.
 *
 * Supports Microsoft ION (Identity Overlay Network) DID method using Sidetree protocol.
 *
 * **Example Usage:**
 * ```kotlin
 * val config = IonDidConfig.builder()
 *     .ionNodeUrl("https://ion-node.example.com")
 *     .bitcoinRpcUrl("https://btc-mainnet.example.com") // For anchoring
 *     .bitcoinNetwork("mainnet")
 *     .build()
 * ```
 */
data class IonDidConfig(
    /**
     * ION node endpoint URL (required).
     * Can be a single node or multiple nodes for redundancy.
     */
    val ionNodeUrl: String,

    /**
     * Bitcoin RPC endpoint for anchoring operations (optional, for direct anchoring).
     */
    val bitcoinRpcUrl: String? = null,

    /**
     * Bitcoin network (mainnet, testnet, regtest) - default: mainnet.
     */
    val bitcoinNetwork: String = "mainnet",

    /**
     * Operation batch size (default: 10).
     */
    val batchSize: Int = 10,

    /**
     * Timeout for ION node requests in seconds (default: 60).
     */
    val timeoutSeconds: Int = 60,

    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap()
) {

    companion object {
        /**
         * Common ION node endpoints.
         */
        const val ION_MAINNET_NODE = "https://ion-node.tbddev.org"
        const val ION_TESTNET_NODE = "https://ion-testnet-node.tbddev.org"

        /**
         * Bitcoin network constants.
         */
        const val BITCOIN_MAINNET = "mainnet"
        const val BITCOIN_TESTNET = "testnet"
        const val BITCOIN_REGTEST = "regtest"

        /**
         * Creates configuration for ION mainnet.
         */
        fun mainnet(ionNodeUrl: String = ION_MAINNET_NODE): IonDidConfig {
            return IonDidConfig(
                ionNodeUrl = ionNodeUrl,
                bitcoinNetwork = BITCOIN_MAINNET
            )
        }

        /**
         * Creates configuration for ION testnet.
         */
        fun testnet(ionNodeUrl: String = ION_TESTNET_NODE): IonDidConfig {
            return IonDidConfig(
                ionNodeUrl = ionNodeUrl,
                bitcoinNetwork = BITCOIN_TESTNET
            )
        }

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): IonDidConfig {
            return IonDidConfig(
                ionNodeUrl = map["ionNodeUrl"] as? String
                    ?: throw IllegalArgumentException("ionNodeUrl is required"),
                bitcoinRpcUrl = map["bitcoinRpcUrl"] as? String,
                bitcoinNetwork = map["bitcoinNetwork"] as? String ?: BITCOIN_MAINNET,
                batchSize = map["batchSize"] as? Int ?: 10,
                timeoutSeconds = map["timeoutSeconds"] as? Int ?: 60,
                additionalProperties = map.filterKeys {
                    it !in setOf("ionNodeUrl", "bitcoinRpcUrl", "bitcoinNetwork", "batchSize", "timeoutSeconds")
                }
            )
        }

        /**
         * Builder for IonDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Builder for IonDidConfig.
     */
    class Builder {
        private var ionNodeUrl: String? = null
        private var bitcoinRpcUrl: String? = null
        private var bitcoinNetwork: String = BITCOIN_MAINNET
        private var batchSize: Int = 10
        private var timeoutSeconds: Int = 60
        private val additionalProperties = mutableMapOf<String, Any?>()

        fun ionNodeUrl(value: String): Builder {
            this.ionNodeUrl = value
            return this
        }

        fun bitcoinRpcUrl(value: String?): Builder {
            this.bitcoinRpcUrl = value
            return this
        }

        fun bitcoinNetwork(value: String): Builder {
            this.bitcoinNetwork = value
            return this
        }

        fun batchSize(value: Int): Builder {
            this.batchSize = value
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

        fun build(): IonDidConfig {
            require(ionNodeUrl != null) { "ionNodeUrl is required" }

            return IonDidConfig(
                ionNodeUrl = ionNodeUrl!!,
                bitcoinRpcUrl = bitcoinRpcUrl,
                bitcoinNetwork = bitcoinNetwork,
                batchSize = batchSize,
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
            put("ionNodeUrl", ionNodeUrl)
            put("bitcoinRpcUrl", bitcoinRpcUrl)
            put("bitcoinNetwork", bitcoinNetwork)
            put("batchSize", batchSize)
            put("timeoutSeconds", timeoutSeconds)
            putAll(additionalProperties)
        }
    }
}

