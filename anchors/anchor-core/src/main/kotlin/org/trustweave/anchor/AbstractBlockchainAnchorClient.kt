package org.trustweave.anchor

import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException as CoreTrustWeaveException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Abstract base class for blockchain anchor client implementations.
 *
 * Provides common functionality shared across blockchain adapters:
 * - Strictly opt-in in-memory test fallback (see [OPTION_IN_MEMORY_TEST_MODE])
 * - Common error handling patterns
 * - AnchorRef construction helpers
 * - Transaction hash generation utilities
 *
 * **Fail-closed behavior**: when the client has no usable chain credentials/connection
 * ([canSubmitTransaction] returns false) and [inMemoryTestMode] is not enabled,
 * [writePayload] throws [BlockchainException.ConfigurationFailed] instead of fabricating
 * a transaction hash. The in-memory fallback is ONLY active when the caller explicitly
 * opts in via `options["inMemoryTestMode"] = true` — it is intended for tests and demos,
 * never production: payloads are NOT anchored on any chain.
 *
 * Subclasses should implement:
 * - [canSubmitTransaction]: Check if credentials are available for real transactions
 * - [submitTransactionToBlockchain]: Submit transaction to actual blockchain
 * - [readTransactionFromBlockchain]: Read transaction from actual blockchain
 * - [getContractAddress]: Get contract address from options (if applicable)
 * - [buildExtraMetadata]: Build extra metadata for AnchorRef
 * - [generateTestTxHash]: Generate test transaction hash for in-memory test mode
 */
abstract class AbstractBlockchainAnchorClient(
    protected val chainId: String,
    protected val options: Map<String, Any?>
) : BlockchainAnchorClient {

    companion object {
        /**
         * Options key that explicitly enables the in-memory test fallback.
         * Accepts `true` (Boolean) or `"true"` (String). Defaults to disabled.
         *
         * Also used as the key in [AnchorRef.extra] marking references produced by the
         * in-memory fallback, so fabricated references are distinguishable from real ones.
         */
        const val OPTION_IN_MEMORY_TEST_MODE: String = "inMemoryTestMode"

        /**
         * Options key bounding how long write operations wait for on-chain confirmation
         * before failing (milliseconds). Accepts a [Number] or a numeric [String].
         */
        const val OPTION_CONFIRMATION_TIMEOUT_MS: String = "confirmationTimeoutMs"

        /**
         * Options key controlling the confirmation poll interval (milliseconds).
         * Accepts a [Number] or a numeric [String].
         */
        const val OPTION_CONFIRMATION_POLL_INTERVAL_MS: String = "confirmationPollIntervalMs"

        /** Default confirmation wait timeout: 30 seconds. */
        const val DEFAULT_CONFIRMATION_TIMEOUT_MS: Long = 30_000L

        /** Default confirmation poll interval: 1 second. */
        const val DEFAULT_CONFIRMATION_POLL_INTERVAL_MS: Long = 1_000L

        /**
         * Process-wide monotonic counter mixed into fabricated test transaction hashes so
         * two test anchors created in the same process can never collide (a purely random
         * small-range component could).
         */
        private val TEST_TX_HASH_COUNTER = AtomicLong()
    }

    /**
     * Whether the in-memory test fallback is enabled. Strictly opt-in (defaults to false).
     * When false and no chain credentials/connection are configured, write/read operations
     * fail with [BlockchainException.ConfigurationFailed] instead of silently fabricating
     * transaction hashes.
     */
    protected val inMemoryTestMode: Boolean =
        options[OPTION_IN_MEMORY_TEST_MODE] == true ||
            (options[OPTION_IN_MEMORY_TEST_MODE] as? String)?.toBoolean() == true

    /**
     * How long write operations wait for on-chain confirmation before failing,
     * in milliseconds (see [OPTION_CONFIRMATION_TIMEOUT_MS]).
     */
    protected val confirmationTimeoutMs: Long =
        longOption(OPTION_CONFIRMATION_TIMEOUT_MS, DEFAULT_CONFIRMATION_TIMEOUT_MS)

    /**
     * Interval between confirmation polls, in milliseconds
     * (see [OPTION_CONFIRMATION_POLL_INTERVAL_MS]).
     */
    protected val confirmationPollIntervalMs: Long =
        longOption(OPTION_CONFIRMATION_POLL_INTERVAL_MS, DEFAULT_CONFIRMATION_POLL_INTERVAL_MS)

    private fun longOption(key: String, default: Long): Long =
        when (val value = options[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: default
            else -> default
        }

    /**
     * In-memory storage for the opt-in test mode (see [OPTION_IN_MEMORY_TEST_MODE]).
     * Used for testing without requiring actual blockchain credentials.
     */
    protected val storage = ConcurrentHashMap<String, AnchorResult>()

    /**
     * Checks if this client can submit transactions to the blockchain.
     * Returns true if credentials/account are configured, false otherwise.
     */
    protected abstract fun canSubmitTransaction(): Boolean

    /**
     * Submits a transaction to the blockchain and returns the transaction hash.
     *
     * @param payloadBytes The payload data as bytes
     * @return The transaction hash
     * @throws TrustWeaveException if submission fails
     */
    protected abstract suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String

    /**
     * Reads a transaction from the blockchain.
     *
     * @param txHash The transaction hash
     * @return The AnchorResult containing the payload
     * @throws TrustWeaveException.NotFound if transaction is not found
     * @throws TrustWeaveException if reading fails
     */
    protected abstract suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult

    /**
     * Gets the contract address from options (if applicable).
     * Returns null if no contract is configured.
     */
    protected open fun getContractAddress(): String? {
        return options["contractAddress"] as? String
            ?: options["appId"] as? String
    }

    /**
     * Builds extra metadata for AnchorRef.
     * Subclasses can override to add chain-specific metadata.
     *
     * @param mediaType The media type of the payload
     * @return Map of extra metadata
     */
    protected open fun buildExtraMetadata(mediaType: String): Map<String, String> {
        return mapOf("mediaType" to mediaType)
    }

    /**
     * Generates a test transaction hash for the opt-in in-memory test mode.
     * Should be unique and identifiable as a test hash. Implementations should build
     * on [uniqueTestHashSuffix] or [uniqueTestHashHex] so fabricated hashes never collide.
     */
    protected abstract fun generateTestTxHash(): String

    /**
     * Returns a process-unique suffix for fabricated test transaction hashes:
     * a monotonically increasing counter plus a random UUID component. The counter
     * guarantees collision-freedom within the process; the UUID across processes.
     */
    protected fun uniqueTestHashSuffix(): String =
        "${TEST_TX_HASH_COUNTER.incrementAndGet()}_${UUID.randomUUID().toString().replace("-", "")}"

    /**
     * Returns a process-unique fixed-length lowercase hex string (default 64 chars,
     * the width of an EVM transaction hash). The leading 16 hex chars encode the
     * process-wide counter, so two fabricated hashes can never collide; the remainder
     * is random UUID entropy.
     *
     * @param length Desired hex length; must be at least 16 so the counter is preserved.
     */
    protected fun uniqueTestHashHex(length: Int = 64): String {
        require(length >= 16) { "length must be >= 16 to preserve the uniqueness counter" }
        val counterHex = TEST_TX_HASH_COUNTER.incrementAndGet().toString(16).padStart(16, '0')
        val entropy = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "")
        return (counterHex + entropy).take(length).padEnd(length, '0')
    }

    /**
     * Gets the blockchain name for error messages.
     */
    protected abstract fun getBlockchainName(): String

    override suspend fun writePayload(
        payload: JsonElement,
        mediaType: String
    ): AnchorResult = withContext(Dispatchers.IO) {
        val payloadJson = Json.encodeToString(JsonElement.serializer(), payload)
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)

        try {
            when {
                canSubmitTransaction() -> {
                    val txHash = submitTransactionToBlockchain(payloadBytes)
                    AnchorResult(
                        ref = buildAnchorRef(
                            txHash = txHash,
                            contract = getContractAddress(),
                            extra = buildExtraMetadata(mediaType)
                        ),
                        payload = payload,
                        mediaType = mediaType,
                        timestamp = System.currentTimeMillis() / 1000
                    )
                }
                inMemoryTestMode -> {
                    // Opt-in in-memory storage for testing without credentials.
                    // References are marked so they are never mistaken for real anchors.
                    val hash = generateTestTxHash()
                    val result = AnchorResult(
                        ref = buildAnchorRef(
                            txHash = hash,
                            contract = getContractAddress(),
                            extra = buildExtraMetadata(mediaType) +
                                (OPTION_IN_MEMORY_TEST_MODE to "true")
                        ),
                        payload = payload,
                        mediaType = mediaType,
                        timestamp = System.currentTimeMillis() / 1000
                    )
                    storage[hash] = result
                    result
                }
                else -> throw BlockchainException.ConfigurationFailed(
                    chainId = chainId,
                    reason = "No usable ${getBlockchainName()} credentials/connection configured " +
                        "for $chainId; refusing to fabricate an anchor. Provide chain credentials " +
                        "in options, or explicitly opt in to the in-memory test fallback with " +
                        "'$OPTION_IN_MEMORY_TEST_MODE' = true (tests/demos only — payloads are " +
                        "NOT anchored on-chain)."
                )
            }
        } catch (e: CoreTrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                operation = "writePayload",
                payloadSize = payloadBytes.size.toLong(),
                reason = "Failed to anchor payload to ${getBlockchainName()}: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    override suspend fun readPayload(ref: AnchorRef): AnchorResult = withContext(Dispatchers.IO) {
        validateChainId(ref.chainId)

        // Read from the blockchain; the in-memory storage is consulted ONLY when the
        // opt-in test mode is enabled, so chain errors are never masked in production.
        try {
            readTransactionFromBlockchain(ref.txHash)
        } catch (e: Exception) {
            val fromTestStorage = if (inMemoryTestMode) storage[ref.txHash] else null
            fromTestStorage ?: when (e) {
                is CoreTrustWeaveException -> throw e
                else -> throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    txHash = ref.txHash,
                    operation = "readPayload",
                    reason = "Failed to read payload from ${getBlockchainName()}: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        }
    }

    /**
     * Validates that the chain ID matches this client's chain ID.
     */
    protected fun validateChainId(refChainId: String) {
        if (refChainId != chainId) {
            throw IllegalArgumentException("Chain ID mismatch: expected $chainId, got $refChainId")
        }
    }

    /**
     * Builds an AnchorRef with common fields.
     */
    protected fun buildAnchorRef(
        txHash: String,
        contract: String? = null,
        extra: Map<String, String> = emptyMap()
    ): AnchorRef {
        return AnchorRef(
            chainId = chainId,
            txHash = txHash,
            contract = contract,
            extra = extra
        )
    }
}

