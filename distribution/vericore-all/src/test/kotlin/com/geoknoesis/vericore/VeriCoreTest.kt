package com.geoknoesis.vericore

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
        val didDoc = vericore.createDid().getOrThrow()
        
        assertNotNull(didDoc)
        assertNotNull(didDoc.id)
        assertTrue(didDoc.id.startsWith("did:"))
    }

    @Test
    fun `test resolveDid`() = runBlocking {
        val vericore = VeriCore.create()
        val didDoc = vericore.createDid().getOrThrow()
        
        val result = vericore.resolveDid(didDoc.id).getOrThrow()
        
        assertNotNull(result)
        assertNotNull(result.document)
        assertEquals(didDoc.id, result.document?.id)
    }

    @Test
    fun `test issueCredential`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.createDid().getOrThrow()
        val issuerKeyId = did.verificationMethod.first().id
        
        val credential = vericore.issueCredential(
            issuerDid = did.id,
            issuerKeyId = issuerKeyId,
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "Test Subject")
            },
            types = listOf("TestCredential")
        ).getOrThrow()
        
        assertNotNull(credential)
        assertEquals(did.id, credential.issuer)
        assertTrue(credential.type.contains("TestCredential"))
    }

    @Test
    fun `test verifyCredential`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.createDid().getOrThrow()
        val issuerKeyId = did.verificationMethod.first().id
        
        val credential = vericore.issueCredential(
            issuerDid = did.id,
            issuerKeyId = issuerKeyId,
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "Test Subject")
            },
            types = listOf("TestCredential")
        ).getOrThrow()
        
        val result = vericore.verifyCredential(credential).getOrThrow()
        
        assertNotNull(result)
        assertTrue(result.valid)
    }

    @Test
    fun `test createWallet`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.createDid().getOrThrow()
        
        val wallet = vericore.createWallet(holderDid = did.id).getOrThrow()
        
        assertNotNull(wallet)
        assertNotNull(wallet.walletId)
    }

    @Test
    fun `test createWallet with custom ID`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.createDid().getOrThrow()
        
        val wallet = vericore.createWallet(
            holderDid = did.id,
            walletId = "my-custom-wallet"
        ).getOrThrow()
        
        assertNotNull(wallet)
        assertEquals("my-custom-wallet", wallet.walletId)
    }

    @Test
    fun `test end-to-end workflow`() = runBlocking {
        // Create VeriCore instance
        val vericore = VeriCore.create()
        
        // Create DIDs for issuer and holder
        val issuerDid = vericore.createDid().getOrThrow()
        val issuerKeyId = issuerDid.verificationMethod.first().id
        val holderDid = vericore.createDid().getOrThrow()
        
        // Issue a credential
        val credential = vericore.issueCredential(
            issuerDid = issuerDid.id,
            issuerKeyId = issuerKeyId,
            credentialSubject = buildJsonObject {
                put("id", holderDid.id)
                put("name", "Alice")
                put("degree", "Bachelor of Science")
            },
            types = listOf("UniversityDegreeCredential")
        ).getOrThrow()
        
        // Verify the credential
        val verificationResult = vericore.verifyCredential(credential).getOrThrow()
        assertTrue(verificationResult.valid)
        
        // Create wallet and store credential
        val wallet = vericore.createWallet(holderDid = holderDid.id).getOrThrow()
        val credentialId = requireNotNull(credential.id) { "Issued credential should contain an id" }
        wallet.store(credential)
        
        // Retrieve credential from wallet
        val stored = wallet.get(credentialId)
        assertNotNull(stored)
        assertEquals(credentialId, stored?.id)
    }
}

