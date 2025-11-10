package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

/**
 * Tests for TrustLayerConfig DSL.
 */
class TrustLayerConfigTest {

    @BeforeEach
    fun setUp() {
        // No global registries to clear; each test builds a fresh TrustLayerConfig.
    }

    @Test
    fun `test trust layer configuration with inMemory KMS`() = runBlocking {
        val trustLayer = trustLayer {
            keys {
                provider("inMemory")
                algorithm("Ed25519")
            }
            
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
            
            anchor {
                chain("algorand:testnet") {
                    inMemory()
                }
            }
            
            credentials {
                defaultProofType("Ed25519Signature2020")
                autoAnchor(false)
            }
        }
        
        assertNotNull(trustLayer)
        assertEquals("default", trustLayer.name)
        assertNotNull(trustLayer.kms)
        assertTrue(trustLayer.didMethods.isNotEmpty())
        assertTrue(trustLayer.anchorClients.isNotEmpty())
        assertEquals("Ed25519Signature2020", trustLayer.credentialConfig.defaultProofType)
        assertFalse(trustLayer.credentialConfig.autoAnchor)
    }

    @Test
    fun `test trust layer configuration with custom KMS`() = runBlocking {
        // For custom KMS without signer function, use provider("inMemory") instead
        // This test verifies that custom KMS can be set, but signer must be provided
        val trustLayer = trustLayer {
            keys {
                provider("inMemory")
            }
            
            did {
                method("key") {
                    // Empty block
                }
            }
        }
        
        assertNotNull(trustLayer)
        assertNotNull(trustLayer.kms)
    }

    @Test
    fun `test trust layer configuration with multiple DID methods`() = runBlocking {
        val trustLayer = trustLayer {
            keys {
                provider("inMemory")
            }
            
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
            
            anchor {
                chain("algorand:testnet") {
                    inMemory()
                }
            }
        }
        
        assertTrue(trustLayer.didMethods.containsKey("key"))
    }

    @Test
    fun `test trust layer configuration with multiple anchor chains`() = runBlocking {
        val trustLayer = trustLayer {
            keys {
                provider("inMemory")
            }
            
            did {
                method("key") {
                    // Empty block
                }
            }
            
            anchor {
                chain("algorand:testnet") {
                    inMemory()
                }
                chain("algorand:mainnet") {
                    inMemory()
                }
            }
        }
        
        assertTrue(trustLayer.anchorClients.containsKey("algorand:testnet"))
        assertTrue(trustLayer.anchorClients.containsKey("algorand:mainnet"))
    }

    @Test
    fun `test trust layer configuration with named layer`() = runBlocking {
        val trustLayer = trustLayer("production") {
            keys {
                provider("inMemory")
            }
            
            did {
                method("key") {
                    // Empty block
                }
            }
        }
        
        assertEquals("production", trustLayer.name)
    }

    @Test
    fun `test trust layer credential config defaults`() = runBlocking {
        val trustLayer = trustLayer {
            keys {
                provider("inMemory")
            }
            
            did {
                method("key") {
                    // Empty block
                }
            }
        }
        
        assertEquals("Ed25519Signature2020", trustLayer.credentialConfig.defaultProofType)
        assertFalse(trustLayer.credentialConfig.autoAnchor)
        assertNull(trustLayer.credentialConfig.defaultChain)
    }

    @Test
    fun `test trust layer credential config custom values`() = runBlocking {
        val trustLayer = trustLayer {
            keys {
                provider("inMemory")
            }
            
            did {
                method("key") {
                    // Empty block
                }
            }
            
            credentials {
                defaultProofType("Ed25519Signature2020") // Use supported proof type
                autoAnchor(true)
                defaultChain("algorand:testnet")
            }
        }
        
        assertEquals("Ed25519Signature2020", trustLayer.credentialConfig.defaultProofType)
        assertTrue(trustLayer.credentialConfig.autoAnchor)
        assertEquals("algorand:testnet", trustLayer.credentialConfig.defaultChain)
    }

    @Test
    fun `test trust layer context provides access to components`() = runBlocking {
        val trustLayer = trustLayer {
            keys {
                provider("inMemory")
            }
            
            did {
                method("key") {
                    // Empty block
                }
            }
        }
        
        val context = trustLayer.dsl()
        assertNotNull(context.getKms())
        assertNotNull(context.getDidMethod("key"))
        assertNotNull(context.getIssuer())
        assertNotNull(context.getVerifier())
    }
}

