package org.trustweave.credential.didcomm.storage.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Timestamp
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.time.Instant as JavaInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import javax.sql.DataSource

/**
 * PostgreSQL-based analytics implementation.
 */
class PostgresMessageAnalytics(
    private val dataSource: DataSource
) : MessageAnalytics {

    override suspend fun getStatistics(
        startTime: Instant,
        endTime: Instant,
        groupBy: GroupBy
    ): MessageStatistics = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val dateFormat = when (groupBy) {
                GroupBy.HOUR -> "YYYY-MM-DD HH24:00:00"
                GroupBy.DAY -> "YYYY-MM-DD"
                GroupBy.WEEK -> "YYYY-\"W\"WW"
                GroupBy.MONTH -> "YYYY-MM"
            }

            // Get total statistics
            conn.prepareStatement("""
                SELECT
                    COUNT(*) as total,
                    COUNT(CASE WHEN from_did IS NOT NULL THEN 1 END) as sent,
                    COUNT(CASE WHEN from_did IS NULL THEN 1 END) as received,
                    AVG(LENGTH(message_json::text)) as avg_size
                FROM didcomm_messages
                WHERE created_time >= ? AND created_time <= ?
            """).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(JavaInstant.ofEpochSecond(startTime.epochSeconds)))
                stmt.setTimestamp(2, Timestamp.from(JavaInstant.ofEpochSecond(endTime.epochSeconds)))
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val total = rs.getInt("total")
                        val sent = rs.getInt("sent")
                        val received = rs.getInt("received")
                        val avgSize = rs.getLong("avg_size")

                        // Get time series
                        val timeSeries = getTimeSeries(conn, startTime, endTime, dateFormat)

                        MessageStatistics(
                            totalMessages = total,
                            sentMessages = sent,
                            receivedMessages = received,
                            averageMessageSize = avgSize,
                            timeSeries = timeSeries
                        )
                    } else {
                        MessageStatistics(0, 0, 0, 0, emptyList())
                    }
                }
            }
        }
    }

    override suspend fun getTrafficPatterns(
        startTime: Instant,
        endTime: Instant
    ): TrafficPatterns = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            // Get hourly distribution
            conn.prepareStatement("""
                SELECT
                    EXTRACT(HOUR FROM created_time::timestamp) as hour,
                    COUNT(*) as count
                FROM didcomm_messages
                WHERE created_time >= ? AND created_time <= ?
                GROUP BY hour
                ORDER BY count DESC
                LIMIT 5
            """).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(JavaInstant.ofEpochSecond(startTime.epochSeconds)))
                stmt.setTimestamp(2, Timestamp.from(JavaInstant.ofEpochSecond(endTime.epochSeconds)))
                stmt.executeQuery().use { rs ->
                    val peakHours = buildList {
                        while (rs.next()) {
                            add(rs.getInt("hour"))
                        }
                    }

                    // Get average messages per hour
                    val durationSeconds = endTime.epochSeconds - startTime.epochSeconds
                    val totalHours = (durationSeconds / 3600.0).toInt().coerceAtLeast(1)
                    val totalMessages = conn.prepareStatement("""
                        SELECT COUNT(*) FROM didcomm_messages
                        WHERE created_time >= ? AND created_time <= ?
                    """).use { stmt2 ->
                        stmt2.setTimestamp(1, Timestamp.from(JavaInstant.ofEpochSecond(startTime.epochSeconds)))
                        stmt2.setTimestamp(2, Timestamp.from(JavaInstant.ofEpochSecond(endTime.epochSeconds)))
                        stmt2.executeQuery().use { rs2 ->
                            if (rs2.next()) rs2.getInt(1) else 0
                        }
                    }

                    val avgPerHour = totalMessages.toDouble() / totalHours

                    // Get busiest day
                    val busiestDay = conn.prepareStatement("""
                        SELECT
                            TO_CHAR(created_time::date, 'Day') as day,
                            COUNT(*) as count
                        FROM didcomm_messages
                        WHERE created_time >= ? AND created_time <= ?
                        GROUP BY day
                        ORDER BY count DESC
                        LIMIT 1
                    """).use { stmt2 ->
                        stmt2.setTimestamp(1, Timestamp.from(JavaInstant.ofEpochSecond(startTime.epochSeconds)))
                        stmt2.setTimestamp(2, Timestamp.from(JavaInstant.ofEpochSecond(endTime.epochSeconds)))
                        stmt2.executeQuery().use { rs2 ->
                            if (rs2.next()) rs2.getString("day").trim() else "Unknown"
                        }
                    }

                    TrafficPatterns(
                        peakHours = peakHours,
                        averageMessagesPerHour = avgPerHour,
                        busiestDay = busiestDay
                    )
                }
            }
        }
    }

    override suspend fun getTopDids(
        limit: Int,
        startTime: Instant?,
        endTime: Instant?
    ): List<DidStatistics> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val whereClause = buildString {
                if (startTime != null || endTime != null) {
                    append("WHERE ")
                    val conditions = mutableListOf<String>()
                    if (startTime != null) conditions.add("created_time >= ?")
                    if (endTime != null) conditions.add("created_time <= ?")
                    append(conditions.joinToString(" AND "))
                }
            }

            conn.prepareStatement("""
                SELECT
                    did,
                    COUNT(*) as total,
                    COUNT(CASE WHEN role = 'from' THEN 1 END) as sent,
                    COUNT(CASE WHEN role = 'to' THEN 1 END) as received
                FROM didcomm_message_dids
                $whereClause
                GROUP BY did
                ORDER BY total DESC
                LIMIT ?
            """).use { stmt ->
                var paramIndex = 1
                if (startTime != null) {
                    stmt.setTimestamp(paramIndex++, Timestamp.from(JavaInstant.ofEpochSecond(startTime.epochSeconds)))
                }
                if (endTime != null) {
                    stmt.setTimestamp(paramIndex++, Timestamp.from(JavaInstant.ofEpochSecond(endTime.epochSeconds)))
                }
                stmt.setInt(paramIndex, limit)

                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(DidStatistics(
                                did = rs.getString("did"),
                                messageCount = rs.getInt("total"),
                                sentCount = rs.getInt("sent"),
                                receivedCount = rs.getInt("received")
                            ))
                        }
                    }
                }
            }
        }
    }

    override suspend fun getTypeDistribution(
        startTime: Instant?,
        endTime: Instant?
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val whereClause = buildString {
                if (startTime != null || endTime != null) {
                    append("WHERE ")
                    val conditions = mutableListOf<String>()
                    if (startTime != null) conditions.add("created_time >= ?")
                    if (endTime != null) conditions.add("created_time <= ?")
                    append(conditions.joinToString(" AND "))
                }
            }

            conn.prepareStatement("""
                SELECT type, COUNT(*) as count
                FROM didcomm_messages
                $whereClause
                GROUP BY type
                ORDER BY count DESC
            """).use { stmt ->
                var paramIndex = 1
                if (startTime != null) {
                    stmt.setTimestamp(paramIndex++, Timestamp.from(JavaInstant.ofEpochSecond(startTime.epochSeconds)))
                }
                if (endTime != null) {
                    stmt.setTimestamp(paramIndex++, Timestamp.from(JavaInstant.ofEpochSecond(endTime.epochSeconds)))
                }

                stmt.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            put(rs.getString("type"), rs.getInt("count"))
                        }
                    }
                }
            }
        }
    }

    private fun getTimeSeries(
        conn: java.sql.Connection,
        startTime: Instant,
        endTime: Instant,
        dateFormat: String
    ): List<TimeSeriesPoint> {
        return conn.prepareStatement("""
            SELECT
                TO_CHAR(created_time::timestamp, ?) as time_bucket,
                COUNT(*) as count
            FROM didcomm_messages
            WHERE created_time >= ? AND created_time <= ?
            GROUP BY time_bucket
            ORDER BY time_bucket
        """).use { stmt ->
            stmt.setString(1, dateFormat)
            stmt.setTimestamp(2, Timestamp.from(JavaInstant.ofEpochSecond(startTime.epochSeconds)))
            stmt.setTimestamp(3, Timestamp.from(JavaInstant.ofEpochSecond(endTime.epochSeconds)))
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val timestampStr = rs.getString("time_bucket")
                        val timestamp = try {
                            Instant.parse(timestampStr)
                        } catch (e: Exception) {
                            startTime // Fallback
                        }
                        add(TimeSeriesPoint(
                            timestamp = timestamp,
                            count = rs.getInt("count")
                        ))
                    }
                }
            }
        }
    }
}

