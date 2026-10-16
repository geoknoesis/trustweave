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

    /**
     * Like [recordIfAbsent], for a message from [sender] (the unpacked `from` header; `null` when
     * the message names none). Message ids are chosen by the sender, so identical ids from two
     * senders are two different messages: one sender must not be able to make another's message
     * look like a replay by reusing its id.
     *
     * The default keys the 3-argument method on the pair (sender, id), unchanged for a `null`
     * sender, so an existing implementation keeps working and is already sender-scoped. Override
     * it to add per-sender bounds, as [InMemoryDidCommReplayStore] does.
     */
    suspend fun recordIfAbsent(
        sender: String?,
        messageId: String,
        retainUntilEpochSeconds: Long,
        nowEpochSeconds: Long,
    ): Boolean = recordIfAbsent(scopedKey(sender, messageId), retainUntilEpochSeconds, nowEpochSeconds)

    companion object {
        /** `messageId` itself for a `null` sender, else an unambiguous `length:sender:id` composite. */
        @JvmStatic
        fun scopedKey(
            sender: String?,
            messageId: String,
        ): String = if (sender == null) messageId else "${sender.length}:$sender:$messageId"
    }
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
 * Ids are scoped by sender: the same id from two senders is two messages, and each sender may hold
 * at most [maxPerSender] unexpired ids, so one sender cannot fill the store and lock out the rest.
 * Messages naming no sender share one bucket of their own.
 *
 * @param capacity Maximum number of unexpired ids held at once.
 * @param maxPerSender Maximum number of unexpired ids held for any one sender (at most [capacity]).
 */
class InMemoryDidCommReplayStore
    @JvmOverloads
    constructor(
        private val capacity: Int = DEFAULT_CAPACITY,
        private val maxPerSender: Int = minOf(capacity, DEFAULT_MAX_PER_SENDER),
    ) : DidCommReplayStore {
        init {
            require(capacity > 0) { "capacity must be positive" }
            require(maxPerSender in 1..capacity) { "maxPerSender must be between 1 and capacity" }
        }

        private data class Key(
            val sender: String?,
            val messageId: String,
        )

        private data class Entry(
            val key: Key,
            val retainUntil: Long,
        )

        private val retainUntilByKey = HashMap<Key, Long>()
        private val countBySender = HashMap<String?, Int>()
        private val byExpiry = PriorityQueue<Entry>(compareBy { it.retainUntil })

        override suspend fun recordIfAbsent(
            messageId: String,
            retainUntilEpochSeconds: Long,
            nowEpochSeconds: Long,
        ): Boolean = recordIfAbsent(null, messageId, retainUntilEpochSeconds, nowEpochSeconds)

        override suspend fun recordIfAbsent(
            sender: String?,
            messageId: String,
            retainUntilEpochSeconds: Long,
            nowEpochSeconds: Long,
        ): Boolean =
            synchronized(this) {
                evictExpired(nowEpochSeconds)
                val key = Key(sender, messageId)
                if (retainUntilByKey.containsKey(key)) return false
                if (retainUntilByKey.size >= capacity) {
                    throw DidCommReplayStoreFullException(
                        "DIDComm replay store holds $capacity unexpired message ids; refusing new messages until some expire",
                    )
                }
                if ((countBySender[sender] ?: 0) >= maxPerSender) {
                    throw DidCommReplayStoreFullException(
                        "DIDComm replay store holds $maxPerSender unexpired message ids for this sender; " +
                            "refusing its new messages until some expire",
                    )
                }
                retainUntilByKey[key] = retainUntilEpochSeconds
                countBySender.merge(sender, 1, Int::plus)
                byExpiry.add(Entry(key, retainUntilEpochSeconds))
                true
            }

        /** Number of ids currently remembered (expired ones may linger until the next insert). */
        fun size(): Int = synchronized(this) { retainUntilByKey.size }

        private fun evictExpired(now: Long) {
            while (true) {
                val head = byExpiry.peek() ?: return
                if (head.retainUntil > now) return
                byExpiry.poll()
                if (retainUntilByKey[head.key] == head.retainUntil) {
                    retainUntilByKey.remove(head.key)
                    if (countBySender.merge(head.key.sender, -1, Int::plus) == 0) countBySender.remove(head.key.sender)
                }
            }
        }

        companion object {
            const val DEFAULT_CAPACITY = 100_000

            /** One sender may hold at most a tenth of the default capacity. */
            const val DEFAULT_MAX_PER_SENDER = 10_000
        }
    }
