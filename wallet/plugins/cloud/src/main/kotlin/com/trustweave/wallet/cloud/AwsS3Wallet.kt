package com.trustweave.wallet.cloud

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
            
            val response = s3Client.getObject(request)
            response.readAllBytes()
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
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun listKeys(prefix: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build()
            
            val response = s3Client.listObjectsV2(request)
            response.contents().map { it.key() }
        } catch (e: Exception) {
            throw RuntimeException("Failed to list keys from S3: ${e.message}", e)
        }
    }
}

