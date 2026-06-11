package org.trustweave.anchor.cardano

import com.bloxbean.cardano.client.account.Account
import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent
import com.bloxbean.cardano.client.common.model.Network
import com.bloxbean.cardano.client.common.model.Networks
import com.bloxbean.cardano.client.function.helper.SignerProviders
import com.bloxbean.cardano.client.metadata.Metadata
import com.bloxbean.cardano.client.metadata.MetadataBuilder
import com.bloxbean.cardano.client.metadata.MetadataList
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder
import com.bloxbean.cardano.client.quicktx.Tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Cardano blockchain anchor client backed by **Blockfrost** + **bloxbean cardano-client-lib**.
 *
 * Anchors the payload in transaction metadata under [CardanoAnchorConfig.metadataLabel]
 * following the **CIP-20** transaction-message convention:
 *
 * ```cbor
 * { 674: { "msg": [ "chunk-1 (<=64 bytes)", "chunk-2", ... ] } }
 * ```
 *
 * Payloads are JSON-serialised then split into 64-byte UTF-8 chunks to fit Cardano's
 * per-string metadata limit. Total metadata is also bounded (≤ 16 KiB serialised CBOR).
 *
 * The **read path** queries Blockfrost (`GET /txs/{hash}/metadata`) via the
 * [com.bloxbean.cardano.client.backend.api.MetadataService] and reassembles chunks.
 *
 * @see CardanoAnchorConfig for configuration options
 * @see <a href="https://cips.cardano.org/cips/cip20/">CIP-20</a>
 */
class CardanoBlockchainAnchorClient internal constructor(
    chainId: String,
    options: Map<String, Any?>,
    private val config: CardanoAnchorConfig,
    backendOverride: BackendService? = null,
    httpClientOverride: OkHttpClient? = null,
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {

    constructor(chainId: String, config: CardanoAnchorConfig) :
        this(chainId, config.toMap(), config, null, null)

    constructor(chainId: String, options: Map<String, Any?> = emptyMap()) :
        this(chainId, options, CardanoAnchorConfig.fromMap(chainId, options), null, null)

    private val backend: BackendService by lazy {
        backendOverride ?: BFBackendService(config.blockfrostBaseUrl() + "/", config.blockfrostProjectId)
    }

    private val httpClient: OkHttpClient by lazy {
        httpClientOverride ?: OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val account: Account? by lazy { buildAccount() }

    init {
        require(chainId.startsWith("cardano:")) { "Invalid chain ID for Cardano: $chainId" }
        val network = CardanoNetwork.fromChainId(chainId)
            ?: throw IllegalArgumentException("Unsupported Cardano network: $chainId")
        require(network == config.network) {
            "chainId=$chainId does not match config.network=${config.network}"
        }
    }

    override fun canSubmitTransaction(): Boolean = config.canSubmit()

    override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val acct = account
                ?: throw BlockchainException.ConfigurationFailed(
                    chainId = chainId,
                    configKey = CardanoAnchorConfig.KEY_MNEMONIC,
                    reason = "No submitter mnemonic or secret key configured",
                )
            if (payloadBytes.size > MAX_PAYLOAD_BYTES) {
                throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    operation = "submitTransaction",
                    payloadSize = payloadBytes.size.toLong(),
                    reason = "Payload ${payloadBytes.size}B exceeds Cardano metadata budget ($MAX_PAYLOAD_BYTES B). " +
                        "Anchor a digest instead.",
                )
            }

            val metadata = buildCip20Metadata(payloadBytes, config.metadataLabel)
            val sender = acct.baseAddress()
            val tx = Tx()
                .from(sender)
                .payToAddress(sender, com.bloxbean.cardano.client.api.model.Amount.lovelace(BigInteger.valueOf(MIN_SELF_PAY)))
                .attachMetadata(metadata)

            val txBuilder = QuickTxBuilder(backend)
            val txContext = txBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(acct))
                .feePayer(sender)

            val result = txContext.completeAndWait(
                java.time.Duration.ofSeconds(config.confirmationTimeoutSeconds),
            )

            if (!result.isSuccessful) {
                throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    operation = "submitTransaction",
                    payloadSize = payloadBytes.size.toLong(),
                    reason = "Blockfrost rejected transaction: ${result.response ?: "(no response)"}",
                )
            }
            result.value
                ?: throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    operation = "submitTransaction",
                    payloadSize = payloadBytes.size.toLong(),
                    reason = "Blockfrost returned success with no txHash",
                )
        }

    override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult =
        withContext(Dispatchers.IO) {
            val result = try {
                backend.metadataService.getJSONMetadataByTxnHash(txHash)
            } catch (e: com.bloxbean.cardano.client.api.exception.ApiException) {
                if (isNotFound(e)) {
                    throw TrustWeaveException.NotFound(resource = "Cardano transaction $txHash")
                }
                throw BlockchainException.ConnectionFailed(
                    chainId = chainId,
                    endpoint = config.blockfrostBaseUrl(),
                    reason = "Blockfrost metadata fetch failed: ${e.message}",
                )
            }

            if (!result.isSuccessful) {
                if (result.code() == 404) {
                    throw TrustWeaveException.NotFound(resource = "Cardano transaction $txHash")
                }
                throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    txHash = txHash,
                    operation = "readTransaction",
                    reason = "Blockfrost returned ${result.code()}: ${result.response}",
                )
            }

            val contents: List<MetadataJSONContent> = result.value ?: emptyList()
            if (contents.isEmpty()) {
                throw TrustWeaveException.NotFound(resource = "Metadata on Cardano transaction $txHash")
            }

            val payload = decodeCip20Payload(contents, config.metadataLabel)
                ?: throw TrustWeaveException.NotFound(
                    resource = "Cardano metadata label ${config.metadataLabel} on tx $txHash",
                )

            AnchorResult(
                ref = buildAnchorRef(
                    txHash = txHash,
                    contract = null,
                    extra = buildExtraMetadata("application/json"),
                ),
                payload = payload,
                mediaType = "application/json",
                timestamp = System.currentTimeMillis() / 1000,
            )
        }

    override fun getContractAddress(): String? = null

    override fun buildExtraMetadata(mediaType: String): Map<String, String> = mapOf(
        "network" to chainId.substringAfter(":"),
        "mediaType" to mediaType,
        "protocol" to "cip-20",
        "label" to config.metadataLabel.toString(),
    )

    override fun generateTestTxHash(): String = "cardano_test_${uniqueTestHashSuffix()}"

    override fun getBlockchainName(): String = "Cardano"

    override fun close() {
        // BackendService has no close() in 0.5.x; okhttp dispatcher pool shuts down on its own.
        try {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        } catch (_: Exception) {
            // best-effort
        }
    }

    private fun buildAccount(): Account? {
        val network = config.network.toBloxbeanNetwork()
        config.submitterMnemonic?.takeIf { it.isNotBlank() }?.let { mnemonic ->
            return Account(network, mnemonic)
        }
        config.submitterSecretKey?.takeIf { it.isNotBlank() }?.let { skHex ->
            val skBytes = hexToBytes(skHex)
            return Account(network, skBytes)
        }
        return null
    }

    private fun isNotFound(e: com.bloxbean.cardano.client.api.exception.ApiException): Boolean {
        val msg = e.message ?: return false
        return msg.contains("404") || msg.contains("Not Found", ignoreCase = true)
    }

    companion object {
        const val MAINNET: String = "cardano:mainnet"
        const val PREVIEW: String = "cardano:preview"
        const val PREPROD: String = "cardano:preprod"

        // Cardano protocol caps transaction metadata at ~16 KiB; reserve some headroom for CBOR framing.
        internal const val MAX_PAYLOAD_BYTES: Int = 14 * 1024

        // CIP-20 string chunk size — Cardano metadata strings are limited to 64 bytes UTF-8.
        internal const val CHUNK_BYTES: Int = 64

        private const val MIN_SELF_PAY: Long = 1_000_000L // 1 ADA, above Babbage min-utxo

        /**
         * Split [bytes] (UTF-8 of a JSON payload) into CIP-20 chunks and build a [Metadata].
         */
        internal fun buildCip20Metadata(bytes: ByteArray, label: Long): Metadata {
            val text = String(bytes, StandardCharsets.UTF_8)
            val chunks = chunkUtf8(text, CHUNK_BYTES)
            val list: MetadataList = MetadataBuilder.createList()
            for (chunk in chunks) list.add(chunk)
            val msgMap = MetadataBuilder.createMap().put("msg", list)
            return MetadataBuilder.createMetadata().put(BigInteger.valueOf(label), msgMap)
        }

        /**
         * Split a string into ≤[maxBytes]-UTF-8-byte chunks without splitting code points.
         */
        internal fun chunkUtf8(text: String, maxBytes: Int): List<String> {
            require(maxBytes > 0) { "maxBytes must be positive" }
            if (text.isEmpty()) return listOf("")
            val result = mutableListOf<String>()
            val sb = StringBuilder()
            var byteCount = 0
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val cpChars = Character.charCount(cp)
                val cpBytes = String(Character.toChars(cp)).toByteArray(StandardCharsets.UTF_8).size
                if (byteCount + cpBytes > maxBytes && byteCount > 0) {
                    result.add(sb.toString())
                    sb.setLength(0)
                    byteCount = 0
                }
                sb.appendCodePoint(cp)
                byteCount += cpBytes
                i += cpChars
            }
            if (sb.isNotEmpty()) result.add(sb.toString())
            return result
        }

        /**
         * Reassemble a CIP-20 payload from Blockfrost's [MetadataJSONContent] list.
         *
         * Blockfrost returns one entry per metadata label; each entry's [MetadataJSONContent.getJsonMetadata]
         * is the *value* under that label (so for CIP-20 it is `{"msg": ["..."]}`).
         */
        internal fun decodeCip20Payload(
            contents: List<MetadataJSONContent>,
            label: Long,
        ): JsonElement? {
            val labelStr = label.toString()
            val match = contents.firstOrNull { it.label == labelStr } ?: return null
            val jsonNode = match.jsonMetadata ?: return null
            val msgNode = jsonNode.get("msg") ?: return null
            val joined = when {
                msgNode.isArray -> msgNode.joinToString("") { it.asText() }
                msgNode.isTextual -> msgNode.asText()
                else -> msgNode.toString()
            }
            return runCatching { Json.parseToJsonElement(joined) }
                .getOrElse { JsonPrimitive(joined) }
        }

        internal fun hexToBytes(hex: String): ByteArray {
            val clean = hex.removePrefix("0x").trim()
            require(clean.length % 2 == 0) { "hex string has odd length" }
            return ByteArray(clean.length / 2) { i ->
                val hi = Character.digit(clean[2 * i], 16)
                val lo = Character.digit(clean[2 * i + 1], 16)
                require(hi >= 0 && lo >= 0) { "invalid hex character" }
                ((hi shl 4) or lo).toByte()
            }
        }

        private fun CardanoNetwork.toBloxbeanNetwork(): Network = when (this) {
            CardanoNetwork.Mainnet -> Networks.mainnet()
            CardanoNetwork.Preview -> Networks.preview()
            CardanoNetwork.Preprod -> Networks.preprod()
        }
    }

    // Reserved for richer fallback metadata embedding; kept off the public path to avoid dead-code warnings.
    @Suppress("unused")
    private fun fallbackJsonPayload(text: String): JsonElement = buildJsonObject {
        put("raw", JsonPrimitive(text))
    }
}
