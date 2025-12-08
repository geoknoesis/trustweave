package com.trustweave.did.identifiers

import com.trustweave.core.identifiers.Iri
import com.trustweave.core.identifiers.KeyId
import com.trustweave.did.validation.DidValidator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Decentralized Identifier (DID).
 * 
 * Extends Iri with DID-specific validation and parsing.
 * Follows W3C DID Core specification: did:method:identifier
 * 
 * **Inheritance**: `Did extends Iri` - a DID IS-A IRI.
 * 
 * **Example:**
 * ```kotlin
 * val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
 * ```
 */
@Serializable(with = DidSerializer::class)
class Did(
    value: String
) : Iri(value.substringBefore("#")), Comparable<Did> {
    
    init {
        // Use DidValidator for consistent validation logic
        val validation = DidValidator.validateFormat(value)
        require(validation.isValid()) {
            (validation as? com.trustweave.core.util.ValidationResult.Invalid)?.message
                ?: "Invalid DID format: '$value'"
        }
        
        // Additional validation: ensure identifier part is non-empty
        val method = DidValidator.extractMethod(value)
        require(method != null && method.isNotEmpty()) {
            "Invalid DID format: '$value'. Method name cannot be empty"
        }
        val identifier = DidValidator.extractMethodSpecificId(value)
        require(identifier != null && identifier.isNotEmpty()) {
            "Invalid DID format: '$value'. Identifier cannot be empty"
        }
    }
    
    /**
     * Get the DID method from this DID (e.g., "key", "web", "ion").
     * Cached for performance.
     */
    private val _method: String by lazy {
        val parts = this.value.substringAfter("did:").split(":", limit = 2)
        parts.firstOrNull() ?: throw IllegalStateException("Invalid DID: ${this.value}")
    }
    
    val method: String
        get() = _method
    
    /**
     * Get the method-specific identifier.
     */
    val identifier: String
        get() = this.value.substringAfter("did:$method:")
    
    /**
     * Parse DID URL path (e.g., "/path" from "did:web:example.com/path").
     */
    val path: String?
        get() {
            val parts = this.value.split("/", limit = 2)
            return parts.getOrNull(1)
        }
    
    /**
     * Get DID without path or fragment.
     */
    val baseDid: Did
        get() = Did(this.value.substringBefore("/"))
    
    override fun toString(): String = value
    
    /**
     * Operator: did + "fragment" creates VerificationMethodId.
     * 
     * **Example:**
     * ```kotlin
     * val vmId = did + "key-1"  // Creates VerificationMethodId
     * ```
     */
    operator fun plus(fragment: String): VerificationMethodId {
        val keyId = if (fragment.startsWith("#")) KeyId(fragment) else KeyId("#$fragment")
        return VerificationMethodId(this, keyId)
    }
    
    /**
     * Infix: did with "fragment" - more readable alternative.
     * 
     * **Example:**
     * ```kotlin
     * val vmId = did with "key-1"  // More readable than operator +
     * ```
     */
    infix fun with(fragment: String): VerificationMethodId = this + fragment
    
    /**
     * Infix: did with keyId - type-safe alternative.
     * 
     * **Example:**
     * ```kotlin
     * val vmId = did with KeyId("key-1")
     * ```
     */
    infix fun with(keyId: KeyId): VerificationMethodId = VerificationMethodId(this, keyId)
    
    /**
     * Infix: Check if DID belongs to method.
     * 
     * **Example:**
     * ```kotlin
     * if (did isMethod "key") { ... }
     * ```
     */
    infix fun isMethod(method: String): Boolean = this.method == method
    
    /**
     * Comparable: Natural ordering for sorting.
     */
    override fun compareTo(other: Did): Int = value.compareTo(other.value)
}

/**
 * Custom serializer for Did.
 */
object DidSerializer : KSerializer<Did> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Did", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Did) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): Did {
        val string = decoder.decodeString()
        return try {
            Did(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize Did: ${e.message}",
                e
            )
        }
    }
}

/**
 * Full verification method identifier.
 * 
 * Combines a DID with a key ID fragment: "did:key:z6Mk...#key-1"
 * 
 * **Example:**
 * ```kotlin
 * val vmId = VerificationMethodId(
 *     did = Did("did:key:z6Mk..."),
 *     keyId = KeyId("key-1")
 * )
 * // Or using operator:
 * val vmId2 = Did("did:key:z6Mk...") + "key-1"
 * ```
 */
@Serializable(with = VerificationMethodIdSerializer::class)
data class VerificationMethodId(
    val did: Did,
    val keyId: KeyId
) {
    /**
     * The full verification method ID string.
     */
    val value: String
        get() {
            val fragment = if (keyId.isFragment) keyId.value else "#${keyId.value}"
            return "${did.value}$fragment"
        }
    
    override fun toString(): String = value
    
    /**
     * Decompose into components for destructuring.
     */
    fun decompose(): Pair<Did, KeyId> = did to keyId
    
    companion object {
        /**
         * Parse a verification method ID string.
         * 
         * Handles both full IDs ("did:key:z6Mk...#key-1") and relative IDs ("#key-1" when did is known).
         * 
         * @param vmIdString The verification method ID string
         * @param baseDid Optional base DID for relative fragments
         * @return VerificationMethodId instance
         * @throws IllegalArgumentException if the string cannot be parsed
         */
        fun parse(vmIdString: String, baseDid: Did? = null): VerificationMethodId {
            return when {
                vmIdString.startsWith("did:") -> {
                    val parts = vmIdString.split("#", limit = 2)
                    if (parts.size != 2) {
                        throw IllegalArgumentException(
                            "VerificationMethodId must contain '#' fragment: '$vmIdString'"
                        )
                    }
                    VerificationMethodId(
                        did = Did(parts[0]),
                        keyId = KeyId("#${parts[1]}")
                    )
                }
                vmIdString.startsWith("#") && baseDid != null -> {
                    VerificationMethodId(baseDid, KeyId(vmIdString))
                }
                else -> throw IllegalArgumentException(
                    "Cannot parse VerificationMethodId: '$vmIdString'. " +
                    "Must be full DID URL (did:...:#...) or fragment (#...) with baseDid"
                )
            }
        }
    }
}

/**
 * Custom serializer for VerificationMethodId.
 */
object VerificationMethodIdSerializer : KSerializer<VerificationMethodId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("VerificationMethodId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: VerificationMethodId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): VerificationMethodId {
        val string = decoder.decodeString()
        return try {
            VerificationMethodId.parse(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize VerificationMethodId: ${e.message}",
                e
            )
        }
    }
}

/**
 * DID URL with optional path and fragment.
 * Useful for referencing specific resources within a DID document.
 */
@Serializable(with = DidUrlSerializer::class)
@JvmInline
value class DidUrl(val value: String) {
    val did: Did
        get() = Did(value.substringBefore("/").substringBefore("#"))
    
    val fragment: String?
        get() = value.substringAfter("#", "").takeIf { it.isNotEmpty() && value.contains("#") }
    
    val path: String?
        get() {
            val withoutFragment = value.substringBefore("#")
            val parts = withoutFragment.split("/", limit = 2)
            return parts.getOrNull(1)
        }
}

object DidUrlSerializer : KSerializer<DidUrl> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("DidUrl", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: DidUrl) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): DidUrl {
        return DidUrl(decoder.decodeString())
    }
}

