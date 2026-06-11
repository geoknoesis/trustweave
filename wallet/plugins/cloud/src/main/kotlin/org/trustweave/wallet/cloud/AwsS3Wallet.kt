package org.trustweave.wallet.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.nio.ByteBuffer

/**
 * AWS S3-backed wallet implementation.
 *
 * Stores credentials in AWS S3 buckets with optional encryption.
 *
 * **Example:**
 * ```kotlin
 * val s3Client = S3Client.builder()
 *     .region(Region.US_EAST_1)
 *     .build()
 *
 * val wallet = AwsS3Wallet(
 *     walletId = "wallet-1",
 *     walletDid = "did:key:wallet-1",
 *     holderDid = "did:key:holder",
 *     bucketName = "my-wallet-bucket",
 *     basePath = "wallets/wallet-1",
 *     s3Client = s3Client
 * )
 * ```
 */
class AwsS3Wallet(
    walletId: String,
    walletDid: String,
    holderDid: String,
    bucketName: String,
    basePath: String,
    private val s3Client: S3Client
) : CloudWallet(walletId, walletDid, holderDid, bucketName, basePath) {

    override suspend fun upload(key: String, data: ByteArray): Unit = withContext(Dispatchers.IO) {
        try {
            val request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/json")
                .build()

            s3Client.putObject(request, RequestBody.fromByteBuffer(ByteBuffer.wrap(data)))
        } catch (e: Exception) {
            throw RuntimeException("Failed to upload to S3: ${e.message}", e)
        }
    }

    override suspend fun download(key: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            // ResponseInputStream holds the underlying HTTP connection — it MUST be
            // closed after reading, otherwise the connection pool is exhausted.
            s3Client.getObject(request).use { it.readAllBytes() }
        } catch (e: NoSuchKeyException) {
            null
        } catch (e: Exception) {
            throw RuntimeException("Failed to download from S3: ${e.message}", e)
        }
    }

    override suspend fun deleteFromStorage(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            s3Client.deleteObject(request)
            true
        } catch (e: NoSuchKeyException) {
            // Missing key means "nothing to delete" — not a storage failure.
            false
        } catch (e: Exception) {
            // Auth failures, networking errors, etc. must NOT be reported as
            // "not found" — propagate them as storage errors.
            throw RuntimeException("Failed to delete from S3: ${e.message}", e)
        }
    }

    override suspend fun listKeys(prefix: String): List<String> = withContext(Dispatchers.IO) {
        try {
            // S3 returns at most 1000 keys per ListObjectsV2 call. Follow the
            // continuation token until the listing is complete; otherwise wallets
            // with >1000 credentials are silently truncated.
            val keys = mutableListOf<String>()
            var continuationToken: String? = null
            do {
                val request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build()

                val response = s3Client.listObjectsV2(request)
                response.contents().forEach { keys.add(it.key()) }
                continuationToken = if (response.isTruncated == true) response.nextContinuationToken() else null
            } while (continuationToken != null)
            keys
        } catch (e: Exception) {
            throw RuntimeException("Failed to list keys from S3: ${e.message}", e)
        }
    }
}

