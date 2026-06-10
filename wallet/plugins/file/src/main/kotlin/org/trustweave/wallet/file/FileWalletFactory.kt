package org.trustweave.wallet.file

import org.trustweave.wallet.services.WalletFactory
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.Wallet
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

/**
 * Encrypted file-based wallet factory implementation.
 *
 * Supports local file storage with encryption for wallet data.
 * Suitable for desktop and mobile applications.
 *
 * The optional `encryptionKey` property must be a Base64-encoded AES key that decodes
 * to 16, 24, or 32 bytes (AES-128/192/256); invalid keys are rejected at creation.
 * Credentials are encrypted with AES-GCM (see [FileWallet] for the on-disk format).
 * When the key is omitted, credentials are stored in plaintext and a warning is logged.
 *
 * **Example:**
 * ```kotlin
 * val factory = FileWalletFactory()
 * val wallet = factory.create(
 *     providerName = "file",
 *     holderDid = "did:key:holder",
 *     options = WalletCreationOptions(
 *         storagePath = "/path/to/wallet/data",
 *         additionalProperties = mapOf(
 *             "encryptionKey" to "base64-encoded-16/24/32-byte-key"
 *         )
 *     )
 * )
 * ```
 */
class FileWalletFactory : WalletFactory {

    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Wallet {
        if (providerName.lowercase() != "file") {
            throw IllegalArgumentException("Provider name must be 'file'")
        }

        val finalWalletId = walletId ?: UUID.randomUUID().toString()
        val finalWalletDid = walletDid ?: "did:key:wallet-$finalWalletId"
        val finalHolderDid = holderDid
            ?: throw IllegalArgumentException("holderDid is required for FileWallet")

        val storagePath = options.storagePath
            ?: throw IllegalArgumentException("storagePath is required for FileWallet")

        val encryptionKey = options.additionalProperties["encryptionKey"] as? String
        val walletDir = Paths.get(storagePath, finalWalletId)

        // Create wallet directory if it doesn't exist
        if (!Files.exists(walletDir)) {
            Files.createDirectories(walletDir)
        }

        return FileWallet(
            walletId = finalWalletId,
            walletDid = finalWalletDid,
            holderDid = finalHolderDid,
            walletDir = walletDir,
            encryptionKey = encryptionKey
        )
    }
}

