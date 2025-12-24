package org.trustweave.credential.didcomm.storage.analytics

import org.trustweave.credential.didcomm.models.DidCommMessage
import kotlinx.datetime.Instant

/**
 * Message analytics and reporting.
 *
 * Provides statistics, traffic patterns, and reporting capabilities.
 */
interface MessageAnalytics {
    /**
     * Gets message statistics for a time period.
     *
     * @param startTime Start time
     * @param endTime End time
     * @param groupBy Grouping granularity
     * @return Message statistics
     */
    suspend fun getStatistics(
        startTime: Instant,
        endTime: Instant,
        groupBy: GroupBy = GroupBy.HOUR
    ): MessageStatistics

    /**
     * Gets traffic patterns.
     *
     * @param startTime Start time
     * @param endTime End time
     * @return Traffic patterns
     */
    suspend fun getTrafficPatterns(
        startTime: Instant,
        endTime: Instant
    ): TrafficPatterns

    /**
     * Gets top DIDs by message count.
     *
     * @param limit Number of top DIDs to return
     * @param startTime Optional start time filter
     * @param endTime Optional end time filter
     * @return List of DID statistics
     */
    suspend fun getTopDids(
        limit: Int = 10,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): List<DidStatistics>

    /**
     * Gets message type distribution.
     *
     * @param startTime Optional start time filter
     * @param endTime Optional end time filter
     * @return Map of message type to count
     */
    suspend fun getTypeDistribution(
        startTime: Instant? = null,
        endTime: Instant? = null
    ): Map<String, Int>
}

enum class GroupBy {
    HOUR, DAY, WEEK, MONTH
}

data class MessageStatistics(
    val totalMessages: Int,
    val sentMessages: Int,
    val receivedMessages: Int,
    val averageMessageSize: Long,
    val timeSeries: List<TimeSeriesPoint>
)

data class TimeSeriesPoint(
    val timestamp: Instant,
    val count: Int
)

data class TrafficPatterns(
    val peakHours: List<Int>,
    val averageMessagesPerHour: Double,
    val busiestDay: String
)

data class DidStatistics(
    val did: String,
    val messageCount: Int,
    val sentCount: Int,
    val receivedCount: Int
)

