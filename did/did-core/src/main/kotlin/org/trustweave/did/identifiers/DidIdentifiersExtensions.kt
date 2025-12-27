package org.trustweave.did.identifiers

import org.trustweave.did.model.VerificationMethod

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
fun org.trustweave.core.identifiers.Iri.asDidOrNull(): Did? =
    if (isDid) try { Did(value) } catch (e: IllegalArgumentException) { null } else null

/**
 * Require Did: Convert Iri to Did, throws if not a DID.
 */
fun org.trustweave.core.identifiers.Iri.requireDid(): Did =
    asDidOrNull() ?: throw IllegalArgumentException("IRI '$value' is not a valid DID")

/**
 * Extract the key ID string from a verification method ID.
 * 
 * Returns the key ID fragment without the '#' prefix (e.g., "key-1").
 * This is the type-safe alternative to using `substringAfter("#")` on the string value.
 * 
 * **Example:**
 * ```kotlin
 * val vmId = VerificationMethodId(did, KeyId("#key-1"))
 * val keyId = vmId.extractKeyId()  // Returns "key-1"
 * ```
 * 
 * @return The key ID string without the fragment prefix
 */
fun VerificationMethodId.extractKeyId(): String {
    return this.keyId.fragmentValue
}

/**
 * Extract the key ID string from a verification method.
 * 
 * Returns the key ID fragment without the '#' prefix (e.g., "key-1").
 * This is a convenience extension that extracts the key ID from the verification method's ID.
 * 
 * **Example:**
 * ```kotlin
 * val verificationMethod = document.verificationMethod.firstOrNull()
 * val keyId = verificationMethod?.extractKeyId()  // Returns "key-1" or null
 * ```
 * 
 * @return The key ID string without the fragment prefix, or null if the method is null
 */
fun VerificationMethod?.extractKeyId(): String? {
    return this?.id?.extractKeyId()
}

