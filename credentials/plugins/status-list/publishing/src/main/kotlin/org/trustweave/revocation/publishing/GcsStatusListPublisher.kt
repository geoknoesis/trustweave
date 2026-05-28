package org.trustweave.revocation.publishing

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Configuration for the Google Cloud Storage-backed status list publisher.
 *
 * @property projectId GCP project ID.
 * @property bucketName GCS bucket name.
 * @property publicUrlPattern URL template for the public URL of a published status list.
 *   Use `{id}` as the placeholder for the status list identifier.
 *   Example: `https://storage.googleapis.com/my-bucket/status-lists/{id}`
 */
data class GcsPublisherConfig(
    val projectId: String,
    val bucketName: String,
    val publicUrlPattern: String,
)

/**
 * Google Cloud Storage-backed implementation of [StatusListPublisher].
 *
 * The GCS client is synchronous/blocking; all operations are dispatched on [Dispatchers.IO].
 *
 * **Example:**
 * ```kotlin
 * val config = GcsPublisherConfig(
 *     projectId = "my-gcp-project",
 *     bucketName = "my-status-lists",
 *     publicUrlPattern = "https://storage.googleapis.com/my-status-lists/{id}"
 * )
 * val publisher = GcsStatusListPublisher(config)
 * val url = publisher.publish("sl-001", bytes, "application/json")
 * ```
 */
class GcsStatusListPublisher(
    private val config: GcsPublisherConfig,
) : StatusListPublisher {

    private val storage by lazy {
        StorageOptions.newBuilder()
            .setProjectId(config.projectId)
            .build()
            .service
    }

    private fun blobId(statusListId: String): BlobId =
        BlobId.of(config.bucketName, statusListId)

    override suspend fun publish(statusListId: String, content: ByteArray, contentType: String): String =
        withContext(Dispatchers.IO) {
            val blobInfo = BlobInfo.newBuilder(blobId(statusListId))
                .setContentType(contentType)
                .build()
            storage.create(blobInfo, content)
            config.publicUrlPattern.replace("{id}", statusListId)
        }

    override suspend fun delete(statusListId: String): Unit =
        withContext(Dispatchers.IO) {
            storage.delete(blobId(statusListId))
        }

    override suspend fun getUrl(statusListId: String): String? =
        withContext(Dispatchers.IO) {
            val blob = storage.get(blobId(statusListId))
            if (blob != null && blob.exists()) {
                config.publicUrlPattern.replace("{id}", statusListId)
            } else {
                null
            }
        }
}
