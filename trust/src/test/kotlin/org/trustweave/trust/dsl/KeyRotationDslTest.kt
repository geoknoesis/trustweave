package org.trustweave.trust.dsl

import org.trustweave.trust.types.DidResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.services.TestkitTrustRegistryFactory
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.inMemory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for KeyRotationDsl.kt
 */
class KeyRotationDslTest {

    private lateinit var trustWeave: org.trustweave.trust.TrustWeave
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()

        val kmsRef = kms
        trustWeave = org.trustweave.trust.TrustWeave.build {
            keys {
                custom(kmsRef)
                signer { data, keyId ->
                    when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is org.trustweave.kms.results.SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
    }

    private fun DidResult.requireSuccess(): DidResult.Success {
        assertIs<DidResult.Success>(this, "Expected successful key rotation but got: $this")
        return this
    }

    @Test
    fun `test rotateKey succeeds with quickStart config`() = runBlocking {
        // Facade-level regression test: the factory must wire a KmsService so
        // rotateKey is usable out of the box (it used to always fail with
        // "KmsService is not configured"). TrustWeave.inMemory is the configuration
        // quickStart() aliases; the testkit trust-registry factory is supplied because
        // no SPI TrustRegistryFactory is on the test classpath.
        val quickStartTrustWeave = TrustWeave.inMemory(
            trustRegistryFactory = TestkitTrustRegistryFactory()
        )
        val did = quickStartTrustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val result = quickStartTrustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519")
        }

        val success = result.requireSuccess()
        assertEquals(did.value, success.did.value)
        assertTrue(success.document.verificationMethod.isNotEmpty())
    }

    @Test
    fun `test rotateKey`() = runBlocking {
        // Create initial DID
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        // Rotate key
        val result = trustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519")
        }

        result.requireSuccess()
    }

    @Test
    fun `test rotateKey without DID returns failure`() = runBlocking {
        val result = trustWeave.rotateKey {
            algorithm("Ed25519")
        }

        val failure = assertIs<DidResult.Failure.UpdateFailed>(result)
        assertTrue(
            failure.reason.contains("DID is required"),
            "Expected 'DID is required' failure but got: ${failure.reason}"
        )
    }

    @Test
    fun `test rotateKey with removeOldKey`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val result = trustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519")
            removeOldKey("key-1")
        }

        result.requireSuccess()
    }

    @Test
    fun `test rotateKey auto-detects method from DID`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val result = trustWeave.rotateKey {
            did(did.value)
            // Method should be auto-detected
            algorithm("Ed25519")
        }

        result.requireSuccess()
    }

    @Test
    fun `test rotateKey with unconfigured method returns failure`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val result = trustWeave.rotateKey {
            did(did.value)
            method("web") // Not configured
            algorithm("Ed25519")
        }

        val failure = assertIs<DidResult.Failure.UpdateFailed>(result)
        assertTrue(
            failure.reason.contains("not configured"),
            "Expected 'not configured' failure but got: ${failure.reason}"
        )
    }

    @Test
    fun `test rotateKey via TrustWeaveContext`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val result = trustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519")
        }

        result.requireSuccess()
    }

    @Test
    fun `test rotateKey with multiple old keys`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val result = trustWeave.rotateKey {
            did(did.value)
            algorithm("Ed25519")
            removeOldKey("key-1")
            removeOldKey("key-2")
        }

        result.requireSuccess()
    }
}
