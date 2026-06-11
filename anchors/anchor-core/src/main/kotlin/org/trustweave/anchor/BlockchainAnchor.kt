package org.trustweave.anchor

import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets

/**
 * Reference to a blockchain anchor (CAIP-2-style chain identifier + transaction reference).
 *
 * @param chainId The chain identifier (e.g., "algorand:mainnet", "eip155:137")
 * @param txHash The transaction hash or operation identifier
 * @param contract Optional registry contract address or app ID
 * @param extra Additional metadata as key-value pairs
 */
data class AnchorRef(
    val chainId: String,
    val txHash: String,
    val contract: String? = null,
    val extra: Map<String, String> = emptyMap()
)

/**
 * Result of anchoring a payload to a blockchain.
 *
 * @param ref The anchor reference pointing to the anchored data
 * @param payload The JSON payload that was anchored
 * @param mediaType The media type of the payload (default: "application/json")
 * @param timestamp Optional timestamp (epoch seconds) when the anchor was created
 * @param fee Actual fee paid for this anchoring operation. `null` for legacy
 *   plugins that do not yet surface fee information; payment-aware plugins
 *   must populate this so the Trusted Domain Manager can settle the
 *   reservation against the treasury ledger.
 * @param payerAddress On-chain address (or chain-native account identifier)
 *   that paid the [fee]. `null` for legacy / unmanaged paths.
 */
data class AnchorResult(
    val ref: AnchorRef,
    val payload: JsonElement,
    val mediaType: String = "application/json",
    val timestamp: Long? = null,
    val fee: TokenAmount? = null,
    val payerAddress: String? = null,
)

/**
 * Interface for blockchain anchoring operations.
 * This interface is chain-agnostic; specific blockchain implementations
 * will be provided in separate adapter modules.
 */
interface BlockchainAnchorClient {

    /**
     * Writes a payload to the blockchain and returns an anchor reference.
     *
     * Legacy entry point — kept for binary compatibility. Routes through
     * [writePayload] with [PaymentContext.unmanaged]; the client falls back
     * to its embedded account credentials and does not consult any treasury.
     *
     * @param payload The JSON payload to anchor
     * @param mediaType The media type of the payload
     * @return An AnchorResult containing the reference and payload
     */
    suspend fun writePayload(
        payload: JsonElement,
        mediaType: String = "application/json"
    ): AnchorResult

    /**
     * Payment-aware write. Plugins that opt into the payment plane override
     * this; the default delegates to the legacy [writePayload] so existing
     * implementations keep compiling.
     *
     * Implementations honouring [ctx] must:
     * - resolve their signing key from [ctx] (via the treasury), never from
     *   their own embedded credentials
     * - abort if the estimated fee exceeds [PaymentContext.maxFee]
     * - populate [AnchorResult.fee] and [AnchorResult.payerAddress] on success
     *
     * @param payload   the JSON payload to anchor
     * @param ctx       payment context — who pays, on what chain, with what
     *                  strategy and what caller-side cap
     * @param mediaType the media type of the payload
     */
    suspend fun writePayload(
        payload: JsonElement,
        ctx: PaymentContext,
        mediaType: String = "application/json",
    ): AnchorResult = writePayload(payload, mediaType)

    /**
     * Estimate the native-token fee that an operation would cost on this
     * chain. Plugins compute this from the chain's native API
     * (`eth_feeHistory`, Algorand suggested params, Cardano protocol params,
     * Bitcoin `estimatesmartfee`, …). The default returns
     * [TokenAmount.unknown] for plugins that have not yet implemented the
     * payment plane.
     */
    suspend fun estimate(op: OperationDescriptor): TokenAmount =
        TokenAmount.unknown(op.chainId)

    /**
     * Reads a payload from the blockchain using an anchor reference.
     *
     * @param ref The anchor reference
     * @return An AnchorResult containing the payload and metadata
     * @throws TrustWeaveException.NotFound if the anchor reference does not exist
     */
    suspend fun readPayload(ref: AnchorRef): AnchorResult

    /**
     * Verifies that [payload] is the payload anchored at [ref], enabling third-party
     * verification of an anchor without trusting the presenter.
     *
     * The on-chain data for [ref] is read and compared:
     * - **Digest anchors** (an [AnchorDigest] envelope on-chain — see
     *   [AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE]): the SHA-256 digest is
     *   recomputed over the UTF-8 bytes of [payload] exactly as serialized by the
     *   write path (`Json.encodeToString(JsonElement.serializer(), payload)` — no
     *   canonicalization) and compared against the anchored digest. A structurally
     *   equal payload with a different key order serializes to different bytes and
     *   does NOT verify.
     * - **Full anchors**: the anchored JSON is compared structurally
     *   (JsonElement equality, not string equality), so key order is irrelevant.
     *
     * Returns `false` — never throws — when the anchor does not exist or the chain
     * read fails for any reason ([CancellationException] is rethrown so coroutine
     * cancellation is preserved); a `false` result therefore means
     * "not verifiable", not necessarily "tampered".
     *
     * @param payload the off-chain payload to verify, exactly as originally anchored
     * @param ref the anchor reference to verify against
     * @return `true` iff the on-chain data attests to [payload]
     */
    suspend fun verifyAnchor(payload: JsonElement, ref: AnchorRef): Boolean {
        val onChain = try {
            readPayload(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        val anchored = onChain.payload
        return if (AnchorDigest.isEnvelope(anchored)) {
            val payloadBytes = Json.encodeToString(JsonElement.serializer(), payload)
                .toByteArray(StandardCharsets.UTF_8)
            AnchorDigest.matches(anchored.jsonObject, payloadBytes)
        } else {
            anchored == payload
        }
    }
}
