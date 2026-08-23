package org.trustweave.trust.dsl

import org.trustweave.trust.types.DidResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.ServiceTypes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for DidDocumentDsl.kt
 */
class DidDocumentDslTest {

    private lateinit var trustWeave: org.trustweave.trust.TrustWeave
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var didMethod: DidKeyMockMethod
    private lateinit var didRegistry: DidMethodRegistry

    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()
        didMethod = DidKeyMockMethod(kms)
        didRegistry = DidMethodRegistry()
        didRegistry.register(didMethod)

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

    @Test
    fun `test updateDid add key`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val keyHandle = when (val result = kms.generateKey("Ed25519")) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }

        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            addKey {
                type("Ed25519VerificationKey2020")
                publicKeyJwk(keyHandle.publicKeyJwk ?: emptyMap())
            }
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test updateDid add service`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test updateDid remove key`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            removeKey("$did#key-1")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test updateDid remove service`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        // First add a service
        trustWeave.updateDid {
            did(did.value)
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
        }

        // Then remove it
        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            removeService("$did#service-1")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test updateDid without DID returns UpdateFailed`() = runBlocking<Unit> {
        val result = trustWeave.updateDid {
            addKey {
                type("Ed25519VerificationKey2020")
            }
        }

        assertTrue(
            result is DidResult.Failure.UpdateFailed,
            "Updating without naming a DID must yield UpdateFailed, got: $result",
        )
    }

    @Test
    fun `test updateDid add service without required fields throws exception`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val result = trustWeave.updateDid {
            did(did.value)
            addService {
                // Missing required fields
            }
        }

        assertTrue(
            result is DidResult.Failure.UpdateFailed,
            "A service with no required fields must yield UpdateFailed, got: $result",
        )
    }

    @Test
    fun `test updateDid add key with multibase`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            addKey {
                type("Ed25519VerificationKey2020")
                publicKeyMultibase("z6Mk...")
            }
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test updateDid add multiple keys and services`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val keyHandle1 = when (val result = kms.generateKey("Ed25519")) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val keyHandle2 = when (val result = kms.generateKey("Ed25519")) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }

        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            addKey {
                type("Ed25519VerificationKey2020")
                publicKeyJwk(keyHandle1.publicKeyJwk ?: emptyMap())
            }
            addKey {
                type("Ed25519VerificationKey2020")
                publicKeyJwk(keyHandle2.publicKeyJwk ?: emptyMap())
            }
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
            addService {
                id("$did#service-2")
                type(ServiceTypes.DID_COMM_MESSAGING)
                endpoint("https://messaging.example.com")
            }
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test updateDid auto-detects method from DID`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            // Method should be auto-detected
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test updateDid via TrustWeaveContext`() = runBlocking<Unit> {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()

        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            addService {
                id("$did#service-1")
                type(ServiceTypes.LINKED_DOMAINS)
                endpoint("https://example.com")
            }
        }

        assertNotNull(updatedDoc)
    }
}


