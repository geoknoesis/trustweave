package com.geoknoesis.vericore.indy

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.anchor.AnchorRef
import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.wallet.Wallet
import com.geoknoesis.vericore.did.DidDocument
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import kotlin.test.*

/**
 * Comprehensive end-to-end test scenario for Indy integration.
 * 
 * This test demonstrates the complete workflow:
 * 1. Setup VeriCore with Indy integration
 * 2. Create DIDs for issuer and holder
 * 3. Issue a verifiable credential
 * 4. Verify the credential
 * 5. Store credential in wallet
 * 6. Anchor credential to Indy blockchain (using in-memory fallback)
 * 7. Read back anchored data
 * 
 * Note: Uses in-memory fallback mode (no wallet required) for testing.
 */
@DisplayName("Indy Integration End-to-End Scenario")
class IndyIntegrationScenarioTest {

    private lateinit var vericore: VeriCore
    private val chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET

    @BeforeEach
    fun setup() {
        // Create VeriCore instance with Indy integration
        // Register Indy blockchain client (uses in-memory fallback for testing)
        // Empty options means no wallet credentials, which enables in-memory fallback
        val indyClient = IndyBlockchainAnchorClient(
            chainId = chainId,
            options = emptyMap() // In-memory fallback mode
        )
        
        vericore = VeriCore.create {
            registerBlockchainClient(chainId, indyClient)
        }
    }

    @Test
    @DisplayName("Complete Indy Integration Scenario")
    fun `complete indy integration scenario`() = runBlocking {
        println("\n=== Indy Integration End-to-End Scenario ===\n")

        // Step 1: Create DIDs for issuer and holder
        println("Step 1: Creating DIDs...")
        val issuerResult = vericore.createDid()
        assertTrue(issuerResult.isSuccess, "Should create issuer DID successfully")
        val issuerDid = issuerResult.getOrThrow()
        println("  ✓ Issuer DID: ${issuerDid.id}")

        val holderResult = vericore.createDid()
        assertTrue(holderResult.isSuccess, "Should create holder DID successfully")
        val holderDid = holderResult.getOrThrow()
        println("  ✓ Holder DID: ${holderDid.id}")

        // Step 2: Get issuer key ID
        val issuerKeyId = issuerDid.verificationMethod.first().id
        println("  ✓ Issuer Key ID: $issuerKeyId")

        // Step 3: Issue a verifiable credential
        println("\nStep 2: Issuing credential...")
        val credentialResult = vericore.issueCredential(
            issuerDid = issuerDid.id,
            issuerKeyId = issuerKeyId,
            credentialSubject = buildJsonObject {
                put("id", holderDid.id)
                put("name", "Alice")
                put("degree", "Bachelor of Science")
                put("university", "Test University")
                put("graduationDate", "2024-05-15")
            },
            types = listOf("UniversityDegreeCredential", "VerifiableCredential")
        )
        
        assertTrue(credentialResult.isSuccess, "Should issue credential successfully")
        val credential = credentialResult.getOrThrow()
        println("  ✓ Credential ID: ${credential.id}")
        println("  ✓ Credential Issuer: ${credential.issuer}")
        println("  ✓ Credential Types: ${credential.type}")
        assertNotNull(credential.proof, "Credential should have proof")
        assertTrue(credential.type.contains("UniversityDegreeCredential"), "Should contain expected type")

        // Step 4: Verify the credential
        println("\nStep 3: Verifying credential...")
        val verificationResult = vericore.verifyCredential(credential)
        
        assertTrue(verificationResult.isSuccess, "Should verify credential successfully")
        val verification = verificationResult.getOrThrow()
        println("  ✓ Verification Valid: ${verification.valid}")
        println("  ✓ Proof Valid: ${verification.proofValid}")
        println("  ✓ Issuer Valid: ${verification.issuerValid}")
        assertTrue(verification.valid, "Credential should be valid")
        assertTrue(verification.proofValid, "Proof should be valid")
        assertTrue(verification.issuerValid, "Issuer should be valid")

        // Step 5: Create wallet and store credential
        println("\nStep 4: Creating wallet and storing credential...")
        val walletResult = vericore.createWallet(holderDid = holderDid.id)
        
        assertTrue(walletResult.isSuccess, "Should create wallet successfully")
        val wallet = walletResult.getOrThrow()
        println("  ✓ Wallet ID: ${wallet.walletId}")

        val credentialId = requireNotNull(credential.id) { "Credential should have an ID" }
        wallet.store(credential)
        println("  ✓ Credential stored in wallet")

        // Step 6: Retrieve credential from wallet
        val storedCredential = wallet.get(credentialId)
        assertNotNull(storedCredential, "Should retrieve credential from wallet")
        assertEquals(credentialId, storedCredential?.id, "Retrieved credential should match stored credential")

        // Step 7: Anchor credential to Indy blockchain
        println("\nStep 5: Anchoring credential to Indy blockchain...")
        // Convert credential to JsonElement for anchoring
        val json = Json { 
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
        val anchorResult = vericore.anchor(
            data = credentialJson,
            serializer = JsonElement.serializer(),
            chainId = chainId
        )
        
        if (anchorResult.isFailure) {
            val error = anchorResult.exceptionOrNull()
            println("  ✗ Anchoring failed: ${error?.message}")
            error?.printStackTrace()
            fail("Should anchor credential successfully: ${error?.message}")
        }
        
        assertTrue(anchorResult.isSuccess, "Should anchor credential successfully")
        val anchor = anchorResult.getOrThrow()
        println("  ✓ Chain ID: ${anchor.ref.chainId}")
        println("  ✓ Transaction Hash: ${anchor.ref.txHash}")
        println("  ✓ Network: ${anchor.ref.extra["network"]}")
        println("  ✓ Pool: ${anchor.ref.extra["pool"]}")
        assertEquals(chainId, anchor.ref.chainId, "Chain ID should match")
        assertNotNull(anchor.ref.txHash, "Transaction hash should not be null")
        assertEquals("testnet", anchor.ref.extra["network"], "Network should be testnet")
        assertEquals("bcovrin", anchor.ref.extra["pool"], "Pool should be bcovrin")

        // Step 8: Read back anchored data
        println("\nStep 6: Reading anchored data...")
        val readResult = vericore.readAnchor<JsonElement>(
            ref = anchor.ref,
            serializer = JsonElement.serializer()
        )
        
        assertTrue(readResult.isSuccess, "Should read anchored data successfully")
        val readJson = readResult.getOrThrow()
        val readCredential = Json.decodeFromJsonElement(VerifiableCredential.serializer(), readJson)
        println("  ✓ Read Credential ID: ${readCredential.id}")
        println("  ✓ Read Credential Issuer: ${readCredential.issuer}")
        assertEquals(credential.id, readCredential.id, "Read credential ID should match")
        assertEquals(credential.issuer, readCredential.issuer, "Read credential issuer should match")

        // Step 9: Verify the read credential
        println("\nStep 7: Verifying read credential...")
        val readVerificationResult = vericore.verifyCredential(readCredential)
        
        assertTrue(readVerificationResult.isSuccess, "Should verify read credential successfully")
        val readVerification = readVerificationResult.getOrThrow()
        println("  ✓ Read Verification Valid: ${readVerification.valid}")
        assertTrue(readVerification.valid, "Read credential should be valid")

        println("\n=== Scenario completed successfully! ===\n")
    }

    @Test
    @DisplayName("Indy Integration with Error Handling")
    fun `indy integration with error handling`() = runBlocking {
        println("\n=== Indy Integration Error Handling Test ===\n")

        // Test invalid chain ID
        println("Testing invalid chain ID...")
        val testData = buildJsonObject { put("test", "data") }
        val invalidChainResult = vericore.anchor(
            data = testData,
            serializer = JsonElement.serializer(),
            chainId = "invalid:chain:id"
        )
        
        assertTrue(invalidChainResult.isFailure, "Should fail with invalid chain ID")
        invalidChainResult.fold(
            onSuccess = { fail("Should not succeed with invalid chain ID") },
            onFailure = { error ->
                when (error) {
                    is VeriCoreError.ChainNotRegistered -> {
                        println("  ✓ Correctly identified invalid chain ID")
                        assertTrue(error.chainId == "invalid:chain:id")
                    }
                    else -> fail("Should return ChainNotRegistered error")
                }
            }
        )

        // Test DID resolution error handling
        println("\nTesting DID resolution error handling...")
        val resolveResult = vericore.resolveDid("did:unknown:test")
        
        resolveResult.fold(
            onSuccess = { 
                // May succeed if method is registered
                println("  ✓ DID resolved (method may be registered)")
            },
            onFailure = { error ->
                when (error) {
                    is VeriCoreError.DidMethodNotRegistered -> {
                        println("  ✓ Correctly identified unregistered DID method")
                    }
                    is VeriCoreError.InvalidDidFormat -> {
                        println("  ✓ Correctly identified invalid DID format")
                    }
                    else -> {
                        println("  ✓ Error: ${error.message}")
                    }
                }
            }
        )

        println("\n=== Error handling test completed ===\n")
    }

    @Test
    @DisplayName("Indy Integration with Custom Data Type")
    fun `indy integration with custom data type`() = runBlocking {
        println("\n=== Indy Integration with Custom Data Type ===\n")

        // Define custom data type
        @Serializable
        data class CredentialDigest(
            val vcId: String,
            val digest: String,
            val issuer: String,
            val timestamp: String
        )

        // Create custom data
        val digest = CredentialDigest(
            vcId = "vc-12345",
            digest = "uABC123...",
            issuer = "did:key:issuer",
            timestamp = "2024-01-01T00:00:00Z"
        )

        // Anchor custom data
        println("Anchoring custom data type...")
        val digestJson = Json.encodeToJsonElement(CredentialDigest.serializer(), digest)
        val anchorResult = vericore.anchor(
            data = digestJson,
            serializer = JsonElement.serializer(),
            chainId = chainId
        )
        
        assertTrue(anchorResult.isSuccess, "Should anchor custom data successfully")
        val anchor = anchorResult.getOrThrow()
        println("  ✓ Anchored at: ${anchor.ref.txHash}")

        // Read back custom data
        println("Reading back custom data...")
        val readResult = vericore.readAnchor<JsonElement>(
            ref = anchor.ref,
            serializer = JsonElement.serializer()
        )
        
        assertTrue(readResult.isSuccess, "Should read custom data successfully")
        val readJson = readResult.getOrThrow()
        val readDigest = Json.decodeFromJsonElement(CredentialDigest.serializer(), readJson)
        println("  ✓ Read VC ID: ${readDigest.vcId}")
        println("  ✓ Read Digest: ${readDigest.digest}")
        println("  ✓ Read Issuer: ${readDigest.issuer}")
        assertEquals(digest.vcId, readDigest.vcId, "VC ID should match")
        assertEquals(digest.digest, readDigest.digest, "Digest should match")
        assertEquals(digest.issuer, readDigest.issuer, "Issuer should match")

        println("\n=== Custom data type test completed ===\n")
    }

    @Test
    @DisplayName("Indy Integration with Multiple Credentials")
    fun `indy integration with multiple credentials`() = runBlocking {
        println("\n=== Indy Integration with Multiple Credentials ===\n")

        // Create issuer and holder
        val issuerDid = vericore.createDid().getOrThrow()
        val holderDid = vericore.createDid().getOrThrow()
        val issuerKeyId = issuerDid.verificationMethod.first().id

        // Issue multiple credentials
        val credentials = mutableListOf<VerifiableCredential>()
        
        for (i in 1..3) {
            val credential = vericore.issueCredential(
                issuerDid = issuerDid.id,
                issuerKeyId = issuerKeyId,
                credentialSubject = buildJsonObject {
                    put("id", holderDid.id)
                    put("credentialNumber", i)
                    put("type", "TestCredential$i")
                },
                types = listOf("TestCredential$i")
            ).getOrThrow()
            
            credentials.add(credential)
            println("  ✓ Issued credential $i: ${credential.id}")
        }

        // Verify all credentials
        println("\nVerifying all credentials...")
        credentials.forEachIndexed { index, credential ->
            val verification = vericore.verifyCredential(credential).getOrThrow()
            assertTrue(verification.valid, "Credential ${index + 1} should be valid")
            println("  ✓ Credential ${index + 1} verified")
        }

        // Anchor all credentials
        println("\nAnchoring all credentials...")
        val anchors = credentials.map { credential ->
            val credentialJson = Json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
            val anchor = vericore.anchor(
                data = credentialJson,
                serializer = JsonElement.serializer(),
                chainId = chainId
            ).getOrThrow()
            println("  ✓ Anchored credential: ${credential.id} at ${anchor.ref.txHash}")
            anchor
        }

        // Verify all anchors
        assertEquals(3, anchors.size, "Should have 3 anchors")
        anchors.forEach { anchor ->
            assertEquals(chainId, anchor.ref.chainId, "Chain ID should match")
            assertNotNull(anchor.ref.txHash, "Transaction hash should not be null")
        }

        println("\n=== Multiple credentials test completed ===\n")
    }

    @Test
    @DisplayName("Indy Integration SPI Discovery")
    fun `indy integration spi discovery`() = runBlocking {
        println("\n=== Indy Integration SPI Discovery Test ===\n")

        // Test SPI discovery
        val integrationResult = IndyIntegration.discoverAndRegister()
        
        assertNotNull(integrationResult, "Integration result should not be null")
        assertNotNull(integrationResult.registry, "Registry should not be null")
        assertTrue(integrationResult.registeredChains.isNotEmpty(), "Should register at least one chain")
        
        println("  ✓ Registered chains: ${integrationResult.registeredChains}")
        integrationResult.registeredChains.forEach { chainId ->
            println("    - $chainId")
            assertTrue(chainId.startsWith("indy:"), "Chain ID should start with 'indy:'")
        }

        // Test manual setup
        println("\nTesting manual setup...")
        val manualSetupResult = IndyIntegration.setup(
            chainIds = listOf(IndyBlockchainAnchorClient.BCOVRIN_TESTNET),
            options = emptyMap()
        )
        
        assertNotNull(manualSetupResult, "Manual setup result should not be null")
        assertTrue(manualSetupResult.registeredChains.contains(IndyBlockchainAnchorClient.BCOVRIN_TESTNET), 
            "Should register BCovrin testnet")
        println("  ✓ Manual setup successful")

        println("\n=== SPI discovery test completed ===\n")
    }
}

