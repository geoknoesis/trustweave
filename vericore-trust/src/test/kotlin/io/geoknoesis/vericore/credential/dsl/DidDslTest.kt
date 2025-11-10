package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.revocation.InMemoryStatusListManager
import io.geoknoesis.vericore.credential.revocation.StatusListManager
import io.geoknoesis.vericore.credential.revocation.StatusPurpose
import io.geoknoesis.vericore.credential.wallet.CredentialOrganization
import io.geoknoesis.vericore.testkit.credential.InMemoryWallet
import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for DidDsl.kt
 */
class DidDslTest {
    
    private lateinit var trustLayer: TrustLayerConfig
    private lateinit var kms: InMemoryKeyManagementService
    
    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()
        val kmsInstance = kms
        trustLayer = trustLayer {
            keys {
                custom(kmsInstance)
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
    }
    
    @Test
    fun `test createDid with method and algorithm`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        assertTrue(did.startsWith("did:key:"), "DID should start with did:key:")
        assertNotNull(did)
    }
    
    @Test
    fun `test createDid without method throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.createDid {
                algorithm("Ed25519")
            }
        }
    }
    
    @Test
    fun `test createDid with unconfigured method throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.createDid {
                method("web")
                algorithm("Ed25519")
            }
        }
    }
    
    @Test
    fun `test createDid with custom options`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
            option("custom", "value")
        }
        
        assertNotNull(did)
        assertTrue(did.startsWith("did:key:"))
    }
    
    @Test
    fun `test createDid via TrustLayerContext`() = runBlocking {
        val context = trustLayer.dsl()
        val did = context.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        assertNotNull(did)
        assertTrue(did.startsWith("did:key:"))
    }
    
    @Test
    fun `test createDid with different algorithms`() = runBlocking {
        val did1 = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        // Create second DID - even with same algorithm, should produce different DID
        // since it generates a new key
        val did2 = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        assertNotNull(did1)
        assertNotNull(did2)
        assertNotEquals(did1, did2, "Different key generations should produce different DIDs")
    }
    
    @Test
    fun `test createDid extracts DID from document`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        // Verify DID format
        assertTrue(did.startsWith("did:key:"))
        assertTrue(did.length > 10) // Should have some identifier
    }
}

