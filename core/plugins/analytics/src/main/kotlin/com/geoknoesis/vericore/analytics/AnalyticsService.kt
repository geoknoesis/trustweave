package com.geoknoesis.vericore.analytics

import com.geoknoesis.vericore.metrics.MetricsCollector
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Analytics report data model.
 */
@Serializable
data class AnalyticsReport(
    val period: TimePeriod,
    val startDate: String, // ISO 8601
    val endDate: String, // ISO 8601
    val metrics: ReportMetrics,
    val trends: List<TrendData> = emptyList()
)

/**
 * Time period for analytics.
 */
enum class TimePeriod {
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

/**
 * Report metrics.
 */
@Serializable
data class ReportMetrics(
    val credentialsIssued: Long = 0,
    val credentialsVerified: Long = 0,
    val credentialsRevoked: Long = 0,
    val presentationsCreated: Long = 0,
    val presentationsVerified: Long = 0,
    val didsCreated: Long = 0,
    val keysGenerated: Long = 0,
    val averageVerificationTime: Double? = null,
    val averageIssuanceTime: Double? = null,
    val errorRate: Double = 0.0,
    val topIssuers: List<IssuerStats> = emptyList(),
    val topCredentialTypes: List<CredentialTypeStats> = emptyList()
)

/**
 * Issuer statistics.
 */
@Serializable
data class IssuerStats(
    val issuerDid: String,
    val credentialsIssued: Long,
    val credentialsRevoked: Long
)

/**
 * Credential type statistics.
 */
@Serializable
data class CredentialTypeStats(
    val type: String,
    val count: Long,
    val percentage: Double
)

/**
 * Trend data.
 */
@Serializable
data class TrendData(
    val date: String, // ISO 8601
    val value: Long,
    val label: String
)

/**
 * Analytics service.
 * 
 * Provides analytics and reporting capabilities for VeriCore operations.
 * 
 * **Example Usage:**
 * ```kotlin
 * val analytics = AnalyticsService(metricsCollector)
 * 
 * // Get daily report
 * val report = analytics.generateReport(TimePeriod.DAILY, days = 7)
 * 
 * // Get credential issuance trends
 * val trends = analytics.getIssuanceTrends(days = 30)
 * ```
 */
interface AnalyticsService {
    /**
     * Generate analytics report.
     * 
     * @param period Time period
     * @param days Number of days to analyze (for DAILY period)
     * @return Analytics report
     */
    suspend fun generateReport(period: TimePeriod, days: Int = 30): AnalyticsReport
    
    /**
     * Get credential issuance trends.
     * 
     * @param days Number of days
     * @return List of trend data points
     */
    suspend fun getIssuanceTrends(days: Int = 30): List<TrendData>
    
    /**
     * Get verification trends.
     * 
     * @param days Number of days
     * @return List of trend data points
     */
    suspend fun getVerificationTrends(days: Int = 30): List<TrendData>
    
    /**
     * Get top issuers.
     * 
     * @param limit Number of top issuers
     * @return List of issuer statistics
     */
    suspend fun getTopIssuers(limit: Int = 10): List<IssuerStats>
    
    /**
     * Get top credential types.
     * 
     * @param limit Number of top types
     * @return List of credential type statistics
     */
    suspend fun getTopCredentialTypes(limit: Int = 10): List<CredentialTypeStats>
}

/**
 * Simple analytics service implementation.
 */
class SimpleAnalyticsService(
    private val metricsCollector: MetricsCollector
) : AnalyticsService {
    
    override suspend fun generateReport(period: TimePeriod, days: Int): AnalyticsReport = withContext(Dispatchers.IO) {
        val endDate = Instant.now()
        val startDate = when (period) {
            TimePeriod.DAILY -> endDate.minus(days.toLong(), ChronoUnit.DAYS)
            TimePeriod.WEEKLY -> endDate.minus((days * 7).toLong(), ChronoUnit.DAYS)
            TimePeriod.MONTHLY -> endDate.minus((days * 30).toLong(), ChronoUnit.DAYS)
            else -> endDate.minus(days.toLong(), ChronoUnit.DAYS)
        }
        
        val snapshot = metricsCollector.getSnapshot()
        
        val metrics = ReportMetrics(
            credentialsIssued = snapshot.counters["credentials.issued"] ?: 0L,
            credentialsVerified = snapshot.counters["credentials.verified"] ?: 0L,
            credentialsRevoked = snapshot.counters["credentials.revoked"] ?: 0L,
            presentationsCreated = snapshot.counters["presentations.created"] ?: 0L,
            presentationsVerified = snapshot.counters["presentations.verified"] ?: 0L,
            didsCreated = snapshot.counters["dids.created"] ?: 0L,
            keysGenerated = snapshot.counters["keys.generated"] ?: 0L,
            averageVerificationTime = snapshot.durations["credential.verification"]?.avg,
            averageIssuanceTime = snapshot.durations["credential.issuance"]?.avg,
            errorRate = calculateErrorRate(snapshot),
            topIssuers = getTopIssuers(10),
            topCredentialTypes = getTopCredentialTypes(10)
        )
        
        AnalyticsReport(
            period = period,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            metrics = metrics,
            trends = getIssuanceTrends(days)
        )
    }
    
    override suspend fun getIssuanceTrends(days: Int): List<TrendData> = withContext(Dispatchers.IO) {
        // Simplified - in production would query time-series data
        val trends = mutableListOf<TrendData>()
        val now = Instant.now()
        
        for (i in days downTo 0) {
            val date = now.minus(i.toLong(), ChronoUnit.DAYS)
            trends.add(
                TrendData(
                    date = date.toString(),
                    value = metricsCollector.getCounter("credentials.issued", mapOf("date" to date.toString())),
                    label = "Issued"
                )
            )
        }
        
        trends
    }
    
    override suspend fun getVerificationTrends(days: Int): List<TrendData> = withContext(Dispatchers.IO) {
        val trends = mutableListOf<TrendData>()
        val now = Instant.now()
        
        for (i in days downTo 0) {
            val date = now.minus(i.toLong(), ChronoUnit.DAYS)
            trends.add(
                TrendData(
                    date = date.toString(),
                    value = metricsCollector.getCounter("credentials.verified", mapOf("date" to date.toString())),
                    label = "Verified"
                )
            )
        }
        
        trends
    }
    
    override suspend fun getTopIssuers(limit: Int): List<IssuerStats> = withContext(Dispatchers.IO) {
        // Simplified - in production would aggregate by issuer DID
        emptyList()
    }
    
    override suspend fun getTopCredentialTypes(limit: Int): List<CredentialTypeStats> = withContext(Dispatchers.IO) {
        // Simplified - in production would aggregate by credential type
        emptyList()
    }
    
    private suspend fun calculateErrorRate(snapshot: com.geoknoesis.vericore.metrics.MetricsSnapshot): Double {
        val total = snapshot.counters["operations.total"] ?: 0L
        val errors = snapshot.counters["operations.errors"] ?: 0L
        return if (total > 0) (errors.toDouble() / total.toDouble()) * 100.0 else 0.0
    }
}

