package org.trustweave.credential.didcomm.crypto.rotation

import kotlinx.coroutines.*
import java.time.Duration

/**
 * Scheduled key rotation service.
 *
 * Automatically checks and rotates keys on a schedule.
 *
 * **Example Usage:**
 * ```kotlin
 * val rotationManager = KeyRotationManager(keyStore, kms, policy)
 * val scheduledRotation = ScheduledKeyRotation(
 *     rotationManager = rotationManager,
 *     interval = Duration.ofDays(1)
 * )
 *
 * scheduledRotation.start()
 * // ... later ...
 * scheduledRotation.stop()
 * ```
 */
class ScheduledKeyRotation(
    private val rotationManager: KeyRotationManager,
    private val interval: Duration = Duration.ofDays(1)
) {
    private var rotationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Starts scheduled rotation.
     */
    fun start() {
        if (rotationJob?.isActive == true) {
            return // Already started
        }

        rotationJob = scope.launch {
            while (isActive) {
                try {
                    rotationManager.checkAndRotate()
                } catch (e: Exception) {
                    // Log error, continue
                }
                delay(interval.toMillis())
            }
        }
    }

    /**
     * Stops scheduled rotation.
     */
    fun stop() {
        rotationJob?.cancel()
        rotationJob = null
    }

    /**
     * Checks if rotation is running.
     */
    fun isRunning(): Boolean {
        return rotationJob?.isActive == true
    }
}

