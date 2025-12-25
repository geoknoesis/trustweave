package org.trustweave.core.identifiers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Base Internationalized Resource Identifier (IRI).
 * 
 * Foundation for all identifier classes. Identifiers are opaque identity references
 * following RFC 3987 (IRI specification).
 * 
 * **Supported Schemes:**
 * - `http://`, `https://` - HTTP/HTTPS URLs (subset of URIs)
 * - `did:` - Decentralized Identifiers (URIs)
 * - `urn:` - Uniform Resource Names (URIs)
 * - `#` - Fragment identifiers
 * 
 * **Note**: This is a regular class (not a value class) to allow other identifiers
 * like `Did` to extend it, maintaining "IS-A" relationship semantics.
 * 
 * **Example:**
 * ```kotlin
 * val iri = Iri("https://example.com/resource#fragment")
 * val did = Did("did:key:z6Mk...")  // Did extends Iri
 * ```
 */
@Serializable(with = IriSerializer::class)
open class Iri(val value: String) {
    init {
        require(value.isNotBlank()) {
            "IRI cannot be blank"
        }
        require(isValidIri(value)) {
            "Invalid IRI format: '$value'"
        }
    }
    
    /**
     * Check if this IRI represents a URI (has a scheme).
     * 
     * All IRIs with a scheme are URIs. This includes URLs, URNs, DIDs, etc.
     * Fragment-only IRIs (starting with #) are not URIs.
     */
    val isUri: Boolean
        get() {
            val colonIndex = value.indexOf(':')
            return colonIndex > 0 && !value.startsWith("#")
        }
    
    /**
     * Check if this IRI represents an HTTP/HTTPS URL.
     * 
     * This checks specifically for web URLs (http:// or https://).
     * Scheme comparison is case-insensitive per RFC 3987.
     * 
     * **Note:** All URLs are URIs, but not all URIs are URLs. 
     * DIDs and URNs are URIs but not URLs.
     */
    val isHttpUrl: Boolean
        get() {
            val schemeLower = scheme.lowercase()
            return schemeLower == "http" || schemeLower == "https"
        }
    
    /**
     * Check if this IRI represents a URN (starts with urn:).
     * Scheme comparison is case-insensitive per RFC 3987.
     */
    val isUrn: Boolean
        get() = scheme.equals("urn", ignoreCase = true)
    
    /**
     * Check if this IRI represents a DID (starts with did:).
     * Scheme comparison is case-insensitive per RFC 3987.
     */
    val isDid: Boolean
        get() = scheme.equals("did", ignoreCase = true)
    
    /**
     * Get the scheme of this IRI (e.g., "http", "https", "did", "urn").
     * 
     * Returns empty string for fragment-only IRIs (starting with #) or relative IRIs.
     */
    val scheme: String
        get() {
            val colonIndex = value.indexOf(':')
            return if (colonIndex > 0) value.substring(0, colonIndex) else ""
        }
    
    /**
     * Parse fragment from IRI if present (e.g., "frag" from "https://example.com#frag").
     * 
     * Returns null if no fragment is present, or if fragment is empty (e.g., "https://example.com#").
     */
    val fragment: String?
        get() {
            val fragmentIndex = value.indexOf('#')
            if (fragmentIndex < 0) return null
            val fragmentValue = value.substring(fragmentIndex + 1)
            return fragmentValue.takeIf { it.isNotEmpty() }
        }
    
    /**
     * Get IRI without fragment.
     * 
     * If no fragment is present, returns this instance (no new object created).
     * If fragment is present, creates a new Iri instance without the fragment.
     */
    val withoutFragment: Iri
        get() {
            val fragmentIndex = value.indexOf('#')
            return if (fragmentIndex >= 0) {
                Iri(value.substring(0, fragmentIndex))
            } else {
                this  // No fragment, return self
            }
        }
    
    override fun toString(): String = value
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Iri) return false
        return value == other.value
    }
    
    override fun hashCode(): Int = value.hashCode()
    
    companion object {
        // Compiled regex patterns for performance
        private val SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$")
        private val RELATIVE_PATH_PATTERN = Regex("^[a-zA-Z0-9._~-]+(/[a-zA-Z0-9._~-]*)*$")
        
        /**
         * Validates IRI format according to RFC 3987.
         * 
         * Allows:
         * - Absolute IRIs with schemes (http, https, did, urn, etc.)
         * - Fragment-only IRIs (relative, starting with #)
         * - Relative path IRIs (no scheme, path-like format)
         * 
         * Note: This is a basic validation. Full RFC 3987 compliance would require
         * more complex parsing including Unicode normalization and percent-encoding validation.
         * 
         * **Validation Logic:**
         * 1. Fragment-only IRIs (starting with #) are always valid
         * 2. IRIs without colons are validated as relative paths
         * 3. IRIs with colons must have a valid scheme (letter followed by alphanumeric/+-.)
         * 4. Scheme must be followed by at least one character
         */
        private fun isValidIri(value: String): Boolean {
            // Step 1: Fragment-only IRIs (relative) are always valid per RFC 3987
            if (value.startsWith("#")) return true
            
            // Step 2: Find the colon that separates scheme from the rest
            val colonIndex = value.indexOf(':')
            
            // Step 3: If no colon found, validate as relative path IRI
            if (colonIndex < 0) {
                // Relative IRI - must match path-like format (alphanumeric, dots, dashes, slashes)
                return value.matches(RELATIVE_PATH_PATTERN)
            }
            
            // Step 4: Extract and validate scheme (everything before the colon)
            val scheme = value.substring(0, colonIndex)
            // Scheme must: start with a letter, followed by letters/numbers/+/./-
            if (scheme.isEmpty() || !scheme.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*$"))) {
                return false
            }
            
            // Step 5: Scheme must be followed by at least one character (can't be "scheme:")
            if (value.length <= colonIndex + 1) return false
            
            // Step 6: Final validation - ensure entire IRI matches scheme pattern
            return value.matches(SCHEME_PATTERN)
        }
    }
}

/**
 * Custom serializer for Iri that serializes as String in JSON.
 * Required because Iri has validation in init block.
 */
object IriSerializer : KSerializer<Iri> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Iri", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Iri) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): Iri {
        val string = decoder.decodeString()
        return try {
            Iri(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize Iri: ${e.message}",
                e
            )
        }
    }
}

/**
 * Key identifier fragment.
 * 
 * Used for relative key references (e.g., "#key-1" or "key-1").
 * Can be combined with a DID to form a full verification method ID.
 * 
 * **Example:**
 * ```kotlin
 * val keyId = KeyId("key-1")
 * val vmId = VerificationMethodId(did, keyId)  // "did:key:z6Mk...#key-1"
 * ```
 */
@Serializable(with = KeyIdSerializer::class)
@JvmInline
value class KeyId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "Key ID cannot be blank"
        }
        require(!value.any { it.isWhitespace() }) {
            "Key ID cannot contain whitespace"
        }
    }
    
    /**
     * Check if this is a fragment identifier (starts with #).
     */
    val isFragment: Boolean
        get() = value.startsWith("#")
    
    /**
     * Get fragment value without #.
     */
    val fragmentValue: String
        get() = if (isFragment) value.substring(1) else value
    
    override fun toString(): String = value
}

object KeyIdSerializer : KSerializer<KeyId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("KeyId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: KeyId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): KeyId {
        val string = decoder.decodeString()
        return try {
            KeyId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize KeyId: ${e.message}",
                e
            )
        }
    }
}

// ============================================================================
// Extension Functions for Safe Parsing
// ============================================================================

/**
 * Safe parsing: Convert String to Iri, returns null if invalid.
 * 
 * This extension function provides a null-safe alternative to the Iri constructor,
 * which throws [IllegalArgumentException] for invalid input.
 * 
 * **Example:**
 * ```kotlin
 * val iri = "https://example.com".toIriOrNull()  // Returns Iri
 * val invalid = ":invalid".toIriOrNull()          // Returns null
 * 
 * // Use with safe call operator
 * iri?.let { println("Valid IRI: ${it.value}") }
 * ```
 */
fun String.toIriOrNull(): Iri? = 
    try { Iri(this) } catch (e: IllegalArgumentException) { null }

/**
 * Safe parsing: Convert String to KeyId, returns null if invalid.
 * 
 * This extension function provides a null-safe alternative to the KeyId constructor,
 * which throws [IllegalArgumentException] for invalid input (e.g., blank strings or
 * strings containing whitespace).
 * 
 * **Example:**
 * ```kotlin
 * val keyId = "key-1".toKeyIdOrNull()        // Returns KeyId
 * val invalid = "key 1".toKeyIdOrNull()      // Returns null (contains space)
 * val blank = "   ".toKeyIdOrNull()          // Returns null (blank)
 * 
 * // Use with safe call operator
 * keyId?.let { println("Valid KeyId: ${it.value}") }
 * ```
 */
fun String.toKeyIdOrNull(): KeyId? = 
    try { KeyId(this) } catch (e: IllegalArgumentException) { null }

