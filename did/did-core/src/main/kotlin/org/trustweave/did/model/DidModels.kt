package org.trustweave.did.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.util.XmlDateTimeSerializer
import org.trustweave.did.util.toXmlDateTime

/**
 * Note: Did class has been moved to org.trustweave.did.identifiers.Did
 * This file now contains only DID document models.
 */

/**
 * Represents a verification method in a DID Document.
 *
 * Following W3C DID Core specification, verification methods can be embedded
 * in the `verificationMethod` array or referenced by ID in relationship arrays.
 *
 * @param id The verification method identifier (typed)
 * @param type The type of verification method (e.g., "Ed25519VerificationKey2020")
 * @param controller The DID that controls this verification method (typed)
 * @param publicKeyJwk Public key in JWK format (optional)
 * @param publicKeyMultibase Public key in multibase format (optional)
 */
@Serializable
data class VerificationMethod(
    val id: VerificationMethodId,
    val type: String,
    val controller: Did,
    val publicKeyJwk: Map<String, @Contextual Any?>? = null,
    val publicKeyMultibase: String? = null,
)

/**
 * Represents a service endpoint in a DID Document.
 *
 * Following W3C DID Core specification, services provide means of communication
 * or interaction with the DID subject. Per DID 1.1 / CID, [type] may be a string
 * or a set of strings; we model it as a non-empty list.
 *
 * @param id The service identifier (can be relative or absolute URI)
 * @param type Service type(s) (e.g. ["LinkedDomains"], ["DIDCommMessaging"]); at least one required
 * @param serviceEndpoint The service endpoint (can be a URL, object, or array)
 */
@Serializable
data class DidService(
    val id: String, // Service IDs are often relative URIs, so keeping as String
    val type: List<String>, // DID 1.1: string or set of strings
    val serviceEndpoint: ServiceEndpoint, // URL, object, or array (see [ServiceEndpoint])
)

/**
 * Represents a DID Document following W3C DID Core structure.
 *
 * @param id The DID identifier (typed)
 * @param context JSON-LD context(s) for the document (defaults to W3C DID Core context)
 * @param alsoKnownAs Alternative identifiers (DID or absolute URL per spec); see [DidOrUrl]
 * @param controller DIDs that control this DID (typed)
 * @param verificationMethod List of verification methods
 * @param authentication List of verification method references for authentication (typed)
 * @param assertionMethod List of verification method references for assertions (typed)
 * @param keyAgreement List of verification method references for key agreement (typed)
 * @param capabilityInvocation List of verification method references for capability invocation (typed)
 * @param capabilityDelegation List of verification method references for capability delegation (typed)
 * @param service List of service endpoints
 */
@Serializable
data class DidDocument(
    val id: Did,
    val context: List<String> = listOf("https://www.w3.org/ns/did/v1"),
    val alsoKnownAs: List<DidOrUrl> = emptyList(),
    val controller: List<Did> = emptyList(),
    val verificationMethod: List<VerificationMethod> = emptyList(),
    val authentication: List<VerificationMethodId> = emptyList(),
    val assertionMethod: List<VerificationMethodId> = emptyList(),
    val keyAgreement: List<VerificationMethodId> = emptyList(),
    val capabilityInvocation: List<VerificationMethodId> = emptyList(),
    val capabilityDelegation: List<VerificationMethodId> = emptyList(),
    val service: List<DidService> = emptyList(),
)

/**
 * DID document metadata per DID Resolution 1.0 §4.3 — metadata about the DID *document*.
 *
 * @param created timestamp of the Create operation (SHOULD be present)
 * @param updated timestamp of the last Update operation (SHOULD be present)
 * @param deactivated MUST be true when the DID is deactivated; omitted otherwise
 * @param versionId version of the last Update operation (SHOULD be present)
 * @param nextUpdate timestamp of the next Update, when this is not the latest version
 * @param nextVersionId version of the next Update, when this is not the latest version
 * @param canonicalId the canonical DID for the subject, per the DID method
 * @param equivalentId DIDs the method guarantees are logically equivalent to `id`
 * @param proof proofs added by the controller or the verifiable data registry
 */
@Serializable
data class DidDocumentMetadata(
    @Serializable(with = XmlDateTimeSerializer::class) val created: Instant? = null,
    @Serializable(with = XmlDateTimeSerializer::class) val updated: Instant? = null,
    val deactivated: Boolean = false,
    val versionId: String? = null,
    @Serializable(with = XmlDateTimeSerializer::class) val nextUpdate: Instant? = null,
    val nextVersionId: String? = null,
    val canonicalId: Did? = null,
    val equivalentId: List<Did> = emptyList(),
    val proof: List<JsonObject> = emptyList(),
) {
    /** Serializes to the §4.3 JSON structure, omitting absent members. */
    fun toJson(): JsonObject =
        buildJsonObject {
            created?.let { put("created", it.toXmlDateTime()) }
            updated?.let { put("updated", it.toXmlDateTime()) }
            if (deactivated) put("deactivated", true)
            versionId?.let { put("versionId", it) }
            nextUpdate?.let { put("nextUpdate", it.toXmlDateTime()) }
            nextVersionId?.let { put("nextVersionId", it) }
            canonicalId?.let { put("canonicalId", it.value) }
            if (equivalentId.isNotEmpty()) {
                put("equivalentId", JsonArray(equivalentId.map { JsonPrimitive(it.value) }))
            }
            if (proof.isNotEmpty()) put("proof", JsonArray(proof))
        }
}
