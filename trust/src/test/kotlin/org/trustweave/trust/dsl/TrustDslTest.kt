package org.trustweave.trust.dsl

import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.trust.InMemoryTrustRegistry
import org.trustweave.testkit.services.TestkitTrustRegistryFactory
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
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
        val trustWeave = TrustWeave.build {
            factories(
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            // KMS and DID methods auto-discovered via SPI
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

        val registry = trustWeave.getDslContext().getTrustRegistry()
        assertNotNull(registry)
    }

    @Test
    fun `test add anchor via DSL`() = runBlocking {
        val trustWeave = TrustWeave.build {
            factories(
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            // KMS and DID methods auto-discovered via SPI
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        trustWeave.trust {
            val added = addAnchor("did:key:university") {
                credentialTypes("EducationCredential")
                description("Trusted university")
            }

            assertTrue(added)
        }
    }

    @Test
    fun `test check trust via DSL`() = runBlocking {
        val trustWeave = TrustWeave.build {
            factories(
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            // KMS and DID methods auto-discovered via SPI
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        trustWeave.trust {
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
        val trustWeave = TrustWeave.build {
            factories(
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            // KMS and DID methods auto-discovered via SPI
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val anchor1 = "did:key:anchor1"
        val anchor2 = "did:key:anchor2"

        trustWeave.trust {
            addAnchor(anchor1) {}
            addAnchor(anchor2) {}

            // Get registry to add relationship
            val registry = trustWeave.getDslContext().getTrustRegistry() as? InMemoryTrustRegistry
            registry?.addTrustRelationship(anchor1, anchor2)

            val path = findTrustPath(
                org.trustweave.did.identifiers.Did(anchor1),
                org.trustweave.did.identifiers.Did(anchor2)
            )
            assertTrue(path is org.trustweave.trust.types.TrustPath.Verified)
        }
    }

    @Test
    fun `test get trusted issuers via DSL`() = runBlocking {
        val trustWeave = TrustWeave.build {
            factories(
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            // KMS and DID methods auto-discovered via SPI
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        trustWeave.trust {
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
        val trustWeave = TrustWeave.build {
            factories(
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            // KMS and DID methods auto-discovered via SPI
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        trustWeave.trust {
            addAnchor("did:key:university") {}
            assertTrue(isTrusted("did:key:university", null))

            val removed = removeAnchor("did:key:university")
            assertTrue(removed)
            assertFalse(isTrusted("did:key:university", null))
        }
    }
}


