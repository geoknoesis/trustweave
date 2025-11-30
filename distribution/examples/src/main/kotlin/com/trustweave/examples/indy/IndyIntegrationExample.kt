package com.trustweave.examples.indy

import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.credential.proof.ProofType
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.did.exception.DidException
import com.trustweave.credential.exception.CredentialException
import com.trustweave.wallet.exception.WalletException
import com.trustweave.anchor.exceptions.BlockchainException
import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.anchor.indy.IndyBlockchainAnchorClient
import com.trustweave.anchor.indy.IndyIntegration
import com.trustweave.services.IssuanceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

/**
 * Complete Indy Integration Example.
 *
 * This example demonstrates the full workflow using Hyperledger Indy for blockchain anchoring:
 * 1. Setup TrustWeave with Indy integration
 * 2. Create DIDs for issuer and holder
 * 3. Issue a verifiable credential (University Degree)
 * 4. Verify the credential
 * 5. Create wallet and store credential
 * 6. Anchor credential to Indy blockchain (BCovrin Testnet)
 * 7. Read back anchored data
 * 8. Verify the read credential
 *
 * Note: Uses in-memory fallback mode (no wallet credentials required) for testing.
 * In production, provide wallet credentials and pool endpoint configuration.
 *
 * Run: `./gradlew :TrustWeave-examples:runIndyIntegration`
 */
fun main() = runBlocking {
    println("=".repeat(70))
    println("Indy Integration - Complete End-to-End Scenario")
    println("=".repeat(70))
    println()

    // Step 1: Setup TrustWeave with Indy integration
    println("Step 1: Setting up TrustWeave with Indy integration...")
    val chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET

    // Create TrustWeave instance with Indy blockchain client
    // Using in-memory fallback mode (empty options) for testing
    // In production, provide: walletName, walletKey, did, poolEndpoint
    val trustweave = TrustWeave.create {
        val indyClient = IndyBlockchainAnchorClient(
            chainId = chainId,
            options = emptyMap() // In-memory fallback mode for testing
        )
        blockchains {
            chainId to indyClient
        }
    }
    println("✓ TrustWeave instance created")
    println("✓ Indy blockchain client registered: $chainId")
    println()

    // Step 2: Create DIDs for issuer and holder
    println("Step 2: Creating DIDs...")
    val issuerDid = try {
        trustweave.createDid()
    } catch (error: DidException.DidMethodNotRegistered) {
        println("✗ DID method not registered: ${error.method}")
        println("  Available methods: ${error.availableMethods.joinToString(", ")}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Failed to create issuer DID: ${error.message}")
        return@runBlocking
    }
    println("✓ Issuer DID created: ${issuerDid.id}")

    val holderDid = try {
        trustweave.createDid()
    } catch (error: DidException.DidMethodNotRegistered) {
        println("✗ DID method not registered: ${error.method}")
        println("  Available methods: ${error.availableMethods.joinToString(", ")}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Failed to create holder DID: ${error.message}")
        return@runBlocking
    }
    println("✓ Holder DID created: ${holderDid.id}")

    val issuerKeyId = issuerDid.verificationMethod.first().id
    println("✓ Issuer Key ID: $issuerKeyId")

    // Resolve the DIDs to verify they're accessible
    println("\n  Resolving DIDs to verify accessibility...")
    try {
        val issuerResolution = trustweave.resolveDid(issuerDid.id)
        when (issuerResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> {
                println("  ✓ Issuer DID resolved successfully")
            }
            else -> {
                println("  ⚠ Issuer DID resolution returned no document (may be in-memory)")
            }
        }
    } catch (e: Throwable) {
        println("  ⚠ Issuer DID resolution failed: ${e.message}")
    }

    try {
        val holderResolution = trustweave.resolveDid(holderDid.id)
        when (holderResolution) {
            is com.trustweave.did.resolver.DidResolutionResult.Success -> {
                println("  ✓ Holder DID resolved successfully")
            }
            else -> {
                println("  ⚠ Holder DID resolution returned no document (may be in-memory)")
            }
        }
    } catch (e: Throwable) {
        println("  ⚠ Holder DID resolution failed: ${e.message}")
    }
    println()

    // Step 3: Issue a verifiable credential
    println("Step 3: Issuing verifiable credential...")
    val credential = try {
        trustweave.issueCredential(
            issuer = issuerDid.id,
            subject = buildJsonObject {
                put("id", holderDid.id)
                put("name", "Alice Smith")
                put("degree", "Bachelor of Science in Computer Science")
                put("university", "Example University")
                put("graduationDate", "2024-05-15")
                put("gpa", "3.8")
                put("honors", true)
            },
            config = IssuanceConfig(
                proofType = ProofType.Ed25519Signature2020,
                keyId = issuerKeyId,
                issuerDid = issuerDid.id
            ),
            types = listOf("UniversityDegreeCredential", "VerifiableCredential")
        )
    } catch (error: DidException.InvalidDidFormat) {
        println("✗ Invalid DID format: ${error.reason}")
        return@runBlocking
    } catch (error: DidException.DidMethodNotRegistered) {
        println("✗ DID method not registered: ${error.method}")
        println("  Available methods: ${error.availableMethods.joinToString(", ")}")
        return@runBlocking
    } catch (error: CredentialException.CredentialInvalid) {
        println("✗ Credential validation failed: ${error.reason}")
        println("  Field: ${error.field}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Failed to issue credential: ${error.message}")
        return@runBlocking
    }
    println("✓ Credential issued successfully")
    println("  - Credential ID: ${credential.id}")
    println("  - Issuer: ${credential.issuer}")
    println("  - Types: ${credential.type.joinToString(", ")}")
    println("  - Has proof: ${credential.proof != null}")
    println()

    // Step 4: Verify the credential
    println("Step 4: Verifying credential...")
    val verification = try {
        trustweave.verifyCredential(credential)
    } catch (error: CredentialException.CredentialInvalid) {
        println("✗ Credential validation failed: ${error.reason}")
        println("  Field: ${error.field}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Verification failed: ${error.message}")
        return@runBlocking
    }

    if (verification.valid) {
        println("✓ Credential verified successfully")
        println("  - Valid: ${verification.valid}")
        println("  - Proof valid: ${verification.proofValid}")
        println("  - Issuer valid: ${verification.issuerValid}")
        println("  - Not expired: ${verification.notExpired}")
        println("  - Not revoked: ${verification.notRevoked}")
        if (verification.allWarnings.isNotEmpty()) {
            println("  - Warnings: ${verification.allWarnings.joinToString(", ")}")
        }
    } else {
        println("✗ Credential verification failed")
        println("  - Errors: ${verification.allErrors.joinToString(", ")}")
        if (verification.allWarnings.isNotEmpty()) {
            println("  - Warnings: ${verification.allWarnings.joinToString(", ")}")
        }
        return@runBlocking
    }
    println()

    // Step 5: Create wallet and store credential
    println("Step 5: Creating wallet and storing credential...")
    val wallet = try {
        trustweave.createWallet(holderDid = holderDid.id)
    } catch (error: WalletException.WalletCreationFailed) {
        println("✗ Wallet creation failed: ${error.reason}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Failed to create wallet: ${error.message}")
        return@runBlocking
    }
    println("✓ Wallet created successfully")
    println("  - Wallet ID: ${wallet.walletId}")

    val credentialId = requireNotNull(credential.id) { "Credential should have an ID" }
    wallet.store(credential)
    println("✓ Credential stored in wallet")
    println("  - Credential ID: $credentialId")

    // Retrieve credential from wallet
    val storedCredential = wallet.get(credentialId)
    if (storedCredential != null) {
        println("✓ Credential retrieved from wallet")
        println("  - Retrieved ID: ${storedCredential.id}")
    } else {
        println("✗ Failed to retrieve credential from wallet")
        return@runBlocking
    }
    println()

    // Step 6: Anchor credential to Indy blockchain
    println("Step 6: Anchoring credential to Indy blockchain...")
    println("  - Chain ID: $chainId")
    println("  - Mode: In-memory fallback (for testing)")
    println("  - Note: In production, provide wallet credentials and pool endpoint")

    // Convert credential to JsonElement for anchoring
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val credentialJson = json.encodeToJsonElement(VerifiableCredential.serializer(), credential)

    val anchor = try {
        trustweave.blockchains.anchor(
            data = credentialJson,
            serializer = JsonElement.serializer(),
            chainId = chainId
        )
    } catch (error: BlockchainException.ChainNotRegistered) {
        println("✗ Chain not registered: ${error.chainId}")
        println("  Available chains: ${error.availableChains.joinToString(", ")}")
        return@runBlocking
    } catch (error: TrustWeaveException.ValidationFailed) {
        println("✗ Validation failed: ${error.reason}")
        println("  Field: ${error.field}")
        println("  Value: ${error.value}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Anchoring failed: ${error.message}")
        return@runBlocking
    }
    println("✓ Credential anchored successfully")
    println("  - Chain ID: ${anchor.ref.chainId}")
    println("  - Transaction Hash: ${anchor.ref.txHash}")
    println("  - Network: ${anchor.ref.extra["network"]}")
    println("  - Pool: ${anchor.ref.extra["pool"]}")
    println()

    // Step 7: Read back anchored data
    println("Step 7: Reading anchored data from Indy blockchain...")
    val readJson = try {
        trustweave.blockchains.read<JsonElement>(
            ref = anchor.ref,
            serializer = JsonElement.serializer()
        )
    } catch (error: BlockchainException.ChainNotRegistered) {
        println("✗ Chain not registered: ${error.chainId}")
        println("  Available chains: ${error.availableChains.joinToString(", ")}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Failed to read anchored data: ${error.message}")
        return@runBlocking
    }
    println("✓ Anchored data read successfully")

    // Deserialize the credential
    val readCredential = json.decodeFromJsonElement(VerifiableCredential.serializer(), readJson)
    println("  - Read Credential ID: ${readCredential.id}")
    println("  - Read Credential Issuer: ${readCredential.issuer}")
    println("  - Read Credential Types: ${readCredential.type.joinToString(", ")}")

    // Verify data integrity
    if (credential.id == readCredential.id && credential.issuer == readCredential.issuer) {
        println("✓ Data integrity verified: Credential matches anchored data")
    } else {
        println("✗ Data integrity check failed: Credential does not match")
        return@runBlocking
    }
    println()

    // Step 8: Verify the read credential
    println("Step 8: Verifying read credential...")
    val readVerification = try {
        trustweave.verifyCredential(readCredential)
    } catch (error: Throwable) {
        println("✗ Verification failed: ${error.message}")
        return@runBlocking
    }

    if (readVerification.valid) {
        println("✓ Read credential verified successfully")
        println("  - Valid: ${readVerification.valid}")
        println("  - Proof valid: ${readVerification.proofValid}")
        println("  - Issuer valid: ${readVerification.issuerValid}")
    } else {
        println("✗ Read credential verification failed")
        println("  - Errors: ${readVerification.allErrors.joinToString(", ")}")
    }
    println()

    // Step 9: Demonstrate multiple credentials
    println("Step 9: Demonstrating multiple credentials...")
    val additionalCredentials = mutableListOf<VerifiableCredential>()
    for (i in 1..2) {
        try {
            val additionalCredential = trustweave.issueCredential(
                issuer = issuerDid.id,
                subject = buildJsonObject {
                    put("id", holderDid.id)
                    put("certificateType", "Professional Certification $i")
                    put("organization", "Example Professional Body")
                    put("issueDate", "2024-0$i-01")
                },
                config = IssuanceConfig(
                    proofType = ProofType.Ed25519Signature2020,
                    keyId = issuerKeyId,
                    issuerDid = issuerDid.id
                ),
                types = listOf("ProfessionalCertification", "VerifiableCredential")
            )

            additionalCredentials.add(additionalCredential)
            wallet.store(additionalCredential)
            println("✓ Additional credential $i issued and stored")
            println("  - Credential ID: ${additionalCredential.id}")
        } catch (error: Throwable) {
            println("✗ Failed to issue additional credential $i: ${error.message}")
        }
    }
    println("  Total credentials in wallet: ${wallet.getStatistics().totalCredentials}")
    println()

    // Step 10: Demonstrate custom data type anchoring
    println("Step 10: Demonstrating custom data type anchoring...")
    @Serializable
    data class CredentialDigest(
        val vcId: String,
        val digest: String,
        val issuer: String,
        val timestamp: String,
        val chainId: String
    )

    val digest = CredentialDigest(
        vcId = requireNotNull(credential.id),
        digest = "uABC123...",
        issuer = credential.issuer,
        timestamp = java.time.Instant.now().toString(),
        chainId = chainId
    )

    val digestJson = json.encodeToJsonElement(CredentialDigest.serializer(), digest)
    val digestAnchor = try {
        trustweave.blockchains.anchor(
            data = digestJson,
            serializer = JsonElement.serializer(),
            chainId = chainId
        )
    } catch (error: BlockchainException.ChainNotRegistered) {
        println("✗ Chain not registered: ${error.chainId}")
        println("  Available chains: ${error.availableChains.joinToString(", ")}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Failed to anchor custom data: ${error.message}")
        return@runBlocking
    }

    println("✓ Custom data anchored successfully")
    println("  - Transaction Hash: ${digestAnchor.ref.txHash}")
    println("  - Chain ID: ${digestAnchor.ref.chainId}")
    println("  - Network: ${digestAnchor.ref.extra["network"]}")
    println("  - Pool: ${digestAnchor.ref.extra["pool"]}")

    // Read back custom data
    val readDigestJson = try {
        trustweave.blockchains.read<JsonElement>(
            ref = digestAnchor.ref,
            serializer = JsonElement.serializer()
        )
    } catch (error: BlockchainException.ChainNotRegistered) {
        println("✗ Chain not registered: ${error.chainId}")
        return@runBlocking
    } catch (error: Throwable) {
        println("✗ Failed to read custom data: ${error.message}")
        return@runBlocking
    }

    val readDigest = json.decodeFromJsonElement(CredentialDigest.serializer(), readDigestJson)
    println("✓ Custom data read successfully")
    println("  - VC ID: ${readDigest.vcId}")
    println("  - Digest: ${readDigest.digest}")
    println("  - Issuer: ${readDigest.issuer}")
    println("  - Timestamp: ${readDigest.timestamp}")
    if (digest.vcId == readDigest.vcId &&
        digest.digest == readDigest.digest &&
        digest.issuer == readDigest.issuer) {
        println("✓ Data integrity verified: All fields match")
    } else {
        println("✗ Data integrity check failed: Fields do not match")
    }
    println()

    // Step 11: Demonstrate SPI discovery
    println("Step 11: Demonstrating SPI discovery...")
    val integrationResult = IndyIntegration.discoverAndRegister()
    println("✓ Indy integration discovered via SPI")
    println("  - Provider name: indy")
    println("  - Registered chains: ${integrationResult.registeredChains.size}")
    integrationResult.registeredChains.forEach { registeredChainId ->
        println("    - $registeredChainId")
        if (!registeredChainId.startsWith("indy:")) {
            println("      ⚠ Warning: Chain ID does not start with 'indy:'")
        }
    }
    println()

    // Step 12: Demonstrate error handling scenarios
    println("Step 12: Demonstrating error handling...")

    // Test invalid chain ID
    println("  Testing invalid chain ID...")
    try {
        trustweave.blockchains.anchor(
            data = buildJsonObject { put("test", "data") },
            serializer = JsonElement.serializer(),
            chainId = "invalid:chain:id"
        )
        println("  ⚠ Unexpected success with invalid chain ID")
    } catch (error: BlockchainException.ChainNotRegistered) {
        println("  ✓ Correctly rejected invalid chain ID: ${error.chainId}")
        println("    Available chains: ${error.availableChains.joinToString(", ")}")
    } catch (error: TrustWeaveException.ValidationFailed) {
        println("  ✓ Correctly rejected invalid chain ID format: ${error.reason}")
    } catch (error: Throwable) {
        println("  ✓ Error handling works: ${error.message}")
    }

    // Test DID resolution error
    println("  Testing DID resolution with unregistered method...")
    try {
        trustweave.resolveDid("did:unknown:test")
        println("  ⚠ Unexpected success with unregistered DID method")
    } catch (error: DidException.DidMethodNotRegistered) {
        println("  ✓ Correctly rejected unregistered DID method: ${error.method}")
        println("    Available methods: ${error.availableMethods.joinToString(", ")}")
    } catch (error: DidException.InvalidDidFormat) {
        println("  ✓ Correctly rejected invalid DID format: ${error.reason}")
    } catch (error: Throwable) {
        println("  ✓ Error handling works: ${error.message}")
    }
    println()

    // Summary
    println("=".repeat(70))
    println("Scenario Summary")
    println("=".repeat(70))
    println("✓ TrustWeave instance created with Indy integration")
    println("✓ Issuer DID: ${issuerDid.id}")
    println("✓ Holder DID: ${holderDid.id}")
    println("✓ Issuer Key ID: $issuerKeyId")
    println("✓ Credential issued: ${credential.id}")
    println("  - Types: ${credential.type.joinToString(", ")}")
    println("  - Has proof: ${credential.proof != null}")
    println("✓ Credential verified: ${verification.valid}")
    println("  - Proof valid: ${verification.proofValid}")
    println("  - Issuer valid: ${verification.issuerValid}")
    println("✓ Wallet created: ${wallet.walletId}")
    println("  - Total credentials: ${wallet.getStatistics().totalCredentials}")
    println("✓ Credential anchored to Indy: ${anchor.ref.txHash}")
    println("  - Chain ID: ${anchor.ref.chainId}")
    println("  - Network: ${anchor.ref.extra["network"]}")
    println("  - Pool: ${anchor.ref.extra["pool"]}")
    println("✓ Data integrity verified: Credential matches anchored data")
    println("✓ Read credential verified: ${readVerification.valid}")
    println("✓ Additional credentials issued: ${additionalCredentials.size}")
    println("✓ Indy integration: ${integrationResult.registeredChains.size} chains registered")
    println()
    println("=".repeat(70))
    println("✅ Complete Indy Integration Scenario Successful!")
    println("=".repeat(70))
    println()
    println("Next Steps:")
    println("  - In production, configure wallet credentials and pool endpoint")
    println("  - Use real Indy pool (BCovrin Testnet, Sovrin Staging, or Sovrin Mainnet)")
    println("  - Implement proper error handling and retry logic")
    println("  - Add monitoring and logging for production deployments")
    println("=".repeat(70))
}

