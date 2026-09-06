package org.trustweave.wallet.file

import org.trustweave.wallet.Wallet
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.services.WalletFactory
import org.trustweave.wallet.services.validateDeployment
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
class FileWalletFactory(
    private val statusResolver: org.trustweave.wallet.WalletStatusResolver? = null,
) : WalletFactory {
    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions,
    ): Wallet {
        options.validateDeployment("wallet:plugins:file")
        if (providerName.lowercase() != "file") {
            throw IllegalArgumentException("Provider name must be 'file'")
        }

        val finalWalletId = walletId ?: UUID.randomUUID().toString()
        require(finalWalletId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
            "walletId must be a safe single path component (letters, digits, underscore or hyphen)"
        }
        val finalWalletDid = walletDid ?: "did:key:wallet-$finalWalletId"
        val finalHolderDid =
            holderDid
                ?: throw IllegalArgumentException("holderDid is required for FileWallet")

        val storagePath =
            options.storagePath
                ?: throw IllegalArgumentException("storagePath is required for FileWallet")

        val legacyKey = options.additionalProperties["encryptionKey"]
        require(legacyKey == null || legacyKey is String) { "encryptionKey must be a string" }
        require(options.encryptionKey == null || legacyKey == null || options.encryptionKey == legacyKey) {
            "Conflicting typed and legacy encryption keys"
        }
        val encryptionKey = options.encryptionKey ?: legacyKey as? String
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
            encryptionKey = encryptionKey,
            statusResolver = statusResolver,
        )
    }
}
