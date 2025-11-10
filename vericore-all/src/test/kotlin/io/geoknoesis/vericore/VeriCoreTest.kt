package io.geoknoesis.vericore

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
        val didDoc = vericore.createDid()
        
        assertNotNull(didDoc)
        assertNotNull(didDoc.id)
        assertTrue(didDoc.id.startsWith("did:"))
    }

    @Test
    fun `test resolveDid`() = runBlocking {
        val vericore = VeriCore.create()
        val didDoc = vericore.createDid()
        
        val result = vericore.resolveDid(didDoc.id)
        
        assertNotNull(result)
        assertNotNull(result.document)
        assertEquals(didDoc.id, result.document?.id)
    }

    @Test
    fun `test issueCredential`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.createDid()
        val issuerKeyId = did.verificationMethod.first().id
        
        val credential = vericore.issueCredential(
            issuerDid = did.id,
            issuerKeyId = issuerKeyId,
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "Test Subject")
            },
            types = listOf("TestCredential")
        )
        
        assertNotNull(credential)
        assertEquals(did.id, credential.issuer)
        assertTrue(credential.type.contains("TestCredential"))
    }

    @Test
    fun `test verifyCredential`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.createDid()
        val issuerKeyId = did.verificationMethod.first().id
        
        val credential = vericore.issueCredential(
            issuerDid = did.id,
            issuerKeyId = issuerKeyId,
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "Test Subject")
            },
            types = listOf("TestCredential")
        )
        
        val result = vericore.verifyCredential(credential)
        
        assertNotNull(result)
        assertTrue(result.valid)
    }

    @Test
    fun `test createWallet`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.createDid()
        
        val wallet = vericore.createWallet(holderDid = did.id)
        
        assertNotNull(wallet)
        assertNotNull(wallet.walletId)
    }

    @Test
    fun `test createWallet with custom ID`() = runBlocking {
        val vericore = VeriCore.create()
        val did = vericore.createDid()
        
        val wallet = vericore.createWallet(
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
        val issuerDid = vericore.createDid()
        val issuerKeyId = issuerDid.verificationMethod.first().id
        val holderDid = vericore.createDid()
        
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
        )
        
        // Verify the credential
        val verificationResult = vericore.verifyCredential(credential)
        assertTrue(verificationResult.valid)
        
        // Create wallet and store credential
        val wallet = vericore.createWallet(holderDid = holderDid.id)
        val credentialId = requireNotNull(credential.id) { "Issued credential should contain an id" }
        wallet.store(credential)
        
        // Retrieve credential from wallet
        val stored = wallet.get(credentialId)
        assertNotNull(stored)
        assertEquals(credentialId, stored?.id)
    }
}

