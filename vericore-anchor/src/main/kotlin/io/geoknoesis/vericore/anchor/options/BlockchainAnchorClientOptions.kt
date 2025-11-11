package io.geoknoesis.vericore.anchor.options

/**
 * Base sealed class for blockchain anchor client options.
 * 
 * Provides type-safe configuration for blockchain adapters.
 * Each blockchain type has its own specific options class.
 * 
 * **Benefits of Type-Safe Options**:
 * - Compile-time validation of configuration
 * - IDE autocomplete support
 * - Clear documentation of available options
 * - Prevents typos in option keys
 * 
 * **Backward Compatibility**: Options can be converted to/from `Map<String, Any?>` for
 * compatibility with existing code and the SPI interface.
 * 
 * **Example Usage**:
 * ```
 * // Type-safe (recommended)
 * val options = AlgorandOptions(
 *     algodUrl = "https://testnet-api.algonode.cloud",
 *     privateKey = "base64-key"
 * )
 * val client = AlgorandBlockchainAnchorClient(chainId, options)
 * 
 * // From map (backward compatible)
 * val map = mapOf("algodUrl" to "https://testnet-api.algonode.cloud")
 * val options = BlockchainAnchorClientOptions.fromMap("algorand:testnet", map)
 * ```
 */
sealed class BlockchainAnchorClientOptions {
    
    /**
     * Converts options to a map for backward compatibility.
     */
    abstract fun toMap(): Map<String, Any?>

    /**
     * Retrieves a typed value from [toMap] representation.
     */
    inline fun <reified T> get(key: String): T? = (toMap()[key] as? T)
    
    companion object {
        /**
         * Creates options from a map (for backward compatibility).
         * Attempts to detect the blockchain type and create appropriate options.
         */
        @JvmStatic
        fun fromMap(chainId: String, map: Map<String, Any?>): BlockchainAnchorClientOptions {
            return when {
                chainId.startsWith("algorand:") -> AlgorandOptions.fromMap(map)
                chainId.startsWith("eip155:") -> {
                    // Determine if Polygon or Ganache based on chain ID
                    val chainIdNum = chainId.substringAfter(":").toIntOrNull()
                    when (chainIdNum) {
                        137, 80001 -> PolygonOptions.fromMap(map)
                        1337 -> GanacheOptions.fromMap(map)
                        else -> PolygonOptions.fromMap(map) // Default to Polygon
                    }
                }
                chainId.startsWith("indy:") -> IndyOptions.fromMap(map)
                else -> throw IllegalArgumentException("Unsupported chain ID: $chainId")
            }
        }
    }
}

/**
 * Algorand blockchain anchor client options.
 * 
 * @param algodUrl Optional Algod API URL (defaults to network-specific endpoint)
 * @param algodToken Optional Algod API token (defaults to empty string)
 * @param privateKey Optional private key in base64 format (required for real transactions)
 * @param appId Optional Algorand application ID for contract interactions
 */
data class AlgorandOptions(
    val algodUrl: String? = null,
    val algodToken: String? = null,
    val privateKey: String? = null,
    val appId: String? = null
) : BlockchainAnchorClientOptions() {
    
    override fun toMap(): Map<String, Any?> = buildMap {
        algodUrl?.let { put("algodUrl", it) }
        algodToken?.let { put("algodToken", it) }
        privateKey?.let { put("privateKey", it) }
        appId?.let { put("appId", it) }
    }
    
    companion object {
        @JvmStatic
        fun fromMap(map: Map<String, Any?>): AlgorandOptions {
            return AlgorandOptions(
                algodUrl = map["algodUrl"] as? String,
                algodToken = map["algodToken"] as? String,
                privateKey = map["privateKey"] as? String,
                appId = map["appId"] as? String
            )
        }
    }
}

/**
 * Polygon blockchain anchor client options.
 * 
 * @param rpcUrl Optional RPC endpoint URL (defaults to network-specific endpoint)
 * @param privateKey Optional private key in hex format (required for real transactions)
 * @param contractAddress Optional contract address for contract interactions
 */
data class PolygonOptions(
    val rpcUrl: String? = null,
    val privateKey: String? = null,
    val contractAddress: String? = null
) : BlockchainAnchorClientOptions() {
    
    override fun toMap(): Map<String, Any?> = buildMap {
        rpcUrl?.let { put("rpcUrl", it) }
        privateKey?.let { put("privateKey", it) }
        contractAddress?.let { put("contractAddress", it) }
    }
    
    companion object {
        @JvmStatic
        fun fromMap(map: Map<String, Any?>): PolygonOptions {
            return PolygonOptions(
                rpcUrl = map["rpcUrl"] as? String,
                privateKey = map["privateKey"] as? String,
                contractAddress = map["contractAddress"] as? String
            )
        }
    }
}

/**
 * Ganache blockchain anchor client options.
 * 
 * @param rpcUrl Optional RPC endpoint URL (defaults to http://localhost:8545)
 * @param privateKey Private key in hex format (required)
 * @param contractAddress Optional contract address for contract interactions
 */
data class GanacheOptions(
    val rpcUrl: String? = null,
    val privateKey: String,
    val contractAddress: String? = null
) : BlockchainAnchorClientOptions() {
    
    override fun toMap(): Map<String, Any?> = buildMap {
        rpcUrl?.let { put("rpcUrl", it) }
        put("privateKey", privateKey)
        contractAddress?.let { put("contractAddress", it) }
    }
    
    companion object {
        @JvmStatic
        fun fromMap(map: Map<String, Any?>): GanacheOptions {
            val privateKey = map["privateKey"] as? String
                ?: throw IllegalArgumentException("privateKey is required for GanacheOptions")
            return GanacheOptions(
                rpcUrl = map["rpcUrl"] as? String,
                privateKey = privateKey,
                contractAddress = map["contractAddress"] as? String
            )
        }
    }
}

/**
 * Hyperledger Indy blockchain anchor client options.
 * 
 * @param poolEndpoint Optional pool endpoint URL (defaults to network-specific endpoint)
 * @param walletName Optional wallet name (required for real transactions)
 * @param walletKey Optional wallet key (required for real transactions)
 * @param did Optional DID for signing transactions (required for real transactions)
 */
data class IndyOptions(
    val poolEndpoint: String? = null,
    val walletName: String? = null,
    val walletKey: String? = null,
    val did: String? = null
) : BlockchainAnchorClientOptions() {
    
    override fun toMap(): Map<String, Any?> = buildMap {
        poolEndpoint?.let { put("poolEndpoint", it) }
        walletName?.let { put("walletName", it) }
        walletKey?.let { put("walletKey", it) }
        did?.let { put("did", it) }
    }
    
    companion object {
        @JvmStatic
        fun fromMap(map: Map<String, Any?>): IndyOptions {
            return IndyOptions(
                poolEndpoint = map["poolEndpoint"] as? String,
                walletName = map["walletName"] as? String,
                walletKey = map["walletKey"] as? String,
                did = map["did"] as? String
            )
        }
    }
}

