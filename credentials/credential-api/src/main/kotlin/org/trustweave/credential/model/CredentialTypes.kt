package org.trustweave.credential.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

/**
 * Credential domain types.
 * 
 * This file declares domain classification types that describe "what" a credential is
 * and "how" it behaves, rather than identifiers or names:
 * - [CredentialType] - The kind/category of credential (e.g., Education, Employment)
 * - [ProofType] - The kind of cryptographic proof used (e.g., Ed25519Signature2020)
 * - [StatusPurpose] - The purpose of a status list (revocation vs suspension)
 * - [SchemaFormat] - The format/type of schema validator
 * - [Claims] - Type alias for credential claims map
 * 
 * These are intrinsic domain concepts that appear in models, requests, and results.
 * For typed identifier wrappers (CredentialId, IssuerId, etc.), see the `identifiers/` package.
 */

/**
 * Type alias for credential claims.
 * 
 * Claims are represented as a map of claim names to JSON values.
 */
typealias Claims = Map<String, JsonElement>

/**
 * Cryptographic proof/signature type classification.
 * 
 * Represents the TYPE of proof used in verifiable credentials.
 * This is a classification, not an identifier.
 */
@Serializable(with = ProofTypeSerializer::class)
sealed class ProofType {
    /**
     * The proof type identifier string used in VC proofs.
     */
    abstract val identifier: String
    
    object Ed25519Signature2020 : ProofType() {
        override val identifier = "Ed25519Signature2020"
    }
    
    object JsonWebSignature2020 : ProofType() {
        override val identifier = "JsonWebSignature2020"
    }
    
    object BbsBlsSignature2020 : ProofType() {
        override val identifier = "BbsBlsSignature2020"
    }
    
    /**
     * Custom proof type with arbitrary identifier.
     */
    data class Custom(override val identifier: String) : ProofType()
    
    override fun toString(): String = identifier
}

/**
 * Custom serializer for ProofType.
 */
object ProofTypeSerializer : KSerializer<ProofType> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("ProofType", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: ProofType) {
        encoder.encodeString(value.identifier)
    }
    
    override fun deserialize(decoder: Decoder): ProofType {
        val string = decoder.decodeString()
        return when (string) {
            "Ed25519Signature2020" -> ProofType.Ed25519Signature2020
            "JsonWebSignature2020" -> ProofType.JsonWebSignature2020
            "BbsBlsSignature2020" -> ProofType.BbsBlsSignature2020
            else -> ProofType.Custom(string)
        }
    }
}

/**
 * Convenience object for ProofType constants.
 */
object ProofTypes {
    val Ed25519 = ProofType.Ed25519Signature2020
    val JWT = ProofType.JsonWebSignature2020
    val BBS = ProofType.BbsBlsSignature2020
    
    fun fromString(identifier: String): ProofType = when (identifier) {
        "Ed25519Signature2020" -> ProofType.Ed25519Signature2020
        "JsonWebSignature2020" -> ProofType.JsonWebSignature2020
        "BbsBlsSignature2020" -> ProofType.BbsBlsSignature2020
        else -> ProofType.Custom(identifier)
    }
}

/**
 * Credential type classification.
 * 
 * Represents the TYPE/CATEGORY of verifiable credential.
 * This is a classification, not an identifier.
 * 
 * **Example:**
 * ```kotlin
 * val types = listOf(
 *     CredentialType.VerifiableCredential,
 *     CredentialType.Education
 * )
 * ```
 */
@Serializable(with = CredentialTypeSerializer::class)
sealed class CredentialType {
    /**
     * The credential type string used in VC type array.
     */
    abstract val value: String
    
    // Standard types
    object VerifiableCredential : CredentialType() {
        override val value = "VerifiableCredential"
    }
    
    // Domain-specific types
    object Education : CredentialType() {
        override val value = "EducationCredential"
    }
    
    object Employment : CredentialType() {
        override val value = "EmploymentCredential"
    }
    
    object Certification : CredentialType() {
        override val value = "CertificationCredential"
    }
    
    object Degree : CredentialType() {
        override val value = "DegreeCredential"
    }
    
    object Person : CredentialType() {
        override val value = "PersonCredential"
    }
    
    /**
     * Custom credential type.
     */
    data class Custom(override val value: String) : CredentialType()
    
    override fun toString(): String = value
    
    companion object {
        /**
         * Create CredentialType from string value.
         */
        fun fromString(value: String): CredentialType = when (value) {
            VerifiableCredential.value -> VerifiableCredential
            Education.value -> Education
            Employment.value -> Employment
            Certification.value -> Certification
            Degree.value -> Degree
            Person.value -> Person
            else -> Custom(value)
        }
    }
}

/**
 * Custom serializer for CredentialType.
 */
object CredentialTypeSerializer : KSerializer<CredentialType> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("CredentialType", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: CredentialType) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): CredentialType {
        val string = decoder.decodeString()
        return CredentialType.fromString(string)
    }
}

/**
 * Convenience object for CredentialType constants.
 */
object CredentialTypes {
    val VERIFIABLE_CREDENTIAL = CredentialType.VerifiableCredential
    val EDUCATION = CredentialType.Education
    val EMPLOYMENT = CredentialType.Employment
    val CERTIFICATION = CredentialType.Certification
    val DEGREE = CredentialType.Degree
    val PERSON = CredentialType.Person
}

/**
 * Credential status purpose enumeration.
 * 
 * Represents the purpose of a status list (revocation vs suspension).
 * This is a classification/enum, not an identifier.
 */
@Serializable(with = StatusPurposeSerializer::class)
enum class StatusPurpose {
    REVOCATION,
    SUSPENSION;
    
    /**
     * Lowercase string representation for JSON serialization.
     */
    val stringValue: String
        get() = name.lowercase()
}

/**
 * Custom serializer for StatusPurpose.
 */
object StatusPurposeSerializer : KSerializer<StatusPurpose> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("StatusPurpose", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: StatusPurpose) {
        encoder.encodeString(value.stringValue)
    }
    
    override fun deserialize(decoder: Decoder): StatusPurpose {
        val string = decoder.decodeString().uppercase()
        return try {
            StatusPurpose.valueOf(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Invalid StatusPurpose: '$string'. Expected 'revocation' or 'suspension'",
                e
            )
        }
    }
}

/**
 * Schema format enumeration.
 * 
 * Represents the format/type of schema validator.
 * This is a classification/enum, not an identifier.
 */
@Serializable(with = SchemaFormatSerializer::class)
enum class SchemaFormat {
    /** JSON Schema Draft 7 or Draft 2020-12 */
    JSON_SCHEMA,
    
    /** SHACL (Shapes Constraint Language) for RDF validation */
    SHACL;
    
    /**
     * String representation for compatibility.
     */
    val stringValue: String
        get() = name.lowercase()
}

/**
 * Custom serializer for SchemaFormat.
 */
object SchemaFormatSerializer : KSerializer<SchemaFormat> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("SchemaFormat", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: SchemaFormat) {
        encoder.encodeString(value.stringValue)
    }
    
    override fun deserialize(decoder: Decoder): SchemaFormat {
        val string = decoder.decodeString().uppercase()
        return try {
            SchemaFormat.valueOf(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Invalid SchemaFormat: '$string'. Expected 'json_schema' or 'shacl'",
                e
            )
        }
    }
}

