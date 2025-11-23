package com.trustweave.did.services

import com.trustweave.did.services.DidDocumentAccess
import com.trustweave.did.services.VerificationMethodAccess
import com.trustweave.did.services.ServiceAccess
import com.trustweave.did.DidDocument
import com.trustweave.did.DidResolutionResult
import com.trustweave.did.VerificationMethodRef
import com.trustweave.did.Service

/**
 * Adapter that provides direct access to DID Document properties.
 * 
 * This adapter eliminates the need for reflection when accessing DidDocument
 * and VerificationMethodRef properties.
 */
class DidDocumentAccessAdapter : DidDocumentAccess {
    override fun getDocument(result: Any): Any? {
        val resolutionResult = result as? DidResolutionResult
        return resolutionResult?.document as? Any
    }
    
    override fun getAssertionMethod(doc: Any): List<String> {
        val document = doc as? DidDocument ?: return emptyList()
        return document.assertionMethod
    }
    
    override fun getAuthentication(doc: Any): List<String> {
        val document = doc as? DidDocument ?: return emptyList()
        return document.authentication
    }
    
    override fun getKeyAgreement(doc: Any): List<String> {
        val document = doc as? DidDocument ?: return emptyList()
        return document.keyAgreement
    }
    
    override fun getCapabilityInvocation(doc: Any): List<String> {
        val document = doc as? DidDocument ?: return emptyList()
        return document.capabilityInvocation
    }
    
    override fun getCapabilityDelegation(doc: Any): List<String> {
        val document = doc as? DidDocument ?: return emptyList()
        return document.capabilityDelegation
    }
    
    override fun getVerificationMethod(doc: Any): List<Any> {
        val document = doc as? DidDocument ?: return emptyList()
        return document.verificationMethod.map { it as Any }
    }
    
    override fun getService(doc: Any): List<Any> {
        val document = doc as? DidDocument ?: return emptyList()
        return document.service.map { it as Any }
    }
    
    override fun getContext(doc: Any): List<String> {
        val document = doc as? DidDocument ?: return listOf("https://www.w3.org/ns/did/v1")
        return document.context
    }
    
    override fun createVerificationMethod(
        id: String,
        type: String,
        controller: String,
        publicKeyJwk: Map<String, Any?>?,
        publicKeyMultibase: String?
    ): Any {
        return VerificationMethodRef(
            id = id,
            type = type,
            controller = controller,
            publicKeyJwk = publicKeyJwk,
            publicKeyMultibase = publicKeyMultibase
        ) as Any
    }
    
    override fun createService(
        id: String,
        type: String,
        serviceEndpoint: Any
    ): Any {
        return Service(
            id = id,
            type = type,
            serviceEndpoint = serviceEndpoint
        ) as Any
    }
    
    override fun copyDocument(
        doc: Any,
        id: String?,
        context: List<String>?,
        alsoKnownAs: List<String>?,
        controller: List<String>?,
        verificationMethod: List<Any>?,
        authentication: List<String>?,
        assertionMethod: List<String>?,
        keyAgreement: List<String>?,
        capabilityInvocation: List<String>?,
        capabilityDelegation: List<String>?,
        service: List<Any>?
    ): Any {
        val document = doc as? DidDocument
            ?: throw IllegalArgumentException("Expected DidDocument, got ${doc.javaClass.name}")
        
        return document.copy(
            id = id ?: document.id,
            context = context ?: document.context,
            alsoKnownAs = alsoKnownAs ?: document.alsoKnownAs,
            controller = controller ?: document.controller,
            verificationMethod = verificationMethod?.map { it as VerificationMethodRef } ?: document.verificationMethod,
            authentication = authentication ?: document.authentication,
            assertionMethod = assertionMethod ?: document.assertionMethod,
            keyAgreement = keyAgreement ?: document.keyAgreement,
            capabilityInvocation = capabilityInvocation ?: document.capabilityInvocation,
            capabilityDelegation = capabilityDelegation ?: document.capabilityDelegation,
            service = service?.map { it as Service } ?: document.service
        ) as Any
    }
    
}

/**
 * Adapter that provides direct access to VerificationMethodRef properties.
 */
class VerificationMethodAccessAdapter : VerificationMethodAccess {
    override fun getId(vm: Any): String {
        val verificationMethod = vm as? VerificationMethodRef
            ?: throw IllegalArgumentException("Expected VerificationMethodRef, got ${vm.javaClass.name}")
        return verificationMethod.id
    }
    
    override fun getController(vm: Any): String {
        val verificationMethod = vm as? VerificationMethodRef
            ?: throw IllegalArgumentException("Expected VerificationMethodRef, got ${vm.javaClass.name}")
        return verificationMethod.controller
    }
    
}

/**
 * Adapter that provides direct access to Service properties.
 */
class ServiceAccessAdapter : ServiceAccess {
    override fun getId(service: Any): String {
        val svc = service as? Service
            ?: throw IllegalArgumentException("Expected Service, got ${service.javaClass.name}")
        return svc.id
    }
    
    override fun getType(service: Any): String {
        val svc = service as? Service
            ?: throw IllegalArgumentException("Expected Service, got ${service.javaClass.name}")
        return svc.type
    }
    
    override fun getServiceEndpoint(service: Any): Any {
        val svc = service as? Service
            ?: throw IllegalArgumentException("Expected Service, got ${service.javaClass.name}")
        return svc.serviceEndpoint
    }
    
}

