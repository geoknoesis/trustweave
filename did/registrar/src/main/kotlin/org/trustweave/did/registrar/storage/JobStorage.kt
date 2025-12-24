package org.trustweave.did.registrar.storage

import org.trustweave.did.registrar.model.DidRegistrationResponse
import java.util.concurrent.ConcurrentHashMap

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
 * This implementation uses a [ConcurrentHashMap] for thread-safe storage.
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
class InMemoryJobStorage : JobStorage {
    private val jobs = ConcurrentHashMap<String, DidRegistrationResponse>()

    override fun store(jobId: String, response: DidRegistrationResponse) {
        jobs[jobId] = response
    }

    override fun get(jobId: String): DidRegistrationResponse? {
        return jobs[jobId]
    }

    override fun remove(jobId: String): Boolean {
        return jobs.remove(jobId) != null
    }

    override fun exists(jobId: String): Boolean {
        return jobs.containsKey(jobId)
    }
}

