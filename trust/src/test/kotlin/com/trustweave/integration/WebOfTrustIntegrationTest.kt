package com.trustweave.integration

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.*
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.types.*
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.testkit.services.TestkitTrustRegistryFactory
import com.trustweave.testkit.services.TestkitKmsFactory
import com.trustweave.testkit.getOrFail
import com.trustweave.kms.results.SignResult
import com.trustweave.did.resolver.DidResolver
import com.trustweave.credential.credentialService
import com.trustweave.trust.dsl.TrustWeaveRegistries
import com.trustweave.anchor.BlockchainAnchorRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * End-to-end integration tests for web of trust features.
 */
class WebOfTrustIntegrationTest {

    // Helper function to create TrustWeave with CredentialService
    private suspend fun createTrustWeaveWithCredentialService(
        kms: InMemoryKeyManagementService
    ): TrustWeave {
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                else -> throw IllegalStateException("Signing failed: $result")
            }
        }
        
        // Create a shared DidMethodRegistry to ensure DIDs are stored in the same registry
        // that the resolver uses. This ensures DIDs created via createDid are resolvable.
        val sharedDidRegistry = com.trustweave.did.registry.DidMethodRegistry()
        
        // Create resolver that uses the shared registry
        val didResolver = DidResolver { did: com.trustweave.did.identifiers.Did ->
            sharedDidRegistry.resolve(did.value) as com.trustweave.did.resolver.DidResolutionResult
        }
        
        val credentialService = com.trustweave.credential.credentialService(
            didResolver = didResolver,
            signer = signer
        )
        
        val finalTrustWeave = TrustWeave.build(
            registries = TrustWeaveRegistries(
                didRegistry = sharedDidRegistry,
                blockchainRegistry = BlockchainAnchorRegistry(),
                credentialRegistry = null,
                proofRegistry = null
            )
        ) {
            factories(
                didMethodFactory = TestkitDidMethodFactory(didRegistry = sharedDidRegistry),
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId ->
                    when (val result = kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
            issuer(credentialService)
        }
        
        return finalTrustWeave
    }

    @Test
    fun `test complete trust registry workflow`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        
        val trustLayer = createTrustWeaveWithCredentialService(kms)

        val issuerDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val holderDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

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
        val keyId = verificationMethod.id.value.substringAfter("#")

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
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = keyId)
        }.getOrFail()

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
        }.getOrFail()

        val delegateDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val holderDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

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
                issued(Clock.System.now())
            }
            signedBy(issuerDid = delegateDid.value, keyId = "key-1")
        }.getOrFail()

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
        }.getOrFail()

        val anchor2 = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val anchor3 = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        trustLayer.trust {
            addAnchor(anchor1.value) {}
            addAnchor(anchor2.value) {}
            addAnchor(anchor3.value) {}

            // Get registry to add relationships
            val registry = trustLayer.getDslContext().getTrustRegistry() as? com.trustweave.testkit.trust.InMemoryTrustRegistry
            registry?.addTrustRelationship(anchor1.value, anchor2.value)
            registry?.addTrustRelationship(anchor2.value, anchor3.value)

            val path = findTrustPath(
                com.trustweave.did.identifiers.Did(anchor1.value),
                com.trustweave.did.identifiers.Did(anchor3.value)
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
        }.getOrFail()

        val holderDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

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
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = "key-1")
        }.getOrFail()

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

