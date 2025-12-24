package org.trustweave.credential.didcomm.storage.archive

import org.trustweave.credential.didcomm.models.DidCommMessage
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Defines when messages should be archived.
 *
 * Archive policies determine which messages should be moved to cold storage.
 */
interface ArchivePolicy {
    /**
     * Determines if a message should be archived.
     *
     * @param message Message to evaluate
     * @return true if message should be archived
     */
    suspend fun shouldArchive(message: DidCommMessage): Boolean
}

/**
 * Age-based archive policy.
 *
 * Archives messages older than a specified number of days.
 */
class AgeBasedArchivePolicy(
    private val maxAgeDays: Int = 90
) : ArchivePolicy {

    override suspend fun shouldArchive(message: DidCommMessage): Boolean {
        val created = message.created?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                null
            }
        } ?: return false

        val now = Clock.System.now()
        val ageSeconds = now.epochSeconds - created.epochSeconds
        val age = Duration.parse("PT${ageSeconds}S")
        return age.inWholeDays > maxAgeDays
    }
}

/**
 * Size-based archive policy.
 *
 * Archives messages when total storage exceeds a threshold.
 * Note: This requires tracking total storage size.
 */
class SizeBasedArchivePolicy(
    private val maxSizeBytes: Long = 1_000_000_000L // 1GB
) : ArchivePolicy {

    private var currentSize: Long = 0

    override suspend fun shouldArchive(message: DidCommMessage): Boolean {
        // This is a simplified check - in production, track actual storage size
        return currentSize > maxSizeBytes
    }

    fun updateSize(size: Long) {
        currentSize = size
    }
}

/**
 * Composite archive policy.
 *
 * Archives messages if any of the component policies say so.
 */
class CompositeArchivePolicy(
    private val policies: List<ArchivePolicy>
) : ArchivePolicy {

    override suspend fun shouldArchive(message: DidCommMessage): Boolean {
        return policies.any { it.shouldArchive(message) }
    }
}

/**
 * Custom archive policy using a predicate function.
 */
class CustomArchivePolicy(
    private val predicate: suspend (DidCommMessage) -> Boolean
) : ArchivePolicy {

    override suspend fun shouldArchive(message: DidCommMessage): Boolean {
        return predicate(message)
    }
}

