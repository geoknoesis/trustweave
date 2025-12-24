package org.trustweave.ensdid

/**
 * Configuration for did:ens method implementation.
 *
 * Supports Ethereum Name Service (ENS) resolver integration with Ethereum DID documents.
 *
 * **Example Usage:**
 * ```kotlin
 * val config = EnsDidConfig.builder()
 *     .ensRegistryAddress("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
 *     .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
 *     .chainId("eip155:1")
 *     .build()
 * ```
 */
data class EnsDidConfig(
    /**
     * ENS registry contract address (required).
     */
    val ensRegistryAddress: String,

    /**
     * Ethereum RPC endpoint URL (required).
     */
    val rpcUrl: String,

    /**
     * Chain ID in CAIP-2 format (e.g., "eip155:1" for mainnet).
     */
    val chainId: String,

    /**
     * Private key for signing transactions (optional).
     */
    val privateKey: String? = null,

    /**
     * Network name (mainnet, sepolia, etc.).
     */
    val network: String? = null,

    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap()
) {

    companion object {
        /**
         * ENS registry contract addresses.
         */
        const val MAINNET_REGISTRY = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
        const val SEPOLIA_REGISTRY = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"

        /**
         * Creates configuration for ENS mainnet.
         */
        fun mainnet(rpcUrl: String, privateKey: String? = null): EnsDidConfig {
            return EnsDidConfig(
                ensRegistryAddress = MAINNET_REGISTRY,
                rpcUrl = rpcUrl,
                chainId = "eip155:1",
                network = "mainnet",
                privateKey = privateKey
            )
        }

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): EnsDidConfig {
            return EnsDidConfig(
                ensRegistryAddress = map["ensRegistryAddress"] as? String
                    ?: throw IllegalArgumentException("ensRegistryAddress is required"),
                rpcUrl = map["rpcUrl"] as? String
                    ?: throw IllegalArgumentException("rpcUrl is required"),
                chainId = map["chainId"] as? String
                    ?: throw IllegalArgumentException("chainId is required"),
                privateKey = map["privateKey"] as? String,
                network = map["network"] as? String,
                additionalProperties = map.filterKeys {
                    it !in setOf("ensRegistryAddress", "rpcUrl", "chainId", "privateKey", "network")
                }
            )
        }

        /**
         * Builder for EnsDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Builder for EnsDidConfig.
     */
    class Builder {
        private var ensRegistryAddress: String? = MAINNET_REGISTRY
        private var rpcUrl: String? = null
        private var chainId: String? = null
        private var privateKey: String? = null
        private var network: String? = null
        private val additionalProperties = mutableMapOf<String, Any?>()

        fun ensRegistryAddress(value: String): Builder {
            this.ensRegistryAddress = value
            return this
        }

        fun rpcUrl(value: String): Builder {
            this.rpcUrl = value
            return this
        }

        fun chainId(value: String): Builder {
            this.chainId = value
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

        fun build(): EnsDidConfig {
            require(ensRegistryAddress != null) { "ensRegistryAddress is required" }
            require(rpcUrl != null) { "rpcUrl is required" }
            require(chainId != null) { "chainId is required" }

            return EnsDidConfig(
                ensRegistryAddress = ensRegistryAddress!!,
                rpcUrl = rpcUrl!!,
                chainId = chainId!!,
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
            put("ensRegistryAddress", ensRegistryAddress)
            put("rpcUrl", rpcUrl)
            put("chainId", chainId)
            put("privateKey", privateKey)
            put("network", network)
            putAll(additionalProperties)
        }
    }
}

