package io.geoknoesis.vericore.did

import java.time.Instant

/**
 * Represents a Decentralized Identifier (DID).
 *
 * @param method The DID method (e.g., "web", "key", "ion")
 * @param id The method-specific identifier
 */
data class Did(
    val method: String,
    val id: String
) {
    override fun toString(): String = "did:$method:$id"

    companion object {
        /**
         * Parses a DID string into a Did object.
         *
         * @param didString The DID string (e.g., "did:web:example.com")
         * @return A Did object
         * @throws IllegalArgumentException if the string is not a valid DID
         */
        fun parse(didString: String): Did {
            if (!didString.startsWith("did:")) {
                throw IllegalArgumentException("Invalid DID format: must start with 'did:'")
            }
            val parts = didString.substring(4).split(":", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid DID format: expected 'did:method:id'")
            }
            return Did(method = parts[0], id = parts[1])
        }
    }
}

/**
 * Represents a verification method reference in a DID Document.
 *
 * @param id The verification method identifier (can be relative or absolute)
 * @param type The type of verification method (e.g., "Ed25519VerificationKey2020")
 * @param controller The DID that controls this verification method
 * @param publicKeyJwk Public key in JWK format (optional)
 * @param publicKeyMultibase Public key in multibase format (optional)
 */
data class VerificationMethodRef(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyJwk: Map<String, Any?>? = null,
    val publicKeyMultibase: String? = null
)

/**
 * Represents a service endpoint in a DID Document.
 *
 * @param id The service identifier
 * @param type The service type (e.g., "LinkedDomains", "DIDCommMessaging")
 * @param serviceEndpoint The service endpoint (can be a URL, object, or array)
 */
data class Service(
    val id: String,
    val type: String,
    val serviceEndpoint: Any  // URL, object, or array
)

/**
 * Represents a DID Document following W3C DID Core structure.
 *
 * @param id The DID identifier
 * @param context JSON-LD context(s) for the document (defaults to W3C DID Core context)
 * @param alsoKnownAs Alternative identifiers for this DID
 * @param controller DIDs that control this DID
 * @param verificationMethod List of verification methods
 * @param authentication List of verification method references for authentication
 * @param assertionMethod List of verification method references for assertions
 * @param keyAgreement List of verification method references for key agreement
 * @param capabilityInvocation List of verification method references for capability invocation
 * @param capabilityDelegation List of verification method references for capability delegation
 * @param service List of service endpoints
 */
data class DidDocument(
    val id: String,
    val context: List<String> = listOf("https://www.w3.org/ns/did/v1"),
    val alsoKnownAs: List<String> = emptyList(),
    val controller: List<String> = emptyList(),
    val verificationMethod: List<VerificationMethodRef> = emptyList(),
    val authentication: List<String> = emptyList(),
    val assertionMethod: List<String> = emptyList(),
    val keyAgreement: List<String> = emptyList(),
    val capabilityInvocation: List<String> = emptyList(),
    val capabilityDelegation: List<String> = emptyList(),
    val service: List<Service> = emptyList()
)

/**
 * Metadata about a DID Document following W3C DID Core specification.
 *
 * @param created ISO 8601 timestamp when the DID document was created
 * @param updated ISO 8601 timestamp when the DID document was last updated
 * @param versionId Version identifier for the DID document
 * @param nextUpdate ISO 8601 timestamp indicating when to check for updates
 * @param canonicalId Canonical form of the DID identifier
 * @param equivalentId List of equivalent DID identifiers
 */
data class DidDocumentMetadata(
    val created: Instant? = null,
    val updated: Instant? = null,
    val versionId: String? = null,
    val nextUpdate: Instant? = null,
    val canonicalId: String? = null,
    val equivalentId: List<String> = emptyList()
)

/**
 * Result of DID resolution.
 *
 * @param document The resolved DID Document (null if not found)
 * @param documentMetadata Metadata about the document (e.g., created, updated timestamps)
 * @param resolutionMetadata Metadata about the resolution process
 */
data class DidResolutionResult(
    val document: DidDocument?,
    val documentMetadata: DidDocumentMetadata = DidDocumentMetadata(),
    val resolutionMetadata: Map<String, Any?> = emptyMap()
)

