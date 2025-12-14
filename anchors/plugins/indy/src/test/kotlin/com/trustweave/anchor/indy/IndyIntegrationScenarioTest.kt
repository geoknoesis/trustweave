package com.trustweave.anchor.indy

import com.trustweave.trust.TrustWeave
import com.trustweave.anchor.AnchorRef
import com.trustweave.anchor.exceptions.BlockchainException
import com.trustweave.core.*
import com.trustweave.core.identifiers.KeyId
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.did.model.DidDocument
import com.trustweave.trust.types.VerificationResult
import com.trustweave.wallet.Wallet
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.testkit.services.TestkitWalletFactory
import com.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Comprehensive end-to-end test scenario for Indy integration.
 *
 * This test demonstrates the complete workflow:
 * 1. Setup TrustWeave with Indy integration
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

    private lateinit var trustweave: TrustWeave
    private lateinit var kms: InMemoryKeyManagementService
    private val chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET

    @BeforeEach
    fun setup() = runBlocking {
        // Create TrustWeave instance with Indy integration
        // Register Indy blockchain client (uses in-memory fallback for testing)
        // Empty options means no wallet credentials, which enables in-memory fallback
        val indyClient = IndyBlockchainAnchorClient(
            chainId = chainId,
            options = emptyMap() // In-memory fallback mode
        )

        // Create KMS instance and capture reference for signer
        kms = InMemoryKeyManagementService()
        val kmsRef = kms

        // Create signer function
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kmsRef.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                is com.trustweave.kms.results.SignResult.Success -> result.signature
                else -> throw IllegalStateException("Signing failed: $result")
            }
        }
        
        // Create shared DID registry for consistent DID resolution
        val sharedDidRegistry = com.trustweave.did.registry.DidMethodRegistry()
        
        // Create DID resolver
        val didResolver = com.trustweave.did.resolver.DidResolver { did: com.trustweave.did.identifiers.Did ->
            sharedDidRegistry.resolve(did.value) as com.trustweave.did.resolver.DidResolutionResult
        }
        
        // Create CredentialService
        val credentialService = com.trustweave.credential.credentialService(
            didResolver = didResolver,
            signer = signer
        )

        trustweave = TrustWeave.build(
        registries = com.trustweave.trust.dsl.TrustWeaveRegistries(
            didRegistry = sharedDidRegistry,
            blockchainRegistry = com.trustweave.anchor.BlockchainAnchorRegistry()
        )
        ) {
            factories(
                didMethodFactory = TestkitDidMethodFactory(didRegistry = sharedDidRegistry),
                walletFactory = TestkitWalletFactory()
            )
            keys {
                custom(kmsRef)
                signer(signer)
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
            issuer(credentialService)
            // Note: Chain is registered manually below, not via DSL
        }.also {
            it.configuration.registries.blockchainRegistry.register(chainId, indyClient)
        }
    }

    @Test
    @DisplayName("Complete Indy Integration Scenario")
    fun `complete indy integration scenario`() = runBlocking {
        println("\n=== Indy Integration End-to-End Scenario ===\n")

        // Step 1: Create DIDs for issuer and holder
        println("Step 1: Creating DIDs...")
        val issuerDid = trustweave.createDid().getOrFail()
        println("  ✓ Issuer DID: ${issuerDid.value}")

        val holderDid = trustweave.createDid().getOrFail()
        println("  ✓ Holder DID: ${holderDid.value}")

        // Step 2: Get issuer key ID by resolving the DID
        val issuerDidDoc = when (val resolution = trustweave.resolveDid(issuerDid)) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> resolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }
        val issuerKeyId = issuerDidDoc.verificationMethod.first().id.value.substringAfter("#")
        println("  ✓ Issuer Key ID: $issuerKeyId")

        // Step 3: Issue a verifiable credential
        println("\nStep 2: Issuing credential...")
        val credentialId = "urn:indy:test:credential:${System.currentTimeMillis()}"
        val credential = trustweave.issue {
            credential {
                id(credentialId)
                type("UniversityDegreeCredential")
                issuer(issuerDid.value)
                subject {
                    id(holderDid.value)
                    "name" to "Alice"
                    "degree" to "Bachelor of Science"
                    "university" to "Test University"
                    "graduationDate" to "2024-05-15"
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
        }.getOrFail()
        println("  ✓ Credential ID: ${credential.id}")
        println("  ✓ Credential Issuer: ${credential.issuer}")
        println("  ✓ Credential Types: ${credential.type}")
        assertNotNull(credential.proof, "Credential should have proof")
        assertTrue(credential.type.any { it.value == "UniversityDegreeCredential" }, "Should contain expected type")

        // Step 4: Verify the credential
        println("\nStep 3: Verifying credential...")
        val verification = trustweave.verify { credential(credential) }
        val isValid = verification is com.trustweave.trust.types.VerificationResult.Valid
        println("  ✓ Verification Valid: $isValid")
        assertTrue(isValid, "Credential should be valid")

        // Step 5: Create wallet and store credential
        println("\nStep 4: Creating wallet and storing credential...")
        val wallet = trustweave.wallet {
            holder(holderDid.value)
        }.getOrFail()
        println("  ✓ Wallet ID: ${wallet.walletId}")

        wallet.store(credential)
        println("  ✓ Credential stored in wallet")

        // Step 6: Retrieve credential from wallet
        val storedCredentialId = requireNotNull(credential.id) { "Credential should have an ID" }
        val storedCredential = wallet.get(storedCredentialId.value)
        assertNotNull(storedCredential, "Should retrieve credential from wallet")
        assertEquals(storedCredentialId, storedCredential?.id, "Retrieved credential should match stored credential")

        // Step 7: Anchor credential to Indy blockchain
        println("\nStep 5: Anchoring credential to Indy blockchain...")
        // Convert credential to JsonElement for anchoring
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "@type" // Use @type instead of type to avoid conflict with LinkedDataProof.type
        }
        val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
        val anchor = trustweave.blockchains.anchor(
            data = credentialJson,
            serializer = JsonElement.serializer(),
            chainId = chainId
        )
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
        val readJson = trustweave.blockchains.read<JsonElement>(
            ref = anchor.ref,
            serializer = JsonElement.serializer()
        )
        val readCredential = json.decodeFromJsonElement(VerifiableCredential.serializer(), readJson)
        println("  ✓ Read Credential ID: ${readCredential.id}")
        println("  ✓ Read Credential Issuer: ${readCredential.issuer}")
        assertEquals(credential.id, readCredential.id, "Read credential ID should match")
        assertEquals(credential.issuer, readCredential.issuer, "Read credential issuer should match")

        // Step 9: Verify the read credential
        println("\nStep 7: Verifying read credential...")
        val readVerification = trustweave.verify { credential(readCredential) }
        val readIsValid = readVerification is com.trustweave.trust.types.VerificationResult.Valid
        println("  ✓ Read Verification Valid: $readIsValid")
        assertTrue(readIsValid, "Read credential should be valid")

        println("\n=== Scenario completed successfully! ===\n")
    }

    @Test
    @DisplayName("Indy Integration with Error Handling")
    fun `indy integration with error handling`() = runBlocking {
        println("\n=== Indy Integration Error Handling Test ===\n")

        // Test invalid chain ID
        println("Testing invalid chain ID...")
        val testData = buildJsonObject { put("test", "data") }
        try {
            trustweave.blockchains.anchor(
                data = testData,
                serializer = JsonElement.serializer(),
                chainId = "invalid:chain:id"
            )
            fail("Should fail with invalid chain ID")
        } catch (error: BlockchainException.ChainNotRegistered) {
            println("  ✓ Correctly identified invalid chain ID")
            assertTrue(error.chainId == "invalid:chain:id")
        } catch (e: Throwable) {
            fail("Should return ChainNotRegistered error, got: ${e::class.simpleName}")
        }

        // Test DID resolution error handling
        println("\nTesting DID resolution error handling...")
        val resolutionResult = trustweave.resolveDid("did:unknown:test")
        when (resolutionResult) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> {
                // May succeed if method is registered
                println("  ✓ DID resolved (method may be registered)")
            }
            is com.trustweave.did.resolver.DidResolutionResult.Failure.MethodNotRegistered -> {
                println("  ✓ Correctly identified unregistered DID method")
            }
            is com.trustweave.did.resolver.DidResolutionResult.Failure.InvalidFormat -> {
                println("  ✓ Correctly identified invalid DID format")
            }
            else -> {
                println("  ✓ Error: ${resolutionResult}")
            }
        }

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
        val anchor = trustweave.blockchains.anchor(
            data = digestJson,
            serializer = JsonElement.serializer(),
            chainId = chainId
        )
        println("  ✓ Anchored at: ${anchor.ref.txHash}")

        // Read back custom data
        println("Reading back custom data...")
        val readJson = trustweave.blockchains.read<JsonElement>(
            ref = anchor.ref,
            serializer = JsonElement.serializer()
        )
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
        val issuerDid = trustweave.createDid().getOrFail()
        val holderDid = trustweave.createDid().getOrFail()
        
        // Get issuer key ID by resolving the DID
        val issuerDidResolution = trustweave.resolveDid(issuerDid)
        val issuerDidDoc = when (issuerDidResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }
        val issuerKeyId = issuerDidDoc.verificationMethod.first().id.value.substringAfter("#")

        // Issue multiple credentials
        val credentials = mutableListOf<VerifiableCredential>()

        for (i in 1..3) {
            val credentialId = "urn:indy:test:credential:$i:${System.currentTimeMillis()}"
            val credential = trustweave.issue {
                credential {
                    id(credentialId)
                    type("TestCredential$i")
                    issuer(issuerDid.value)
                    subject {
                        id(holderDid.value)
                        "credentialNumber" to i.toString()
                        "type" to "TestCredential$i"
                    }
                    issued(Clock.System.now())
                }
                signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
            }.getOrFail()

            credentials.add(credential)
            println("  ✓ Issued credential $i: ${credential.id}")
        }

        // Verify all credentials
        println("\nVerifying all credentials...")
        credentials.forEachIndexed { index, credential ->
            val verification = trustweave.verify { credential(credential) }
            val isValid = verification is com.trustweave.trust.types.VerificationResult.Valid
            assertTrue(isValid, "Credential ${index + 1} should be valid")
            println("  ✓ Credential ${index + 1} verified")
        }

        // Anchor all credentials
        println("\nAnchoring all credentials...")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "@type" // Use @type instead of type to avoid conflict with LinkedDataProof.type
        }
        val anchors = credentials.map { credential ->
            val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
            val anchor = trustweave.blockchains.anchor(
                data = credentialJson,
                serializer = JsonElement.serializer(),
                chainId = chainId
            )
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
        val integrationResult = try {
            IndyIntegration.discoverAndRegister()
        } catch (e: java.util.ServiceConfigurationError) {
            // SPI discovery can fail if provider class has missing dependencies
            // This is acceptable in test environments where Indy dependencies may not be available
            println("SPI discovery failed (may be due to missing runtime dependencies): ${e.message}")
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "SPI discovery requires full runtime dependencies")
            return@runBlocking
        }

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

