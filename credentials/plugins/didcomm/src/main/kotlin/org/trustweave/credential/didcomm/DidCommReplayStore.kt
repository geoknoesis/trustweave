package org.trustweave.credential.didcomm

import java.util.PriorityQueue

/**
 * Remembers accepted DIDComm message ids so a replayed message can be refused.
 *
 * Implementations decide where the state lives. The default, [InMemoryDidCommReplayStore], is
 * per-process; a deployment that needs replay protection across restarts or replicas should
 * supply a durable, shared implementation (e.g. backed by the message database).
 */
interface DidCommReplayStore {
    /**
     * Atomically records [messageId] as accepted, to be remembered until at least
     * [retainUntilEpochSeconds].
     *
     * @return `true` when the id was newly recorded; `false` when it is already remembered (a replay).
     * @throws DidCommReplayStoreFullException when the id cannot be recorded without forgetting an
     *         id that has not yet expired. Implementations must never make room by evicting
     *         unexpired ids, because that would re-open the replay window for them.
     */
    suspend fun recordIfAbsent(
        messageId: String,
        retainUntilEpochSeconds: Long,
        nowEpochSeconds: Long,
    ): Boolean
}

/** The replay store is at capacity with ids that have not expired yet. */
class DidCommReplayStoreFullException(
    message: String,
) : IllegalStateException(message)

/**
 * Bounded, expiry-ordered in-memory [DidCommReplayStore].
 *
 * Ids are evicted only once their retention time has passed. When [capacity] unexpired ids are
 * held, new messages are refused with [DidCommReplayStoreFullException] instead of evicting a
 * still-relevant id — a flood can delay legitimate traffic, but it cannot re-enable a replay.
 *
 * @param capacity Maximum number of unexpired ids held at once.
 */
class InMemoryDidCommReplayStore(
    private val capacity: Int = DEFAULT_CAPACITY,
) : DidCommReplayStore {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private data class Entry(
        val messageId: String,
        val retainUntil: Long,
    )

    private val retainUntilById = HashMap<String, Long>()
    private val byExpiry = PriorityQueue<Entry>(compareBy { it.retainUntil })

    override suspend fun recordIfAbsent(
        messageId: String,
        retainUntilEpochSeconds: Long,
        nowEpochSeconds: Long,
    ): Boolean =
        synchronized(this) {
            evictExpired(nowEpochSeconds)
            if (retainUntilById.containsKey(messageId)) return false
            if (retainUntilById.size >= capacity) {
                throw DidCommReplayStoreFullException(
                    "DIDComm replay store holds $capacity unexpired message ids; refusing new messages until some expire",
                )
            }
            retainUntilById[messageId] = retainUntilEpochSeconds
            byExpiry.add(Entry(messageId, retainUntilEpochSeconds))
            true
        }

    /** Number of ids currently remembered (expired ones may linger until the next insert). */
    fun size(): Int = synchronized(this) { retainUntilById.size }

    private fun evictExpired(now: Long) {
        while (true) {
            val head = byExpiry.peek() ?: return
            if (head.retainUntil > now) return
            byExpiry.poll()
            if (retainUntilById[head.messageId] == head.retainUntil) retainUntilById.remove(head.messageId)
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 100_000
    }
}
