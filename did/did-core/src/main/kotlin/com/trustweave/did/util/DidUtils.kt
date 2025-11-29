package com.trustweave.did.util

/**
 * Utility functions for DID operations.
 */

/**
 * Normalizes a key ID by extracting the fragment identifier.
 *
 * Key IDs can be provided in multiple formats:
 * - Full DID URL: `did:key:z6Mk...#key-1` → returns `key-1`
 * - Fragment only: `#key-1` → returns `key-1`
 * - Plain key ID: `key-1` → returns `key-1`
 *
 * **Example:**
 * ```kotlin
 * normalizeKeyId("did:key:z6Mk...#key-1") // returns "key-1"
 * normalizeKeyId("#key-1")                // returns "key-1"
 * normalizeKeyId("key-1")                  // returns "key-1"
 * normalizeKeyId("did:key:z6Mk...")       // returns "did:key:z6Mk..."
 * ```
 *
 * @param keyId The key ID to normalize (may include DID URL or fragment)
 * @return The normalized key ID (fragment part if present, otherwise the original keyId)
 */
fun normalizeKeyId(keyId: String): String {
    if (keyId.isEmpty()) return keyId

    val fragmentIndex = keyId.indexOf('#')
    return if (fragmentIndex >= 0 && fragmentIndex < keyId.length - 1) {
        // Has # and content after it
        keyId.substring(fragmentIndex + 1)
    } else {
        // No # or # is at the end (edge case)
        keyId
    }
}

