package com.trustweave.core.identifiers

/**
 * Extension functions for safe parsing of base identifiers.
 * Returns null instead of throwing exceptions.
 */

/**
 * Safe parsing: Convert String to Iri, returns null if invalid.
 */
inline fun String.toIriOrNull(): Iri? = 
    try { Iri(this) } catch (e: IllegalArgumentException) { null }

/**
 * Safe parsing: Convert String to KeyId, returns null if invalid.
 */
inline fun String.toKeyIdOrNull(): KeyId? = 
    try { KeyId(this) } catch (e: IllegalArgumentException) { null }

