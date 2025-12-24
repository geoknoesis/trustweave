package org.trustweave.wallet.cloud

import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.BlobItem
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
    private val blobServiceClient: BlobServiceClient
) : CloudWallet(walletId, walletDid, holderDid, containerName, basePath) {

    private val containerClient: BlobContainerClient = blobServiceClient.getBlobContainerClient(containerName)

    init {
        // Ensure container exists
        if (!containerClient.exists()) {
            containerClient.create()
        }
    }

    override suspend fun upload(key: String, data: ByteArray): Unit = withContext(Dispatchers.IO) {
        try {
            val blobClient: BlobClient = containerClient.getBlobClient(key)
            blobClient.upload(data.inputStream(), data.size.toLong(), true)
        } catch (e: Exception) {
            throw RuntimeException("Failed to upload to Azure Blob Storage: ${e.message}", e)
        }
    }

    override suspend fun download(key: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val blobClient: BlobClient = containerClient.getBlobClient(key)
            if (!blobClient.exists()) {
                return@withContext null
            }

            val outputStream = java.io.ByteArrayOutputStream()
            blobClient.downloadStream(outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            throw RuntimeException("Failed to download from Azure Blob Storage: ${e.message}", e)
        }
    }

    override suspend fun deleteFromStorage(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val blobClient: BlobClient = containerClient.getBlobClient(key)
            if (!blobClient.exists()) {
                return@withContext false
            }

            blobClient.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun listKeys(prefix: String): List<String> = withContext(Dispatchers.IO) {
        try {
            containerClient.listBlobsByHierarchy(prefix)
                .map { it.name }
                .toList()
        } catch (e: Exception) {
            throw RuntimeException("Failed to list blobs from Azure Blob Storage: ${e.message}", e)
        }
    }
}

