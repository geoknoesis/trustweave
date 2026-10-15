package org.trustweave.wallet.cloud

import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlobStorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Azure Blob Storage-backed wallet implementation.
 *
 * Stores credentials in Azure Blob Storage containers.
 *
 * **Example:**
 * ```kotlin
 * val blobServiceClient = BlobServiceClientBuilder()
 *     .connectionString(connectionString)
 *     .buildClient()
 *
 * val wallet = AzureBlobWallet(
 *     walletId = "wallet-1",
 *     walletDid = "did:key:wallet-1",
 *     holderDid = "did:key:holder",
 *     containerName = "my-wallet-container",
 *     basePath = "wallets/wallet-1",
 *     blobServiceClient = blobServiceClient
 * )
 * ```
 */
class AzureBlobWallet(
    walletId: String,
    walletDid: String,
    holderDid: String,
    private val containerName: String,
    basePath: String,
    private val blobServiceClient: BlobServiceClient,
    encryptionKey: ByteArray? = null,
) : CloudWallet(walletId, walletDid, holderDid, containerName, basePath, encryptionKey) {
    private val containerClient: BlobContainerClient = blobServiceClient.getBlobContainerClient(containerName)

    /**
     * Creates the container on first use, off the caller's thread (every operation runs on
     * [Dispatchers.IO]); the constructor performs no network I/O. A failed attempt is retried.
     */
    private val containerReady: Unit by lazy {
        if (!containerClient.exists()) {
            try {
                containerClient.create()
            } catch (e: BlobStorageException) {
                // Lost a creation race with another wallet; the container exists now.
                if (e.statusCode != 409) throw e
            }
        }
    }

    override suspend fun upload(
        key: String,
        data: ByteArray,
    ): Unit =
        withContext(Dispatchers.IO) {
            try {
                containerReady
                val blobClient: BlobClient = containerClient.getBlobClient(key)
                blobClient.upload(data.inputStream(), data.size.toLong(), true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException("Failed to upload to Azure Blob Storage: ${e.message}", e)
            }
        }

    override suspend fun download(key: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                containerReady
                val blobClient: BlobClient = containerClient.getBlobClient(key)
                val outputStream = java.io.ByteArrayOutputStream()
                blobClient.downloadStream(outputStream)
                outputStream.toByteArray()
            } catch (e: BlobStorageException) {
                // A missing blob (never written, or deleted since it was listed) is "absent", not a
                // failure; any other status propagates.
                if (e.statusCode == 404) {
                    null
                } else {
                    throw RuntimeException("Failed to download from Azure Blob Storage: ${e.message}", e)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException("Failed to download from Azure Blob Storage: ${e.message}", e)
            }
        }

    override suspend fun deleteFromStorage(key: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Delete directly and rely on the service's 404 instead of a racy
                // exists() pre-check (TOCTOU between exists() and delete()).
                containerReady
                val blobClient: BlobClient = containerClient.getBlobClient(key)
                blobClient.delete()
                true
            } catch (e: BlobStorageException) {
                if (e.statusCode == 404) {
                    // Missing blob means "nothing to delete" — not a storage failure.
                    false
                } else {
                    // Auth failures, networking errors, etc. must NOT be reported as
                    // "not found" — propagate them as storage errors.
                    throw RuntimeException("Failed to delete from Azure Blob Storage: ${e.message}", e)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException("Failed to delete from Azure Blob Storage: ${e.message}", e)
            }
        }

    override suspend fun listKeys(prefix: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                containerReady
                containerClient
                    .listBlobs(
                        com.azure.storage.blob.models
                            .ListBlobsOptions()
                            .setPrefix(prefix),
                        null,
                    ).map { it.name }
                    .toList()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException("Failed to list blobs from Azure Blob Storage: ${e.message}", e)
            }
        }
}
