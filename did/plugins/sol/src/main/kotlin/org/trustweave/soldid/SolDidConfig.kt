package org.trustweave.soldid

/**
 * Configuration for did:sol method implementation.
 *
 * Supports Solana DID method for Solana blockchain.
 *
 * **Example Usage:**
 * ```kotlin
 * val config = SolDidConfig.builder()
 *     .rpcUrl("https://api.mainnet-beta.solana.com")
 *     .network("mainnet")
 *     .programId("YourDIDProgramId") // Optional
 *     .privateKey("base58...") // Optional: for transactions
 *     .build()
 * ```
 */
data class SolDidConfig(
    /**
     * Solana RPC endpoint URL (required).
     */
    val rpcUrl: String,

    /**
     * Solana network (mainnet-beta, devnet, testnet, localhost).
     */
    val network: String = "mainnet-beta",

    /**
     * DID registry program ID (optional, uses default if not provided).
     */
    val programId: String? = null,

    /**
     * Private key for signing transactions (base58 encoded, optional).
     */
    val privateKey: String? = null,

    /**
     * Commitment level for transactions (finalized, confirmed, processed).
     */
    val commitment: String = "finalized",

    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap()
) {

    companion object {
        /**
         * Solana network constants.
         */
        const val MAINNET = "mainnet-beta"
        const val DEVNET = "devnet"
        const val TESTNET = "testnet"
        const val LOCALHOST = "localhost"

        /**
         * Common Solana RPC endpoints.
         */
        const val MAINNET_RPC_URL = "https://api.mainnet-beta.solana.com"
        const val DEVNET_RPC_URL = "https://api.devnet.solana.com"
        const val TESTNET_RPC_URL = "https://api.testnet.solana.com"

        /**
         * Commitment levels.
         */
        const val COMMITMENT_FINALIZED = "finalized"
        const val COMMITMENT_CONFIRMED = "confirmed"
        const val COMMITMENT_PROCESSED = "processed"

        /**
         * Creates configuration for Solana mainnet.
         */
        fun mainnet(rpcUrl: String = MAINNET_RPC_URL, privateKey: String? = null): SolDidConfig {
            return SolDidConfig(
                rpcUrl = rpcUrl,
                network = MAINNET,
                privateKey = privateKey
            )
        }

        /**
         * Creates configuration for Solana devnet.
         */
        fun devnet(rpcUrl: String = DEVNET_RPC_URL, privateKey: String? = null): SolDidConfig {
            return SolDidConfig(
                rpcUrl = rpcUrl,
                network = DEVNET,
                privateKey = privateKey
            )
        }

        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): SolDidConfig {
            return SolDidConfig(
                rpcUrl = map["rpcUrl"] as? String
                    ?: throw IllegalArgumentException("rpcUrl is required"),
                network = map["network"] as? String ?: MAINNET,
                programId = map["programId"] as? String,
                privateKey = map["privateKey"] as? String,
                commitment = map["commitment"] as? String ?: COMMITMENT_FINALIZED,
                additionalProperties = map.filterKeys {
                    it !in setOf("rpcUrl", "network", "programId", "privateKey", "commitment")
                }
            )
        }

        /**
         * Builder for SolDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * Builder for SolDidConfig.
     */
    class Builder {
        private var rpcUrl: String? = null
        private var network: String = MAINNET
        private var programId: String? = null
        private var privateKey: String? = null
        private var commitment: String = COMMITMENT_FINALIZED
        private val additionalProperties = mutableMapOf<String, Any?>()

        fun rpcUrl(value: String): Builder {
            this.rpcUrl = value
            return this
        }

        fun network(value: String): Builder {
            this.network = value
            return this
        }

        fun programId(value: String?): Builder {
            this.programId = value
            return this
        }

        fun privateKey(value: String?): Builder {
            this.privateKey = value
            return this
        }

        fun commitment(value: String): Builder {
            this.commitment = value
            return this
        }

        fun property(key: String, value: Any?): Builder {
            this.additionalProperties[key] = value
            return this
        }

        fun build(): SolDidConfig {
            require(rpcUrl != null) { "rpcUrl is required" }

            return SolDidConfig(
                rpcUrl = rpcUrl!!,
                network = network,
                programId = programId,
                privateKey = privateKey,
                commitment = commitment,
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
            put("network", network)
            put("programId", programId)
            put("privateKey", privateKey)
            put("commitment", commitment)
            putAll(additionalProperties)
        }
    }
}

