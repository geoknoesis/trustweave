package com.trustweave.credential.identifiers

import com.trustweave.core.identifiers.Iri
import com.trustweave.did.identifiers.Did
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Credential identifier types.
 * 
 * This file declares **strongly-typed identifier wrappers** for credential domain entities.
 * These are typed wrappers around strings/URIs that provide validation and type safety:
 * 
 * - [CredentialId] - Credential identifier (URI, URN, DID, or other IRI)
 * - [IssuerId] - Credential issuer identifier (DID or URI)
 * - [StatusListId] - Credential status list identifier
 * - [SchemaId] - Credential schema identifier
 * - [SubjectId] - Subject identifier (sealed class for type safety)
 * 
 * All identifiers extend [Iri] and provide type-safe validation and serialization.
 * 
 * **Note:** For domain classification types (CredentialType, ProofType, etc.) that describe
 * "what" a credential is or "how" it behaves, see the `model/` package.
 */

/**
 * Credential identifier (URI, URN, DID, or other IRI).
 * 
 * Extends Iri with credential-specific semantics.
 * Per W3C VC specification, credential IDs are URIs/IRIs, so CredentialId IS-A Iri.
 * 
 * **Inheritance**: `CredentialId extends Iri` - a credential ID IS-A IRI.
 */
@Serializable(with = CredentialIdSerializer::class)
class CredentialId(
    value: String
) : Iri(value) {
    /**
     * Try to parse as Did (since both CredentialId and Did extend Iri).
     */
    fun asDid(): Did? = if (isDid) try { Did(value) } catch (e: IllegalArgumentException) { null } else null
    
    override fun toString(): String = value
    
    companion object {
        /**
         * Create from Iri (useful when you have an Iri and want to narrow it).
         */
        fun fromIri(iri: Iri): CredentialId = CredentialId(iri.value)
        
        /**
         * Create from Did (when credential ID is a DID).
         */
        fun fromDid(did: Did): CredentialId = CredentialId(did.value)
    }
}

object CredentialIdSerializer : KSerializer<CredentialId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("CredentialId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: CredentialId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): CredentialId {
        val string = decoder.decodeString()
        return try {
            CredentialId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize CredentialId: ${e.message}",
                e
            )
        }
    }
}

/**
 * Credential issuer identifier (DID or URI).
 * 
 * Extends Iri with issuer-specific semantics.
 * Per W3C VC specification, issuers are URIs or DIDs, so IssuerId IS-A Iri.
 * 
 * **Inheritance**: `IssuerId extends Iri` - an issuer ID IS-A IRI.
 */
@Serializable(with = IssuerIdSerializer::class)
class IssuerId(
    value: String
) : Iri(value) {
    /**
     * Try to parse as Did (since both IssuerId and Did extend Iri).
     */
    fun asDid(): Did? = if (isDid) try { Did(value) } catch (e: IllegalArgumentException) { null } else null
    
    override fun toString(): String = value
    
    companion object {
        /**
         * Create IssuerId from Did (common case).
         * More ergonomic than: IssuerId(did.value)
         */
        fun fromDid(did: Did): IssuerId = IssuerId(did.value)
        
        /**
         * Create IssuerId from Iri (if needed).
         */
        fun fromIri(iri: Iri): IssuerId = IssuerId(iri.value)
    }
}

object IssuerIdSerializer : KSerializer<IssuerId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("IssuerId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: IssuerId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): IssuerId {
        val string = decoder.decodeString()
        return try {
            IssuerId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize IssuerId: ${e.message}",
                e
            )
        }
    }
}

/**
 * Credential status list identifier.
 * 
 * Extends Iri with status list-specific semantics.
 * Status list IDs are URIs/IRIs, so StatusListId IS-A Iri.
 * 
 * **Inheritance**: `StatusListId extends Iri` - a status list ID IS-A IRI.
 */
@Serializable(with = StatusListIdSerializer::class)
class StatusListId(
    value: String
) : Iri(value) {
    override fun toString(): String = value
}

object StatusListIdSerializer : KSerializer<StatusListId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("StatusListId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: StatusListId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): StatusListId {
        val string = decoder.decodeString()
        return try {
            StatusListId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize StatusListId: ${e.message}",
                e
            )
        }
    }
}

/**
 * Credential schema identifier.
 * 
 * Extends Iri with schema-specific semantics.
 * Schema IDs are URIs/IRIs, so SchemaId IS-A Iri.
 * 
 * **Inheritance**: `SchemaId extends Iri` - a schema ID IS-A IRI.
 */
@Serializable(with = SchemaIdSerializer::class)
class SchemaId(
    value: String
) : Iri(value) {
    override fun toString(): String = value
}

object SchemaIdSerializer : KSerializer<SchemaId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("SchemaId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: SchemaId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): SchemaId {
        val string = decoder.decodeString()
        return try {
            SchemaId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize SchemaId: ${e.message}",
                e
            )
        }
    }
}

/**
 * Subject identifier - sealed class for type safety.
 * 
 * Represents the subject of a credential (the entity the credential is about).
 * Can be a DID, URI, or other identifier type.
 */
sealed class SubjectId {
    abstract val value: String
    
    data class DidSubject(val did: Did) : SubjectId() {
        override val value: String get() = did.value
    }
    
    data class UriSubject(override val value: String) : SubjectId()
    
    data class OtherSubject(override val value: String) : SubjectId()
    
    companion object {
        fun fromDid(did: Did): SubjectId = DidSubject(did)
        fun fromUri(uri: String): SubjectId = UriSubject(uri)
        fun fromString(id: String): SubjectId = OtherSubject(id)
    }
}

