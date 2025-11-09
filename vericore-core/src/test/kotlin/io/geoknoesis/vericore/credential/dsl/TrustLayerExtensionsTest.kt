package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.testkit.credential.InMemoryWallet
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for TrustLayerExtensions.kt
 */
class TrustLayerExtensionsTest {
    
    private lateinit var trustLayer: TrustLayerConfig
    private lateinit var wallet: InMemoryWallet
    
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
    fun `test createDidAndIssue`() = runBlocking {
        val credential = trustLayer.createDidAndIssue(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            }
        ) { did ->
            VerifiableCredential(
                type = listOf("VerifiableCredential"),
                issuer = did,
                credentialSubject = buildJsonObject {
                    put("id", "did:key:subject")
                },
                issuanceDate = "2024-01-01T00:00:00Z"
            )
        }
        
        assertNotNull(credential)
        assertTrue(credential.issuer.startsWith("did:key:"))
    }
    
    @Test
    fun `test createDidIssueAndStore`() = runBlocking {
        val stored = trustLayer.createDidIssueAndStore(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            },
            credentialBlock = { did ->
                VerifiableCredential(
                    type = listOf("VerifiableCredential"),
                    issuer = did,
                    credentialSubject = buildJsonObject {
                        put("id", "did:key:subject")
                    },
                    issuanceDate = "2024-01-01T00:00:00Z"
                )
            },
            wallet = wallet
        )
        
        assertNotNull(stored)
        assertEquals(wallet, stored.wallet)
        assertNotNull(stored.credentialId)
    }
    
    @Test
    fun `test completeWorkflow`() = runBlocking {
        val result = trustLayer.completeWorkflow(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            },
            credentialBlock = { did ->
                VerifiableCredential(
                    type = listOf("VerifiableCredential"),
                    issuer = did,
                    credentialSubject = buildJsonObject {
                        put("id", "did:key:subject")
                    },
                    issuanceDate = "2024-01-01T00:00:00Z"
                )
            },
            wallet = wallet,
            organizeBlock = { stored ->
                wallet.organize {
                    tag(stored.credentialId, "test")
                }
            }
        )
        
        assertNotNull(result)
        assertTrue(result.did.startsWith("did:key:"))
        assertNotNull(result.credential)
        assertNotNull(result.storedCredential)
        assertNotNull(result.verificationResult)
    }
    
    @Test
    fun `test completeWorkflow without organization`() = runBlocking {
        val result = trustLayer.completeWorkflow(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            },
            credentialBlock = { did ->
                VerifiableCredential(
                    type = listOf("VerifiableCredential"),
                    issuer = did,
                    credentialSubject = buildJsonObject {
                        put("id", "did:key:subject")
                    },
                    issuanceDate = "2024-01-01T00:00:00Z"
                )
            },
            wallet = wallet
        )
        
        assertNotNull(result)
        assertNull(result.organizationResult)
    }
    
    @Test
    fun `test createDidAndIssue via TrustLayerContext`() = runBlocking {
        val context = trustLayer.dsl()
        val credential = context.createDidAndIssue(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            }
        ) { did ->
            VerifiableCredential(
                type = listOf("VerifiableCredential"),
                issuer = did,
                credentialSubject = buildJsonObject {
                    put("id", "did:key:subject")
                },
                issuanceDate = "2024-01-01T00:00:00Z"
            )
        }
        
        assertNotNull(credential)
        assertTrue(credential.issuer.startsWith("did:key:"))
    }
    
    @Test
    fun `test createDidIssueAndStore via TrustLayerContext`() = runBlocking {
        val context = trustLayer.dsl()
        val stored = context.createDidIssueAndStore(
            didBlock = {
                method("key")
                algorithm("Ed25519")
            },
            credentialBlock = { did ->
                VerifiableCredential(
                    type = listOf("VerifiableCredential"),
                    issuer = did,
                    credentialSubject = buildJsonObject {
                        put("id", "did:key:subject")
                    },
                    issuanceDate = "2024-01-01T00:00:00Z"
                )
            },
            wallet = wallet
        )
        
        assertNotNull(stored)
        assertEquals(wallet, stored.wallet)
    }
}

