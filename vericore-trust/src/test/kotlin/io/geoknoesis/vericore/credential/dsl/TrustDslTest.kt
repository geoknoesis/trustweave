package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import io.geoknoesis.vericore.testkit.trust.InMemoryTrustRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Trust Registry DSL.
 */
class TrustDslTest {
    
    @Test
    fun `test trust layer configuration with trust registry`() = runBlocking {
        val trustLayer = trustLayer {
            keys {
                provider("inMemory")
                algorithm(KeyAlgorithms.ED25519)
            }
            
            did {
                method(DidMethods.KEY) {
                    algorithm(KeyAlgorithms.ED25519)
                }
            }
            
            trust {
                provider("inMemory")
            }
        }
        
        val registry = trustLayer.dsl().getTrustRegistry()
        assertNotNull(registry)
    }
    
    @Test
    fun `test add anchor via DSL`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        trustLayer.trust {
            val added = addAnchor("did:key:university") {
                credentialTypes("EducationCredential")
                description("Trusted university")
            }
            
            assertTrue(added)
        }
    }
    
    @Test
    fun `test check trust via DSL`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        trustLayer.trust {
            addAnchor("did:key:university") {
                credentialTypes("EducationCredential")
            }
            
            val isTrusted = isTrusted("did:key:university", "EducationCredential")
            assertTrue(isTrusted)
            
            val notTrusted = isTrusted("did:key:university", "EmploymentCredential")
            assertFalse(notTrusted)
        }
    }
    
    @Test
    fun `test get trust path via DSL`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        val anchor1 = "did:key:anchor1"
        val anchor2 = "did:key:anchor2"
        
        trustLayer.trust {
            addAnchor(anchor1) {}
            addAnchor(anchor2) {}
            
            // Get registry to add relationship
            val registry = trustLayer.dsl().getTrustRegistry() as? InMemoryTrustRegistry
            registry?.addTrustRelationship(anchor1, anchor2)
            
            val path = getTrustPath(anchor1, anchor2)
            assertNotNull(path)
            assertTrue(path.valid)
        }
    }
    
    @Test
    fun `test get trusted issuers via DSL`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        trustLayer.trust {
            addAnchor("did:key:university") {
                credentialTypes("EducationCredential")
            }
            addAnchor("did:key:company") {
                credentialTypes("EmploymentCredential")
            }
            
            val educationIssuers = getTrustedIssuers("EducationCredential")
            assertEquals(1, educationIssuers.size)
            assertTrue(educationIssuers.contains("did:key:university"))
        }
    }
    
    @Test
    fun `test remove anchor via DSL`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        trustLayer.trust {
            addAnchor("did:key:university") {}
            assertTrue(isTrusted("did:key:university", null))
            
            val removed = removeAnchor("did:key:university")
            assertTrue(removed)
            assertFalse(isTrusted("did:key:university", null))
        }
    }
}

