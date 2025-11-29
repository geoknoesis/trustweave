package com.trustweave.wallet

/**
 * Type-safe wallet type representation.
 *
 * Replaces the WalletProvider sealed class with clearer naming.
 *
 * **Example:**
 * ```kotlin
 * val wallet = trustweave.wallets.create(
 *     holderDid = "did:key:holder",
 *     type = WalletType.InMemory
 * )
 * ```
 */
sealed class WalletType(val id: String) {
    /**
     * In-memory wallet (for testing).
     */
    object InMemory : WalletType("inMemory")

    /**
     * File-based wallet.
     */
    object File : WalletType("file")

    /**
     * Database-backed wallet.
     */
    object Database : WalletType("database")

    /**
     * Cloud storage wallet (AWS S3, Azure Blob, Google Cloud Storage).
     */
    object Cloud : WalletType("cloud")

    /**
     * Custom wallet type.
     */
    data class Custom(val customId: String) : WalletType(customId)

    override fun toString(): String = id
}

object WalletTypes {
    val InMemory = WalletType.InMemory
    val File = WalletType.File
    val Database = WalletType.Database
    val Cloud = WalletType.Cloud

    fun fromString(id: String): WalletType = when (id) {
        "inMemory" -> WalletType.InMemory
        "file" -> WalletType.File
        "database" -> WalletType.Database
        "cloud" -> WalletType.Cloud
        else -> WalletType.Custom(id)
    }
}

