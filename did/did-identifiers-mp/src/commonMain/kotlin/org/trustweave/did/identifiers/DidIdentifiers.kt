package org.trustweave.did.identifiers

import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.validation.DidValidator
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
            (validation as? org.trustweave.core.util.ValidationResult.Invalid)?.message
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
     * Cached for performance using lazy initialization.
     */
    val method: String by lazy {
        val parts = this.value.substringAfter("did:").split(":", limit = 2)
        parts.firstOrNull() ?: throw IllegalStateException("Invalid DID: ${this.value}")
    }

    /**
     * Get the method-specific identifier.
     * Cached for performance.
     */
    val identifier: String by lazy {
        this.value.substringAfter("did:$method:")
    }

    /**
     * Parse DID URL path (e.g., "/path" from "did:web:example.com/path").
     * Cached for performance.
     */
    val path: String? by lazy {
        val parts = this.value.split("/", limit = 2)
        parts.getOrNull(1)
    }

    /**
     * Get DID without path or fragment.
     * Optimized to avoid creating new instance if no path/fragment exists.
     */
    val baseDid: Did
        get() {
            val withoutFragment = this.value.substringBefore("#")
            val withoutPath = withoutFragment.substringBefore("/")
            return if (withoutPath == this.value) {
                // No path or fragment, return this instance
                this
            } else {
                // Path or fragment exists, create new instance
                Did(withoutPath)
            }
        }

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
 * A DID URL: `did:method:id[/path][?query][#fragment]`.
 *
 * Components are split in RFC 3986 order so a query never leaks into [path] and a fragment never
 * leaks into [query]. DID parameters defined by DID Resolution 1.0 §3 are exposed by name.
 */
@Serializable(with = DidUrlSerializer::class)
@JvmInline
value class DidUrl(val value: String) {

    /** The DID portion, with path, query and fragment removed. */
    val did: Did
        get() = Did(value.substringBefore("#").substringBefore("?").substringBefore("/"))

    /** The path component without its leading `/`, or null when absent. */
    val path: String?
        get() {
            val beforeQuery = value.substringBefore("#").substringBefore("?")
            val slash = beforeQuery.indexOf('/')
            return if (slash < 0) null else beforeQuery.substring(slash + 1).takeIf { it.isNotEmpty() }
        }

    /** The raw query component without its leading `?`, or null when absent. */
    val query: String?
        get() {
            val beforeFragment = value.substringBefore("#")
            val mark = beforeFragment.indexOf('?')
            return if (mark < 0) null else beforeFragment.substring(mark + 1).takeIf { it.isNotEmpty() }
        }

    /** The fragment without its leading `#`, or null when absent. */
    val fragment: String?
        get() {
            val hash = value.indexOf('#')
            return if (hash < 0) null else value.substring(hash + 1).takeIf { it.isNotEmpty() }
        }

    /**
     * DID parameters parsed from [query], with percent-encoded octets decoded.
     *
     * When a parameter name repeats, the first occurrence wins; callers that care should check
     * [hasDuplicateParameters], which §3.2.2 identifies as ambiguous input.
     */
    val parameters: Map<String, String>
        get() = query?.split('&')
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) null else percentDecode(pair.substring(0, eq)) to percentDecode(pair.substring(eq + 1))
            }
            ?.reversed()
            ?.toMap()
            ?: emptyMap()

    /** True when a parameter name occurs more than once (§3.2.2: ambiguous input). */
    val hasDuplicateParameters: Boolean
        get() {
            val names = query?.split('&')
                ?.filter { it.isNotEmpty() }
                ?.map { it.substringBefore('=') }
                ?: return false
            return names.size != names.toSet().size
        }

    /** The `service` DID parameter (§3). */
    val service: String? get() = parameters["service"]

    /** The `serviceType` DID parameter (§3). */
    val serviceType: String? get() = parameters["serviceType"]

    /** The `relativeRef` DID parameter (§3). */
    val relativeRef: String? get() = parameters["relativeRef"]

    /** The `versionId` DID parameter (§3, §13.4). */
    val versionId: String? get() = parameters["versionId"]

    /** The `versionTime` DID parameter (§3, §13.4), as its raw datetime string. */
    val versionTime: String? get() = parameters["versionTime"]
}

/**
 * Decodes percent-encoded octets in a DID URL query component.
 *
 * Percent-escapes are accumulated as a byte sequence and decoded as UTF-8, so multi-byte
 * characters (`%C3%A9` -> `e-acute`) survive. Decoding each escape independently as a char would
 * corrupt them. An escape that is not valid hex is left literal.
 */
private fun percentDecode(raw: String): String {
    if (!raw.contains('%')) return raw
    val out = StringBuilder(raw.length)
    val pending = ArrayList<Byte>()

    fun flush() {
        if (pending.isEmpty()) return
        out.append(pending.toByteArray().decodeToString())
        pending.clear()
    }

    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '%' && i + 2 < raw.length) {
            val code = raw.substring(i + 1, i + 3).toIntOrNull(16)
            if (code != null) {
                pending.add(code.toByte())
                i += 3
                continue
            }
        }
        flush()
        out.append(c)
        i++
    }
    flush()
    return out.toString()
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
