package com.trustweave.credential.revocation

import java.time.Duration
import java.time.Instant

/**
 * Strategy for determining when to anchor status lists to blockchain.
 * 
 * Different strategies optimize for different use cases:
 * - **Periodic**: Anchor on a schedule (hourly, daily)
 * - **Lazy**: Anchor only when verification is requested
 * - **Hybrid**: Combine periodic and lazy strategies
 */
sealed interface AnchorStrategy {
    /**
     * Determines if a status list should be anchored to blockchain.
     * 
     * @param statusListId The status list ID
     * @param lastAnchorTime When the status list was last anchored (null if never)
     * @param updateCount Number of updates since last anchor
     * @param lastUpdateTime When the status list was last updated
     * @return true if the status list should be anchored now
     */
    suspend fun shouldAnchor(
        statusListId: String,
        lastAnchorTime: Instant?,
        updateCount: Int,
        lastUpdateTime: Instant
    ): Boolean
}

/**
 * Periodic anchoring strategy.
 * 
 * Anchors status lists on a schedule (time-based) or after a certain number of updates.
 * 
 * **Example:**
 * ```kotlin
 * // Anchor every hour or after 100 updates
 * val strategy = PeriodicAnchorStrategy(
 *     interval = Duration.ofHours(1),
 *     maxUpdates = 100
 * )
 * ```
 * 
 * @param interval Maximum time between anchors
 * @param maxUpdates Maximum number of updates before forcing an anchor
 */
data class PeriodicAnchorStrategy(
    val interval: Duration = Duration.ofHours(1),
    val maxUpdates: Int = 100
) : AnchorStrategy {
    
    override suspend fun shouldAnchor(
        statusListId: String,
        lastAnchorTime: Instant?,
        updateCount: Int,
        lastUpdateTime: Instant
    ): Boolean {
        // If never anchored, anchor if we have updates
        if (lastAnchorTime == null) {
            return updateCount > 0
        }
        
        // Check if max updates threshold reached
        if (updateCount >= maxUpdates) {
            return true
        }
        
        // Check if time interval threshold reached
        val timeSinceAnchor = Duration.between(lastAnchorTime, Instant.now())
        if (timeSinceAnchor >= interval) {
            return true
        }
        
        return false
    }
}

/**
 * Lazy anchoring strategy.
 * 
 * Only anchors status lists when verification is requested (on-demand).
 * This minimizes blockchain transactions but may have higher latency for first verification.
 * 
 * **Example:**
 * ```kotlin
 * // Anchor only when verification is requested
 * val strategy = LazyAnchorStrategy(
 *     maxStaleness = Duration.ofDays(1) // Force anchor if older than 1 day
 * )
 * ```
 * 
 * @param maxStaleness Maximum age before forcing an anchor (null = never force)
 */
data class LazyAnchorStrategy(
    val maxStaleness: Duration? = Duration.ofDays(1)
) : AnchorStrategy {
    
    override suspend fun shouldAnchor(
        statusListId: String,
        lastAnchorTime: Instant?,
        updateCount: Int,
        lastUpdateTime: Instant
    ): Boolean {
        // If never anchored and we have updates, anchor
        if (lastAnchorTime == null) {
            return updateCount > 0
        }
        
        // If max staleness is set and exceeded, force anchor
        if (maxStaleness != null) {
            val age = Duration.between(lastAnchorTime, Instant.now())
            if (age >= maxStaleness) {
                return true
            }
        }
        
        // Otherwise, don't anchor automatically (only on verification request)
        return false
    }
}

/**
 * Hybrid anchoring strategy.
 * 
 * Combines periodic anchoring with on-demand anchoring for critical verifications.
 * 
 * **Example:**
 * ```kotlin
 * // Anchor every hour, but also anchor on-demand for verifications
 * val strategy = HybridAnchorStrategy(
 *     periodicInterval = Duration.ofHours(1),
 *     maxUpdates = 100,
 *     forceAnchorOnVerify = true
 * )
 * ```
 * 
 * @param periodicInterval Maximum time between periodic anchors
 * @param maxUpdates Maximum number of updates before forcing an anchor
 * @param forceAnchorOnVerify Whether to anchor when verification is requested (if stale)
 */
data class HybridAnchorStrategy(
    val periodicInterval: Duration = Duration.ofHours(1),
    val maxUpdates: Int = 100,
    val forceAnchorOnVerify: Boolean = true
) : AnchorStrategy {
    
    override suspend fun shouldAnchor(
        statusListId: String,
        lastAnchorTime: Instant?,
        updateCount: Int,
        lastUpdateTime: Instant
    ): Boolean {
        // If never anchored, anchor if we have updates
        if (lastAnchorTime == null) {
            return updateCount > 0
        }
        
        // Check periodic thresholds
        if (updateCount >= maxUpdates) {
            return true
        }
        
        val timeSinceAnchor = Duration.between(lastAnchorTime, Instant.now())
        if (timeSinceAnchor >= periodicInterval) {
            return true
        }
        
        // Note: forceAnchorOnVerify is handled separately in checkRevocationOnChain
        return false
    }
    
    /**
     * Determines if anchoring should be forced for verification.
     * 
     * @param lastAnchorTime When the status list was last anchored
     * @param lastUpdateTime When the status list was last updated
     * @return true if anchoring should be forced for verification
     */
    fun shouldForceAnchorForVerify(
        lastAnchorTime: Instant?,
        lastUpdateTime: Instant
    ): Boolean {
        if (!forceAnchorOnVerify) {
            return false
        }
        
        // If never anchored, force anchor
        if (lastAnchorTime == null) {
            return true
        }
        
        // If there are updates since last anchor, force anchor
        if (lastUpdateTime.isAfter(lastAnchorTime)) {
            return true
        }
        
        return false
    }
}

/**
 * Data class to track pending anchors for status lists.
 * 
 * @param statusListId The status list ID
 * @param lastUpdate Time of last update
 * @param updateCount Number of updates since last anchor
 * @param lastAnchorTime Time of last anchor (null if never anchored)
 */
data class PendingAnchor(
    val statusListId: String,
    val lastUpdate: Instant,
    val updateCount: Int,
    val lastAnchorTime: Instant? = null
)

