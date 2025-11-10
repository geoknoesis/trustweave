package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidResolutionResult
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

/**
 * Comprehensive tests for Trust Registry DSL integration.
 */
class TrustRegistryDslComprehensiveTest {
    
    @Test
    fun `test trust registry configuration in trust layer`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        val registry = trustLayer.dsl().getTrustRegistry()
        assertNotNull(registry)
    }
    
    @Test
    fun `test add multiple trust anchors with different credential types`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        val universityDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val companyDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        trustLayer.trust {
            addAnchor(universityDid) {
                credentialTypes("EducationCredential", "DegreeCredential")
            }
            
            addAnchor(companyDid) {
                credentialTypes("EmploymentCredential")
            }
            
            assertTrue(isTrusted(universityDid, "EducationCredential"))
            assertTrue(isTrusted(universityDid, "DegreeCredential"))
            assertFalse(isTrusted(universityDid, "EmploymentCredential"))
            
            assertTrue(isTrusted(companyDid, "EmploymentCredential"))
            assertFalse(isTrusted(companyDid, "EducationCredential"))
        }
    }
    
    @Test
    fun `test trust path discovery with multiple anchors`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        val anchor1 = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val anchor2 = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val anchor3 = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        trustLayer.trust {
            addAnchor(anchor1) {}
            addAnchor(anchor2) {}
            addAnchor(anchor3) {}
            
            val registry = trustLayer.dsl().getTrustRegistry() as? io.geoknoesis.vericore.testkit.trust.InMemoryTrustRegistry
            registry?.addTrustRelationship(anchor1, anchor2)
            registry?.addTrustRelationship(anchor2, anchor3)
            
            val path = getTrustPath(anchor1, anchor3)
            assertNotNull(path)
            assertTrue(path.valid)
            assertTrue(path.path.size >= 2)
            assertTrue(path.trustScore > 0.0)
            assertTrue(path.trustScore <= 1.0)
        }
    }
    
    @Test
    fun `test get trusted issuers with filtering`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        val eduIssuer1 = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val eduIssuer2 = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val empIssuer = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        trustLayer.trust {
            addAnchor(eduIssuer1) {
                credentialTypes("EducationCredential")
            }
            
            addAnchor(eduIssuer2) {
                credentialTypes("EducationCredential")
            }
            
            addAnchor(empIssuer) {
                credentialTypes("EmploymentCredential")
            }
            
            val educationIssuers = getTrustedIssuers("EducationCredential")
            assertEquals(2, educationIssuers.size)
            assertTrue(educationIssuers.contains(eduIssuer1))
            assertTrue(educationIssuers.contains(eduIssuer2))
            
            val employmentIssuers = getTrustedIssuers("EmploymentCredential")
            assertEquals(1, employmentIssuers.size)
            assertTrue(employmentIssuers.contains(empIssuer))
            
            val allIssuers = getTrustedIssuers(null)
            assertEquals(3, allIssuers.size)
        }
    }
    
    @Test
    fun `test remove trust anchor via DSL`() = runBlocking {
        val trustLayer = trustLayer {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        val issuerDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        trustLayer.trust {
            addAnchor(issuerDid) {
                credentialTypes("TestCredential")
            }
            
            assertTrue(isTrusted(issuerDid, "TestCredential"))
            
            val removed = removeAnchor(issuerDid)
            assertTrue(removed)
            
            assertFalse(isTrusted(issuerDid, "TestCredential"))
        }
    }
    
    @Test
    fun `test trust registry with credential verification integration`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val kmsRef = kms
        
        val trustLayer = trustLayer {
            keys {
                custom(kmsRef)
                signer { data, keyId -> kmsRef.sign(keyId, data) }
            }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        val issuerDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val holderDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        trustLayer.trust {
            addAnchor(issuerDid) {
                credentialTypes("TestCredential")
            }
        }
        
        // Generate key for issuer
        val issuerKey = kms.generateKey("Ed25519")
        
        val credential = trustLayer.issue {
            credential {
                id("https://example.com/credential-1")
                type("TestCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    "test" to "value"
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDid, keyId = issuerKey.id)
        }
        
        val result = trustLayer.verify {
            credential(credential)
            checkTrustRegistry()
        }
        
        assertTrue(result.trustRegistryValid)
        assertTrue(result.valid)
    }
}

