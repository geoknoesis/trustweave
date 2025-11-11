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
 * Unit tests for WalletOperationsDsl.kt
 */
class WalletOperationsDslTest {
    
    private lateinit var wallet: InMemoryWallet
    
    @BeforeEach
    fun setup() = runBlocking {
        wallet = InMemoryWallet(
            walletId = "test-wallet",
            holderDid = "did:key:holder"
        )
    }
    
    @Test
    fun `test organize credentials into collections`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred2 = createTestCredential("cred-2")
        val cred1Id = wallet.store(cred1)
        val cred2Id = wallet.store(cred2)
        
        val result = wallet.organize {
            collection("Education", "Academic credentials") {
                add(cred1Id, cred2Id)
                tag(cred1Id, "education", "degree")
                tag(cred2Id, "education", "certificate")
            }
        }
        
        assertTrue(result.success)
        assertEquals(1, result.collectionsCreated)
        assertEquals(0, result.errors.size)
    }
    
    @Test
    fun `test organize with tags`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred1Id = wallet.store(cred1)
        
        val result = wallet.organize {
            tag(cred1Id, "important", "verified")
            metadata(cred1Id) {
                "source" to "issuer.com"
                "verified" to true
            }
        }
        
        assertTrue(result.success)
        val tags = wallet.getTags(cred1Id)
        assertTrue(tags.contains("important"))
        assertTrue(tags.contains("verified"))
    }
    
    @Test
    fun `test organize with notes`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred1Id = wallet.store(cred1)
        
        val result = wallet.organize {
            notes(cred1Id, "This is an important credential")
        }
        
        assertTrue(result.success)
        val metadata = wallet.getMetadata(cred1Id)
        assertEquals("This is an important credential", metadata?.notes)
    }
    
    @Test
    fun `test organize on non-organization wallet throws exception`() = runBlocking {
        // Create a basic wallet without organization support
        val basicWallet = object : com.geoknoesis.vericore.credential.wallet.Wallet {
            override val walletId = "basic-wallet"
            override suspend fun store(credential: VerifiableCredential) = "id"
            override suspend fun get(credentialId: String) = null
            override suspend fun list(filter: com.geoknoesis.vericore.credential.wallet.CredentialFilter?): List<VerifiableCredential> = emptyList()
            override suspend fun delete(credentialId: String) = false
            override suspend fun query(query: com.geoknoesis.vericore.credential.wallet.CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = emptyList()
        }
        
        assertFailsWith<IllegalStateException> {
            runBlocking {
                basicWallet.organize {
                    collection("Test") {
                        add("cred-1")
                    }
                }
            }
        }
    }
    
    @Test
    fun `test organize multiple collections`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred2 = createTestCredential("cred-2")
        val cred1Id = wallet.store(cred1)
        val cred2Id = wallet.store(cred2)
        
        val result = wallet.organize {
            collection("Collection1") {
                add(cred1Id)
            }
            collection("Collection2") {
                add(cred2Id)
            }
        }
        
        assertTrue(result.success)
        assertEquals(2, result.collectionsCreated)
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

