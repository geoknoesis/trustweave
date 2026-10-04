package org.trustweave.did.registrar.storage

import org.trustweave.did.registrar.model.DidRegistrationResponse
import kotlin.time.Duration

/**
 * Storage for tracking long-running DID registration operations.
 *
 * This interface allows registrars to track the state of asynchronous operations
 * that may take time to complete (e.g., blockchain confirmations, external service calls).
 *
 * **Example Usage:**
 * ```kotlin
 * val storage = InMemoryJobStorage()
 * storage.store("job-123", response)
 * val retrieved = storage.get("job-123")
 * ```
 *
 * **Implementations:**
 * - [InMemoryJobStorage] - Simple in-memory storage (good for testing)
 * - [DatabaseJobStorage] - Database-backed storage (production use)
 */
interface JobStorage {
    /**
     * Stores a registration response for a job.
     *
     * @param jobId Unique job identifier
     * @param response Registration response
     */
    fun store(jobId: String, response: DidRegistrationResponse)

    /**
     * Retrieves a registration response by job ID.
     *
     * @param jobId Unique job identifier
     * @return Registration response, or null if not found
     */
    fun get(jobId: String): DidRegistrationResponse?

    /**
     * Removes a job from storage.
     *
     * @param jobId Unique job identifier
     * @return true if the job was removed, false if not found
     */
    fun remove(jobId: String): Boolean

    /**
     * Checks if a job exists in storage.
     *
     * @param jobId Unique job identifier
     * @return true if the job exists, false otherwise
     */
    fun exists(jobId: String): Boolean
}

/**
 * In-memory implementation of [JobStorage].
 *
 * This implementation keeps jobs under a lock, bounded by entry count and TTL (see the constructor).
 * Jobs are stored in memory and will be lost on application restart.
 *
 * **Use Cases:**
 * - Testing and development
 * - Single-instance deployments where persistence is not required
 * - Short-lived operations that don't need to survive restarts
 *
 * For production use with persistence, consider using
 * [DatabaseJobStorage] or implementing a custom persistent storage solution.
 */
class InMemoryJobStorage
    @JvmOverloads
    constructor(
        /** Maximum number of tracked jobs; the oldest finished (then oldest overall) are evicted beyond it. */
        private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
        /** How long a finished job stays retrievable after it was stored. */
        private val finishedJobTtl: Duration = DEFAULT_FINISHED_TTL,
        /** How long an unfinished job stays tracked at most (guards against jobs that never finish). */
        private val unfinishedJobTtl: Duration = DEFAULT_UNFINISHED_TTL,
        private val nowMillis: () -> Long = System::currentTimeMillis,
    ) : JobStorage {
        private class Entry(
            val response: DidRegistrationResponse,
            val storedAt: Long,
        )

        private val lock = Any()
        private val jobs = LinkedHashMap<String, Entry>()

        init {
            require(maxEntries >= 1) { "maxEntries must be >= 1" }
        }

        private fun expired(
            e: Entry,
            now: Long,
        ): Boolean {
            val ttl = if (e.response.isComplete()) finishedJobTtl else unfinishedJobTtl
            return now - e.storedAt >= ttl.inWholeMilliseconds
        }

        private fun sweep(now: Long) {
            jobs.values.removeAll { expired(it, now) }
            var excess = jobs.size - maxEntries
            if (excess <= 0) return
            // Insertion order is oldest first. Prefer to drop finished jobs, then the oldest overall.
            val it = jobs.entries.iterator()
            while (it.hasNext() && excess > 0) {
                if (it
                        .next()
                        .value.response
                        .isComplete()
                ) {
                    it.remove()
                    excess--
                }
            }
            val it2 = jobs.entries.iterator()
            while (it2.hasNext() && excess > 0) {
                it2.next()
                it2.remove()
                excess--
            }
        }

        override fun store(
            jobId: String,
            response: DidRegistrationResponse,
        ) {
            synchronized(lock) {
                val now = nowMillis()
                jobs.remove(jobId) // re-insert so insertion order tracks last write
                jobs[jobId] = Entry(response, now)
                sweep(now)
            }
        }

        override fun get(jobId: String): DidRegistrationResponse? =
            synchronized(lock) {
                val e = jobs[jobId] ?: return null
                if (expired(e, nowMillis())) {
                    jobs.remove(jobId)
                    return null
                }
                e.response
            }

        override fun remove(jobId: String): Boolean = synchronized(lock) { jobs.remove(jobId) != null }

        override fun exists(jobId: String): Boolean = get(jobId) != null

        /** Number of currently tracked jobs (after expiring stale ones). */
        fun size(): Int =
            synchronized(lock) {
                sweep(nowMillis())
                jobs.size
            }

        companion object {
            const val DEFAULT_MAX_ENTRIES: Int = 10_000
            val DEFAULT_FINISHED_TTL: Duration = Duration.parse("PT1H")
            val DEFAULT_UNFINISHED_TTL: Duration = Duration.parse("PT24H")
        }
    }
