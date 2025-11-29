package com.trustweave.polygondid

/**
 * Configuration for did:polygon method implementation.
 *
 * Supports Polygon DID method for Polygon/EVM-compatible chains.
 * Reuses Ethereum DID registry pattern for EVM compatibility.
 *
 * **Example Usage:**
 * ```kotlin
 * val config = PolygonDidConfig.builder()
 *     .rpcUrl("https://polygon-rpc.com")
 *     .chainId("eip155:137")
 *     .privateKey("0x...")
 *     .build()
 * ```
 */
data class PolygonDidConfig(
    /**
     * Polygon RPC endpoint URL (required).
     */
    val rpcUrl: String,

    /**
     * Chain ID in CAIP-2 format (e.g., "eip155:137" for mainnet, "eip155:80001" for Mumbai).
     */
    val chainId: String,

    /**
     * DID registry contract address (optional, uses default if not provided).
     */
    val registryAddress: String? = null,

    /**
     * Private key for signing transactions (optional, for read-only operations).
     */
    val privateKey: String? = null,

    /**
     * Network name (mainnet, mumbai, etc.) for convenience.
     */
    val network: String? = null,

    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap()
) {

    companion object {
        /**
         * Polygon mainnet configuration constants.
         */
        const val MAINNET_CHAIN_ID = "eip155:137"
        const val MAINNET_RPC_URL = "https://polygon-rpc.com"

        /**
         * Mumbai testnet configuration constants.
         */
        const val MUMBAI_CHAIN_ID = "eip155:80001"
        const val MUMBAI_RPC_URL = "https://rpc-mumbai.maticvigil.com"

        /**
         * Default registry contract address (same as Ethereum).
         */
        const val DEFAULT_REGISTRY_ADDRESS = "0xdca7ef03e98e0dc2b855be647c39abe984fcf21b"

        /**
         * Creates configuration for Polygon mainnet.
         */
        fun mainnet(rpcUrl: String = MAINNET_RPC_URL, privateKey: String? = null): PolygonDidConfig {
            return PolygonDidConfig(
                rpcUrl = rpcUrl,
                chainId = MAINNET_CHAIN_ID,
                network = "mainnet",
                privateKey = privateKey
            )
        }

        /**
         * Creates configuration for Mumbai testnet.
         */
        fun mumbai(rpcUrl: String = MUMBAI_RPC_URL, privateKey: String? = null): PolygonDidConfig {
            return PolygonDidConfig(
                rpcUrl = rpcUrl,
                chainId = MUMBAI_CHAIN_ID,
                network = "mumbai",
                privateKey = privateKey
            )
        }

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): PolygonDidConfig {
            return PolygonDidConfig(
                rpcUrl = map["rpcUrl"] as? String
                    ?: throw IllegalArgumentException("rpcUrl is required"),
                chainId = map["chainId"] as? String
                    ?: throw IllegalArgumentException("chainId is required"),
                registryAddress = map["registryAddress"] as? String ?: DEFAULT_REGISTRY_ADDRESS,
                privateKey = map["privateKey"] as? String,
                network = map["network"] as? String,
                additionalProperties = map.filterKeys {
                    it !in setOf("rpcUrl", "chainId", "registryAddress", "privateKey", "network")
                }
            )
        }

        /**
         * Builder for PolygonDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Builder for PolygonDidConfig.
     */
    class Builder {
        private var rpcUrl: String? = null
        private var chainId: String? = null
        private var registryAddress: String? = DEFAULT_REGISTRY_ADDRESS
        private var privateKey: String? = null
        private var network: String? = null
        private val additionalProperties = mutableMapOf<String, Any?>()

        fun rpcUrl(value: String): Builder {
            this.rpcUrl = value
            return this
        }

        fun chainId(value: String): Builder {
            this.chainId = value
            return this
        }

        fun registryAddress(value: String?): Builder {
            this.registryAddress = value
            return this
        }

        fun privateKey(value: String?): Builder {
            this.privateKey = value
            return this
        }

        fun network(value: String): Builder {
            this.network = value
            return this
        }

        fun property(key: String, value: Any?): Builder {
            this.additionalProperties[key] = value
            return this
        }

        fun build(): PolygonDidConfig {
            require(rpcUrl != null) { "rpcUrl is required" }
            require(chainId != null) { "chainId is required" }

            return PolygonDidConfig(
                rpcUrl = rpcUrl!!,
                chainId = chainId!!,
                registryAddress = registryAddress,
                privateKey = privateKey,
                network = network,
                additionalProperties = additionalProperties.toMap()
            )
        }
    }

    /**
     * Converts to map format.
     */
    fun toMap(): Map<String, Any?> {
        return buildMap {
            put("rpcUrl", rpcUrl)
            put("chainId", chainId)
            put("registryAddress", registryAddress)
            put("privateKey", privateKey)
            put("network", network)
            putAll(additionalProperties)
        }
    }
}

