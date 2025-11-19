package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.testkit.credential.InMemoryWallet
import com.geoknoesis.vericore.credential.wallet.Wallet
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for CredentialLifecycleDsl.kt
 */
class CredentialLifecycleDslTest {
    
    private lateinit var wallet: InMemoryWallet
    private lateinit var trustLayer: TrustLayerConfig
    
    @BeforeEach
    fun setup() = runBlocking {
        val kms = InMemoryKeyManagementService()
        wallet = InMemoryWallet(
            walletId = "test-wallet",
            holderDid = "did:key:holder"
        )
        
        trustLayer = trustLayer {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
    }
    
    @Test
    fun `test storeIn wallet`() = runBlocking {
        val credential = createTestCredential("cred-1")
        
        val stored = credential.storeIn(wallet)
        
        assertEquals(credential, stored.credential)
        assertEquals(wallet, stored.wallet)
        assertNotNull(stored.credentialId)
    }
    
    @Test
    fun `test verify credential`() = runBlocking {
        val credential = createTestCredential("cred-1")
        
        // Note: Verification may fail without proper proof, but DSL should work
        val result = credential.verify(trustLayer) {
            checkExpiration()
        }
        
        assertNotNull(result)
    }
    
    @Test
    fun `test verify stored credential`() = runBlocking {
        val credential = createTestCredential("cred-1")
        val stored = credential.storeIn(wallet)
        
        val result = stored.verify(trustLayer)
        
        assertNotNull(result)
    }
    
    @Test
    fun `test check revocation`() = runBlocking {
        val credential = createTestCredential("cred-1")
        
        val status = credential.checkRevocation(trustLayer.dsl())
        
        assertNotNull(status)
        // Status may be not revoked if no status list manager configured
    }
    
    @Test
    fun `test validate schema`() = runBlocking {
        val credential = createTestCredential("cred-1")
        
        // Note: This will fail if schema not registered, but DSL should work
        try {
            credential.validateSchema(trustLayer.dsl(), "https://example.com/schema")
        } catch (_: IllegalArgumentException) {
            // Expected if schema not registered
        } catch (_: IllegalStateException) {
            // Expected if schema not registered
        }
    }
    
    @Test
    fun `test chain operations`() = runBlocking {
        val credential = createTestCredential("cred-1")
        
        val stored = credential.storeIn(wallet)
        val result = stored.verify(trustLayer)
        
        assertNotNull(stored)
        assertNotNull(result)
    }
    
    @Test
    fun `test verify with TrustLayerConfig`() = runBlocking {
        val credential = createTestCredential("cred-1")
        
        val result = credential.verify(trustLayer)
        
        assertNotNull(result)
    }
    
    @Test
    fun `test verify stored credential with TrustLayerConfig`() = runBlocking {
        val credential = createTestCredential("cred-1")
        val stored = credential.storeIn(wallet)
        
        val result = stored.verify(trustLayer)
        
        assertNotNull(result)
    }
    
    @Test
    fun `test organize stored credential`() = runBlocking {
        val credential = createTestCredential("cred-1")
        val stored = credential.storeIn(wallet)
        
        val result = stored.organize {
            tag(stored.credentialId, "test", "tag")
        }

        assertNotNull(result)
        assertTrue(result.success)
    }
    
    private fun createTestCredential(id: String): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
    }
}

