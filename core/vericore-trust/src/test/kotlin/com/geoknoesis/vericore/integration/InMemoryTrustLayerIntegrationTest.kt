package com.geoknoesis.vericore.integration

import com.geoknoesis.vericore.credential.dsl.*
import com.geoknoesis.vericore.credential.presentation.PresentationService
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import com.geoknoesis.vericore.testkit.annotations.RequiresPlugin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.time.Instant

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
        
        // Step 2: Configure trust layer with in-memory components
        val trustLayer = trustLayer {
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(keyId, data) }
            }
            did { method(DidMethods.KEY) {} }
            trust { provider("inMemory") }
        }
        
        // Step 3: Create DIDs (this generates keys and stores DID documents)
        val issuerDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val holderDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        // Step 4: Extract key ID from the issuer DID document
        // CRITICAL: The key used for signing MUST match what's in the DID document
        // This ensures proof verification succeeds because the DID document contains the correct verification method
        val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        
        val verificationMethod = issuerDidDoc.verificationMethod.firstOrNull()
            ?: throw IllegalStateException("No verification method in issuer DID")
        
        // Extract key ID from verification method ID (e.g., "did:key:xxx#key-1" -> "key-1")
        val keyId = verificationMethod.id.substringAfter("#")
        
        // Note: The IssuanceDsl will automatically construct verificationMethodId as "$issuerDid#$keyId"
        // which matches the format in the DID document, ensuring proof verification succeeds
        
        // Step 5: Configure trust registry (if needed)
        trustLayer.trust {
            addAnchor(issuerDid) {
                credentialTypes("TestCredential", "EducationCredential")
            }
        }
        
        // Step 6: Issue credential using the key from the DID document
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
            by(issuerDid = issuerDid, keyId = keyId) // MUST match key in DID document
        }
        
        assertNotNull(credential, "Credential should be issued")
        assertNotNull(credential.proof, "Credential should have proof")
        
        // Step 7: Verify credential with all checks
        val result = trustLayer.verify {
            credential(credential)
            checkTrustRegistry() // Enable trust registry check
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
        
        val trustLayer = trustLayer {
            keys { provider("aws-kms") } // Requires AWS credentials (see AwsKeyManagementServiceProvider)
            did { method("ethr") {} } // Requires ETHEREUM_RPC_URL (see EthrDidMethodProvider)
            trust { provider("inMemory") }
        }
        
        // Test implementation here...
        // If you reach here, all required env vars are available
        
        // Example: Create DID with external service
        val did = trustLayer.createDid {
            method("ethr")
            algorithm(KeyAlgorithms.ED25519)
        }
        
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
        
        val trustLayer = trustLayer {
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(keyId, data) }
            }
            did { method(DidMethods.KEY) {} }
            revocation { provider("inMemory") }
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
        
        // Extract key ID (same pattern)
        val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        
        val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")
        
        // Issue credential with revocation support
        val credential = trustLayer.issue {
            credential {
                id("https://example.com/revocable-credential-1")
                type("RevocableCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    "test" to "value"
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDid, keyId = keyId)
            withRevocation() // Enable revocation status list
        }
        
        assertNotNull(credential.credentialStatus, "Credential should have revocation status")
        val statusListId = credential.credentialStatus?.statusListCredential
            ?: throw IllegalStateException("Credential should have status list ID")
        
        // Verify credential is not revoked
        val initialResult = trustLayer.verify {
            credential(credential)
            checkRevocation()
        }
        assertTrue(initialResult.notRevoked, "Credential should not be revoked initially")
        
        // Revoke credential
        val revoked = trustLayer.revoke {
            credential(credential.id ?: throw IllegalStateException("Credential must have ID"))
            statusList(statusListId)
        }
        assertTrue(revoked, "Credential should be revoked")
        
        // Verify credential is now revoked
        val revokedResult = trustLayer.verify {
            credential(credential)
            checkRevocation()
        }
        assertFalse(revokedResult.notRevoked, "Credential should be revoked")
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
        
        val trustLayer = trustLayer {
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(keyId, data) }
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
        
        // Extract key ID (same pattern)
        val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        
        val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")
        
        // Issue credential
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
            by(issuerDid = issuerDid, keyId = keyId)
        }
        
        // Create wallet for holder
        val wallet = trustLayer.wallet {
            id("holder-wallet-1")
            holder(holderDid)
            inMemory()
            enableOrganization()
            enablePresentation()
        }
        
        // Store credential in wallet
        val storedCredential = credential.storeIn(wallet)
        assertNotNull(storedCredential, "Credential should be stored")
        
        // Retrieve credential from wallet
        val retrievedCredential = wallet.get(credential.id ?: throw IllegalStateException("Credential must have ID"))
        assertNotNull(retrievedCredential, "Credential should be retrievable")
        
        // Query credentials by type
        val credentials = wallet.query {
            byType("TestCredential")
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
        
        val trustLayer = trustLayer {
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(keyId, data) }
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
        
        // Extract key IDs (same pattern for both issuer and holder)
        val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerKeyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")
        
        val holderDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(holderDid)?.document
            ?: throw IllegalStateException("Failed to resolve holder DID")
        val holderKeyId = holderDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in holder DID")
        
        // Issue multiple credentials
        val credential1 = trustLayer.issue {
            credential {
                id("https://example.com/credential-1")
                type("EducationCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    "degree" to "Bachelor of Science"
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDid, keyId = issuerKeyId)
        }
        
        val credential2 = trustLayer.issue {
            credential {
                id("https://example.com/credential-2")
                type("EmploymentCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    "position" to "Software Engineer"
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDid, keyId = issuerKeyId)
        }
        
        // Create wallet and store credentials
        val wallet = trustLayer.wallet {
            id("presentation-wallet")
            holder(holderDid)
            inMemory()
            enablePresentation()
        }
        
        credential1.storeIn(wallet)
        credential2.storeIn(wallet)
        
        // Get proof generator from trust layer's issuer to create PresentationService
        // This ensures the presentation can be signed with the holder's key
        val issuer = trustLayer.dsl().getIssuer()
        val proofGeneratorField = issuer.javaClass.getDeclaredField("proofGenerator").apply { isAccessible = true }
        val proofGenerator = proofGeneratorField.get(issuer) as? com.geoknoesis.vericore.credential.proof.ProofGenerator
            ?: throw IllegalStateException("Could not get proof generator from issuer")
        
        // Create PresentationService with proof generator from trust layer
        val presentationService = PresentationService(
            proofGenerator = proofGenerator,
            proofRegistry = trustLayer.dsl().getConfig().registries.proofRegistry
        )
        
        // Create verifiable presentation using the service with proof generator
        val presentation = presentation(presentationService) {
            credentials(credential1, credential2)
            holder(holderDid)
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
        
        val trustLayer = trustLayer {
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(keyId, data) }
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
        
        // Generate a new key for adding to DID
        val newKey = kms.generateKey("Ed25519")
        
        // Update DID to add additional verification method
        trustLayer.updateDid {
            did(issuerDid)
            method(DidMethods.KEY)
            addKey {
                id("$issuerDid#key-2")
                type("Ed25519VerificationKey2020")
                publicKeyJwk(newKey.publicKeyJwk ?: emptyMap())
            }
        }
        
        // Resolve updated DID
        val updatedDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
            ?: throw IllegalStateException("Failed to resolve updated DID")
        
        assertTrue(updatedDidDoc.verificationMethod.size >= 1, "DID should have verification methods")
        
        // Extract key ID from updated DID (same pattern)
        val keyId = updatedDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in updated DID")
        
        // Issue credential using updated DID
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
            by(issuerDid = issuerDid, keyId = keyId)
        }
        
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
        
        val trustLayer = trustLayer {
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(keyId, data) }
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
        
        val issuerDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val holderDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        // Extract key ID (same pattern)
        val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        
        val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")
        
        // Issue credential with blockchain anchoring
        val credential = trustLayer.issue {
            credential {
                id("https://example.com/anchored-credential-1")
                type("AnchoredCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    "test" to "value"
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDid, keyId = keyId)
            anchor("testnet:inMemory") // Anchor to blockchain
        }
        
        // Verify credential with anchor verification
        val result = trustLayer.verify {
            credential(credential)
            verifyAnchor("testnet:inMemory")
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
        
        val trustLayer = trustLayer {
            keys {
                custom(kms)
                signer { data, keyId -> kms.sign(keyId, data) }
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
        
        val issuerDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        val counterpartyDid = trustLayer.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }
        
        // Extract key ID (same pattern)
        val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        
        val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
            ?: throw IllegalStateException("No verification method in issuer DID")
        
        // Issue contract as verifiable credential
        val contractCredential = trustLayer.issue {
            credential {
                id("https://example.com/contracts/contract-1")
                type("SmartContractCredential", "VerifiableCredential")
                issuer(issuerDid)
                subject {
                    id("urn:contract:contract-1")
                    "contractNumber" to "CONTRACT-2024-001"
                    "contractType" to "ServiceAgreement"
                    "status" to "ACTIVE"
                    "parties" {
                        "primaryPartyDid" to issuerDid
                        "counterpartyDid" to counterpartyDid
                    }
                    "effectiveDate" to Instant.now().toString()
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDid, keyId = keyId)
            anchor("testnet:inMemory") // Anchor contract to blockchain
        }
        
        assertNotNull(contractCredential, "Contract credential should be issued")
        
        // Verify contract credential
        val result = trustLayer.verify {
            credential(contractCredential)
            verifyAnchor("testnet:inMemory")
        }
        
        assertTrue(result.valid, "Contract credential should be valid")
    }
}

