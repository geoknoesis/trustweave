package org.trustweave.anchor.options

/**
 * Single-method parser contract for converting a raw map into typed [BlockchainAnchorClientOptions].
 *
 * Implement this interface (or use a lambda via SAM conversion) to add support for a new
 * blockchain type without modifying [BlockchainAnchorClientOptions]:
 *
 * ```kotlin
 * val myChainParser = BlockchainAnchorOptionsParser { chainId, map ->
 *     if (chainId.startsWith("mychain:")) MyChainOptions.fromMap(map) else null
 * }
 * BlockchainAnchorClientOptions.registerParser(myChainParser)
 * ```
 *
 * Return `null` if this parser does not handle the given [chainId].
 */
fun interface BlockchainAnchorOptionsParser {
    fun parse(chainId: String, map: Map<String, Any?>): BlockchainAnchorClientOptions?
}

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
 * **Extensibility**: New blockchain types can be supported without modifying this class by
 * calling [BlockchainAnchorClientOptions.registerParser] with a [BlockchainAnchorOptionsParser].
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
 *
 * // Extend for a new chain without modifying this class
 * BlockchainAnchorClientOptions.registerParser { chainId, map ->
 *     if (chainId.startsWith("mychain:")) MyChainOptions.fromMap(map) else null
 * }
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
        private val parsers: MutableList<BlockchainAnchorOptionsParser> = mutableListOf(
            // Built-in parsers for the known blockchain types shipped with the SDK
            BlockchainAnchorOptionsParser { chainId, map ->
                if (chainId.startsWith("algorand:")) AlgorandOptions.fromMap(map) else null
            },
            BlockchainAnchorOptionsParser { chainId, map ->
                if (chainId.startsWith("eip155:")) {
                    val chainNum = chainId.substringAfter(":").toIntOrNull()
                    when (chainNum) {
                        1337 -> GanacheOptions.fromMap(map)
                        else -> PolygonOptions.fromMap(map)
                    }
                } else null
            },
            BlockchainAnchorOptionsParser { chainId, map ->
                if (chainId.startsWith("indy:")) IndyOptions.fromMap(map) else null
            }
        )

        /**
         * Registers an additional [BlockchainAnchorOptionsParser].
         *
         * Registered parsers are tried in registration order after the built-in parsers.
         * This is the extension point that keeps [fromMap] closed for modification
         * (Open/Closed Principle).
         */
        @JvmStatic
        fun registerParser(parser: BlockchainAnchorOptionsParser) {
            parsers.add(parser)
        }

        /**
         * Creates options from a map by delegating to registered parsers.
         *
         * @throws IllegalArgumentException if no registered parser handles [chainId]
         */
        @JvmStatic
        fun fromMap(chainId: String, map: Map<String, Any?>): BlockchainAnchorClientOptions {
            return parsers.firstNotNullOfOrNull { it.parse(chainId, map) }
                ?: throw IllegalArgumentException(
                    "No parser registered for chain ID '$chainId'. " +
                    "Register one via BlockchainAnchorClientOptions.registerParser(...)."
                )
        }
    }
}

/**
 * Algorand blockchain anchor client options.
 *
 * @param algodUrl Optional Algod API URL (defaults to network-specific endpoint)
 * @param algodToken Optional Algod API token (defaults to empty string)
 * @param privateKey Optional private key in base64 format (required for real transactions).
 *   Accepts either the 32-byte Ed25519 seed or the 64-byte exported secret key
 *   (seed || public key, e.g. JS SDK `account.sk` / `algokey` output)
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

