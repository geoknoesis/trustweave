package com.trustweave.wallet.services

/**
 * Strongly-typed configuration used when creating wallets through [WalletFactory].
 *
 * Keeps well-known settings explicit while still allowing provider specific
 * options through [additionalProperties].
 */
data class WalletCreationOptions(
    val label: String? = null,
    val storagePath: String? = null,
    val encryptionKey: String? = null,
    val enableOrganization: Boolean = false,
    val enablePresentation: Boolean = false,
    val additionalProperties: Map<String, Any?> = emptyMap()
) {
    /**
    * Converts the structured options into a legacy map for providers that still
    * expect key/value pairs.
    */
    fun toLegacyMap(): Map<String, Any?> = buildMap {
        label?.let { put("label", it) }
        storagePath?.let { put("storagePath", it) }
        encryptionKey?.let { put("encryptionKey", it) }
        if (enableOrganization) put("enableOrganization", true)
        if (enablePresentation) put("enablePresentation", true)
        putAll(additionalProperties)
    }
}

/**
 * Fluent builder used by DSLs to configure [WalletCreationOptions].
 */
class WalletCreationOptionsBuilder {
    var label: String? = null
    var storagePath: String? = null
    var encryptionKey: String? = null
    var enableOrganization: Boolean = false
    var enablePresentation: Boolean = false

    private val customProperties = mutableMapOf<String, Any?>()

    fun property(key: String, value: Any?) {
        customProperties[key] = value
    }

    fun build(): WalletCreationOptions = WalletCreationOptions(
        label = label,
        storagePath = storagePath,
        encryptionKey = encryptionKey,
        enableOrganization = enableOrganization,
        enablePresentation = enablePresentation,
        additionalProperties = customProperties.toMap()
    )
}

fun walletCreationOptions(block: WalletCreationOptionsBuilder.() -> Unit): WalletCreationOptions {
    val builder = WalletCreationOptionsBuilder()
    builder.block()
    return builder.build()
}

