package org.trustweave.wallet.cloud

import org.trustweave.wallet.services.WalletFactory
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.Wallet
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
 */
class CloudWalletFactory : WalletFactory {

    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Wallet {
        if (providerName.lowercase() != "cloud") {
            throw IllegalArgumentException("Provider name must be 'cloud'")
        }

        val finalWalletId = walletId ?: UUID.randomUUID().toString()
        val finalWalletDid = walletDid ?: "did:key:wallet-$finalWalletId"
        val finalHolderDid = holderDid
            ?: throw IllegalArgumentException("holderDid is required for CloudWallet")

        val storagePath = options.storagePath
            ?: throw IllegalArgumentException("storagePath is required for CloudWallet")

        val provider = options.additionalProperties["provider"] as? String
            ?: detectProviderFromPath(storagePath)
            ?: throw IllegalArgumentException("Cloud storage provider must be specified (aws, azure, or gcs)")

        // Extract bucket/container name and path from storagePath
        val (bucketName, basePath) = when (provider) {
            "aws" -> {
                val s3Path = storagePath.removePrefix("s3://")
                val parts = s3Path.split("/", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
            "azure" -> {
                // Format: https://account.blob.core.windows.net/container/path
                val uri = java.net.URI(storagePath)
                val pathParts = uri.path.removePrefix("/").split("/", limit = 2)
                pathParts[0] to (pathParts.getOrNull(1) ?: "")
            }
            "gcs" -> {
                val gsPath = storagePath.removePrefix("gs://")
                val parts = gsPath.split("/", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
            else -> throw IllegalArgumentException("Unsupported cloud provider: $provider")
        }

        // Create provider-specific wallet implementation
        return when (provider.lowercase()) {
            "aws" -> {
                val region = options.additionalProperties["region"] as? String ?: "us-east-1"
                val accessKeyId = options.additionalProperties["accessKeyId"] as? String
                val secretAccessKey = options.additionalProperties["secretAccessKey"] as? String

                val s3Client = if (accessKeyId != null && secretAccessKey != null) {
                    software.amazon.awssdk.services.s3.S3Client.builder()
                        .region(software.amazon.awssdk.regions.Region.of(region))
                        .credentialsProvider(
                            software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                                    accessKeyId, secretAccessKey
                                )
                            )
                        )
                        .build()
                } else {
                    // Use default credentials provider (environment variables, IAM role, etc.)
                    software.amazon.awssdk.services.s3.S3Client.builder()
                        .region(software.amazon.awssdk.regions.Region.of(region))
                        .build()
                }

                AwsS3Wallet(
                    walletId = finalWalletId,
                    walletDid = finalWalletDid,
                    holderDid = finalHolderDid,
                    bucketName = bucketName,
                    basePath = basePath,
                    s3Client = s3Client
                )
            }
            "azure" -> {
                val connectionString = options.additionalProperties["connectionString"] as? String
                val accountName = options.additionalProperties["accountName"] as? String
                val accountKey = options.additionalProperties["accountKey"] as? String

                val blobServiceClient = when {
                    connectionString != null -> {
                        com.azure.storage.blob.BlobServiceClientBuilder()
                            .connectionString(connectionString)
                            .buildClient()
                    }
                    accountName != null && accountKey != null -> {
                        val endpoint = "https://$accountName.blob.core.windows.net"
                        com.azure.storage.blob.BlobServiceClientBuilder()
                            .endpoint(endpoint)
                            .credential(com.azure.storage.common.StorageSharedKeyCredential(accountName, accountKey))
                            .buildClient()
                    }
                    else -> {
                        throw IllegalArgumentException(
                            "Azure Blob Storage requires either 'connectionString' or 'accountName' + 'accountKey'"
                        )
                    }
                }

                AzureBlobWallet(
                    walletId = finalWalletId,
                    walletDid = finalWalletDid,
                    holderDid = finalHolderDid,
                    containerName = bucketName,
                    basePath = basePath,
                    blobServiceClient = blobServiceClient
                )
            }
            "gcs" -> {
                val projectId = options.additionalProperties["projectId"] as? String
                val credentialsPath = options.additionalProperties["credentialsPath"] as? String

                val storage = when {
                    credentialsPath != null -> {
                        com.google.cloud.storage.StorageOptions.newBuilder()
                            .setProjectId(projectId)
                            .setCredentials(
                                com.google.auth.oauth2.GoogleCredentials.fromStream(
                                    java.io.FileInputStream(credentialsPath)
                                )
                            )
                            .build()
                            .service
                    }
                    projectId != null -> {
                        com.google.cloud.storage.StorageOptions.newBuilder()
                            .setProjectId(projectId)
                            .build()
                            .service
                    }
                    else -> {
                        // Use default credentials (environment variable GOOGLE_APPLICATION_CREDENTIALS)
                        com.google.cloud.storage.StorageOptions.getDefaultInstance().service
                    }
                }

                GoogleCloudStorageWallet(
                    walletId = finalWalletId,
                    walletDid = finalWalletDid,
                    holderDid = finalHolderDid,
                    bucketName = bucketName,
                    basePath = basePath,
                    storage = storage
                )
            }
            else -> throw IllegalArgumentException("Unsupported cloud provider: $provider")
        }
    }

    private fun detectProviderFromPath(path: String): String? {
        return when {
            path.startsWith("s3://") -> "aws"
            path.startsWith("https://") && path.contains(".blob.core.windows.net") -> "azure"
            path.startsWith("gs://") -> "gcs"
            else -> null
        }
    }
}

