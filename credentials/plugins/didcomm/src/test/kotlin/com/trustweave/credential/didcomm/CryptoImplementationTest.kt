package com.trustweave.credential.didcomm

import com.trustweave.credential.didcomm.crypto.DidCommCrypto
import com.trustweave.credential.didcomm.crypto.DidCommCryptoAdapter
import com.trustweave.did.DidDocument
import com.trustweave.did.VerificationMethod
import com.trustweave.testkit.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests to demonstrate the difference between placeholder and production crypto.
 */
class CryptoImplementationTest {
    
    @Test
    fun testPlaceholderCryptoReturnsDummyData() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            DidDocument(
                id = did,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = "$did#key-1",
                        type = "Ed25519VerificationKey2020",
                        controller = did,
                        publicKeyJwk = mapOf(
                            "kty" to "OKP",
                            "crv" to "Ed25519",
                            "x" to "test-key"
                        )
                    )
                ),
                keyAgreement = listOf("$did#key-1")
            )
        }
        
        val crypto = DidCommCrypto(kms, resolveDid)
        
        val message = buildJsonObject {
            put("id", "test-123")
            put("type", "test")
            put("body", buildJsonObject {
                put("content", "Hello")
            })
        }
        
        // This will "encrypt" but with dummy data
        val envelope = crypto.encrypt(
            message = message,
            fromDid = "did:key:alice",
            fromKeyId = "did:key:alice#key-1",
            toDid = "did:key:bob",
            toKeyId = "did:key:bob#key-1"
        )
        
        // Envelope structure is correct
        assertNotNull(envelope.protected)
        assertNotNull(envelope.recipients)
        assertNotNull(envelope.ciphertext)
        assertNotNull(envelope.iv)
        assertNotNull(envelope.tag)
        
        // But the encryption is not real - it's placeholder data
        // In a real implementation, decrypting this would fail or return garbage
    }
    
    @Test
    fun testAdapterWithPlaceholderCrypto() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            DidDocument(
                id = did,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = "$did#key-1",
                        type = "Ed25519VerificationKey2020",
                        controller = did,
                        publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519", "x" to "test")
                    )
                ),
                keyAgreement = listOf("$did#key-1")
            )
        }
        
        // Use adapter with placeholder crypto
        val adapter = DidCommCryptoAdapter(kms, resolveDid, useProduction = false)
        
        assertTrue(!adapter.useProduction, "Should use placeholder crypto")
        
        val message = buildJsonObject {
            put("id", "test-123")
            put("type", "test")
        }
        
        val envelope = adapter.encrypt(
            message = message,
            fromDid = "did:key:alice",
            fromKeyId = "did:key:alice#key-1",
            toDid = "did:key:bob",
            toKeyId = "did:key:bob#key-1"
        )
        
        assertNotNull(envelope)
    }
    
    @Test
    fun testAdapterWithProductionCryptoFailsGracefully() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            DidDocument(
                id = did,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = "$did#key-1",
                        type = "Ed25519VerificationKey2020",
                        controller = did,
                        publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519", "x" to "test")
                    )
                ),
                keyAgreement = listOf("$did#key-1")
            )
        }
        
        // Try to use production crypto (will fail and fall back to placeholder)
        val adapter = DidCommCryptoAdapter(kms, resolveDid, useProduction = true)
        
        assertTrue(adapter.useProduction, "Should attempt production crypto")
        
        val message = buildJsonObject {
            put("id", "test-123")
            put("type", "test")
        }
        
        // This will fall back to placeholder since didcomm-java is not available
        val envelope = adapter.encrypt(
            message = message,
            fromDid = "did:key:alice",
            fromKeyId = "did:key:alice#key-1",
            toDid = "did:key:bob",
            toKeyId = "did:key:bob#key-1"
        )
        
        // Should still work (using placeholder fallback)
        assertNotNull(envelope)
    }
}

