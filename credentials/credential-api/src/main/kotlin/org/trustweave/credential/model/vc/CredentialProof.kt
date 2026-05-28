package org.trustweave.credential.model.vc

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.JsonElement
import kotlinx.datetime.Instant

/**
 * VC Proof - format-specific (VC-LD, VC-JWT, SD-JWT-VC).
 * 
 * Per W3C VC Data Model, proof can be in different formats:
 * - Linked Data Proofs (VC-LD) - JSON-LD proof structure
 * - JWT (VC-JWT) - JWT compact serialization
 * - SD-JWT-VC - Selective Disclosure JWT VC
 */
@Serializable(with = CredentialProofSerializer::class)
sealed class CredentialProof {
    /**
     * VC-LD (Linked Data Proofs) - JSON-LD proof structure.
     * 
     * Used for Verifiable Credentials with Linked Data Proofs.
     */
    @Serializable
    data class LinkedDataProof(
        val type: String,  // e.g., "Ed25519Signature2020", "JsonWebSignature2020"
        @Contextual val created: Instant,
        val verificationMethod: String,  // DID URL or IRI
        val proofPurpose: String,  // e.g., "assertionMethod", "authentication"
        val proofValue: String,  // Base58/Base64 encoded signature
        val additionalProperties: Map<String, JsonElement> = emptyMap()
    ) : CredentialProof()
    
    /**
     * VC-JWT - JWT compact serialization.
     * 
     * Used for Verifiable Credentials encoded as JWTs.
     */
    @Serializable
    data class JwtProof(
        val jwt: String  // Compact JWT string
    ) : CredentialProof()
    
    /**
     * SD-JWT-VC - Selective Disclosure JWT VC.
     * 
     * Used for Verifiable Credentials with selective disclosure capabilities.
     */
    @Serializable
    data class SdJwtVcProof(
        val sdJwtVc: String,  // SD-JWT-VC compact format
        val disclosures: List<String>? = null  // Optional disclosures
    ) : CredentialProof()

    /**
     * ISO 18013-5 mDL/mDoc proof.
     *
     * Stores the CBOR-encoded DeviceResponse bytes produced by MdocProofEngine.
     * The bytes contain the full ISO 18013-5 DeviceResponse structure including
     * IssuerAuth (COSE_Sign1 over the MSO) and optional DeviceAuth.
     */
    @Serializable
    data class MdocProof(
        val deviceResponse: ByteArray,
        val docType: String
    ) : CredentialProof() {
        override fun equals(other: Any?): Boolean =
            other is MdocProof && deviceResponse.contentEquals(other.deviceResponse) && docType == other.docType

        override fun hashCode(): Int = 31 * deviceResponse.contentHashCode() + docType.hashCode()
    }

    /**
     * ETSI TS 119 182-1 JAdES proof.
     *
     * The signature is wrapped in JWS JSON Serialization (Flattened) when the unsigned `etsiU`
     * block is non-empty (B-T and beyond) and in JWS Compact when it is empty (strict B-B).
     * Either form fits in [jws] as a single string.
     *
     * @property jws        The JWS Compact (B-B) or JWS JSON Flattened (B-T) string emitted by
     *                      [org.trustweave.signatures.jades.DefaultJadesSigner].
     * @property profile    The JAdES profile level, encoded as a string for serialization
     *                      stability: `"B-B"` or `"B-T"`. Verifiers cross-check this against
     *                      what they actually find in the [jws] envelope.
     */
    @Serializable
    data class JAdES(
        val jws: String,
        val profile: String,
    ) : CredentialProof()
}

