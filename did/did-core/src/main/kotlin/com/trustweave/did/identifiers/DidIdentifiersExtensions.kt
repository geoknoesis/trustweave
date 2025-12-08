package com.trustweave.did.identifiers

/**
 * Extension functions for safe parsing of DID-related identifiers.
 */

/**
 * Safe parsing: Convert String to Did, returns null if invalid.
 */
inline fun String.toDidOrNull(): Did? = 
    try { Did(this) } catch (e: IllegalArgumentException) { null }

/**
 * Safe parsing: Convert String to VerificationMethodId, returns null if invalid.
 */
inline fun String.toVerificationMethodIdOrNull(baseDid: Did? = null): VerificationMethodId? = 
    try { VerificationMethodId.parse(this, baseDid) } catch (e: IllegalArgumentException) { null }

/**
 * Type narrowing: Convert Iri to Did if it represents a DID.
 */
fun com.trustweave.core.identifiers.Iri.asDidOrNull(): Did? = 
    if (isDid) try { Did(value) } catch (e: IllegalArgumentException) { null } else null

/**
 * Require Did: Convert Iri to Did, throws if not a DID.
 */
fun com.trustweave.core.identifiers.Iri.requireDid(): Did = 
    asDidOrNull() ?: throw IllegalArgumentException("IRI '$value' is not a valid DID")

