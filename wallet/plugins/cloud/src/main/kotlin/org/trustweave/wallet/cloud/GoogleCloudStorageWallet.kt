package org.trustweave.wallet.cloud

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Cloud Storage-backed wallet implementation.
 *
 * Stores credentials in Google Cloud Storage buckets.
 *
 * **Example:**
 * ```kotlin
 * val storage = StorageOptions.getDefaultInstance().service
 *
 * val wallet = GoogleCloudStorageWallet(
 *     walletId = "wallet-1",
 *     walletDid = "did:key:wallet-1",
 *     holderDid = "did:key:holder",
 *     bucketName = "my-wallet-bucket",
 *     basePath = "wallets/wallet-1",
 *     storage = storage
 * )
 * ```
 */
class GoogleCloudStorageWallet(
    walletId: String,
    walletDid: String,
    holderDid: String,
    bucketName: String,
    basePath: String,
    private val storage: Storage
) : CloudWallet(walletId, walletDid, holderDid, bucketName, basePath) {

    override suspend fun upload(key: String, data: ByteArray): Unit = withContext(Dispatchers.IO) {
        try {
            val blobId = BlobId.of(bucketName, key)
            val blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("application/json")
                .build()

            storage.create(blobInfo, data)
        } catch (e: Exception) {
            throw RuntimeException("Failed to upload to Google Cloud Storage: ${e.message}", e)
        }
    }

    override suspend fun download(key: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val blobId = BlobId.of(bucketName, key)
            val blob: Blob? = storage.get(blobId)

            blob?.getContent() ?: null
        } catch (e: Exception) {
            throw RuntimeException("Failed to download from Google Cloud Storage: ${e.message}", e)
        }
    }

    override suspend fun deleteFromStorage(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val blobId = BlobId.of(bucketName, key)
            storage.delete(blobId)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun listKeys(prefix: String): List<String> = withContext(Dispatchers.IO) {
        try {
            storage.list(bucketName, Storage.BlobListOption.prefix(prefix))
                .iterateAll()
                .map { it.name }
                .toList()
        } catch (e: Exception) {
            throw RuntimeException("Failed to list objects from Google Cloud Storage: ${e.message}", e)
        }
    }
}

