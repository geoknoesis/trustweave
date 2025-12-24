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
 * - `http://`, `https://` - URIs/URLs
 * - `did:` - Decentralized Identifiers
 * - `urn:` - Uniform Resource Names
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
     * Check if this IRI represents a URI/URL (starts with http:// or https://).
     */
    val isUri: Boolean
        get() = value.startsWith("http://") || value.startsWith("https://")
    
    /**
     * Check if this IRI represents a URN (starts with urn:).
     */
    val isUrn: Boolean
        get() = value.startsWith("urn:")
    
    /**
     * Check if this IRI represents a DID (starts with did:).
     */
    val isDid: Boolean
        get() = value.startsWith("did:")
    
    /**
     * Get the scheme of this IRI (e.g., "http", "https", "did", "urn").
     */
    val scheme: String
        get() = value.substringBefore(':')
    
    /**
     * Parse fragment from IRI if present (e.g., "frag" from "https://example.com#frag").
     */
    val fragment: String?
        get() = value.substringAfter("#", "")
            .takeIf { it.isNotEmpty() && value.contains("#") }
    
    /**
     * Get IRI without fragment.
     */
    val withoutFragment: Iri
        get() = Iri(value.substringBefore("#"))
    
    override fun toString(): String = value
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Iri) return false
        return value == other.value
    }
    
    override fun hashCode(): Int = value.hashCode()
    
    companion object {
        /**
         * Basic IRI validation - allows common schemes used in Web-of-Trust.
         */
        private fun isValidIri(value: String): Boolean {
            // Allow: http, https, did, urn, and other common schemes
            // Also allow relative IRIs (no scheme) for fragments like "#key-1"
            return value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$")) ||
                   value.startsWith("#") ||
                   value.matches(Regex("^[a-zA-Z0-9._~-]+(/[a-zA-Z0-9._~-]*)*$"))
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
        require(!value.contains(" ") && !value.contains("\n")) {
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

