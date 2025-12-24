package org.trustweave.ethrdid

/**
 * Configuration for did:ethr method implementation.
 *
 * Supports Ethereum DID method (ERC1056) for Ethereum and EVM-compatible chains.
 *
 * **Example Usage:**
 * ```kotlin
 * val config = EthrDidConfig.builder()
 *     .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
 *     .chainId("eip155:1")
 *     .registryAddress("0xdca7ef03e98e0dc2b855be647c39abe984fcf21b") // ERC1056 registry
 *     .privateKey("0x...")
 *     .build()
 * ```
 */
data class EthrDidConfig(
    /**
     * Ethereum RPC endpoint URL (required).
     */
    val rpcUrl: String,

    /**
     * Chain ID in CAIP-2 format (e.g., "eip155:1" for mainnet, "eip155:11155111" for Sepolia).
     */
    val chainId: String,

    /**
     * DID registry contract address (optional, uses default ERC1056 if not provided).
     */
    val registryAddress: String? = null,

    /**
     * Private key for signing transactions (optional, for read-only operations).
     */
    val privateKey: String? = null,

    /**
     * Network name (mainnet, sepolia, etc.) for convenience.
     */
    val network: String? = null,

    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap()
) {

    companion object {
        /**
         * Ethereum mainnet configuration constants.
         */
        const val MAINNET_CHAIN_ID = "eip155:1"
        const val MAINNET_RPC_URL = "https://eth-mainnet.g.alchemy.com/v2/demo"

        /**
         * Sepolia testnet configuration constants.
         */
        const val SEPOLIA_CHAIN_ID = "eip155:11155111"
        const val SEPOLIA_RPC_URL = "https://eth-sepolia.g.alchemy.com/v2/demo"

        /**
         * Default ERC1056 registry contract address (common deployment).
         */
        const val DEFAULT_REGISTRY_ADDRESS = "0xdca7ef03e98e0dc2b855be647c39abe984fcf21b"

        /**
         * Creates configuration for Ethereum mainnet.
         */
        fun mainnet(rpcUrl: String, privateKey: String? = null): EthrDidConfig {
            return EthrDidConfig(
                rpcUrl = rpcUrl,
                chainId = MAINNET_CHAIN_ID,
                network = "mainnet",
                privateKey = privateKey
            )
        }

        /**
         * Creates configuration for Sepolia testnet.
         */
        fun sepolia(rpcUrl: String, privateKey: String? = null): EthrDidConfig {
            return EthrDidConfig(
                rpcUrl = rpcUrl,
                chainId = SEPOLIA_CHAIN_ID,
                network = "sepolia",
                privateKey = privateKey
            )
        }

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): EthrDidConfig {
            return EthrDidConfig(
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
         * Builder for EthrDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Builder for EthrDidConfig.
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

        fun build(): EthrDidConfig {
            require(rpcUrl != null) { "rpcUrl is required" }
            require(chainId != null) { "chainId is required" }

            return EthrDidConfig(
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
     * Converts configuration to a map for backward compatibility.
     */
    fun toMap(): Map<String, Any?> {
        return buildMap {
            put("rpcUrl", rpcUrl)
            put("chainId", chainId)
            if (registryAddress != null) {
                put("registryAddress", registryAddress)
            }
            if (privateKey != null) {
                put("privateKey", privateKey)
            }
            if (network != null) {
                put("network", network)
            }
            putAll(additionalProperties)
        }
    }
}

