package org.trustweave.credential.model.vc

import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * VC Issuer - can be an IRI string (DID, URI, etc.) or object with id and optional name.
 * 
 * Per W3C VC Data Model, issuer can be any IRI (typically a DID, but can be URI/URN).
 * Leverages the common Iri base class for identifier support.
 * 
 * **Examples:**
 * ```kotlin
 * // DID issuer (most common)
 * val issuer1 = Issuer.fromDid(Did("did:example:issuer"))
 * 
 * // IRI issuer (URI or URN)
 * val issuer2 = Issuer.from(Iri("https://example.com/issuers/123"))
 * 
 * // Object issuer with name
 * val issuer3 = Issuer.ObjectIssuer(
 *     id = Did("did:example:issuer"),
 *     name = "Example University"
 * )
 * ```
 */
@Serializable
sealed class Issuer {
    /**
     * The issuer IRI (DID, URI, URN, etc.).
     */
    abstract val id: Iri
    
    /**
     * Check if issuer is a DID.
     */
    val isDid: Boolean get() = id.isDid
    
    /**
     * Issuer as an IRI (DID, URI, URN, etc.).
     */
    @Serializable
    data class IriIssuer(override val id: Iri) : Issuer()
    
    /**
     * Issuer as an object with id and optional properties.
     */
    @Serializable
    data class ObjectIssuer(
        override val id: Iri,  // Can be DID, URI, URN, etc.
        val name: String? = null,
        val additionalProperties: Map<String, JsonElement> = emptyMap()
    ) : Issuer()
    
    companion object {
        /**
         * Create issuer from IRI string (DID, URI, URN, etc.).
         */
        fun from(iri: String): Issuer = IriIssuer(Iri(iri))
        
        /**
         * Create issuer from IRI.
         */
        fun from(iri: Iri): Issuer = IriIssuer(iri)
        
        /**
         * Create issuer from DID (convenience method).
         */
        fun fromDid(did: Did): Issuer = IriIssuer(did)  // Did extends Iri
    }
}

