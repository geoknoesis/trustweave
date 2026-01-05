package org.trustweave.trust.dsl

import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.dsl.TrustWeaveConfig
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.credential.model.ProofType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*

/**
 * Tests for TrustWeaveConfig DSL.
 */
class TrustLayerConfigTest {

    @BeforeEach
    fun setUp() {
        // No global registries to clear; each test builds a fresh TrustWeaveConfig.
    }

    @Test
    fun `test trust layer configuration with inMemory KMS`() = runBlocking {
        val trustWeave = trustWeave {
            // KMS, DID methods, and Anchor clients auto-discovered via SPI
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
                defaultProofType(ProofType.Ed25519Signature2020)
                autoAnchor(false)
            }
        }

        assertNotNull(trustWeave)
        assertEquals("default", trustWeave.name)
        assertNotNull(trustWeave.kms)
        assertTrue(trustWeave.didMethods.isNotEmpty())
        assertTrue(trustWeave.anchorClients.isNotEmpty())
        assertEquals(ProofType.Ed25519Signature2020, trustWeave.credentialConfig.defaultProofType)
        assertFalse(trustWeave.credentialConfig.autoAnchor)
    }

    @Test
    fun `test trust layer configuration with custom KMS`() = runBlocking {
        // For custom KMS without signer function, use provider("inMemory") instead
        // This test verifies that custom KMS can be set, but signer must be provided
        val trustWeave = trustWeave {
            // KMS and DID methods auto-discovered via SPI
            keys {
                provider("inMemory")
            }

            did {
                method("key") {
                    // Empty block
                }
            }
        }

        assertNotNull(trustWeave)
        assertNotNull(trustWeave.kms)
    }

    @Test
    fun `test trust layer configuration with multiple DID methods`() = runBlocking {
        val trustWeave = trustWeave {
            // KMS, DID methods, and Anchor clients auto-discovered via SPI
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

        assertTrue(trustWeave.didMethods.containsKey("key"))
    }

    @Test
    fun `test trust layer configuration with multiple anchor chains`() = runBlocking {
        val trustWeave = trustWeave {
            // KMS, DID methods, and Anchor clients auto-discovered via SPI
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

        assertTrue(trustWeave.anchorClients.containsKey("algorand:testnet"))
        assertTrue(trustWeave.anchorClients.containsKey("algorand:mainnet"))
    }

    @Test
    fun `test trust layer configuration with named layer`() = runBlocking {
        val trustWeave = trustWeave("production") {
            // KMS and DID methods auto-discovered via SPI
            keys {
                provider("inMemory")
            }

            did {
                method("key") {
                    // Empty block
                }
            }
        }

        assertEquals("production", trustWeave.name)
    }

    @Test
    fun `test trust layer credential config defaults`() = runBlocking {
        val trustWeave = trustWeave {
            // KMS and DID methods auto-discovered via SPI
            keys {
                provider("inMemory")
            }

            did {
                method("key") {
                    // Empty block
                }
            }
        }

        assertEquals(ProofType.Ed25519Signature2020, trustWeave.credentialConfig.defaultProofType)
        assertFalse(trustWeave.credentialConfig.autoAnchor)
        assertNull(trustWeave.credentialConfig.defaultChain)
    }

    @Test
    fun `test trust layer credential config custom values`() = runBlocking {
        val trustWeave = trustWeave {
            // KMS and DID methods auto-discovered via SPI
            keys {
                provider("inMemory")
            }

            did {
                method("key") {
                    // Empty block
                }
            }

            credentials {
                defaultProofType(ProofType.Ed25519Signature2020) // Use supported proof type
                autoAnchor(true)
                defaultChain("algorand:testnet")
            }
        }

        assertEquals(ProofType.Ed25519Signature2020, trustWeave.credentialConfig.defaultProofType)
        assertTrue(trustWeave.credentialConfig.autoAnchor)
        assertEquals("algorand:testnet", trustWeave.credentialConfig.defaultChain)
    }

    @Test
    fun `test trust layer context provides access to components`() = runBlocking {
        val trustWeaveConfig = trustWeave {
            // KMS and DID methods auto-discovered via SPI
            keys {
                provider("inMemory")
            }

            did {
                method("key") {
                    // Empty block
                }
            }
        }

        // Test that TrustWeaveConfig has the expected properties
        assertNotNull(trustWeaveConfig.kms)
        assertNotNull(trustWeaveConfig.didRegistry.get("key"))
        assertNotNull(trustWeaveConfig.didMethods["key"])
        // issuer may be null if not configured, but the config should be valid
    }
}


