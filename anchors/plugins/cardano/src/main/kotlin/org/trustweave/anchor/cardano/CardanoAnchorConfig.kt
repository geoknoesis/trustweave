package org.trustweave.anchor.cardano

/**
 * Cardano network selector.
 *
 * The CAIP-2 chain-id mapping is:
 * - [Mainnet] -> `cardano:mainnet`
 * - [Preview] -> `cardano:preview`
 * - [Preprod] -> `cardano:preprod`
 *
 * The Blockfrost project id must be issued for the corresponding network — the prefix
 * (`mainnet...`, `preview...`, `preprod...`) is validated by Blockfrost, not by this client.
 */
enum class CardanoNetwork(val chainId: String, val blockfrostBaseUrl: String) {
    Mainnet("cardano:mainnet", "https://cardano-mainnet.blockfrost.io/api/v0"),
    Preview("cardano:preview", "https://cardano-preview.blockfrost.io/api/v0"),
    Preprod("cardano:preprod", "https://cardano-preprod.blockfrost.io/api/v0"),
    ;

    companion object {
        fun fromChainId(chainId: String): CardanoNetwork? = values().firstOrNull { it.chainId == chainId }
    }
}

/**
 * Type-safe configuration for [CardanoBlockchainAnchorClient].
 *
 * Either [submitterMnemonic] (BIP-39 word list, recommended for testing) or [submitterSecretKey]
 * (raw 64-byte CIP-1852 extended secret key, base16 encoded) must be supplied for the **write**
 * path; the **read** path only needs [blockfrostProjectId].
 *
 * [metadataLabel] defaults to **674** — the CIP-20 transaction-message label. Set to any
 * other unsigned long if you need a private metadata label.
 *
 * @property blockfrostProjectId Blockfrost project id (`mainnet...`, `preview...`, `preprod...`)
 * @property network Cardano network the project id is valid for
 * @property submitterMnemonic BIP-39 mnemonic for the submitter wallet (15 or 24 words)
 * @property submitterSecretKey Hex-encoded extended secret key (alternative to mnemonic)
 * @property metadataLabel Transaction metadata label (default 674 = CIP-20)
 * @property blockfrostBaseUrlOverride Override the default Blockfrost base URL (mock servers, IPFS gateway, …)
 * @property confirmationTimeoutSeconds Max seconds to wait for transaction confirmation (default 90)
 */
data class CardanoAnchorConfig(
    val blockfrostProjectId: String,
    val network: CardanoNetwork = CardanoNetwork.Preview,
    val submitterMnemonic: String? = null,
    val submitterSecretKey: String? = null,
    val metadataLabel: Long = CIP20_MESSAGE_LABEL,
    val blockfrostBaseUrlOverride: String? = null,
    val confirmationTimeoutSeconds: Long = 90,
) {

    init {
        require(blockfrostProjectId.isNotBlank()) { "blockfrostProjectId must not be blank" }
        require(metadataLabel in 0..MAX_METADATA_LABEL) {
            "metadataLabel out of range [0, $MAX_METADATA_LABEL]: $metadataLabel"
        }
        require(confirmationTimeoutSeconds > 0) { "confirmationTimeoutSeconds must be positive" }
    }

    /** Returns true if this config can submit transactions (has a submitter key). */
    fun canSubmit(): Boolean = !submitterMnemonic.isNullOrBlank() || !submitterSecretKey.isNullOrBlank()

    /** Returns the Blockfrost base URL — override if set, otherwise the network default. */
    fun blockfrostBaseUrl(): String = blockfrostBaseUrlOverride ?: network.blockfrostBaseUrl

    fun toMap(): Map<String, Any?> = mapOf(
        KEY_PROJECT_ID to blockfrostProjectId,
        KEY_NETWORK to network.name,
        KEY_MNEMONIC to submitterMnemonic,
        KEY_SECRET_KEY to submitterSecretKey,
        KEY_LABEL to metadataLabel,
        KEY_BASE_URL to blockfrostBaseUrlOverride,
        KEY_CONFIRMATION_TIMEOUT to confirmationTimeoutSeconds,
    )

    companion object {
        /** CIP-20 transaction-message metadata label. */
        const val CIP20_MESSAGE_LABEL: Long = 674L

        /** CBOR unsigned-integer limit for metadata labels (per Cardano spec). */
        const val MAX_METADATA_LABEL: Long = 0xFFFFFFFFFL // 2^64 - 1 clamped to a safe long

        // Map keys for legacy options Map<String, Any?> compatibility with [BlockchainAnchorClientProvider].
        const val KEY_PROJECT_ID = "blockfrostProjectId"
        const val KEY_NETWORK = "network"
        const val KEY_MNEMONIC = "submitterMnemonic"
        const val KEY_SECRET_KEY = "submitterSecretKey"
        const val KEY_LABEL = "metadataLabel"
        const val KEY_BASE_URL = "blockfrostBaseUrl"
        const val KEY_CONFIRMATION_TIMEOUT = "confirmationTimeoutSeconds"

        fun fromMap(chainId: String, map: Map<String, Any?>): CardanoAnchorConfig {
            val network = CardanoNetwork.fromChainId(chainId)
                ?: (map[KEY_NETWORK] as? String)?.let { runCatching { CardanoNetwork.valueOf(it) }.getOrNull() }
                ?: throw IllegalArgumentException(
                    "Cannot derive Cardano network from chainId=$chainId or options[$KEY_NETWORK]",
                )
            return CardanoAnchorConfig(
                blockfrostProjectId = (map[KEY_PROJECT_ID] as? String).orEmpty(),
                network = network,
                submitterMnemonic = map[KEY_MNEMONIC] as? String,
                submitterSecretKey = map[KEY_SECRET_KEY] as? String,
                metadataLabel = (map[KEY_LABEL] as? Number)?.toLong() ?: CIP20_MESSAGE_LABEL,
                blockfrostBaseUrlOverride = map[KEY_BASE_URL] as? String,
                confirmationTimeoutSeconds = (map[KEY_CONFIRMATION_TIMEOUT] as? Number)?.toLong() ?: 90L,
            )
        }
    }
}
