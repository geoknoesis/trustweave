package org.trustweave.core.util

/**
 * Common constants and utilities for TrustWeave.
 *
 * This object provides shared constants used across the TrustWeave SDK.
 * Use these constants instead of hardcoding values to ensure consistency
 * and make future updates easier.
 *
 * **Example:**
 * ```kotlin
 * val contentType = TrustWeaveConstants.DEFAULT_JSON_MEDIA_TYPE
 * ```
 */
object TrustWeaveConstants {
    /**
     * Default media type for JSON content.
     *
     * Use this constant when setting Content-Type headers or specifying
     * media types for JSON-based APIs.
     *
     * **Value:** `"application/json"`
     */
    const val DEFAULT_JSON_MEDIA_TYPE = "application/json"
}

