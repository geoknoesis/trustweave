package org.trustweave.wallet.cloud

import org.trustweave.wallet.Wallet
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.services.WalletFactory
import org.trustweave.wallet.services.validateDeployment
import java.util.UUID

/**
 * Cloud storage-backed wallet factory implementation.
 *
 * Supports AWS S3, Azure Blob Storage, and Google Cloud Storage.
 * Enables multi-device wallet synchronization.
 *
 * **Example:**
 * ```kotlin
 * val factory = CloudWalletFactory()
 *
 * // AWS S3
 * val s3Wallet = factory.create(
 *     providerName = "cloud",
 *     holderDid = "did:key:holder",
 *     options = WalletCreationOptions(
 *         storagePath = "s3://my-bucket/wallets",
 *         additionalProperties = mapOf(
 *             "provider" to "aws",
 *             "region" to "us-east-1"
 *         )
 *     )
 * )
 *
 * // Azure Blob Storage
 * val azureWallet = factory.create(
 *     providerName = "cloud",
 *     holderDid = "did:key:holder",
 *     options = WalletCreationOptions(
 *         storagePath = "https://account.blob.core.windows.net/container",
 *         additionalProperties = mapOf(
 *             "provider" to "azure",
 *             "accountName" to "account",
 *             "accountKey" to "key"
 *         )
 *     )
 * )
 *
 * // Google Cloud Storage
 * val gcsWallet = factory.create(
 *     providerName = "cloud",
 *     holderDid = "did:key:holder",
 *     options = WalletCreationOptions(
 *         storagePath = "gs://my-bucket/wallets",
 *         additionalProperties = mapOf(
 *             "provider" to "gcs",
 *             "projectId" to "my-project"
 *         )
 *     )
 * )
 * ```
 *
 * **Object layout:** every wallet gets its own key prefix, `<storagePath>/wallet-<percent-encoded walletId>/`,
 * so wallets configured with the same `storagePath` never see each other's credentials. Slashes in
 * `storagePath` are normalised (empty and `.`/`..` segments are rejected) and an empty bucket or container is
 * refused.
 *
 * **Migration:** wallets created by earlier versions stored objects directly under `<storagePath>/credentials/`,
 * `/collections/`, `/metadata/` and `/tags/`, shared by every wallet using that path. Those objects are not
 * visible to the new layout, because the factory cannot tell which wallet owned them. Either copy them under
 * `<storagePath>/wallet-<walletId>/`, or, for the single wallet that owns the path, pass
 * `additionalProperties = mapOf("legacyLayout" to true)` to keep using the old prefix.
 *
 * **Encryption:** `WalletCreationOptions.encryptionKey` is rejected; objects are written as plain JSON.
 */
class CloudWalletFactory : WalletFactory {
    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions,
    ): Wallet {
        options.validateDeployment("wallet:plugins:cloud")
        if (providerName.lowercase() != "cloud") {
            throw IllegalArgumentException("Provider name must be 'cloud'")
        }

        require(options.encryptionKey == null && options.additionalProperties["encryptionKey"] == null) {
            "CloudWalletFactory cannot honour encryptionKey: cloud wallets store credentials as plain JSON and rely on " +
                "the storage provider's own at-rest encryption (SSE / Azure Storage encryption / CMEK). " +
                "Remove encryptionKey or use the 'file' wallet provider."
        }

        val finalWalletId = walletId ?: UUID.randomUUID().toString()
        val finalWalletDid = walletDid ?: "did:key:wallet-$finalWalletId"
        val finalHolderDid =
            holderDid
                ?: throw IllegalArgumentException("holderDid is required for CloudWallet")

        val storagePath =
            options.storagePath
                ?: throw IllegalArgumentException("storagePath is required for CloudWallet")

        require(storagePath.isNotBlank()) { "storagePath must not be empty" }

        val provider =
            (options.additionalProperties["provider"] as? String)?.lowercase()
                ?: detectProviderFromPath(storagePath)
                ?: throw IllegalArgumentException("Cloud storage provider must be specified (aws, azure, or gcs)")

        val legacyLayout = options.additionalProperties["legacyLayout"].let { it == true || it == "true" }
        val (bucketName, basePath) = resolveCloudLocation(provider.lowercase(), storagePath, finalWalletId, legacyLayout)

        // Create provider-specific wallet implementation
        return when (provider) {
            "aws" -> {
                val region = options.additionalProperties["region"] as? String ?: "us-east-1"
                val accessKeyId = options.additionalProperties["accessKeyId"] as? String
                val secretAccessKey = options.additionalProperties["secretAccessKey"] as? String

                val s3Client =
                    if (accessKeyId != null && secretAccessKey != null) {
                        software.amazon.awssdk.services.s3.S3Client
                            .builder()
                            .region(
                                software.amazon.awssdk.regions.Region
                                    .of(region),
                            ).credentialsProvider(
                                software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                                        accessKeyId,
                                        secretAccessKey,
                                    ),
                                ),
                            ).build()
                    } else {
                        // Use default credentials provider (environment variables, IAM role, etc.)
                        software.amazon.awssdk.services.s3.S3Client
                            .builder()
                            .region(
                                software.amazon.awssdk.regions.Region
                                    .of(region),
                            ).build()
                    }

                AwsS3Wallet(
                    walletId = finalWalletId,
                    walletDid = finalWalletDid,
                    holderDid = finalHolderDid,
                    bucketName = bucketName,
                    basePath = basePath,
                    s3Client = s3Client,
                )
            }
            "azure" -> {
                val connectionString = options.additionalProperties["connectionString"] as? String
                val accountName = options.additionalProperties["accountName"] as? String
                val accountKey = options.additionalProperties["accountKey"] as? String

                val blobServiceClient =
                    when {
                        connectionString != null -> {
                            com.azure.storage.blob
                                .BlobServiceClientBuilder()
                                .connectionString(connectionString)
                                .buildClient()
                        }
                        accountName != null && accountKey != null -> {
                            val endpoint = "https://$accountName.blob.core.windows.net"
                            com.azure.storage.blob
                                .BlobServiceClientBuilder()
                                .endpoint(endpoint)
                                .credential(
                                    com.azure.storage.common
                                        .StorageSharedKeyCredential(accountName, accountKey),
                                ).buildClient()
                        }
                        else -> {
                            throw IllegalArgumentException(
                                "Azure Blob Storage requires either 'connectionString' or 'accountName' + 'accountKey'",
                            )
                        }
                    }

                AzureBlobWallet(
                    walletId = finalWalletId,
                    walletDid = finalWalletDid,
                    holderDid = finalHolderDid,
                    containerName = bucketName,
                    basePath = basePath,
                    blobServiceClient = blobServiceClient,
                )
            }
            "gcs" -> {
                val projectId = options.additionalProperties["projectId"] as? String
                val credentialsPath = options.additionalProperties["credentialsPath"] as? String

                val storage =
                    when {
                        credentialsPath != null -> {
                            com.google.cloud.storage.StorageOptions
                                .newBuilder()
                                .setProjectId(projectId)
                                .setCredentials(
                                    com.google.auth.oauth2.GoogleCredentials.fromStream(
                                        java.io.FileInputStream(credentialsPath),
                                    ),
                                ).build()
                                .service
                        }
                        projectId != null -> {
                            com.google.cloud.storage.StorageOptions
                                .newBuilder()
                                .setProjectId(projectId)
                                .build()
                                .service
                        }
                        else -> {
                            // Use default credentials (environment variable GOOGLE_APPLICATION_CREDENTIALS)
                            com.google.cloud.storage.StorageOptions
                                .getDefaultInstance()
                                .service
                        }
                    }

                GoogleCloudStorageWallet(
                    walletId = finalWalletId,
                    walletDid = finalWalletDid,
                    holderDid = finalHolderDid,
                    bucketName = bucketName,
                    basePath = basePath,
                    storage = storage,
                )
            }
            else -> throw IllegalArgumentException("Unsupported cloud provider: $provider")
        }
    }

    private fun detectProviderFromPath(path: String): String? =
        when {
            path.startsWith("s3://") -> "aws"
            path.startsWith("https://") && path.contains(".blob.core.windows.net") -> "azure"
            path.startsWith("gs://") -> "gcs"
            else -> null
        }
}

internal data class CloudLocation(
    val bucket: String,
    val basePath: String,
)

/**
 * Splits [storagePath] into bucket/container and a normalised base path and appends the per-wallet
 * segment (unless [legacyLayout]). Throws [IllegalArgumentException] for an empty bucket or `.`/`..` segments.
 */
internal fun resolveCloudLocation(
    provider: String,
    storagePath: String,
    walletId: String,
    legacyLayout: Boolean,
): CloudLocation {
    require(storagePath.isNotBlank()) { "storagePath must not be empty" }
    val rawPath =
        when (provider) {
            "aws" -> storagePath.removePrefix("s3://")
            "gcs" -> storagePath.removePrefix("gs://")
            // Format: https://account.blob.core.windows.net/container/path
            "azure" ->
                java.net
                    .URI(storagePath)
                    .path
                    .orEmpty()
            else -> throw IllegalArgumentException("Unsupported cloud provider: $provider")
        }
    require(provider == "azure" || !rawPath.startsWith("/")) { "storagePath must name a bucket or container" }
    val segments = rawPath.split('/').filter { it.isNotEmpty() }
    require(segments.none { it == "." || it == ".." }) { "storagePath must not contain '.' or '..' segments" }
    require(segments.isNotEmpty()) { "storagePath must name a bucket or container" }
    val bucket = segments.first()
    val base = segments.drop(1)
    val own = if (legacyLayout) base else base + "wallet-${encodeCloudKeySegment(walletId)}"
    return CloudLocation(bucket, own.joinToString("/"))
}
