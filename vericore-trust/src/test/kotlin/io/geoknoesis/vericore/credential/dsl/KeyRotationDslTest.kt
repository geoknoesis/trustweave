package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for KeyRotationDsl.kt
 */
class KeyRotationDslTest {
    
    private lateinit var trustLayer: TrustLayerConfig
    private lateinit var kms: InMemoryKeyManagementService
    
    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()
        
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
    fun `test rotateKey`() = runBlocking {
        // Create initial DID
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        // Rotate key
        val updatedDoc = trustLayer.rotateKey {
            did(did)
            algorithm("Ed25519")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test rotateKey without DID throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.rotateKey {
                algorithm("Ed25519")
            }
        }
    }
    
    @Test
    fun `test rotateKey with removeOldKey`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val updatedDoc = trustLayer.rotateKey {
            did(did)
            algorithm("Ed25519")
            removeOldKey("key-1")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test rotateKey auto-detects method from DID`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val updatedDoc = trustLayer.rotateKey {
            did(did)
            // Method should be auto-detected
            algorithm("Ed25519")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test rotateKey with unconfigured method throws exception`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        assertFailsWith<IllegalStateException> {
            trustLayer.rotateKey {
                did(did)
                method("web") // Not configured
                algorithm("Ed25519")
            }
        }
    }
    
    @Test
    fun `test rotateKey via TrustLayerContext`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val context = trustLayer.dsl()
        val updatedDoc = context.rotateKey {
            did(did)
            algorithm("Ed25519")
        }
        
        assertNotNull(updatedDoc)
    }
    
    @Test
    fun `test rotateKey with multiple old keys`() = runBlocking {
        val did = trustLayer.createDid {
            method("key")
            algorithm("Ed25519")
        }
        
        val updatedDoc = trustLayer.rotateKey {
            did(did)
            algorithm("Ed25519")
            removeOldKey("key-1")
            removeOldKey("key-2")
        }
        
        assertNotNull(updatedDoc)
    }
}

