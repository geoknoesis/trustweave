package org.trustweave.revocation.publishing

import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * Configuration for the S3-backed status list publisher.
 *
 * @property bucket S3 bucket name.
 * @property region AWS region (e.g. `us-east-1`).
 * @property keyPrefix Object key prefix within the bucket (default: `status-lists/`).
 * @property publicUrlPattern URL template for the public URL of a published status list.
 *   Use `{id}` as the placeholder for the status list identifier.
 *   Example: `https://my-bucket.s3.amazonaws.com/status-lists/{id}`
 */
data class S3PublisherConfig(
    val bucket: String,
    val region: String,
    val keyPrefix: String = "status-lists/",
    val publicUrlPattern: String,
)

/**
 * AWS S3-backed implementation of [StatusListPublisher].
 *
 * Uses [S3AsyncClient] so all operations are non-blocking; coroutines bridge via
 * [kotlinx.coroutines.future.await].
 *
 * **Example:**
 * ```kotlin
 * val config = S3PublisherConfig(
 *     bucket = "my-status-lists",
 *     region = "us-east-1",
 *     publicUrlPattern = "https://my-status-lists.s3.amazonaws.com/status-lists/{id}"
 * )
 * val publisher = S3StatusListPublisher(config)
 * val url = publisher.publish("sl-001", bytes, "application/json")
 * ```
 */
class S3StatusListPublisher(
    private val config: S3PublisherConfig,
    private val s3Client: S3AsyncClient = S3AsyncClient.builder()
        .region(Region.of(config.region))
        .build(),
) : StatusListPublisher {

    private fun objectKey(statusListId: String): String = "${config.keyPrefix}$statusListId"

    override suspend fun publish(statusListId: String, content: ByteArray, contentType: String): String {
        val request = PutObjectRequest.builder()
            .bucket(config.bucket)
            .key(objectKey(statusListId))
            .contentType(contentType)
            .contentLength(content.size.toLong())
            .build()

        s3Client.putObject(request, AsyncRequestBody.fromBytes(content)).await()

        return config.publicUrlPattern.replace("{id}", statusListId)
    }

    override suspend fun delete(statusListId: String) {
        val request = DeleteObjectRequest.builder()
            .bucket(config.bucket)
            .key(objectKey(statusListId))
            .build()

        s3Client.deleteObject(request).await()
    }

    override suspend fun getUrl(statusListId: String): String? {
        val request = HeadObjectRequest.builder()
            .bucket(config.bucket)
            .key(objectKey(statusListId))
            .build()

        return try {
            s3Client.headObject(request).await()
            config.publicUrlPattern.replace("{id}", statusListId)
        } catch (_: NoSuchKeyException) {
            null
        }
    }
}
