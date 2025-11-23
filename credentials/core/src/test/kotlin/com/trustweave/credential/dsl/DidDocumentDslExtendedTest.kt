package com.trustweave.credential.dsl

import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for extended DID Document DSL.
 */
class DidDocumentDslExtendedTest {
    
    @Test
    fun `test add capability invocation via DSL`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustLayer = trustLayer {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }
        
        val did = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            method(DidMethods.KEY)
            addCapabilityInvocation("$did#key-1")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test add capability delegation via DSL`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustLayer = trustLayer {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }
        
        val did = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            method(DidMethods.KEY)
            addCapabilityDelegation("$did#key-1")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test set context via DSL`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustLayer = trustLayer {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }
        
        val did = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            method(DidMethods.KEY)
            context("https://www.w3.org/ns/did/v1", "https://example.com/context/v1")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test remove capability invocation via DSL`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustLayer = trustLayer {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }
        
        val did = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        // First add, then remove
        trustLayer.updateDid {
            did(did)
            method(DidMethods.KEY)
            addCapabilityInvocation("$did#key-1")
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            method(DidMethods.KEY)
            removeCapabilityInvocation("$did#key-1")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test full DID document update with all new fields`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustLayer = trustLayer {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }
        
        val did = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            method(DidMethods.KEY)
            addKey {
                type("Ed25519VerificationKey2020")
            }
            addCapabilityInvocation("$did#key-1")
            addCapabilityDelegation("$did#key-1")
            context("https://www.w3.org/ns/did/v1")
            addService {
                id("$did#service-1")
                type("LinkedDomains")
                endpoint("https://example.com")
            }
        }
        
        assertNotNull(updatedDoc)
    }
}

