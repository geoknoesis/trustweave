package org.trustweave.trust.dsl.did

import org.trustweave.did.model.DidDocument
import org.trustweave.did.DidMethod
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.trust.TrustWeave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DID Document Builder DSL.
 *
 * Provides a fluent API for updating DID documents (adding/removing keys, services, etc.).
 *
 * **Example Usage**:
 * ```kotlin
 * val updatedDoc = trustWeave.updateDid {
 *     did("did:key:issuer")
 *     method("key")
 *
 *     addKey {
 *         type("Ed25519VerificationKey2020")
 *         publicKeyJwk(newKeyHandle.publicKeyJwk)
 *     }
 *
 *     removeKey("did:key:issuer#key-1")
 *
 *     addService {
 *         id("did:key:issuer#service-1")
 *         type("LinkedDomains")
 *         endpoint("https://example.com")
 *     }
 * }
 * ```
 */
class DidDocumentBuilder(
    private val trustWeave: TrustWeave
) {
    private var did: String? = null
    private var method: String? = null
    private val newVerificationMethods = mutableListOf<VerificationMethodData>()
    private val removedVerificationMethods = mutableListOf<String>()
    private val newServices = mutableListOf<ServiceData>()
    private val removedServices = mutableListOf<String>()
    private val addedCapabilityInvocation = mutableListOf<String>()
    private val removedCapabilityInvocation = mutableListOf<String>()
    private val addedCapabilityDelegation = mutableListOf<String>()
    private val removedCapabilityDelegation = mutableListOf<String>()
    private var contextValues: List<String>? = null

    /**
     * Set DID to update.
     */
    fun did(did: String) {
        this.did = did
    }

    /**
     * Set DID method (auto-detected from DID if not provided).
     */
    fun method(method: String) {
        this.method = method
    }

    /**
     * Add a new verification method (key).
     */
    fun addKey(block: VerificationMethodBuilder.() -> Unit) {
        val builder = VerificationMethodBuilder(did ?: "")
        builder.block()
        newVerificationMethods.add(builder.build())
    }

    /**
     * Remove a verification method by ID.
     */
    fun removeKey(keyId: String) {
        removedVerificationMethods.add(keyId)
    }

    /**
     * Add a new service endpoint.
     */
    fun addService(block: ServiceBuilder.() -> Unit) {
        val builder = ServiceBuilder()
        builder.block()
        newServices.add(builder.build())
    }

    /**
     * Remove a service by ID.
     */
    fun removeService(serviceId: String) {
        removedServices.add(serviceId)
    }

    /**
     * Add a verification method reference to capability invocation.
     */
    fun addCapabilityInvocation(keyId: String) {
        addedCapabilityInvocation.add(keyId)
    }

    /**
     * Remove a verification method reference from capability invocation.
     */
    fun removeCapabilityInvocation(keyId: String) {
        removedCapabilityInvocation.add(keyId)
    }

    /**
     * Add a verification method reference to capability delegation.
     */
    fun addCapabilityDelegation(keyId: String) {
        addedCapabilityDelegation.add(keyId)
    }

    /**
     * Remove a verification method reference from capability delegation.
     */
    fun removeCapabilityDelegation(keyId: String) {
        removedCapabilityDelegation.add(keyId)
    }

    /**
     * Set JSON-LD context values for the DID document.
     */
    fun context(vararg contexts: String) {
        contextValues = contexts.toList()
    }

    /**
     * Update the DID document.
     *
     * @return Updated DID document
     */
    suspend fun update(): DidDocument = withContext(Dispatchers.IO) {
        val targetDid = did ?: throw IllegalStateException(
            "DID is required. Use did(\"did:key:...\")"
        )

        // Detect method from DID if not provided
        val methodName = method ?: run {
            if (targetDid.startsWith("did:")) {
                val parts = targetDid.substring(4).split(":", limit = 2)
                if (parts.isNotEmpty()) parts[0] else null
            } else null
        } ?: throw IllegalStateException(
            "Could not determine DID method. Use method(\"key\") or provide a valid DID"
        )

        // Get DID method from provider
        val didMethod = trustWeave.getDidMethod(methodName) as? DidMethod
            ?: throw IllegalStateException(
                "DID method '$methodName' is not configured. " +
                "Configure it in trustLayer { did { method(\"$methodName\") { ... } } }"
            )

        // Update DID document using proper types
        val targetDidObj = Did(targetDid)
        val updatedDoc = didMethod.updateDid(targetDidObj) { currentDoc ->
            updateDocument(currentDoc, targetDidObj)
        }

        updatedDoc
    }

    /**
     * Update document using proper types (no reflection).
     */
    private fun updateDocument(currentDoc: DidDocument, targetDid: Did): DidDocument {
        // Filter out removed verification methods
        val filteredVm = currentDoc.verificationMethod.filter { vm ->
            !removedVerificationMethods.any { removed -> vm.id.value.contains(removed) }
        }

        // Create new verification method objects
        val newVmObjects = newVerificationMethods.map { vmData ->
            VerificationMethod(
                id = VerificationMethodId.parse(vmData.id, targetDid),
                type = vmData.type,
                controller = Did(vmData.controller),
                publicKeyJwk = vmData.publicKeyJwk,
                publicKeyMultibase = vmData.publicKeyMultibase
            )
        }

        // Filter out removed services
        val filteredServices = currentDoc.service.filter { service ->
            !removedServices.contains(service.id)
        }

        // Create new service objects
        val newServiceObjects = newServices.map { serviceData ->
            DidService(
                id = serviceData.id,
                type = serviceData.type,
                serviceEndpoint = serviceData.endpoint
            )
        }

        // Update authentication and assertion method lists
        val filteredAuth = currentDoc.authentication.filter { auth ->
            val authStr = auth.value
            !removedVerificationMethods.any { removed -> authStr.contains(removed) }
        }.plus(newVerificationMethods.map { 
            org.trustweave.did.identifiers.VerificationMethodId.parse(it.id, targetDid)
        })

        val filteredAssertion = currentDoc.assertionMethod.filter { assertion ->
            val assertionStr = assertion.value
            !removedVerificationMethods.any { removed -> assertionStr.contains(removed) }
        }.plus(newVerificationMethods.map { 
            org.trustweave.did.identifiers.VerificationMethodId.parse(it.id, targetDid)
        })

        // Update capability invocation and delegation
        val updatedCapabilityInvocation = currentDoc.capabilityInvocation
            .filter { !removedCapabilityInvocation.contains(it.value) }
            .plus(addedCapabilityInvocation.map { 
                org.trustweave.did.identifiers.VerificationMethodId.parse(it, targetDid)
            })

        val updatedCapabilityDelegation = currentDoc.capabilityDelegation
            .filter { !removedCapabilityDelegation.contains(it.value) }
            .plus(addedCapabilityDelegation.map { 
                org.trustweave.did.identifiers.VerificationMethodId.parse(it, targetDid)
            })

        // Use context from builder or keep current
        val updatedContext = contextValues ?: currentDoc.context

        // Combine verification methods and services
        val updatedVm = filteredVm + newVmObjects
        val updatedServices = filteredServices + newServiceObjects

        // Use copy method with proper types
        return currentDoc.copy(
            id = targetDid,
            context = updatedContext,
            verificationMethod = updatedVm,
            authentication = filteredAuth,
            assertionMethod = filteredAssertion,
            capabilityInvocation = updatedCapabilityInvocation,
            capabilityDelegation = updatedCapabilityDelegation,
            service = updatedServices
        )
    }

    /**
     * Verification method builder.
     */
    inner class VerificationMethodBuilder(private val controllerDid: String) {
        private var id: String? = null
        private var type: String = "Ed25519VerificationKey2020"
        private var publicKeyJwk: Map<String, Any?>? = null
        private var publicKeyMultibase: String? = null

        /**
         * Set verification method ID.
         */
        fun id(id: String) {
            this.id = id
        }

        /**
         * Set verification method type.
         */
        fun type(type: String) {
            this.type = type
        }

        /**
         * Set public key in JWK format.
         */
        fun publicKeyJwk(jwk: Map<String, Any?>) {
            this.publicKeyJwk = jwk
        }

        /**
         * Set public key in multibase format.
         */
        fun publicKeyMultibase(multibase: String) {
            this.publicKeyMultibase = multibase
        }

        /**
         * Build verification method data.
         */
        internal fun build(): VerificationMethodData {
            val vmId = id ?: "$controllerDid#key-${System.currentTimeMillis()}"
            return VerificationMethodData(
                id = vmId,
                type = type,
                controller = controllerDid,
                publicKeyJwk = publicKeyJwk,
                publicKeyMultibase = publicKeyMultibase
            )
        }
    }

    /**
     * Service builder.
     */
    inner class ServiceBuilder {
        private var id: String? = null
        private var type: String? = null
        private var endpoint: Any? = null

        /**
         * Set service ID.
         */
        fun id(id: String) {
            this.id = id
        }

        /**
         * Set service type.
         */
        fun type(type: String) {
            this.type = type
        }

        /**
         * Set service endpoint (URL, object, or array).
         */
        fun endpoint(endpoint: Any) {
            this.endpoint = endpoint
        }

        /**
         * Build service data.
         */
        internal fun build(): ServiceData {
            val serviceId = id ?: throw IllegalStateException("Service ID is required")
            val serviceType = type ?: throw IllegalStateException("Service type is required")
            val serviceEndpoint = endpoint ?: throw IllegalStateException("Service endpoint is required")

            return ServiceData(
                id = serviceId,
                type = serviceType,
                endpoint = serviceEndpoint
            )
        }
    }

    /**
     * Verification method data.
     */
    internal data class VerificationMethodData(
        val id: String,
        val type: String,
        val controller: String,
        val publicKeyJwk: Map<String, Any?>?,
        val publicKeyMultibase: String?
    )

    /**
     * Service data.
     */
    internal data class ServiceData(
        val id: String,
        val type: String,
        val endpoint: Any
    )
}

/**
 * Extension function to update a DID document using TrustWeave.
 */
suspend fun TrustWeave.updateDid(block: DidDocumentBuilder.() -> Unit): DidDocument {
    val builder = DidDocumentBuilder(this)
    builder.block()
    return builder.update()
}

