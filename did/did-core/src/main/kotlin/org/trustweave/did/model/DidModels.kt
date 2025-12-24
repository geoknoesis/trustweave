package org.trustweave.did.model

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

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
    val publicKeyMultibase: String? = null
)

/**
 * Represents a service endpoint in a DID Document.
 *
 * Following W3C DID Core specification, services provide means of communication
 * or interaction with the DID subject.
 *
 * @param id The service identifier (can be relative or absolute URI)
 * @param type The service type (e.g., "LinkedDomains", "DIDCommMessaging")
 * @param serviceEndpoint The service endpoint (can be a URL, object, or array)
 */
@Serializable
data class DidService(
    val id: String,  // Service IDs are often relative URIs, so keeping as String
    val type: String,
    @Contextual val serviceEndpoint: Any  // URL, object, or array
)

/**
 * Represents a DID Document following W3C DID Core structure.
 *
 * @param id The DID identifier (typed)
 * @param context JSON-LD context(s) for the document (defaults to W3C DID Core context)
 * @param alsoKnownAs Alternative identifiers for this DID (typed)
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
    val alsoKnownAs: List<Did> = emptyList(),
    val controller: List<Did> = emptyList(),
    val verificationMethod: List<VerificationMethod> = emptyList(),
    val authentication: List<VerificationMethodId> = emptyList(),
    val assertionMethod: List<VerificationMethodId> = emptyList(),
    val keyAgreement: List<VerificationMethodId> = emptyList(),
    val capabilityInvocation: List<VerificationMethodId> = emptyList(),
    val capabilityDelegation: List<VerificationMethodId> = emptyList(),
    val service: List<DidService> = emptyList()
)

/**
 * Metadata about a DID Document following W3C DID Core specification.
 *
 * @param created ISO 8601 timestamp when the DID document was created
 * @param updated ISO 8601 timestamp when the DID document was last updated
 * @param versionId Version identifier for the DID document
 * @param nextUpdate ISO 8601 timestamp indicating when to check for updates
 * @param canonicalId Canonical form of the DID identifier (typed)
 * @param equivalentId List of equivalent DID identifiers (typed)
 */
@Serializable
data class DidDocumentMetadata(
    @Contextual val created: kotlinx.datetime.Instant? = null,
    @Contextual val updated: kotlinx.datetime.Instant? = null,
    val versionId: String? = null,
    @Contextual val nextUpdate: kotlinx.datetime.Instant? = null,
    val canonicalId: Did? = null,
    val equivalentId: List<Did> = emptyList()
)

