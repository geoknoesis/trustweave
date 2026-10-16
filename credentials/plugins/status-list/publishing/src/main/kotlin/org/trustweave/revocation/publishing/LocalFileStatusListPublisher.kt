package org.trustweave.revocation.publishing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Configuration for the local-file-backed status list publisher.
 *
 * Useful for testing and self-hosted deployments where a web server serves files
 * directly from a local directory.
 *
 * @property directory Directory path where status list files are written.
 * @property publicUrlPattern URL template for the public URL of a published status list.
 *   Use `{id}` as the placeholder for the status list identifier.
 *   Example: `https://example.com/status-lists/{id}`
 */
data class LocalFilePublisherConfig(
    val directory: Path,
    val publicUrlPattern: String,
)

/**
 * Local file system-backed implementation of [StatusListPublisher].
 *
 * Each status list is stored as a file named after its identifier inside [LocalFilePublisherConfig.directory].
 * File I/O is dispatched on [Dispatchers.IO].
 *
 * **Example:**
 * ```kotlin
 * val config = LocalFilePublisherConfig(
 *     directory = Path.of("/var/www/status-lists"),
 *     publicUrlPattern = "https://example.com/status-lists/{id}"
 * )
 * val publisher = LocalFileStatusListPublisher(config)
 * val url = publisher.publish("sl-001", bytes, "application/json")
 * ```
 */
class LocalFileStatusListPublisher(
    private val config: LocalFilePublisherConfig,
) : StatusListPublisher {
    /**
     * Resolves [statusListId] to a file directly inside the configured directory.
     *
     * The identifier becomes a file name, so it is restricted to a single plain path segment of
     * letters, digits, `.`, `_` and `-` (not starting with `.`): no separators, no `..`, no
     * absolute or drive-qualified paths. The result is also checked to sit directly under the
     * directory. Anything else is an [IllegalArgumentException], for publish, delete and lookup.
     */
    private fun filePath(statusListId: String): Path {
        require(SAFE_ID.matches(statusListId)) {
            "Invalid status list identifier: must match ${SAFE_ID.pattern}"
        }
        val base = config.directory.toAbsolutePath().normalize()
        val resolved = base.resolve(statusListId).normalize()
        require(resolved.parent == base) { "Status list identifier escapes the publishing directory" }
        return config.directory.resolve(statusListId)
    }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,254}")
    }

    override suspend fun publish(
        statusListId: String,
        content: ByteArray,
        contentType: String,
    ): String =
        withContext(Dispatchers.IO) {
            Files.createDirectories(config.directory)
            Files.write(filePath(statusListId), content)
            config.publicUrlPattern.replace("{id}", statusListId)
        }

    override suspend fun delete(statusListId: String): Unit =
        withContext(Dispatchers.IO) {
            Files.deleteIfExists(filePath(statusListId))
        }

    override suspend fun getUrl(statusListId: String): String? =
        withContext(Dispatchers.IO) {
            if (Files.exists(filePath(statusListId))) {
                config.publicUrlPattern.replace("{id}", statusListId)
            } else {
                null
            }
        }
}
