package com.geoknoesis.vericore

import com.geoknoesis.vericore.core.types.ProofType
import com.geoknoesis.vericore.services.IssuanceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for VeriCore facade - the main entry point.
 */
class VeriCoreTest {

    @Test
    fun `test VeriCore create`() {
        val vericore = VeriCore.create()
        
        assertNotNull(vericore)
    }

    @Test
    fun `test createDid with default options`() = runBlocking {
        val vericore = VeriCore.create()
        val didDoc = vericore.dids.create()
        
        assertNotNull(didDoc)
        assertNotNull(didDoc.id)
        assertTrue(didDoc.id.startsWith("did:"))
    }

    @Test
    fun `test resolveDid`() = runBlocking {
        val vericore = VeriCore.create()
        val didDoc = vericore.dids.create()
        
        val result = vericore.dids.resolve(didDoc.id)
        
        assertNotNull(result)
        assertNotNull(result.document)
        assertEquals(didDoc.id, result.document?.id)
    }

    @Test
    fun `test issueCredential`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.dids.create()
        val issuerKeyId = did.verificationMethod.first().id
        
        val credential = vericore.credentials.issue(
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
        val vericore = VeriCore.create()
        val did = vericore.dids.create()
        val issuerKeyId = did.verificationMethod.first().id
        
        println("[DEBUG] Created DID: ${did.id}")
        println("[DEBUG] Verification Method ID: $issuerKeyId")
        
        val credential = vericore.credentials.issue(
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
        
        val result = vericore.credentials.verify(credential)
        
        println("[DEBUG] Verification result:")
        println("[DEBUG]   valid: ${result.valid}")
        println("[DEBUG]   proofValid: ${result.proofValid}")
        println("[DEBUG]   issuerValid: ${result.issuerValid}")
        println("[DEBUG]   notExpired: ${result.notExpired}")
        println("[DEBUG]   notRevoked: ${result.notRevoked}")
        println("[DEBUG]   schemaValid: ${result.schemaValid}")
        println("[DEBUG]   blockchainAnchorValid: ${result.blockchainAnchorValid}")
        println("[DEBUG]   trustRegistryValid: ${result.trustRegistryValid}")
        println("[DEBUG]   delegationValid: ${result.delegationValid}")
        println("[DEBUG]   proofPurposeValid: ${result.proofPurposeValid}")
        println("[DEBUG]   errors: ${result.errors}")
        println("[DEBUG]   warnings: ${result.warnings}")
        
        assertNotNull(result)
        assertTrue(result.valid)
    }

    @Test
    fun `test createWallet`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.dids.create()
        
        val wallet = vericore.wallets.create(holderDid = did.id)
        
        assertNotNull(wallet)
        assertNotNull(wallet.walletId)
    }

    @Test
    fun `test createWallet with custom ID`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.dids.create()
        
        val wallet = vericore.wallets.create(
            holderDid = did.id,
            walletId = "my-custom-wallet"
        )
        
        assertNotNull(wallet)
        assertEquals("my-custom-wallet", wallet.walletId)
    }

    @Test
    fun `test end-to-end workflow`() = runBlocking {
        // Create VeriCore instance
        val vericore = VeriCore.create()
        
        // Create DIDs for issuer and holder
        val issuerDid = vericore.dids.create()
        val issuerKeyId = issuerDid.verificationMethod.first().id
        val holderDid = vericore.dids.create()
        
        // Issue a credential
        val credential = vericore.credentials.issue(
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
        val verificationResult = vericore.credentials.verify(credential)
        assertTrue(verificationResult.valid)
        
        // Create wallet and store credential
        val wallet = vericore.wallets.create(holderDid = holderDid.id)
        val credentialId = requireNotNull(credential.id) { "Issued credential should contain an id" }
        wallet.store(credential)
        
        // Retrieve credential from wallet
        val stored = wallet.get(credentialId)
        assertNotNull(stored)
        assertEquals(credentialId, stored?.id)
    }
}

