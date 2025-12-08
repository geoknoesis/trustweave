package com.trustweave.credential.internal.util

import java.time.Duration

/**
 * Duration extensions for fluent validity periods.
 * 
 * **Internal utility** - not part of public API.
 * 
 * **Usage:**
 * ```kotlin
 * validFor(1.years)
 * validFor(30.days)
 * validFor(2.hours)
 * validFor(15.minutes)
 * ```
 */
internal val Int.years: Duration get() = Duration.ofDays(this * 365L)
internal val Int.days: Duration get() = Duration.ofDays(this.toLong())
internal val Int.hours: Duration get() = Duration.ofHours(this.toLong())
internal val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())

