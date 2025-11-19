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
 * Unit tests for WalletPresentationDsl.kt
 */
class WalletPresentationDslTest {
    
    private lateinit var wallet: InMemoryWallet
    
    @BeforeEach
    fun setup() = runBlocking {
        wallet = InMemoryWallet(
            walletId = "test-wallet",
            holderDid = "did:key:holder"
        )
    }
    
    @Test
    fun `test presentation from wallet credential IDs`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred2 = createTestCredential("cred-2")
        val cred1Id = wallet.store(cred1)
        val cred2Id = wallet.store(cred2)
        
        val presentation = wallet.presentation {
            fromWallet(cred1Id, cred2Id)
            holder("did:key:holder")
        }
        
        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
        assertEquals("did:key:holder", presentation.holder)
    }
    
    @Test
    fun `test presentation from query`() = runBlocking {
        val cred1 = VerifiableCredential(
            id = "cred-1",
            type = listOf("VerifiableCredential", "EducationCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        wallet.store(cred1)
        
        val presentation = wallet.presentation {
            fromQuery {
                byType("EducationCredential")
            }
            holder("did:key:holder")
        }
        
        assertNotNull(presentation)
        assertEquals(1, presentation.verifiableCredential.size)
    }
    
    @Test
    fun `test presentation with challenge and domain`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred1Id = wallet.store(cred1)
        
        val presentation = wallet.presentation {
            fromWallet(cred1Id)
            holder("did:key:holder")
            challenge("test-challenge-123")
            domain("example.com")
        }
        
        assertNotNull(presentation)
        assertEquals("test-challenge-123", presentation.challenge)
        assertEquals("example.com", presentation.domain)
    }
    
    @Test
    fun `test presentation with selective disclosure`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred1Id = wallet.store(cred1)
        
        val presentation = wallet.presentation {
            fromWallet(cred1Id)
            holder("did:key:holder")
            selectiveDisclosure {
                reveal("degree.field", "degree.institution")
            }
        }
        
        assertNotNull(presentation)
    }
    
    @Test
    fun `test presentation without holder throws exception`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred1Id = wallet.store(cred1)
        
        assertFailsWith<IllegalStateException> {
            runBlocking {
                wallet.presentation {
                    fromWallet(cred1Id)
                    // Missing holder
                }
            }
        }
    }
    
    @Test
    fun `test presentation without credentials throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            runBlocking {
                wallet.presentation {
                    holder("did:key:holder")
                    // No credentials
                }
            }
        }
    }
    
    @Test
    fun `test presentation with keyId`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred1Id = wallet.store(cred1)
        
        val presentation = wallet.presentation {
            fromWallet(cred1Id)
            holder("did:key:holder")
            keyId("key-1")
        }
        
        assertNotNull(presentation)
    }
    
    @Test
    fun `test presentation with proofType`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred1Id = wallet.store(cred1)
        
        val presentation = wallet.presentation {
            fromWallet(cred1Id)
            holder("did:key:holder")
            proofType("JsonWebSignature2020")
        }
        
        assertNotNull(presentation)
    }
    
    @Test
    fun `test presentation from wallet with list`() = runBlocking {
        val cred1 = createTestCredential("cred-1")
        val cred2 = createTestCredential("cred-2")
        val cred1Id = wallet.store(cred1)
        val cred2Id = wallet.store(cred2)
        
        val presentation = wallet.presentation {
            fromWallet(listOf(cred1Id, cred2Id))
            holder("did:key:holder")
        }
        
        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
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

