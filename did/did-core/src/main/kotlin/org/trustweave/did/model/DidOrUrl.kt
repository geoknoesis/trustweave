package org.trustweave.did.model

import org.trustweave.did.identifiers.Did
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URI

/**
 * An entry for [DidDocument.alsoKnownAs] per W3C DID / Controlled Identifiers:
 * each value is either a **DID** (`did:…`) or a **non-DID URL** (absolute URI, e.g. `https://…`).
 *
 * JSON serializes as a single string (same as the wire format).
 */
@Serializable(with = DidOrUrlSerializer::class)
sealed class DidOrUrl {

    /** A decentralized identifier. */
    data class AsDid(val did: Did) : DidOrUrl()

    /**
     * A URL per typical `alsoKnownAs` usage (absolute URI with a scheme, not a `did:` identifier).
     */
    data class AsUrl(val url: String) : DidOrUrl()

    fun toStringValue(): String = when (this) {
        is AsDid -> did.value
        is AsUrl -> url
    }

    companion object {

        /**
         * Parses a string into [AsDid] if it is a valid DID, otherwise as [AsUrl] if it is a valid absolute URI.
         *
         * @throws IllegalArgumentException if the string is blank or neither a valid DID nor a valid absolute URI.
         */
        fun parse(raw: String): DidOrUrl {
            val s = raw.trim()
            require(s.isNotEmpty()) { "alsoKnownAs entry cannot be empty" }
            if (s.startsWith("did:")) {
                return AsDid(Did(s))
            }
            val uri = try {
                URI(s)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid alsoKnownAs URL: $raw", e)
            }
            require(uri.isAbsolute) { "alsoKnownAs URL must be absolute: $raw" }
            require(!uri.scheme.isNullOrEmpty()) { "alsoKnownAs URL must have a scheme: $raw" }
            return AsUrl(s)
        }

        fun tryParse(raw: String): DidOrUrl? = try {
            parse(raw)
        } catch (_: Exception) {
            null
        }
    }
}

object DidOrUrlSerializer : KSerializer<DidOrUrl> {
    override val descriptor = PrimitiveSerialDescriptor("DidOrUrl", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DidOrUrl) {
        encoder.encodeString(value.toStringValue())
    }

    override fun deserialize(decoder: Decoder): DidOrUrl {
        return DidOrUrl.parse(decoder.decodeString())
    }
}
