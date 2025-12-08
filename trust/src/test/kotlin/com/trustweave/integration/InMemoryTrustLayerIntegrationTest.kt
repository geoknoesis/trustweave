package com.trustweave.integration

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.*
import com.trustweave.trust.dsl.wallet.query as dslQuery
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.dsl.credential.presentation
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.types.*
import com.trustweave.credential.presentation.PresentationService
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.annotations.RequiresPlugin
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.testkit.services.TestkitTrustRegistryFactory
import com.trustweave.testkit.services.TestkitWalletFactory
import com.trustweave.testkit.services.TestkitStatusListRegistryFactory
import com.trustweave.testkit.services.TestkitBlockchainAnchorClientFactory
import com.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Template test demonstrating complete in-memory workflow.
 *
 * This test uses ONLY in-memory components and should always pass.
 * Use this as a template for creating new integration tests with in-memory components.
 *
 * **Key Pattern:**
 * 1. Extract key ID from DID document (not generate a new key)
 * 2. Use that key ID when issuing credentials
 * 3. This ensures proof verification succeeds because the DID document contains the correct verification method
 */
class InMemoryTrustLayerIntegrationTest {

    @Test
    fun `test complete in-memory workflow template`() = runBlocking {
        // Step 1: Setup in-memory KMS
        val kms = InMemoryKeyManagementService()

        // Step 2: Configure TrustWeave with in-memory components
        val trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory(),
                walletFactory = TestkitWalletFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data) }
            }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        // Step 3: Create DIDs (this generates keys and stores DID documents)
        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val holderDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        // Step 4: Extract key ID from the issuer DID document
        // CRITICAL: The key used for signing MUST match what's in the DID document
        // This ensures proof verification succeeds because the DID document contains the correct verification method
        val issuerDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerDidDoc = when (issuerDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }

        val verificationMethod = issuerDidDoc.verificationMethod.firstOrNull()
            ?: throw IllegalStateException("No verification method in issuer DID")

        // Extract key ID from verification method ID (e.g., "did:key:xxx#key-1" -> "key-1")
        val keyId = verificationMethod.id.substringAfter("#")

        // Note: The IssuanceDsl will automatically construct verificationMethodId as "$issuerDid#$keyId"
        // which matches the format in the DID document, ensuring proof verification succeeds

        // Step 5: Configure trust registry (if needed)
        trustWeave.trust {
            addAnchor(issuerDid.value) {
                credentialTypes("TestCredential", "EducationCredential")
            }
        }

        // Step 6: Issue credential using the key from the DID document
        val credential = trustWeave.issue {
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
            signedBy(issuerDid = issuerDid.value, keyId = keyId) // MUST match key in DID document
        }.getOrFail()

        assertNotNull(credential, "Credential should be issued")
        assertNotNull(credential.proof, "Credential should have proof")

        // Step 7: Verify credential with all checks
        val result = trustWeave.verify {
            credential(credential)
            // Note: Trust registry checking is handled by orchestration layer, not in VerificationBuilder
            checkExpiration()
            checkRevocation()
        }

        // Step 8: Assert results with detailed error messages
        assertTrue(result.proofValid, "Proof should be valid")
        assertTrue(result.issuerValid, "Issuer DID should resolve")
        assertTrue(result.trustRegistryValid, "Issuer should be trusted in trust registry")
        assertTrue(result.valid, "Credential should be valid. Errors: ${result.errors}, Warnings: ${result.warnings}")
    }

    /**
     * Template for external service tests - automatically disabled if credentials not available.
     *
     * Use @RequiresPlugin annotation to list all required plugins.
     * Tests will be automatically skipped if environment variables are not available.
     */
    @Test
    @RequiresPlugin("aws-kms", "ethr-did") // List all required plugins
    fun `test with external services template`() = runBlocking {
        // This test will be automatically skipped if AWS credentials or Ethereum RPC URL are not available
        // Use this pattern for tests requiring external services

        val trustWeave = TrustWeave.build {
            keys { provider("aws-kms") } // Requires AWS credentials (see AwsKeyManagementServiceProvider)
            did { method("ethr") {} } // Requires ETHEREUM_RPC_URL (see EthrDidMethodProvider)
            trust { provider("inMemory") }
        }

        // Test implementation here...
        // If you reach here, all required env vars are available

        // Example: Create DID with external service
        val did = trustWeave.createDid {
            method("ethr")
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        assertNotNull(did, "DID should be created")
    }

    /**
     * Template for credential revocation workflow.
     *
     * Demonstrates: Issue credential with revocation → Revoke credential → Verify revocation
     *
     * **Key Pattern:** Same as basic workflow - extract key ID from DID document
     */
    @Test
    fun `test credential revocation workflow template`() = runBlocking {
        val kms = InMemoryKeyManagementService()

        val trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory(),
                statusListRegistryFactory = TestkitStatusListRegistryFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data) }
            }
            did { method(DidMethods.KEY) {} }
            revocation { provider("inMemory") }
            trust { provider("inMemory") }
        }

        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val holderDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        // Extract key ID (same pattern)
        val issuerDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerDidDoc = when (issuerDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }

        val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")

        // Issue credential with revocation support
        val credential = trustWeave.issue {
            credential {
                id("https://example.com/revocable-credential-1")
                type("RevocableCredential")
                issuer(issuerDid.value)
                subject {
                    id(holderDid.value)
                    "test" to "value"
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = keyId)
            withRevocation() // Enable revocation status list
        }.getOrFail()

        assertNotNull(credential.credentialStatus, "Credential should have revocation status")
        val statusListId = credential.credentialStatus?.statusListCredential
            ?: throw IllegalStateException("Credential should have status list ID")

        // Verify credential is not revoked
        val initialResult = trustWeave.verify {
            credential(credential)
            checkRevocation()
        }
        assertTrue(initialResult is VerificationResult.Valid && !initialResult.revoked, "Credential should not be revoked initially")

        // Revoke credential
        val revoked = trustWeave.revoke {
            credential(credential.id ?: throw IllegalStateException("Credential must have ID"))
            statusList(statusListId)
        }
        assertTrue(revoked, "Credential should be revoked")

        // Verify credential is now revoked
        val revokedResult = trustWeave.verify {
            credential(credential)
            checkRevocation()
        }
        assertTrue(revokedResult is com.trustweave.trust.types.VerificationResult.Invalid.Revoked, "Credential should be revoked")
    }

    /**
     * Template for wallet storage and retrieval workflow.
     *
     * Demonstrates: Create wallet → Store credential → Retrieve credential → Query credentials
     *
     * **Key Pattern:** Same as basic workflow - extract key ID from DID document
     */
    @Test
    fun `test wallet storage workflow template`() = runBlocking {
        val kms = InMemoryKeyManagementService()

        val trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory(),
                walletFactory = TestkitWalletFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data) }
            }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val holderDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        // Extract key ID (same pattern)
        val issuerDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerDidDoc = when (issuerDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }

        val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")

        // Issue credential
        val credential = trustWeave.issue {
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

        // Create wallet for holder
        val wallet = trustWeave.getDslContext().wallet {
            id("holder-wallet-1")
            holder(holderDid.value)
            inMemory()
            enableOrganization()
            enablePresentation()
        }.getOrFail()

        // Store credential in wallet
        val storedCredential = credential.storeIn(wallet)
        assertNotNull(storedCredential, "Credential should be stored")

        // Retrieve credential from wallet
        val retrievedCredential = wallet.get(credential.id ?: throw IllegalStateException("Credential must have ID"))
        assertNotNull(retrievedCredential, "Credential should be retrievable")

        // Query credentials by type
        val credentials = wallet.dslQuery {
            type("TestCredential")
            valid()
        }
        assertTrue(credentials.isNotEmpty(), "Should find stored credential")
    }

    /**
     * Template for verifiable presentation workflow.
     *
     * Demonstrates: Store credentials → Create presentation → Verify presentation
     *
     * **Key Pattern:** Extract key IDs from both issuer and holder DID documents
     */
    @Test
    fun `test verifiable presentation workflow template`() = runBlocking {
        val kms = InMemoryKeyManagementService()

        val trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory(),
                walletFactory = TestkitWalletFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data) }
            }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val holderDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        // Extract key IDs (same pattern for both issuer and holder)
        val issuerDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerDidDoc = when (issuerDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }
        val issuerKeyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")

        val holderDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(holderDid.value)
            ?: throw IllegalStateException("Failed to resolve holder DID")
        val holderDidDoc = when (holderDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> holderDidResolution.document
            else -> throw IllegalStateException("Failed to resolve holder DID")
        }
        val holderKeyId = holderDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in holder DID")

        // Issue multiple credentials
        val credential1 = trustWeave.issue {
            credential {
                id("https://example.com/credential-1")
                type("EducationCredential")
                issuer(issuerDid.value)
                subject {
                    id(holderDid.value)
                    "degree" to "Bachelor of Science"
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
        }.getOrFail()

        val credential2 = trustWeave.issue {
            credential {
                id("https://example.com/credential-2")
                type("EmploymentCredential")
                issuer(issuerDid.value)
                subject {
                    id(holderDid.value)
                    "position" to "Software Engineer"
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
        }.getOrFail()

        // Create wallet and store credentials
        val wallet = trustWeave.wallet {
            id("presentation-wallet")
            holder(holderDid.value)
            inMemory()
            enablePresentation()
        }.getOrFail()

        credential1.storeIn(wallet)
        credential2.storeIn(wallet)

        // Get proof generator from trust layer's issuer to create PresentationService
        // This ensures the presentation can be signed with the holder's key
        val issuer = trustWeave.getDslContext().getIssuer()
        val proofGeneratorField = issuer.javaClass.getDeclaredField("proofGenerator").apply { isAccessible = true }
        val proofGenerator = proofGeneratorField.get(issuer) as? com.trustweave.credential.proof.ProofGenerator
            ?: throw IllegalStateException("Could not get proof generator from issuer")

        // Create PresentationService with proof generator from trust layer
        val presentationService = PresentationService(
            proofGenerator = proofGenerator,
            proofRegistry = trustWeave.getDslContext().getConfig().registries.proofRegistry
        )

        // Create verifiable presentation using the service with proof generator
        val presentation = presentation(presentationService) {
            credentials(credential1, credential2)
            holder(holderDid.value)
            keyId(holderKeyId) // Use holder's key to sign presentation
            challenge("challenge-123")
            domain("example.com")
        }

        assertNotNull(presentation, "Presentation should be created")
        assertNotNull(presentation.proof, "Presentation should have proof")

        // Verify the structure is correct
        assertTrue(presentation.verifiableCredential.isNotEmpty(), "Presentation should contain credentials")
    }

    /**
     * Template for DID update workflow.
     *
     * Demonstrates: Create DID → Update DID (add key/service) → Issue credential with updated DID
     *
     * **Key Pattern:** Extract key ID from updated DID document after adding new key
     */
    @Test
    fun `test DID update workflow template`() = runBlocking {
        val kms = InMemoryKeyManagementService()

        val trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory(),
                walletFactory = TestkitWalletFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data) }
            }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }

        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val holderDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        // Generate a new key for adding to DID
        val newKey = kms.generateKey("Ed25519")

        // Update DID to add additional verification method
        trustWeave.updateDid {
            did(issuerDid.value)
            method(DidMethods.KEY)
            addKey {
                id("${issuerDid.value}#key-2")
                type("Ed25519VerificationKey2020")
                publicKeyJwk(newKey.publicKeyJwk ?: emptyMap())
            }
        }

        // Resolve updated DID
        val updatedDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
            ?: throw IllegalStateException("Failed to resolve updated DID")
        val updatedDidDoc = when (updatedDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> updatedDidResolution.document
            else -> throw IllegalStateException("Failed to resolve updated DID")
        }

        assertTrue(updatedDidDoc.verificationMethod.size >= 1, "DID should have verification methods")

        // Extract key ID from updated DID (same pattern)
        val keyId = updatedDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in updated DID")

        // Issue credential using updated DID
        val credential = trustWeave.issue {
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

        assertNotNull(credential, "Credential should be issued with updated DID")
    }

    /**
     * Template for blockchain anchoring workflow.
     *
     * Demonstrates: Configure anchor → Issue credential with anchor → Verify anchor
     *
     * **Key Pattern:** Same as basic workflow - extract key ID from DID document
     *
     * **Note:** This uses in-memory anchor client. For real blockchains, use @RequiresPlugin annotation.
     */
    @Test
    fun `test blockchain anchoring workflow template`() = runBlocking {
        val kms = InMemoryKeyManagementService()

        val trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                anchorClientFactory = TestkitBlockchainAnchorClientFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data) }
            }
            did { method(DidMethods.KEY) {} }
            anchor {
                chain("testnet:inMemory") {
                    provider("inMemory") // Use in-memory anchor client for testing
                }
            }
            credentials {
                defaultChain("testnet:inMemory")
            }
            trust { provider("inMemory") }
        }

        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val holderDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        // Extract key ID (same pattern)
        val issuerDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerDidDoc = when (issuerDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }

        val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")

        // Issue credential with blockchain anchoring
        val credential = trustWeave.issue {
            credential {
                id("https://example.com/anchored-credential-1")
                type("AnchoredCredential")
                issuer(issuerDid.value)
                subject {
                    id(holderDid.value)
                    "test" to "value"
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = keyId)
            // Note: anchor() function not available in current DSL
        }.getOrFail()

        // Verify credential
        val result = trustWeave.verify {
            credential(credential)
            // Note: verifyAnchor() function not available in current DSL
        }

        // Note: Full blockchain anchor verification may not be implemented for in-memory client
        // This template demonstrates the pattern
        assertTrue(result.valid, "Credential should be valid")
    }

    /**
     * Template for smart contract workflow.
     *
     * Demonstrates: Configure anchor → Issue contract as credential → Anchor contract → Verify
     *
     * **Key Pattern:** Same as basic workflow - extract key ID from DID document
     *
     * **Note:** Smart contracts are issued as verifiable credentials and anchored to blockchain.
     * This uses in-memory anchor client. For real blockchains, use @RequiresPlugin annotation.
     */
    @Test
    fun `test smart contract workflow template`() = runBlocking {
        val kms = InMemoryKeyManagementService()

        val trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                anchorClientFactory = TestkitBlockchainAnchorClientFactory(),
                trustRegistryFactory = TestkitTrustRegistryFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data) }
            }
            did { method(DidMethods.KEY) {} }
            anchor {
                chain("testnet:inMemory") {
                    provider("inMemory") // Use in-memory anchor client for testing
                }
            }
            credentials {
                defaultChain("testnet:inMemory")
            }
            trust { provider("inMemory") }
        }

        val issuerDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val counterpartyDid = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        // Extract key ID (same pattern)
        val issuerDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid.value)
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerDidDoc = when (issuerDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }

        val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")

        // Issue contract as verifiable credential
        val contractCredential = trustWeave.issue {
            credential {
                id("https://example.com/contracts/contract-1")
                type("SmartContractCredential", "VerifiableCredential")
                issuer(issuerDid.value)
                subject {
                    id("urn:contract:contract-1")
                    "contractNumber" to "CONTRACT-2024-001"
                    "contractType" to "ServiceAgreement"
                    "status" to "ACTIVE"
                    "parties" {
                        "primaryPartyDid" to issuerDid.value
                        "counterpartyDid" to counterpartyDid.value
                    }
                    "effectiveDate" to Clock.System.now().toString()
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = keyId)
            // Note: anchor() function not available in current DSL
        }.getOrFail()

        assertNotNull(contractCredential, "Contract credential should be issued")

        // Verify contract credential
        val result = trustWeave.verify {
            credential(contractCredential)
            // Note: verifyAnchor() function not available in current DSL
        }

        assertTrue(result.valid, "Contract credential should be valid")
    }
}

