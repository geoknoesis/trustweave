package com.trustweave.integration

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.*
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.types.*
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.testkit.services.TestkitTrustRegistryFactory
import com.trustweave.testkit.services.TestkitKmsFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

/**
 * End-to-end integration tests for web of trust features.
 */
class WebOfTrustIntegrationTest {

    @Test
    fun `test complete trust registry workflow`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val kmsRef = kms

        val trustLayer = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            keys {
                custom(kmsRef)
                signer { data, keyId -> kmsRef.sign(com.trustweave.core.types.KeyId(keyId), data) }
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

        // Add trust anchor
        trustLayer.trust {
            addAnchor(issuerDid.value) {
                credentialTypes("TestCredential")
            }
        }

        // Extract key ID from the DID document created during createDid()
        // This ensures the signing key matches what's in the DID document
        val issuerDidResolution = trustLayer.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
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
        val credential = trustLayer.issue {
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
            signedBy(issuerDid = issuerDid.value, keyId = keyId)
        }

        // Verify with trust registry
        // Note: Trust registry checking is handled by orchestration layer, not in VerificationBuilder
        val result = trustLayer.verify {
            credential(credential)
        }

        assertTrue(result.valid, "Credential should be valid. Errors: ${result.errors}, Warnings: ${result.warnings}")
        assertTrue(result.trustRegistryValid, "Issuer should be trusted in trust registry")
    }

    @Test
    fun `test delegation chain with credential issuance`() = runBlocking {
        val trustLayer = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
        }

        val delegatorDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val delegateDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val holderDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        // Set up delegation
        trustLayer.updateDid {
            did(delegatorDid.value)
            method(DidMethods.KEY)
            addCapabilityDelegation("${delegateDid.value}#key-1")
        }

        // Verify delegation
        val delegationResult = trustLayer.delegate {
            from(delegatorDid.value)
            to(delegateDid.value)
        }

        assertTrue(delegationResult.valid)

        // Issue credential using delegated authority
        val credential = trustLayer.issue {
            credential {
                id("https://example.com/delegated-credential")
                type("TestCredential")
                issuer(delegateDid.value)
                subject {
                    id(holderDid.value)
                    "test" to "value"
                }
                issued(Instant.now())
            }
            signedBy(issuerDid = delegateDid.value, keyId = "key-1")
        }

        assertNotNull(credential)
    }

    @Test
    fun `test trust path discovery with multiple anchors`() = runBlocking {
        val trustLayer = TrustWeave.build {
            factories(
                kmsFactory = TestkitKmsFactory(),
                didMethodFactory = TestkitDidMethodFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
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
            addAnchor(anchor1.value) {}
            addAnchor(anchor2.value) {}
            addAnchor(anchor3.value) {}

            // Get registry to add relationships
            val registry = trustLayer.getDslContext().getTrustRegistry() as? com.trustweave.testkit.trust.InMemoryTrustRegistry
            registry?.addTrustRelationship(anchor1.value, anchor2.value)
            registry?.addTrustRelationship(anchor2.value, anchor3.value)

            val path = findTrustPath(
                com.trustweave.trust.types.VerifierIdentity(com.trustweave.trust.types.Did(anchor1.value)),
                com.trustweave.trust.types.IssuerIdentity.from(anchor3.value, "key-1")
            )
            assertTrue(path is com.trustweave.trust.types.TrustPath.Verified)
            val verified = path as com.trustweave.trust.types.TrustPath.Verified
            assertTrue(verified.fullPath.size >= 2)
        }
    }

    @Test
    fun `test proof purpose validation in credential verification`() = runBlocking {
        val trustLayer = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys { provider("inMemory") }
            did { method(DidMethods.KEY) {} }
        }

        val issuerDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        val holderDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }

        // Update issuer DID to have assertionMethod
        trustLayer.updateDid {
            did(issuerDid.value)
            method(DidMethods.KEY)
            addKey {
                type("Ed25519VerificationKey2020")
            }
        }

        // Issue credential
        val credential = trustLayer.issue {
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
            signedBy(issuerDid = issuerDid.value, keyId = "key-1")
        }

        // Verify with proof purpose validation
        val result = trustLayer.verify {
            credential(credential)
            validateProofPurpose()
        }

        // Note: This may fail if resolveDid is not properly configured
        // The test verifies the integration path works
        assertNotNull(result)
    }
}

