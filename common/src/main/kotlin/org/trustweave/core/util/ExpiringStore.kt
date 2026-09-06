package org.trustweave.core.util

import java.time.Clock
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bounded session storage with fixed TTL (reads do not extend secret retention). */
public class ExpiringStore<T>(
    private val clock: Clock = Clock.systemUTC(),
    private val ttlMillis: Long = 300_000,
    private val capacity: Int = 1_000,
) : AutoCloseable {
    private data class Entry<T>(
        val value: T,
        val expires: Long,
    )

    private val entries = HashMap<String, Entry<T>>()
    private var closed = false

    init {
        require(ttlMillis > 0 && capacity > 0)
        synchronized(stores) { stores[this] = true }
    }

    @Synchronized
    operator fun get(key: String): T? {
        purge()
        return entries[key]?.value
    }

    @Synchronized
    operator fun set(
        key: String,
        value: T,
    ) {
        check(!closed) { "Exchange store closed" }
        purge()
        if (key !in entries && entries.size >= capacity) throw SessionCapacityExceededException(capacity)
        entries[key] = Entry(value, entries[key]?.expires ?: (clock.millis() + ttlMillis))
    }

    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
    }

    @Synchronized
    fun purge() {
        entries.entries.removeIf { it.value.expires <= clock.millis() }
    }

    @Synchronized
    fun size(): Int {
        purge()
        return entries.size
    }

    @Synchronized
    override fun close() {
        closed = true
        entries.clear()
    }

    companion object {
        // One daemon for all service instances; weak references never keep a wallet alive.
        private val stores = WeakHashMap<ExpiringStore<*>, Boolean>()
        private val sweeper =
            Executors
                .newSingleThreadScheduledExecutor { task ->
                    Thread(task, "trustweave-session-expiry").apply { isDaemon = true }
                }.also { executor ->
                    executor.scheduleWithFixedDelay({
                        val snapshot = synchronized(stores) { stores.keys.toList() }
                        snapshot.forEach { it.purge() }
                    }, 1, 1, TimeUnit.MINUTES)
                }
    }
}

/** Retry after capacity becomes available; map to HTTP 429 at application boundaries. */
public class SessionCapacityExceededException(
    public val capacity: Int,
) : IllegalStateException("Pending session capacity exceeded ($capacity)")
