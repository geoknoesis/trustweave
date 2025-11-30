package com.trustweave.trust.dsl

import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.trust.dsl.TrustWeaveConfig
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for KeyRotationDsl.kt
 */
class KeyRotationDslTest {

    private lateinit var trustWeave: TrustWeaveConfig
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()

        val kmsRef = kms
        trustWeave = trustWeave {
            keys {
                custom(kmsRef)
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
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }

        // Rotate key
        val updatedDoc = trustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test rotateKey without DID throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.rotateKey {
                algorithm("Ed25519")
            }
        }
    }

    @Test
    fun `test rotateKey with removeOldKey`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }

        val updatedDoc = trustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519")
            removeOldKey("key-1")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test rotateKey auto-detects method from DID`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }

        val updatedDoc = trustWeave.rotateKey {
            did(did.value)
            // Method should be auto-detected
            algorithm("Ed25519")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test rotateKey with unconfigured method throws exception`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }

        assertFailsWith<IllegalStateException> {
            trustWeave.rotateKey {
                did(did.value)
                method("web") // Not configured
                algorithm("Ed25519")
            }
        }
    }

    @Test
    fun `test rotateKey via TrustWeaveContext`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }

        val context = trustWeave.getDslContext()
        val updatedDoc = context.rotateKey {
            did(did.value)
            algorithm("Ed25519")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test rotateKey with multiple old keys`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }

        val updatedDoc = trustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519")
            removeOldKey("key-1")
            removeOldKey("key-2")
        }

        assertNotNull(updatedDoc)
    }
}


