package org.trustweave.anchor.starknet

import org.trustweave.anchor.*
import org.trustweave.anchor.exceptions.BlockchainException

/**
 * **STUB — NOT IMPLEMENTED.** Skeleton for a StarkNet blockchain anchor client.
 *
 * No real chain interaction exists: [canSubmitTransaction] always returns `false`, so
 * the [AbstractBlockchainAnchorClient] fail-closed path governs — [writePayload] fails
 * with a configuration error unless the caller explicitly opts into the in-memory test
 * mode (`options["inMemoryTestMode"] = true`), and nothing is ever anchored on StarkNet
 * regardless of which credentials are supplied. [readPayload] likewise cannot read from
 * the chain.
 *
 * This class is intentionally NOT registered for ServiceLoader discovery (no
 * `META-INF/services` entry), so it never silently masquerades as a working anchor
 * client. It can only be instantiated explicitly.
 *
 * A real implementation would require a StarkNet SDK and a Cairo storage contract
 * (StarkNet is Cairo-based, not EVM), neither of which exists here.
 *
 * Chain ID format: "starknet:<network>"
 * Examples:
 * - "starknet:mainnet" (StarkNet mainnet)
 * - "starknet:testnet" (StarkNet testnet)
 *
 * **Example:**
 * ```kotlin
 * val client = StarkNetBlockchainAnchorClient(
 *     chainId = "starknet:mainnet",
 *     options = mapOf(
 *         "rpcUrl" to "https://starknet-mainnet.public.blastapi.io",
 *         "privateKey" to "0x...",
 *         "contractAddress" to "0x..."
 *     )
 * )
 * ```
 */
class StarkNetBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {

    companion object {
        const val MAINNET = "starknet:mainnet"
        const val TESTNET = "starknet:testnet"

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://starknet-mainnet.public.blastapi.io"
        private const val TESTNET_RPC_URL = "https://starknet-testnet.public.blastapi.io"
    }

    init {
        require(chainId.startsWith("starknet:")) {
            "Invalid chain ID for StarkNet: $chainId"
        }
        val network = chainId.substringAfter("starknet:")
        require(network == "mainnet" || network == "testnet") {
            "Unsupported StarkNet network: $network. Use 'mainnet' or 'testnet'"
        }
    }

    override protected fun canSubmitTransaction(): Boolean {
        // Always false: transaction submission is NOT implemented. Returning true based on
        // the presence of credentials would route writePayload into submitTransactionToBlockchain
        // and fail after pretending to be a working client. With false, the base class's
        // fail-closed / opt-in in-memory test-mode path governs instead.
        return false
    }

    override protected suspend fun submitTransactionToBlockchain(
        payloadBytes: ByteArray
    ): String {
        // Unreachable through the base class while canSubmitTransaction() is false;
        // kept as an honest guard in case a subclass or future change reaches it.
        throw BlockchainException.UnsupportedOperation(
            chainId = chainId,
            operation = "submitTransaction",
            reason = "The StarkNet anchor client is a stub and is not implemented: " +
                "transaction submission would require a StarkNet SDK and a Cairo storage " +
                "contract, neither of which exists."
        )
    }

    override protected suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult {
        throw BlockchainException.UnsupportedOperation(
            chainId = chainId,
            operation = "readTransaction",
            reason = "The StarkNet anchor client is a stub and is not implemented: " +
                "reading transactions would require a StarkNet SDK, which does not exist."
        )
    }

    override protected fun getContractAddress(): String? {
        return options["contractAddress"] as? String
    }

    override protected fun buildExtraMetadata(mediaType: String): Map<String, String> {
        val network = when (chainId) {
            MAINNET -> "starknet-mainnet"
            TESTNET -> "starknet-testnet"
            else -> chainId
        }
        return mapOf(
            "network" to network,
            "mediaType" to mediaType,
            "protocol" to "cairo"
        )
    }

    override protected fun generateTestTxHash(): String {
        // Generate a unique test transaction hash (64 hex characters)
        return "0x${uniqueTestHashHex()}"
    }

    override protected fun getBlockchainName(): String {
        return "StarkNet"
    }

    override fun close() {
        // Cleanup if needed
    }
}

