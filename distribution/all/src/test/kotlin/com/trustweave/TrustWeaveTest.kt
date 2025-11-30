package com.trustweave

import com.trustweave.credential.proof.ProofType
import com.trustweave.services.IssuanceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for TrustWeave facade - the main entry point.
 */
class TrustWeaveTest {

    @Test
    fun `test TrustWeave create`() {
        val trustweave = TrustWeave.create()

        assertNotNull(trustweave)
    }

    @Test
    fun `test createDid with default options`() = runBlocking {
        val trustweave = TrustWeave.create()
        val didDoc = trustweave.createDid()

        assertNotNull(didDoc)
        assertNotNull(didDoc.id)
        assertTrue(didDoc.id.startsWith("did:"))
    }

    @Test
    fun `test resolveDid`() = runBlocking {
        val trustweave = TrustWeave.create()
        val didDoc = trustweave.createDid()

        val result: com.trustweave.did.resolver.DidResolutionResult = trustweave.resolveDid(didDoc.id)

        assertNotNull(result)
        assertTrue(result is com.trustweave.did.resolver.DidResolutionResult.Success)
        val successResult = result as com.trustweave.did.resolver.DidResolutionResult.Success
        assertEquals(didDoc.id, successResult.document.id)
    }

    @Test
    fun `test issueCredential`() = runBlocking {
        val trustweave = TrustWeave.create()
        val did = trustweave.createDid()
        val issuerKeyId = did.verificationMethod.first().id

        val credential = trustweave.issueCredential(
            issuer = did.id,
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "Test Subject")
            },
            config = IssuanceConfig(
                proofType = ProofType.Ed25519Signature2020,
                keyId = issuerKeyId,
                issuerDid = did.id
            ),
            types = listOf("TestCredential")
        )

        assertNotNull(credential)
        assertEquals(did.id, credential.issuer)
        assertTrue(credential.type.contains("TestCredential"))
    }

    @Test
    fun `test verifyCredential`() = runBlocking {
        val trustweave = TrustWeave.create()
        val did = trustweave.createDid()
        val issuerKeyId = did.verificationMethod.first().id

        println("[DEBUG] Created DID: ${did.id}")
        println("[DEBUG] Verification Method ID: $issuerKeyId")

        val credential = trustweave.issueCredential(
            issuer = did.id,
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "Test Subject")
            },
            config = IssuanceConfig(
                proofType = ProofType.Ed25519Signature2020,
                keyId = issuerKeyId,
                issuerDid = did.id
            ),
            types = listOf("TestCredential")
        )

        println("[DEBUG] Issued credential with proof: ${credential.proof}")
        println("[DEBUG] Proof verificationMethod: ${credential.proof?.verificationMethod}")

        val result = trustweave.verifyCredential(credential)

        println("[DEBUG] Verification result:")
        println("[DEBUG]   valid: ${result.valid}")
        println("[DEBUG]   proofValid: ${result.proofValid}")
        println("[DEBUG]   issuerValid: ${result.issuerValid}")
        println("[DEBUG]   notExpired: ${result.notExpired}")
        println("[DEBUG]   notRevoked: ${result.notRevoked}")
        println("[DEBUG]   schemaValid: ${result.schemaValid}")
        println("[DEBUG]   blockchainAnchorValid: ${result.blockchainAnchorValid}")
        // Note: trustRegistryValid, delegationValid, and proofPurposeValid are not available on CredentialVerificationResult
        // They are only available on VerificationResult (from trust module)
        println("[DEBUG]   errors: ${result.allErrors}")
        println("[DEBUG]   warnings: ${result.allWarnings}")

        assertNotNull(result)
        assertTrue(result.valid)
    }

    @Test
    fun `test createWallet`() = runBlocking {
        val trustweave = TrustWeave.create()
        val did = trustweave.createDid()

        val wallet = trustweave.createWallet(holderDid = did.id)

        assertNotNull(wallet)
        assertNotNull(wallet.walletId)
    }

    @Test
    fun `test createWallet with custom ID`() = runBlocking {
        val trustweave = TrustWeave.create()
        val did = trustweave.createDid()

        val wallet = trustweave.createWallet(
            holderDid = did.id,
            walletId = "my-custom-wallet"
        )

        assertNotNull(wallet)
        assertEquals("my-custom-wallet", wallet.walletId)
    }

    @Test
    fun `test end-to-end workflow`() = runBlocking {
        // Create TrustWeave instance
        val trustweave = TrustWeave.create()

        // Create DIDs for issuer and holder
        val issuerDid = trustweave.createDid()
        val issuerKeyId = issuerDid.verificationMethod.first().id
        val holderDid = trustweave.createDid()

        // Issue a credential
        val credential = trustweave.issueCredential(
            issuer = issuerDid.id,
            subject = buildJsonObject {
                put("id", holderDid.id)
                put("name", "Alice")
                put("degree", "Bachelor of Science")
            },
            config = IssuanceConfig(
                proofType = ProofType.Ed25519Signature2020,
                keyId = issuerKeyId,
                issuerDid = issuerDid.id
            ),
            types = listOf("UniversityDegreeCredential")
        )

        // Verify the credential
        val verificationResult = trustweave.verifyCredential(credential)
        assertTrue(verificationResult.valid)

        // Create wallet and store credential
        val wallet = trustweave.createWallet(holderDid = holderDid.id)
        val credentialId = requireNotNull(credential.id) { "Issued credential should contain an id" }
        wallet.store(credential)

        // Retrieve credential from wallet
        val stored = wallet.get(credentialId)
        assertNotNull(stored)
        assertEquals(credentialId, stored?.id)
    }
}

