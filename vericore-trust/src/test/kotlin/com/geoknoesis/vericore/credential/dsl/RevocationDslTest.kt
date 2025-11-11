package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.revocation.InMemoryStatusListManager
import com.geoknoesis.vericore.credential.revocation.StatusPurpose
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for RevocationDsl.kt
 */
class RevocationDslTest {
    
    private lateinit var trustLayer: TrustLayerConfig
    private lateinit var statusListManager: InMemoryStatusListManager
    
    @BeforeEach
    fun setup() = runBlocking {
        val kms = InMemoryKeyManagementService()
        statusListManager = InMemoryStatusListManager()
        
        trustLayer = trustLayer {
            keys {
                custom(kms)
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
            revocation {
                provider("inMemory")
            }
        }
        
        // Manually set status list manager in context for testing
        // In real usage, this would be configured via TrustLayerConfig
    }
    
    @Test
    fun `test createStatusList`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val statusList = trustLayer.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
        }.createStatusList()
        
        assertNotNull(statusList)
        assertEquals(issuerDid, statusList.issuer)
        assertEquals("revocation", statusList.credentialSubject.statusPurpose)
    }
    
    @Test
    fun `test createStatusList without issuer throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.revocation {
                purpose(StatusPurpose.REVOCATION)
            }.createStatusList()
        }
    }
    
    @Test
    fun `test revoke credential`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val credentialId = "cred-123"
        
        val statusList = trustLayer.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
        }.createStatusList()
        
        val revoked = trustLayer.revoke {
            credential(credentialId)
            statusList(statusList.id)
        }
        
        assertTrue(revoked)
    }
    
    @Test
    fun `test revoke credential without credentialId throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.revoke {
                statusList("list-id")
            }
        }
    }
    
    @Test
    fun `test check revocation status`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val credentialId = "cred-123"
        
        val statusList = trustLayer.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
        }.createStatusList()
        
        trustLayer.revoke {
            credential(credentialId)
            statusList(statusList.id)
        }
        
        val credential = VerifiableCredential(
            id = credentialId,
            type = listOf("VerifiableCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2024-01-01T00:00:00Z",
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "${statusList.id}#0",
                type = "StatusList2021Entry",
                statusListCredential = statusList.id
            )
        )
        
        val status = trustLayer.revocation {
            statusList(statusList.id)
        }.check(credential)
        
        assertTrue(status.revoked)
    }
    
    @Test
    fun `test suspend credential`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val credentialId = "cred-123"
        
        val statusList = trustLayer.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.SUSPENSION)
        }.createStatusList()
        
        val suspended = trustLayer.revocation {
            credential(credentialId)
            statusList(statusList.id)
        }.suspend()
        
        assertTrue(suspended)
    }
    
    @Test
    fun `test getStatusList`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val createdList = trustLayer.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
        }.createStatusList()
        
        val retrievedList = trustLayer.revocation {
            statusList(createdList.id)
        }.getStatusList()
        
        assertNotNull(retrievedList)
        assertEquals(createdList.id, retrievedList?.id)
    }
    
    @Test
    fun `test createStatusList with custom size`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val statusList = trustLayer.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
            size(65536)
        }.createStatusList()
        
        assertNotNull(statusList)
        assertEquals(issuerDid, statusList.issuer)
    }
    
    @Test
    fun `test revoke credential without statusListId throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.revoke {
                credential("cred-123")
            }
        }
    }
    
    @Test
    fun `test getStatusList without statusListId throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.revocation { }.getStatusList()
        }
    }
}

