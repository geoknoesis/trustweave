package com.trustweave.did.services

/**
 * Service for accessing DID Document properties without reflection.
 *
 * Provides type-safe access to DID Document and VerificationMethodRef properties
 * without direct dependency on TrustWeave-did module types.
 */

/**
 * Service for accessing DID Document properties.
 *
 * Eliminates the need for reflection when accessing DidDocument properties.
 * Implementations should provide direct property access.
 */
interface DidDocumentAccess {
    /**
     * Extracts the document from a resolution result.
     *
     * @param result The DID resolution result (as Any to avoid dependency)
     * @return The DID document (as Any to avoid dependency), or null if not found
     */
    fun getDocument(result: Any): Any? // DidDocument? - using Any to avoid dependency

    /**
     * Gets the assertionMethod list from a DID document.
     *
     * @param doc The DID document (as Any to avoid dependency)
     * @return List of assertion method references as strings
     */
    fun getAssertionMethod(doc: Any): List<String>

    /**
     * Gets the authentication list from a DID document.
     *
     * @param doc The DID document (as Any to avoid dependency)
     * @return List of authentication method references as strings
     */
    fun getAuthentication(doc: Any): List<String>

    /**
     * Gets the keyAgreement list from a DID document.
     *
     * @param doc The DID document (as Any to avoid dependency)
     * @return List of key agreement method references as strings
     */
    fun getKeyAgreement(doc: Any): List<String>

    /**
     * Gets the capabilityInvocation list from a DID document.
     *
     * @param doc The DID document (as Any to avoid dependency)
     * @return List of capability invocation method references as strings
     */
    fun getCapabilityInvocation(doc: Any): List<String>

    /**
     * Gets the capabilityDelegation list from a DID document.
     *
     * @param doc The DID document (as Any to avoid dependency)
     * @return List of capability delegation method references as strings
     */
    fun getCapabilityDelegation(doc: Any): List<String>

    /**
     * Gets the verificationMethod list from a DID document.
     *
     * @param doc The DID document (as Any to avoid dependency)
     * @return List of verification method references (as Any to avoid dependency)
     */
    fun getVerificationMethod(doc: Any): List<Any> // List<VerificationMethodRef> - using Any to avoid dependency

    /**
     * Gets the service list from a DID document.
     *
     * @param doc The DID document (as Any to avoid dependency)
     * @return List of services (as Any to avoid dependency)
     */
    fun getService(doc: Any): List<Any> // List<Service> - using Any to avoid dependency

    /**
     * Gets the context list from a DID document.
     *
     * @param doc The DID document (as Any to avoid dependency)
     * @return List of context URIs as strings
     */
    fun getContext(doc: Any): List<String>

    /**
     * Creates a new verification method reference.
     *
     * @param id The verification method ID
     * @param type The verification method type
     * @param controller The controller DID
     * @param publicKeyJwk Optional public key in JWK format
     * @param publicKeyMultibase Optional public key in multibase format
     * @return The verification method reference (as Any to avoid dependency)
     */
    fun createVerificationMethod(
        id: String,
        type: String,
        controller: String,
        publicKeyJwk: Map<String, Any?>? = null,
        publicKeyMultibase: String? = null
    ): Any // VerificationMethodRef - using Any to avoid dependency

    /**
     * Creates a new service endpoint.
     *
     * @param id The service ID
     * @param type The service type
     * @param serviceEndpoint The service endpoint (URL, object, or array)
     * @return The service (as Any to avoid dependency)
     */
    fun createService(
        id: String,
        type: String,
        serviceEndpoint: Any
    ): Any // Service - using Any to avoid dependency

    /**
     * Creates a copy of a DID document with updated properties.
     *
     * @param doc The original DID document (as Any to avoid dependency)
     * @param id The DID identifier
     * @param context The JSON-LD context list
     * @param alsoKnownAs Alternative identifiers
     * @param controller Controller DIDs
     * @param verificationMethod Verification methods
     * @param authentication Authentication references
     * @param assertionMethod Assertion method references
     * @param keyAgreement Key agreement references
     * @param capabilityInvocation Capability invocation references
     * @param capabilityDelegation Capability delegation references
     * @param service Service endpoints
     * @return The updated DID document (as Any to avoid dependency)
     */
    fun copyDocument(
        doc: Any,
        id: String? = null,
        context: List<String>? = null,
        alsoKnownAs: List<String>? = null,
        controller: List<String>? = null,
        verificationMethod: List<Any>? = null,
        authentication: List<String>? = null,
        assertionMethod: List<String>? = null,
        keyAgreement: List<String>? = null,
        capabilityInvocation: List<String>? = null,
        capabilityDelegation: List<String>? = null,
        service: List<Any>? = null
    ): Any // DidDocument - using Any to avoid dependency
}

/**
 * Service for accessing VerificationMethodRef properties.
 *
 * Eliminates the need for reflection when accessing VerificationMethodRef properties.
 */
interface VerificationMethodAccess {
    /**
     * Gets the ID from a verification method reference.
     *
     * @param vm The verification method reference (as Any to avoid dependency)
     * @return The verification method ID
     */
    fun getId(vm: Any): String

    /**
     * Gets the controller from a verification method reference.
     *
     * @param vm The verification method reference (as Any to avoid dependency)
     * @return The controller DID
     */
    fun getController(vm: Any): String
}

/**
 * Service for accessing Service properties.
 *
 * Eliminates the need for reflection when accessing Service properties.
 */
interface ServiceAccess {
    /**
     * Gets the ID from a service.
     *
     * @param service The service (as Any to avoid dependency)
     * @return The service ID
     */
    fun getId(service: Any): String

    /**
     * Gets the type from a service.
     *
     * @param service The service (as Any to avoid dependency)
     * @return The service type
     */
    fun getType(service: Any): String

    /**
     * Gets the endpoint from a service.
     *
     * @param service The service (as Any to avoid dependency)
     * @return The service endpoint
     */
    fun getServiceEndpoint(service: Any): Any
}

