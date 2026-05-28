package org.trustweave.revocation.publishing

import com.azure.storage.blob.BlobServiceClientBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Configuration for the Azure Blob Storage-backed status list publisher.
 *
 * @property connectionString Azure Storage connection string.
 * @property containerName Azure Blob container name.
 * @property publicUrlPattern URL template for the public URL of a published status list.
 *   Use `{id}` as the placeholder for the status list identifier.
 *   Example: `https://myaccount.blob.core.windows.net/status-lists/{id}`
 */
data class AzureBlobPublisherConfig(
    val connectionString: String,
    val containerName: String,
    val publicUrlPattern: String,
)

/**
 * Azure Blob Storage-backed implementation of [StatusListPublisher].
 *
 * The Azure SDK is synchronous/blocking; all operations are dispatched on [Dispatchers.IO].
 *
 * **Example:**
 * ```kotlin
 * val config = AzureBlobPublisherConfig(
 *     connectionString = "DefaultEndpointsProtocol=https;AccountName=...",
 *     containerName = "status-lists",
 *     publicUrlPattern = "https://myaccount.blob.core.windows.net/status-lists/{id}"
 * )
 * val publisher = AzureBlobStatusListPublisher(config)
 * val url = publisher.publish("sl-001", bytes, "application/json")
 * ```
 */
class AzureBlobStatusListPublisher(
    private val config: AzureBlobPublisherConfig,
) : StatusListPublisher {

    private val serviceClient by lazy {
        BlobServiceClientBuilder()
            .connectionString(config.connectionString)
            .buildClient()
    }

    private fun blobClient(statusListId: String) =
        serviceClient
            .getBlobContainerClient(config.containerName)
            .getBlobClient(statusListId)

    override suspend fun publish(statusListId: String, content: ByteArray, contentType: String): String =
        withContext(Dispatchers.IO) {
            val client = blobClient(statusListId)
            client.upload(content.inputStream(), content.size.toLong(), true)
            client.setHttpHeaders(
                com.azure.storage.blob.models.BlobHttpHeaders().setContentType(contentType)
            )
            config.publicUrlPattern.replace("{id}", statusListId)
        }

    override suspend fun delete(statusListId: String): Unit =
        withContext(Dispatchers.IO) {
            blobClient(statusListId).deleteIfExists()
        }

    override suspend fun getUrl(statusListId: String): String? =
        withContext(Dispatchers.IO) {
            val client = blobClient(statusListId)
            if (client.exists() == true) {
                config.publicUrlPattern.replace("{id}", statusListId)
            } else {
                null
            }
        }
}
