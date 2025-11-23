package com.trustweave.credential.dsl

import com.trustweave.did.DidMethodRegistry
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for DidDocumentDsl.kt
 */
class DidDocumentDslTest {
    
    private lateinit var trustLayer: TrustLayerConfig
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var didMethod: DidKeyMockMethod
    private lateinit var didRegistry: DidMethodRegistry
    
    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()
        didMethod = DidKeyMockMethod(kms)
        didRegistry = DidMethodRegistry()
        didRegistry.register(didMethod)
        
        trustLayer = trustLayer {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
    }
    
    @Test
    fun `test updateDid add key`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val keyHandle = kms.generateKey("Ed25519")
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            addKey {
                type("Ed25519VerificationKey2020")
                publicKeyJwk(keyHandle.publicKeyJwk ?: emptyMap())
            }
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test updateDid add service`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test updateDid remove key`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            removeKey("$did#key-1")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test updateDid remove service`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        // First add a service
        trustLayer.updateDid {
            did(did)
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
        }
        
        // Then remove it
        val updatedDoc = trustLayer.updateDid {
            did(did)
            removeService("$did#service-1")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test updateDid without DID throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.updateDid {
                addKey {
                    type("Ed25519VerificationKey2020")
                }
            }
        }
    }
    
    @Test
    fun `test updateDid add service without required fields throws exception`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        assertFailsWith<IllegalStateException> {
            trustLayer.updateDid {
                did(did)
                addService {
                    // Missing required fields
                }
            }
        }
    }
    
    @Test
    fun `test updateDid add key with multibase`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            addKey {
                type("Ed25519VerificationKey2020")
                publicKeyMultibase("z6Mk...")
            }
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test updateDid add multiple keys and services`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val keyHandle1 = kms.generateKey("Ed25519")
        val keyHandle2 = kms.generateKey("Ed25519")
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            addKey {
                type("Ed25519VerificationKey2020")
                publicKeyJwk(keyHandle1.publicKeyJwk ?: emptyMap())
            }
            addKey {
                type("Ed25519VerificationKey2020")
                publicKeyJwk(keyHandle2.publicKeyJwk ?: emptyMap())
            }
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
            addService {
                id("$did#service-2")
                type(ServiceTypes.DID_COMM_MESSAGING)
                endpoint("https://messaging.example.com")
            }
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test updateDid auto-detects method from DID`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val updatedDoc = trustLayer.updateDid {
            did(did)
            // Method should be auto-detected
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test updateDid via TrustLayerContext`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val context = trustLayer.dsl()
        val updatedDoc = context.updateDid {
            did(did)
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
        }
        
        assertNotNull(updatedDoc)
    }
}

