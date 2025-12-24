package org.trustweave.credential.didcomm.crypto.rotation

import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Defines when keys should be rotated.
 */
interface KeyRotationPolicy {
    /**
     * Determines if a key should be rotated.
     *
     * @param keyId Key ID
     * @param keyMetadata Key metadata
     * @return true if key should be rotated
     */
    suspend fun shouldRotate(keyId: String, keyMetadata: KeyMetadata): Boolean
}

/**
 * Key metadata for rotation decisions.
 */
data class KeyMetadata(
    val keyId: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val usageCount: Int = 0
)

/**
 * Time-based rotation policy.
 *
 * Rotates keys after a specified number of days.
 */
class TimeBasedRotationPolicy(
    private val maxAgeDays: Int = 90
) : KeyRotationPolicy {

    override suspend fun shouldRotate(
        keyId: String,
        keyMetadata: KeyMetadata
    ): Boolean {
        val now = Clock.System.now()
        val ageSeconds = now.epochSeconds - keyMetadata.createdAt.epochSeconds
        val age = Duration.parse("PT${ageSeconds}S")
        return age.inWholeDays >= maxAgeDays
    }
}

/**
 * Usage-based rotation policy.
 *
 * Rotates keys after a specified number of uses.
 */
class UsageBasedRotationPolicy(
    private val maxUsageCount: Int = 10000
) : KeyRotationPolicy {

    override suspend fun shouldRotate(
        keyId: String,
        keyMetadata: KeyMetadata
    ): Boolean {
        return keyMetadata.usageCount >= maxUsageCount
    }
}

/**
 * Composite rotation policy.
 *
 * Rotates if any policy says so.
 */
class CompositeRotationPolicy(
    private val policies: List<KeyRotationPolicy>
) : KeyRotationPolicy {

    override suspend fun shouldRotate(
        keyId: String,
        keyMetadata: KeyMetadata
    ): Boolean {
        return policies.any { it.shouldRotate(keyId, keyMetadata) }
    }
}

/**
 * Custom rotation policy using a predicate.
 */
class CustomRotationPolicy(
    private val predicate: suspend (String, KeyMetadata) -> Boolean
) : KeyRotationPolicy {

    override suspend fun shouldRotate(
        keyId: String,
        keyMetadata: KeyMetadata
    ): Boolean {
        return predicate(keyId, keyMetadata)
    }
}

