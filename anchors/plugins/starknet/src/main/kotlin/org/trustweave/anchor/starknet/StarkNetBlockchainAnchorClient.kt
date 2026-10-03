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
 * (StarkNet is Cairo-based, not EVM), neither of which exists here. It ships no default RPC
 * endpoints (the previously referenced public testnet has been retired); a real implementation
 * must take the endpoint from configuration.
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
 *         "rpcUrl" to "https://<your-starknet-rpc>",
 *         "privateKey" to "0x...",
 *         "contractAddress" to "0x..."
 *     )
 * )
 * ```
 */
class StarkNetBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap(),
) : AbstractBlockchainAnchorClient(chainId, options),
    java.io.Closeable {
    companion object {
        const val MAINNET = "starknet:mainnet"
        const val TESTNET = "starknet:testnet"
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

    protected override fun canSubmitTransaction(): Boolean {
        // Always false: transaction submission is NOT implemented. Returning true based on
        // the presence of credentials would route writePayload into submitTransactionToBlockchain
        // and fail after pretending to be a working client. With false, the base class's
        // fail-closed / opt-in in-memory test-mode path governs instead.
        return false
    }

    protected override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String {
        // Unreachable through the base class while canSubmitTransaction() is false;
        // kept as an honest guard in case a subclass or future change reaches it.
        throw BlockchainException.UnsupportedOperation(
            chainId = chainId,
            operation = "submitTransaction",
            reason =
                "The StarkNet anchor client is a stub and is not implemented: " +
                    "transaction submission would require a StarkNet SDK and a Cairo storage " +
                    "contract, neither of which exists.",
        )
    }

    protected override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult =
        throw BlockchainException.UnsupportedOperation(
            chainId = chainId,
            operation = "readTransaction",
            reason =
                "The StarkNet anchor client is a stub and is not implemented: " +
                    "reading transactions would require a StarkNet SDK, which does not exist.",
        )

    protected override fun getContractAddress(): String? = options["contractAddress"] as? String

    protected override fun buildExtraMetadata(mediaType: String): Map<String, String> {
        val network =
            when (chainId) {
                MAINNET -> "starknet-mainnet"
                TESTNET -> "starknet-testnet"
                else -> chainId
            }
        return mapOf(
            "network" to network,
            "mediaType" to mediaType,
            "protocol" to "cairo",
        )
    }

    protected override fun generateTestTxHash(): String {
        // Generate a unique test transaction hash (64 hex characters)
        return "0x${uniqueTestHashHex()}"
    }

    protected override fun getBlockchainName(): String = "StarkNet"

    override fun close() {
        // Cleanup if needed
    }
}
