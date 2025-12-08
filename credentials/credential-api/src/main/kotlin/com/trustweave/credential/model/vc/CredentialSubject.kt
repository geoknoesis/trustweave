package com.trustweave.credential.model.vc

import com.trustweave.core.identifiers.Iri
import com.trustweave.did.identifiers.Did
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * VC Credential Subject - contains an IRI id (DID, URI, URN, etc.) and claims.
 * 
 * Per W3C VC Data Model, credentialSubject.id can be any IRI, not just a DID.
 * Leverages the common Iri base class for identifier support.
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
 * ```
 */
@Serializable
data class CredentialSubject(
    val id: Iri,  // Subject IRI (DID, URI, URN, etc.) - required per VC spec
    val claims: Map<String, JsonElement> = emptyMap()  // Additional claims
) {
    /**
     * Check if the subject ID is a DID.
     */
    val isDid: Boolean
        get() = id.isDid
    
    /**
     * Check if the subject ID is a URI.
     */
    val isUri: Boolean
        get() = id.isUri
    
    /**
     * Check if the subject ID is a URN.
     */
    val isUrn: Boolean
        get() = id.isUrn
    
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

