package com.trustweave.trust.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.did.DidDocument
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.types.*
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
        val trustWeave = TrustWeave.build {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val registry = trustWeave.getDslContext().getTrustRegistry()
        assertNotNull(registry)
    }

    @Test
    fun `test add multiple trust anchors with different credential types`() = runBlocking {
        val trustWeave = TrustWeave.build {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val universityDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val companyDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        trustWeave.trust {
            addAnchor(universityDid.value) {
                credentialTypes("EducationCredential", "DegreeCredential")
            }

            addAnchor(companyDid.value) {
                credentialTypes("EmploymentCredential")
            }

            assertTrue(isTrusted(universityDid.value, "EducationCredential"))
            assertTrue(isTrusted(universityDid.value, "DegreeCredential"))
            assertFalse(isTrusted(universityDid.value, "EmploymentCredential"))

            assertTrue(isTrusted(companyDid.value, "EmploymentCredential"))
            assertFalse(isTrusted(companyDid.value, "EducationCredential"))
        }
    }

    @Test
    fun `test trust path discovery with multiple anchors`() = runBlocking {
        val trustWeave = TrustWeave.build {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val anchor1 = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val anchor2 = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val anchor3 = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        trustWeave.trust {
            addAnchor(anchor1.value) {}
            addAnchor(anchor2.value) {}
            addAnchor(anchor3.value) {}

            val registry = trustWeave.getDslContext().getTrustRegistry() as? com.trustweave.testkit.trust.InMemoryTrustRegistry
            registry?.addTrustRelationship(anchor1.value, anchor2.value)
            registry?.addTrustRelationship(anchor2.value, anchor3.value)

            val path = getTrustPath(anchor1.value, anchor3.value)
            assertNotNull(path)
            assertTrue(path.valid)
            assertTrue(path.path.size >= 2)
            assertTrue(path.trustScore > 0.0)
            assertTrue(path.trustScore <= 1.0)
        }
    }

    @Test
    fun `test get trusted issuers with filtering`() = runBlocking {
        val trustWeave = TrustWeave.build {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val eduIssuer1 = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val eduIssuer2 = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val empIssuer = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        trustWeave.trust {
            addAnchor(eduIssuer1.value) {
                credentialTypes("EducationCredential")
            }

            addAnchor(eduIssuer2.value) {
                credentialTypes("EducationCredential")
            }

            addAnchor(empIssuer.value) {
                credentialTypes("EmploymentCredential")
            }

            val educationIssuers = getTrustedIssuers("EducationCredential")
            assertEquals(2, educationIssuers.size)
            assertTrue(educationIssuers.contains(eduIssuer1.value))
            assertTrue(educationIssuers.contains(eduIssuer2.value))

            val employmentIssuers = getTrustedIssuers("EmploymentCredential")
            assertEquals(1, employmentIssuers.size)
            assertTrue(employmentIssuers.contains(empIssuer.value))

            val allIssuers = getTrustedIssuers(null)
            assertEquals(3, allIssuers.size)
        }
    }

    @Test
    fun `test remove trust anchor via DSL`() = runBlocking {
        val trustWeave = TrustWeave.build {
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        trustWeave.trust {
            addAnchor(issuerDid.value) {
                credentialTypes("TestCredential")
            }

            assertTrue(isTrusted(issuerDid.value, "TestCredential"))

            val removed = removeAnchor(issuerDid.value)
            assertTrue(removed)

            assertFalse(isTrusted(issuerDid.value, "TestCredential"))
        }
    }

    @Test
    fun `test trust registry with credential verification integration`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val kmsRef = kms

        val trustWeave = TrustWeave.build {
            keys {
                custom(kmsRef)
                signer { data, keyId -> kmsRef.sign(com.trustweave.core.types.KeyId(keyId), data) }
            }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val holderDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        trustWeave.trust {
            addAnchor(issuerDid.value) {
                credentialTypes("TestCredential")
            }
        }

        // Extract key ID from the DID document created during createDid()
        // This ensures the signing key matches what's in the DID document
        val issuerDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerDidDoc = when (issuerDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }

        val verificationMethod = issuerDidDoc.verificationMethod.firstOrNull()
            ?: throw IllegalStateException("No verification method found in issuer DID document")

        // Extract key ID from verification method ID (e.g., "did:key:xxx#key-1" -> "key-1")
        val keyId = verificationMethod.id.substringAfter("#")

        // Issue credential using the key ID from the DID document
        // The IssuanceDsl will construct verificationMethodId as "$issuerDid#$keyId" which matches the DID document
        val credential = trustWeave.issue {
            credential {
                id("https://example.com/credential-1")
                type("TestCredential")
                issuer(issuerDid.value)
                subject {
                    id(holderDid.value)
                    "test" to "value"
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDid.value, keyId = keyId)
        }

        val result = trustWeave.verify {
            credential(credential)
            // Note: checkTrustRegistry() and checkTrust() not available in VerificationBuilder
            // Trust checking is handled by the orchestration layer
        }

        // Use VerificationResult extension properties (imported from VerificationResults.kt)
        assertTrue(result.trustRegistryValid, "Issuer should be trusted in trust registry")
        assertTrue(result.valid, "Credential should be valid. Errors: ${result.errors}, Warnings: ${result.warnings}")
    }
}


