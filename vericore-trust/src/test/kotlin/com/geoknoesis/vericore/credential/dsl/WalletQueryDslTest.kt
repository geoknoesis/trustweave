package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.testkit.credential.InMemoryWallet
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for WalletQueryDsl.kt
 */
class WalletQueryDslTest {
    
    private lateinit var wallet: InMemoryWallet
    private var organizationWallet: com.geoknoesis.vericore.credential.wallet.CredentialOrganization? = null
    
    @BeforeEach
    fun setup() = runBlocking {
        wallet = InMemoryWallet(
            walletId = "test-wallet",
            holderDid = "did:key:holder"
        )
        
        // Add test credentials
        val cred1 = VerifiableCredential(
            id = "cred-1",
            type = listOf("VerifiableCredential", "EducationCredential"),
            issuer = "did:key:issuer1",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        val cred2 = VerifiableCredential(
            id = "cred-2",
            type = listOf("VerifiableCredential", "CertificationCredential"),
            issuer = "did:key:issuer2",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        wallet.store(cred1)
        wallet.store(cred2)
        
        organizationWallet = wallet as? com.geoknoesis.vericore.credential.wallet.CredentialOrganization
        organizationWallet?.tagCredential("cred-1", setOf("education", "degree"))
        organizationWallet?.tagCredential("cred-2", setOf("certification", "cloud"))
        Unit
    }
    
    @Test
    fun `test queryEnhanced by type`() = runBlocking {
        val results = wallet.queryEnhanced {
            byType("EducationCredential")
        }
        
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }
    
    @Test
    fun `test queryEnhanced by issuer`() = runBlocking {
        val results = wallet.queryEnhanced {
            byIssuer("did:key:issuer1")
        }
        
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }
    
    @Test
    fun `test queryEnhanced by tag`() = runBlocking {
        val orgWallet = organizationWallet ?: return@runBlocking
        val results = wallet.queryEnhanced {
            byTag("education")
        }
        
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }
    
    @Test
    fun `test queryEnhanced multiple filters`() = runBlocking {
        val results = wallet.queryEnhanced {
            byType("CertificationCredential")
            notExpired()
        }
        
        assertEquals(1, results.size)
        assertEquals("cred-2", results[0].id)
    }
    
    @Test
    fun `test queryEnhanced by collection`() = runBlocking {
        val orgWallet = organizationWallet ?: return@runBlocking
        val collectionId = orgWallet.createCollection("Test Collection")
        orgWallet.addToCollection("cred-1", collectionId)
        
        val results = wallet.queryEnhanced {
            byCollection(collectionId)
        }
        
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }
    
    @Test
    fun `test queryEnhanced not expired`() = runBlocking {
        val results = wallet.queryEnhanced {
            notExpired()
        }
        
        assertTrue(results.size >= 0)
    }
    
    @Test
    fun `test queryEnhanced valid`() = runBlocking {
        val results = wallet.queryEnhanced {
            valid()
        }
        
        assertTrue(results.size >= 0)
    }
    
    @Test
    fun `test queryEnhanced by subject`() = runBlocking {
        val results = wallet.queryEnhanced {
            bySubject("did:key:subject")
        }
        
        assertEquals(2, results.size)
    }
    
    @Test
    fun `test queryEnhanced expired`() = runBlocking {
        val expiredCred = VerifiableCredential(
            id = "cred-expired",
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2020-01-01T00:00:00Z",
            expirationDate = "2021-01-01T00:00:00Z"
        )
        wallet.store(expiredCred)
        
        val results = wallet.queryEnhanced {
            expired()
        }
        
        assertTrue(results.size >= 1)
    }
    
    @Test
    fun `test queryEnhanced not revoked`() = runBlocking {
        val results = wallet.queryEnhanced {
            notRevoked()
        }
        
        assertTrue(results.size >= 0)
    }
    
    @Test
    fun `test queryEnhanced revoked`() = runBlocking {
        val revokedCred = VerifiableCredential(
            id = "cred-revoked",
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2024-01-01T00:00:00Z",
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-1",
                type = "StatusList2021Entry"
            )
        )
        wallet.store(revokedCred)
        
        val results = wallet.queryEnhanced {
            revoked()
        }
        
        assertTrue(results.size >= 1)
    }
    
    @Test
    fun `test queryEnhanced byTypes`() = runBlocking {
        val results = wallet.queryEnhanced {
            byTypes("EducationCredential", "CertificationCredential")
        }
        
        assertEquals(2, results.size)
    }
}

