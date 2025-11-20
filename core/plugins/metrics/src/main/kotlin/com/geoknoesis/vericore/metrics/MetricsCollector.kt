package com.geoknoesis.vericore.metrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Metrics collector interface for tracking performance and usage metrics.
 * 
 * **Example Usage:**
 * ```kotlin
 * val collector = InMemoryMetricsCollector()
 * 
 * collector.incrementCounter("credentials.issued")
 * collector.recordDuration("credential.verification", Duration.ofMillis(150))
 * collector.recordValue("wallet.size", 42)
 * ```
 */
interface MetricsCollector {
    /**
     * Increment a counter metric.
     * 
     * @param name Metric name (e.g., "credentials.issued", "dids.created")
     * @param tags Optional tags for filtering
     * @param value Increment value (default: 1)
     */
    suspend fun incrementCounter(
        name: String,
        tags: Map<String, String> = emptyMap(),
        value: Long = 1
    )
    
    /**
     * Record a duration metric.
     * 
     * @param name Metric name
     * @param duration Duration in milliseconds
     * @param tags Optional tags
     */
    suspend fun recordDuration(
        name: String,
        duration: Long,
        tags: Map<String, String> = emptyMap()
    )
    
    /**
     * Record a value metric.
     * 
     * @param name Metric name
     * @param value Value to record
     * @param tags Optional tags
     */
    suspend fun recordValue(
        name: String,
        value: Double,
        tags: Map<String, String> = emptyMap()
    )
    
    /**
     * Get counter value.
     * 
     * @param name Metric name
     * @param tags Optional tags
     * @return Counter value
     */
    suspend fun getCounter(name: String, tags: Map<String, String> = emptyMap()): Long
    
    /**
     * Get duration statistics.
     * 
     * @param name Metric name
     * @param tags Optional tags
     * @return Duration statistics (min, max, avg, count)
     */
    suspend fun getDurationStats(name: String, tags: Map<String, String> = emptyMap()): DurationStats?
    
    /**
     * Get value statistics.
     * 
     * @param name Metric name
     * @param tags Optional tags
     * @return Value statistics (min, max, avg, count)
     */
    suspend fun getValueStats(name: String, tags: Map<String, String> = emptyMap()): ValueStats?
    
    /**
     * Get all metrics snapshot.
     * 
     * @return Snapshot of all metrics
     */
    suspend fun getSnapshot(): MetricsSnapshot
}

/**
 * Duration statistics.
 */
data class DurationStats(
    val min: Long,
    val max: Long,
    val avg: Double,
    val count: Long,
    val p50: Long,
    val p95: Long,
    val p99: Long
)

/**
 * Value statistics.
 */
data class ValueStats(
    val min: Double,
    val max: Double,
    val avg: Double,
    val count: Long
)

/**
 * Metrics snapshot.
 */
data class MetricsSnapshot(
    val timestamp: Instant,
    val counters: Map<String, Long>,
    val durations: Map<String, DurationStats>,
    val values: Map<String, ValueStats>
)

/**
 * In-memory metrics collector implementation.
 */
class InMemoryMetricsCollector : MetricsCollector {
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val durations = ConcurrentHashMap<String, MutableList<Long>>()
    private val values = ConcurrentHashMap<String, MutableList<Double>>()
    private val lock = Any()
    
    override suspend fun incrementCounter(
        name: String,
        tags: Map<String, String>,
        value: Long
    ) = withContext(Dispatchers.IO) {
        val key = buildKey(name, tags)
        counters.computeIfAbsent(key) { AtomicLong(0) }.addAndGet(value)
    }
    
    override suspend fun recordDuration(
        name: String,
        duration: Long,
        tags: Map<String, String>
    ) = withContext(Dispatchers.IO) {
        val key = buildKey(name, tags)
        synchronized(lock) {
            durations.computeIfAbsent(key) { mutableListOf() }.add(duration)
            // Keep only last 1000 values
            durations[key]?.let { if (it.size > 1000) it.removeAt(0) }
        }
    }
    
    override suspend fun recordValue(
        name: String,
        value: Double,
        tags: Map<String, String>
    ) = withContext(Dispatchers.IO) {
        val key = buildKey(name, tags)
        synchronized(lock) {
            values.computeIfAbsent(key) { mutableListOf() }.add(value)
            // Keep only last 1000 values
            values[key]?.let { if (it.size > 1000) it.removeAt(0) }
        }
    }
    
    override suspend fun getCounter(name: String, tags: Map<String, String>): Long = withContext(Dispatchers.IO) {
        val key = buildKey(name, tags)
        counters[key]?.get() ?: 0L
    }
    
    override suspend fun getDurationStats(name: String, tags: Map<String, String>): DurationStats? = withContext(Dispatchers.IO) {
        val key = buildKey(name, tags)
        synchronized(lock) {
            durations[key]?.let { values ->
                if (values.isEmpty()) return@withContext null
                val sorted = values.sorted()
                DurationStats(
                    min = sorted.first(),
                    max = sorted.last(),
                    avg = sorted.average(),
                    count = sorted.size.toLong(),
                    p50 = sorted[sorted.size / 2],
                    p95 = sorted[(sorted.size * 95) / 100],
                    p99 = sorted[(sorted.size * 99) / 100]
                )
            }
        }
    }
    
    override suspend fun getValueStats(name: String, tags: Map<String, String>): ValueStats? = withContext(Dispatchers.IO) {
        val key = buildKey(name, tags)
        synchronized(lock) {
            values[key]?.let { valueList ->
                if (valueList.isEmpty()) return@withContext null
                ValueStats(
                    min = valueList.minOrNull() ?: 0.0,
                    max = valueList.maxOrNull() ?: 0.0,
                    avg = valueList.average(),
                    count = valueList.size.toLong()
                )
            }
        }
    }
    
    override suspend fun getSnapshot(): MetricsSnapshot = withContext(Dispatchers.IO) {
        val counterMap = counters.mapValues { it.value.get() }
        val durationMap = durations.mapNotNull { (key, values) ->
            if (values.isEmpty()) null
            else {
                val sorted = values.sorted()
                key to DurationStats(
                    min = sorted.first(),
                    max = sorted.last(),
                    avg = sorted.average(),
                    count = sorted.size.toLong(),
                    p50 = sorted[sorted.size / 2],
                    p95 = sorted[(sorted.size * 95) / 100],
                    p99 = sorted[(sorted.size * 99) / 100]
                )
            }
        }.toMap()
        
        val valueMap = values.mapNotNull { (key, valueList) ->
            if (valueList.isEmpty()) null
            else {
                key to ValueStats(
                    min = valueList.minOrNull() ?: 0.0,
                    max = valueList.maxOrNull() ?: 0.0,
                    avg = valueList.average(),
                    count = valueList.size.toLong()
                )
            }
        }.toMap()
        
        MetricsSnapshot(
            timestamp = Instant.now(),
            counters = counterMap,
            durations = durationMap,
            values = valueMap
        )
    }
    
    private fun buildKey(name: String, tags: Map<String, String>): String {
        if (tags.isEmpty()) return name
        val tagString = tags.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
        return "$name{$tagString}"
    }
}

