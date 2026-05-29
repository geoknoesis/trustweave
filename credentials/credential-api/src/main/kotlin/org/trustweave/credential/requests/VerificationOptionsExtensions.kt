package org.trustweave.credential.requests

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Preset verification options for common use cases.
 * 
 * Provides ready-to-use [VerificationOptions] configurations for different
 * security and validation requirements.
 * 
 * **Example Usage:**
 * ```kotlin
 * val result = service.verify(
 *     credential,
 *     options = VerificationOptionPresets.strict()
 * )
 * ```
 */
object VerificationOptionPresets {
    /**
     * Strict verification - checks everything.
     * 
     * Use when you need maximum security and validation.
     */
    fun strict(): VerificationOptions = VerificationOptions(
        checkRevocation = true,
        checkExpiration = true,
        checkNotBefore = true,
        resolveIssuerDid = true,
        validateSchema = true,
        clockSkewTolerance = 1.minutes  // Tight tolerance
    )
    
    /**
     * Loose verification - minimal checks.
     * 
     * Use for testing or when you only care about cryptographic validity.
     */
    fun loose(): VerificationOptions = VerificationOptions(
        checkRevocation = false,
        checkExpiration = false,
        checkNotBefore = false,
        resolveIssuerDid = false,
        validateSchema = false,
        clockSkewTolerance = 10.minutes  // Lenient tolerance
    )
    
    /**
     * Standard verification - balanced checks.
     * 
     * Good default for most production use cases.
     */
    fun standard(): VerificationOptions = VerificationOptions(
        checkRevocation = true,
        checkExpiration = true,
        checkNotBefore = true,
        resolveIssuerDid = true,
        validateSchema = false,  // Schema validation is expensive
        clockSkewTolerance = 5.minutes
    )
}

