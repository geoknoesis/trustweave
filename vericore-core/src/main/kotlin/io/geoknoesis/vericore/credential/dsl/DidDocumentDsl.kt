package io.geoknoesis.vericore.credential.dsl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/**
 * DID Document Builder DSL.
 * 
 * Provides a fluent API for updating DID documents (adding/removing keys, services, etc.).
 * 
 * **Example Usage**:
 * ```kotlin
 * val updatedDoc = trustLayer.updateDid {
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
    private val context: TrustLayerContext
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
    suspend fun update(): Any = withContext(Dispatchers.IO) { // Returns DidDocument but using Any to avoid dependency
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
        
        // Get DID method from trust layer
        val didMethod = context.getDidMethod(methodName)
            ?: throw IllegalStateException(
                "DID method '$methodName' is not configured in trust layer. " +
                "Configure it in trustLayer { did { method(\"$methodName\") { ... } } }"
            )
        
        // Update DID document using reflection
        val updatedDoc = try {
            val updateDidMethod = didMethod.javaClass.getMethod(
                "updateDid",
                String::class.java,
                kotlin.jvm.functions.Function1::class.java,
                Continuation::class.java
            )
            
            suspendCoroutineUninterceptedOrReturn<Any> { cont ->
                try {
                    // Create updater function
                    val updater: (Any) -> Any = { currentDoc ->
                        // Extract current verification methods
                        val currentVm = try {
                            val getVmMethod = currentDoc.javaClass.getMethod("getVerificationMethod")
                            getVmMethod.invoke(currentDoc) as? List<*>
                        } catch (e: Exception) {
                            try {
                                val vmField = currentDoc.javaClass.getDeclaredField("verificationMethod")
                                vmField.isAccessible = true
                                vmField.get(currentDoc) as? List<*>
                            } catch (e2: Exception) {
                                emptyList<Any>()
                            }
                        } ?: emptyList<Any>()
                        
                        // Extract current services
                        val currentServices = try {
                            val getServicesMethod = currentDoc.javaClass.getMethod("getService")
                            getServicesMethod.invoke(currentDoc) as? List<*>
                        } catch (e: Exception) {
                            try {
                                val servicesField = currentDoc.javaClass.getDeclaredField("service")
                                servicesField.isAccessible = true
                                servicesField.get(currentDoc) as? List<*>
                            } catch (e2: Exception) {
                                emptyList<Any>()
                            }
                        } ?: emptyList<Any>()
                        
                        // Extract current authentication and assertion method
                        val currentAuth = try {
                            val getAuthMethod = currentDoc.javaClass.getMethod("getAuthentication")
                            getAuthMethod.invoke(currentDoc) as? List<*>
                        } catch (e: Exception) {
                            try {
                                val authField = currentDoc.javaClass.getDeclaredField("authentication")
                                authField.isAccessible = true
                                authField.get(currentDoc) as? List<*>
                            } catch (e2: Exception) {
                                emptyList<String>()
                            }
                        } ?: emptyList<String>()
                        
                        val currentAssertion = try {
                            val getAssertionMethod = currentDoc.javaClass.getMethod("getAssertionMethod")
                            getAssertionMethod.invoke(currentDoc) as? List<*>
                        } catch (e: Exception) {
                            try {
                                val assertionField = currentDoc.javaClass.getDeclaredField("assertionMethod")
                                assertionField.isAccessible = true
                                assertionField.get(currentDoc) as? List<*>
                            } catch (e2: Exception) {
                                emptyList<String>()
                            }
                        } ?: emptyList<String>()
                        
                        // Extract current capability invocation and delegation
                        val currentCapabilityInvocation = try {
                            val getMethod = currentDoc.javaClass.getMethod("getCapabilityInvocation")
                            getMethod.invoke(currentDoc) as? List<*>
                        } catch (e: Exception) {
                            try {
                                val field = currentDoc.javaClass.getDeclaredField("capabilityInvocation")
                                field.isAccessible = true
                                field.get(currentDoc) as? List<*>
                            } catch (e2: Exception) {
                                emptyList<String>()
                            }
                        } ?: emptyList<String>()
                        
                        val currentCapabilityDelegation = try {
                            val getMethod = currentDoc.javaClass.getMethod("getCapabilityDelegation")
                            getMethod.invoke(currentDoc) as? List<*>
                        } catch (e: Exception) {
                            try {
                                val field = currentDoc.javaClass.getDeclaredField("capabilityDelegation")
                                field.isAccessible = true
                                field.get(currentDoc) as? List<*>
                            } catch (e2: Exception) {
                                emptyList<String>()
                            }
                        } ?: emptyList<String>()
                        
                        // Extract current context
                        val currentContext = try {
                            val getMethod = currentDoc.javaClass.getMethod("getContext")
                            getMethod.invoke(currentDoc) as? List<*>
                        } catch (e: Exception) {
                            try {
                                val field = currentDoc.javaClass.getDeclaredField("context")
                                field.isAccessible = true
                                field.get(currentDoc) as? List<*>
                            } catch (e2: Exception) {
                                listOf("https://www.w3.org/ns/did/v1")
                            }
                        } ?: listOf("https://www.w3.org/ns/did/v1")
                        
                        // Filter out removed verification methods
                        val filteredVm = currentVm.filter { vm ->
                            val vmId = try {
                                val getIdMethod = vm?.javaClass?.getMethod("getId")
                                getIdMethod?.invoke(vm) as? String
                            } catch (e: Exception) {
                                try {
                                    val idField = vm?.javaClass?.getDeclaredField("id")
                                    idField?.isAccessible = true
                                    idField?.get(vm) as? String
                                } catch (e2: Exception) {
                                    null
                                }
                            }
                            !removedVerificationMethods.any { removed -> vmId?.contains(removed) == true }
                        }
                        
                        // Create new verification method objects
                        val newVmObjects = newVerificationMethods.map { vmData ->
                            try {
                                val vmClass = Class.forName("io.geoknoesis.vericore.did.VerificationMethodRef")
                                val constructor = vmClass.getDeclaredConstructor(
                                    String::class.java,
                                    String::class.java,
                                    String::class.java,
                                    Map::class.java,
                                    String::class.java
                                )
                                constructor.newInstance(
                                    vmData.id,
                                    vmData.type,
                                    vmData.controller,
                                    vmData.publicKeyJwk,
                                    vmData.publicKeyMultibase
                                )
                            } catch (e: Exception) {
                                // Fallback: create map representation
                                mapOf(
                                    "id" to vmData.id,
                                    "type" to vmData.type,
                                    "controller" to vmData.controller,
                                    "publicKeyJwk" to (vmData.publicKeyJwk ?: emptyMap()),
                                    "publicKeyMultibase" to (vmData.publicKeyMultibase ?: "")
                                )
                            }
                        }
                        
                        // Filter out removed services
                        val filteredServices = currentServices.filter { service ->
                            val serviceId = try {
                                val getIdMethod = service?.javaClass?.getMethod("getId")
                                getIdMethod?.invoke(service) as? String
                            } catch (e: Exception) {
                                try {
                                    val idField = service?.javaClass?.getDeclaredField("id")
                                    idField?.isAccessible = true
                                    idField?.get(service) as? String
                                } catch (e2: Exception) {
                                    null
                                }
                            }
                            !removedServices.contains(serviceId)
                        }
                        
                        // Create new service objects
                        val newServiceObjects = newServices.map { serviceData ->
                            try {
                                val serviceClass = Class.forName("io.geoknoesis.vericore.did.Service")
                                val constructor = serviceClass.getDeclaredConstructor(
                                    String::class.java,
                                    String::class.java,
                                    Any::class.java
                                )
                                constructor.newInstance(
                                    serviceData.id,
                                    serviceData.type,
                                    serviceData.endpoint
                                )
                            } catch (e: Exception) {
                                // Fallback: create map representation
                                mapOf(
                                    "id" to serviceData.id,
                                    "type" to serviceData.type,
                                    "serviceEndpoint" to serviceData.endpoint
                                )
                            }
                        }
                        
                        // Update authentication and assertion method lists
                        val filteredAuth = currentAuth.filter { auth ->
                            val authStr = auth?.toString() ?: ""
                            !removedVerificationMethods.any { removed -> authStr.contains(removed) }
                        } + newVerificationMethods.map { it.id }
                        
                        val filteredAssertion = currentAssertion.filter { assertion ->
                            val assertionStr = assertion?.toString() ?: ""
                            !removedVerificationMethods.any { removed -> assertionStr.contains(removed) }
                        } + newVerificationMethods.map { it.id }
                        
                        // Update capability invocation and delegation
                        val updatedCapabilityInvocation = currentCapabilityInvocation
                            .filter { it?.toString()?.let { str -> !removedCapabilityInvocation.contains(str) } != false }
                            .map { it.toString() } + addedCapabilityInvocation
                        
                        val updatedCapabilityDelegation = currentCapabilityDelegation
                            .filter { it?.toString()?.let { str -> !removedCapabilityDelegation.contains(str) } != false }
                            .map { it.toString() } + addedCapabilityDelegation
                        
                        // Use context from builder or keep current
                        val updatedContext = contextValues ?: currentContext.map { it.toString() }
                        
                        // Combine verification methods and services
                        val updatedVm = filteredVm + newVmObjects
                        val updatedServices = filteredServices + newServiceObjects
                        
                        // Use copy method if available - try with all new fields first
                        try {
                            val copyMethod = currentDoc.javaClass.getMethod(
                                "copy",
                                String::class.java,
                                List::class.java,
                                List::class.java,
                                List::class.java,
                                List::class.java,
                                List::class.java,
                                List::class.java,
                                List::class.java,
                                List::class.java,
                                List::class.java
                            )
                            copyMethod.invoke(
                                currentDoc,
                                targetDid,
                                updatedContext, // context
                                emptyList<String>(), // alsoKnownAs
                                emptyList<String>(), // controller
                                updatedVm,
                                filteredAuth,
                                filteredAssertion,
                                emptyList<String>(), // keyAgreement
                                updatedCapabilityInvocation,
                                updatedCapabilityDelegation,
                                updatedServices
                            )
                        } catch (e: Exception) {
                            // Fallback: try with original signature
                            try {
                                val copyMethod = currentDoc.javaClass.getMethod(
                                    "copy",
                                    String::class.java,
                                    List::class.java,
                                    List::class.java,
                                    List::class.java,
                                    List::class.java,
                                    List::class.java,
                                    List::class.java
                                )
                                copyMethod.invoke(
                                    currentDoc,
                                    targetDid,
                                    emptyList<String>(), // alsoKnownAs
                                    emptyList<String>(), // controller
                                    updatedVm,
                                    filteredAuth,
                                    filteredAssertion,
                                    emptyList<String>(), // keyAgreement
                                    updatedServices
                                )
                            } catch (e2: Exception) {
                                // Last fallback: try with fewer parameters
                                try {
                                    val copyMethod = currentDoc.javaClass.getMethod(
                                        "copy",
                                        List::class.java,
                                        List::class.java,
                                        List::class.java,
                                        List::class.java
                                    )
                                    copyMethod.invoke(
                                        currentDoc,
                                        updatedVm,
                                        filteredAuth,
                                        filteredAssertion,
                                        updatedServices
                                    )
                                } catch (e3: Exception) {
                                    // Last resort: return current doc
                                    currentDoc
                                }
                            }
                        }
                    }
                    
                    val result = updateDidMethod.invoke(didMethod, targetDid, updater, cont)
                    if (result === COROUTINE_SUSPENDED) {
                        COROUTINE_SUSPENDED
                    } else {
                        cont.resumeWith(Result.success(result))
                        COROUTINE_SUSPENDED
                    }
                } catch (e: Exception) {
                    cont.resumeWith(Result.failure(
                        IllegalStateException(
                            "Failed to update DID document '$targetDid': ${e.message}",
                            e
                        )
                    ))
                    COROUTINE_SUSPENDED
                }
            } ?: throw IllegalStateException("DID update was suspended")
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException(
                "DID method '$methodName' does not implement updateDid method. " +
                "Ensure it implements DidMethod interface.",
                e
            )
        }
        
        updatedDoc
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
 * Extension function to update a DID document using trust layer configuration.
 */
suspend fun TrustLayerContext.updateDid(block: DidDocumentBuilder.() -> Unit): Any {
    val builder = DidDocumentBuilder(this)
    builder.block()
    return builder.update()
}

/**
 * Extension function for direct DID document update on trust layer config.
 */
suspend fun TrustLayerConfig.updateDid(block: DidDocumentBuilder.() -> Unit): Any {
    return dsl().updateDid(block)
}

