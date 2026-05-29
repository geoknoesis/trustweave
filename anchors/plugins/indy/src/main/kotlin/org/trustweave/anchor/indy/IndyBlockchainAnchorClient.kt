package org.trustweave.anchor.indy

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.options.IndyOptions
import org.trustweave.core.exception.TrustWeaveException
import java.nio.charset.StandardCharsets

/**
 * Hyperledger Indy blockchain anchor client.
 *
 * Anchors payloads on an Indy ledger by writing ATTRIB transactions against a
 * submitter DID. The on-ledger `raw` field carries a small JSON object containing
 * the SHA-256 digest of the payload, the original media type and (where it fits) the
 * payload itself, so [readPayload] can return the original content via GET_ATTRIB
 * without an external store.
 *
 * Chain ID format: `indy:<network>:<pool-name>` (e.g. `indy:testnet:bcovrin`).
 *
 * Options (see [IndyOptions] for the type-safe variant):
 * | Key              | Required for writes | Description                                                  |
 * |------------------|---------------------|--------------------------------------------------------------|
 * | `poolEndpoint`   | yes                 | Base URL of an `indy-vdr-proxy` instance                    |
 * | `did`            | yes                 | Submitter DID (`identifier` field on the ATTRIB request)    |
 * | `signingKeySeed` | yes                 | Base58-encoded 32-byte Ed25519 seed for the submitter       |
 * | `targetDid`      | no (default = `did`)| DID the attribute is attached to (`operation.dest`)         |
 * | `walletName`     | optional            | Reserved for future wallet-backed signing                   |
 * | `walletKey`      | optional            | Reserved for future wallet-backed signing                   |
 *
 * Without `signingKeySeed` the client falls back to in-memory storage so unit tests
 * and example code can run without a live pool.
 */
class IndyBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap(),
    httpClient: HttpClient? = null
) : AbstractBlockchainAnchorClient(chainId, options) {

    constructor(chainId: String, options: IndyOptions) : this(chainId, options.toMap(), null)

    companion object {
        const val SOVRIN_MAINNET = "indy:mainnet:sovrin"
        const val SOVRIN_STAGING = "indy:testnet:sovrin-staging"
        const val BCOVRIN_TESTNET = "indy:testnet:bcovrin"

        private const val DEFAULT_SOVRIN_MAINNET_POOL = "https://sovrin-mainnet.pool.sovrin.org"
        private const val DEFAULT_SOVRIN_STAGING_POOL = "https://sovrin-staging.pool.sovrin.org"
        private const val DEFAULT_BCOVRIN_TESTNET_POOL = "https://test.bcovrin.vonx.io"

        /**
         * Payload size threshold above which we store only the digest on-ledger and
         * keep the full payload in [storage]. The wire-format ATTRIB `raw` field can in
         * theory hold up to 64 KiB but most pools enforce stricter limits.
         */
        internal const val MAX_INLINE_PAYLOAD_BYTES = 32 * 1024
    }

    private val poolEndpoint: String
    private val submitterDid: String?
    private val targetDid: String?
    private val signer: IndySigner?
    private val transport: IndyVdrProxyTransport
    private val ownedHttpClient: HttpClient?

    init {
        require(chainId.startsWith("indy:")) {
            "Invalid chain ID for Indy: $chainId. Expected format: indy:<network>:<pool-name>"
        }
        val parts = chainId.split(":")
        require(parts.size >= 3) {
            "Invalid Indy chain ID format: $chainId. Expected: indy:<network>:<pool-name>"
        }

        poolEndpoint = (options["poolEndpoint"] as? String)?.takeIf { it.isNotBlank() } ?: when (chainId) {
            SOVRIN_MAINNET -> DEFAULT_SOVRIN_MAINNET_POOL
            SOVRIN_STAGING -> DEFAULT_SOVRIN_STAGING_POOL
            BCOVRIN_TESTNET -> DEFAULT_BCOVRIN_TESTNET_POOL
            else -> throw BlockchainException.ConfigurationFailed(
                chainId = chainId,
                configKey = "poolEndpoint",
                reason = "Unknown Indy pool: $chainId. Provide 'poolEndpoint' in options."
            )
        }

        submitterDid = (options["did"] as? String)?.takeIf { it.isNotBlank() }
        targetDid = (options["targetDid"] as? String)?.takeIf { it.isNotBlank() } ?: submitterDid

        val seedB58 = (options["signingKeySeed"] as? String)?.takeIf { it.isNotBlank() }
        val signingKeyB58 = (options["signingKey"] as? String)?.takeIf { it.isNotBlank() }
        signer = when {
            seedB58 != null -> IndySigner.fromBase58Seed(seedB58)
            signingKeyB58 != null -> IndySigner.fromBase58SigningKey(signingKeyB58)
            else -> null
        }

        val (client, owned) = when {
            httpClient != null -> httpClient to null
            else -> {
                val created = HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = IndyVdrProxyTransport.DEFAULT_TIMEOUT_MILLIS
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = IndyVdrProxyTransport.DEFAULT_TIMEOUT_MILLIS
                    }
                }
                created to created
            }
        }
        ownedHttpClient = owned
        transport = IndyVdrProxyTransport(poolEndpoint, client)
    }

    override protected fun canSubmitTransaction(): Boolean {
        return submitterDid != null && signer != null
    }

    override protected suspend fun submitTransactionToBlockchain(
        payloadBytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val submitter = submitterDid
            ?: throw BlockchainException.ConfigurationFailed(
                chainId = chainId,
                configKey = "did",
                reason = "Submitter DID is required to write ATTRIB transactions"
            )
        val keypair = signer
            ?: throw BlockchainException.ConfigurationFailed(
                chainId = chainId,
                configKey = "signingKeySeed",
                reason = "Signing key seed is required to write ATTRIB transactions"
            )
        val dest = targetDid ?: submitter

        val rawObject = buildRawObject(payloadBytes)
        val unsigned = IndyRequestCodec.buildAttribRequest(
            submitterDid = submitter,
            targetDid = dest,
            rawPayload = rawObject
        )
        val signingPayload = IndyRequestCodec.signingPayload(unsigned)
        val signatureBase58 = keypair.signBase58(signingPayload)
        val signed = IndyRequestCodec.attachSignature(unsigned, signatureBase58)

        val reply = try {
            transport.submit(signed)
        } catch (e: BlockchainException) {
            throw e
        } catch (t: Throwable) {
            throw BlockchainException.ConnectionFailed(
                chainId = chainId,
                endpoint = poolEndpoint,
                reason = "Failed to submit ATTRIB to Indy pool: ${t.message}"
            ).apply { initCause(t) }
        }

        IndyRequestCodec.parseWriteReply(reply)
    }

    override protected suspend fun readTransactionFromBlockchain(
        txHash: String
    ): AnchorResult = withContext(Dispatchers.IO) {
        val submitter = submitterDid
            ?: throw TrustWeaveException.NotFound(
                resource = "Anchor $txHash on $chainId (no submitter DID configured for reads)"
            )
        val dest = targetDid ?: submitter

        val getRequest = IndyRequestCodec.buildGetAttribRequest(
            submitterDid = submitter,
            targetDid = dest
        )
        val reply = try {
            transport.submit(getRequest)
        } catch (e: BlockchainException) {
            throw e
        } catch (t: Throwable) {
            throw BlockchainException.ConnectionFailed(
                chainId = chainId,
                endpoint = poolEndpoint,
                reason = "Failed to query GET_ATTRIB from Indy pool: ${t.message}"
            ).apply { initCause(t) }
        }

        val parsed = IndyRequestCodec.parseGetAttribResponse(reply)
        val raw = parsed.raw
            ?: throw TrustWeaveException.NotFound(
                resource = "Anchor $txHash on $chainId (ATTRIB has no raw payload)"
            )

        val payload = decodePayloadFromRaw(raw)
        val mediaType = (raw[IndyAttribFields.MEDIA_TYPE] as? JsonPrimitive)
            ?.contentOrNull ?: "application/json"

        AnchorResult(
            ref = buildAnchorRef(
                txHash = txHash,
                contract = null,
                extra = buildExtraMetadata(mediaType) + buildMap {
                    parsed.seqNo?.let { put("seqNo", it.toString()) }
                    parsed.txnTime?.let { put("txnTime", it.toString()) }
                }
            ),
            payload = payload,
            mediaType = mediaType,
            timestamp = parsed.txnTime
        )
    }

    override protected fun buildExtraMetadata(mediaType: String): Map<String, String> {
        val parts = chainId.split(":")
        return mapOf(
            "network" to if (parts.size >= 2) parts[1] else "unknown",
            "pool" to if (parts.size >= 3) parts[2] else "unknown",
            "mediaType" to mediaType
        )
    }

    override protected fun generateTestTxHash(): String {
        return "indy_test_${System.currentTimeMillis()}_${(0..1_000_000).random()}"
    }

    override protected fun getBlockchainName(): String = "Indy"

    /**
     * Build the JSON object stored under `operation.raw`. For payloads under
     * [MAX_INLINE_PAYLOAD_BYTES] the original JSON is included verbatim so reads
     * round-trip; for larger payloads only the digest and media type are stored and
     * the caller must keep the original off-ledger.
     */
    private fun buildRawObject(payloadBytes: ByteArray): JsonObject {
        val digestHex = IndyRequestCodec.sha256Hex(payloadBytes)
        val payloadString = payloadBytes.toString(StandardCharsets.UTF_8)
        return buildJsonObject {
            put(IndyAttribFields.DIGEST, JsonPrimitive(digestHex))
            put(IndyAttribFields.MEDIA_TYPE, JsonPrimitive("application/json"))
            if (payloadBytes.size <= MAX_INLINE_PAYLOAD_BYTES) {
                val parsed = try {
                    inlineJson.parseToJsonElement(payloadString)
                } catch (t: Throwable) {
                    JsonPrimitive(payloadString)
                }
                put(IndyAttribFields.PAYLOAD, parsed)
            }
        }
    }

    private fun decodePayloadFromRaw(raw: JsonObject): JsonElement {
        val payloadField = raw[IndyAttribFields.PAYLOAD]
        if (payloadField != null && payloadField !is JsonNull) return payloadField

        val digest = (raw[IndyAttribFields.DIGEST] as? JsonPrimitive)?.contentOrNull
        return buildJsonObject {
            digest?.let { put(IndyAttribFields.DIGEST, JsonPrimitive(it)) }
        }
    }

    private val inlineJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Close the owned HTTP client, if any. Safe to call multiple times. */
    fun close() {
        ownedHttpClient?.close()
    }
}
