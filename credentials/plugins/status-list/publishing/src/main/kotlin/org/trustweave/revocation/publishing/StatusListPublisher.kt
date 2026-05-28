package org.trustweave.revocation.publishing

/**
 * SPI for publishing signed status list credentials/tokens to public cloud storage.
 *
 * Implementations are provided for AWS S3, Azure Blob Storage, Google Cloud Storage,
 * and local file system (for testing and self-hosted deployments).
 */
interface StatusListPublisher {
    /**
     * Publish a status list to cloud storage.
     *
     * @param statusListId Unique identifier for the status list.
     * @param content The serialized status list bytes to publish.
     * @param contentType MIME type of the content (e.g. `application/json`, `application/jwt`).
     * @return The public URL at which the status list can be fetched by verifiers.
     */
    suspend fun publish(statusListId: String, content: ByteArray, contentType: String): String

    /**
     * Delete a published status list from cloud storage.
     *
     * @param statusListId The identifier of the status list to remove.
     */
    suspend fun delete(statusListId: String)

    /**
     * Return the public URL for an already-published status list, or null if not found.
     *
     * @param statusListId The identifier of the status list.
     * @return The public URL, or null if the status list has not been published.
     */
    suspend fun getUrl(statusListId: String): String?
}
