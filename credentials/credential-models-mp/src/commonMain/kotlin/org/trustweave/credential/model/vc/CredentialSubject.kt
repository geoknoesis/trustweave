package org.trustweave.credential.model.vc

import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * VC Credential Subject - contains an optional IRI id (DID, URI, URN, etc.) and claims.
 *
 * Per W3C VC Data Model, credentialSubject.id can be any IRI, not just a DID.
 * Leverages the common Iri base class for identifier support.
 *
 * **Note:** Per W3C VC 2.0 §4.4, the `id` field is **optional**. Anonymous-subject credentials
 * (where `credentialSubject` carries only claims and no `id`) are spec-valid VC 2.0 documents.
 * Use `id = null` to represent such credentials.
 *
 * **Examples:**
 * ```kotlin
 * // DID subject (most common)
 * val did = Did("did:key:z6Mk...")
 * val subject = CredentialSubject.fromDid(did, claims = mapOf(
 *     "degree" to buildJsonObject {
 *         put("type", "BachelorDegree")
 *         put("name", "Bachelor of Science")
 *     }
 * ))
 *
 * // URI subject
 * val uri = Iri("https://example.com/users/123")
 * val subject = CredentialSubject.fromIri(uri, claims = mapOf(...))
 *
 * // URN subject
 * val urn = Iri("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
 * val subject = CredentialSubject.fromIri(urn, claims = mapOf(...))
 *
 * // Anonymous subject (VC 2.0 §4.4)
 * val anon = CredentialSubject(id = null, claims = mapOf(...))
 * ```
 */
@Serializable(with = CredentialSubjectSerializer::class)
data class CredentialSubject(
    val id: Iri? = null, // Subject IRI (DID, URI, URN, etc.) - optional per W3C VC 2.0 §4.4
    val claims: Map<String, JsonElement> = emptyMap() // Additional claims
) {
    /**
     * Check if the subject ID is a DID.
     */
    val isDid: Boolean
        get() = id?.isDid ?: false

    /**
     * Check if the subject ID is an HTTP/HTTPS URL.
     *
     * **Note:** All URLs are URIs, but not all URIs are URLs.
     * DIDs and URNs are URIs but not URLs.
     */
    val isHttpUrl: Boolean
        get() = id?.isHttpUrl ?: false

    /**
     * Check if the subject ID is a URN.
     */
    val isUrn: Boolean
        get() = id?.isUrn ?: false

    /**
     * Convenience accessor for claims.
     */
    operator fun get(key: String): JsonElement? = claims[key]

    companion object {
        /**
         * Create CredentialSubject from DID (convenience method).
         */
        fun fromDid(did: Did, claims: Map<String, JsonElement> = emptyMap()): CredentialSubject {
            return CredentialSubject(id = did, claims = claims)  // Did extends Iri
        }

        /**
         * Create CredentialSubject from IRI string.
         */
        fun fromIri(iri: String, claims: Map<String, JsonElement> = emptyMap()): CredentialSubject {
            return CredentialSubject(id = Iri(iri), claims = claims)
        }

        /**
         * Create CredentialSubject from IRI.
         */
        fun fromIri(iri: Iri, claims: Map<String, JsonElement> = emptyMap()): CredentialSubject {
            return CredentialSubject(id = iri, claims = claims)
        }
    }
}

/**
 * Symmetric JSON serializer for [CredentialSubject].
 *
 * The W3C VC Data Model requires `credentialSubject` to carry its claims **flattened**
 * at the top level (alongside an optional `id`), e.g.
 * `{"id":"did:example:s","name":"Jane","degree":"BS"}`. The default generated serializer
 * would instead nest them under `"claims": {...}`, which external verifiers reject.
 *
 * - **serialize:** emits `id` (only when non-null) as a plain IRI string, then each entry of
 *   `claims` at the top level. A claim literally named `"id"` is skipped so it cannot collide
 *   with / duplicate the subject id key.
 * - **deserialize:** reads the JSON object, lifts `id` out (as `Iri`), and collects every other
 *   key into `claims`. This is the symmetric counterpart so flattened VCs round-trip and incoming
 *   flattened claims are no longer silently dropped.
 *
 * JSON-only: every wire-format path (`toJsonLd` / `toJwt` / `toCbor` / VC-API) serializes via
 * kotlinx `Json` first, so a JSON-targeted serializer suffices; the CBOR step (Jackson) merely
 * re-encodes the already-flattened JSON.
 */
@OptIn(ExperimentalSerializationApi::class)
object CredentialSubjectSerializer : KSerializer<CredentialSubject> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("org.trustweave.credential.model.vc.CredentialSubject")

    override fun serialize(encoder: Encoder, value: CredentialSubject) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("CredentialSubject can only be serialized to JSON")
        val obj = buildJsonObject {
            value.id?.let { put("id", it.value) }
            value.claims.forEach { (k, v) -> if (k != "id") put(k, v) }
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): CredentialSubject {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("CredentialSubject can only be deserialized from JSON")
        val element = jsonDecoder.decodeJsonElement()
        val obj = element as? JsonObject
            ?: throw SerializationException(
                "credentialSubject must be a JSON object; an array of subjects is not supported",
            )
        val id = when (val idEl = obj["id"]) {
            null, JsonNull -> null
            is JsonPrimitive -> if (idEl.isString) {
                try {
                    Iri(idEl.content)
                } catch (e: IllegalArgumentException) {
                    throw SerializationException("credentialSubject.id is not a valid IRI: ${idEl.content}", e)
                }
            } else {
                throw SerializationException("credentialSubject.id must be a string IRI")
            }
            else -> throw SerializationException(
                "credentialSubject.id must be a string IRI, got ${idEl::class.simpleName}",
            )
        }
        val claims = obj.filterKeys { it != "id" }
        return CredentialSubject(id = id, claims = claims)
    }
}

