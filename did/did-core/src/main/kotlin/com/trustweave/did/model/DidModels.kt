package com.trustweave.did

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
        /**
         * Parses a DID string or DID URL into a Did object.
         *
         * Handles:
         * - DID: `did:method:id`
         * - DID URL: `did:method:id/path#fragment` (fragment and path are ignored)
         *
         * @param didString The DID string (e.g., "did:web:example.com" or "did:web:example.com/path#fragment")
         * @return A Did object
         * @throws IllegalArgumentException if the string is not a valid DID
         */
        fun parse(didString: String): Did {
            if (!didString.startsWith("did:")) {
                throw com.trustweave.did.exception.DidException.InvalidDidFormat(
                    did = didString,
                    reason = "Invalid DID format: must start with 'did:'"
                )
            }

            // Remove fragment if present (everything after #)
            val withoutFragment = didString.split('#').first()

            // Split by : to get method and id
            val parts = withoutFragment.substring(4).split(":", limit = 2)
            if (parts.size != 2) {
                throw com.trustweave.did.exception.DidException.InvalidDidFormat(
                    did = didString,
                    reason = "Invalid DID format: expected 'did:method:id'"
                )
            }

            // Extract method and id (id may contain path like /path/to/resource)
            val method = parts[0]
            val idWithPath = parts[1]

            // For now, include path in id. Could be separated if needed:
            // val idParts = idWithPath.split("/", limit = 2)
            // val id = idParts[0]
            // val path = idParts.getOrNull(1)

            return Did(method = method, id = idWithPath)
        }
    }
}

/**
 * Represents a verification method in a DID Document.
 *
 * Following W3C DID Core specification, verification methods can be embedded
 * in the `verificationMethod` array or referenced by ID in relationship arrays.
 *
 * @param id The verification method identifier (can be relative or absolute)
 * @param type The type of verification method (e.g., "Ed25519VerificationKey2020")
 * @param controller The DID that controls this verification method
 * @param publicKeyJwk Public key in JWK format (optional)
 * @param publicKeyMultibase Public key in multibase format (optional)
 */
data class VerificationMethod(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyJwk: Map<String, Any?>? = null,
    val publicKeyMultibase: String? = null
)

/**
 * Represents a service endpoint in a DID Document.
 *
 * Following W3C DID Core specification, services provide means of communication
 * or interaction with the DID subject.
 *
 * @param id The service identifier
 * @param type The service type (e.g., "LinkedDomains", "DIDCommMessaging")
 * @param serviceEndpoint The service endpoint (can be a URL, object, or array)
 */
data class DidService(
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
    val verificationMethod: List<VerificationMethod> = emptyList(),
    val authentication: List<String> = emptyList(),
    val assertionMethod: List<String> = emptyList(),
    val keyAgreement: List<String> = emptyList(),
    val capabilityInvocation: List<String> = emptyList(),
    val capabilityDelegation: List<String> = emptyList(),
    val service: List<DidService> = emptyList()
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

