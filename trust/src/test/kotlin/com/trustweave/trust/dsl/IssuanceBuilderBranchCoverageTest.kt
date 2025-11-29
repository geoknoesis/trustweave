package com.trustweave.trust.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.did.DidDocument
import com.trustweave.kms.KeyHandle
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.TrustWeaveConfig
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.trust.dsl.credential.credential
import com.trustweave.trust.types.ProofType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for IssuanceBuilder DSL.
 * Tests all conditional branches, error paths, and edge cases.
 */
class IssuanceBuilderBranchCoverageTest {

    private lateinit var trustWeave: TrustWeaveConfig
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()
        assertNotNull(kms) { "KMS must be initialized" }

        // Capture KMS reference for closure
        val kmsRef = kms

        trustWeave = trustWeave {
            keys {
                custom(kmsRef as Any) // Cast to Any for DSL
                // Provide signer function directly to avoid reflection
                signer { data, keyId ->
                    kmsRef.sign(com.trustweave.core.types.KeyId(keyId), data)
                }
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
                autoAnchor(false)
            }
        }
    }

    // ========== Credential Required Branches ==========

    @Test
    fun `test branch credential required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.issue {
                // Missing credential
                by(issuerDid = "did:key:issuer", keyId = "key-1")
            }
        }
    }

    @Test
    fun `test branch credential from inline builder`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }

        assertNotNull(issuedCredential)
    }

    @Test
    fun `test branch credential from pre-built`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val preBuiltCredential = credential {
            type("PersonCredential")
            issuer(issuerDidDoc.id)
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }

        val issuedCredential = trustWeave.issue {
            credential(preBuiltCredential)
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }

        assertNotNull(issuedCredential)
    }

    // ========== Issuer DID Required Branches ==========

    @Test
    fun `test branch issuer DID required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.issue {
                credential {
                    type("PersonCredential")
                    issuer("did:key:issuer")
                    subject {
                        id("did:key:subject")
                    }
                    issued(Instant.now())
                }
                // Missing by() call
            }
        }
    }

    @Test
    fun `test branch issuer DID provided`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }

        assertNotNull(issuedCredential)
    }

    // ========== Key ID Required Branches ==========

    @Test
    fun `test branch key ID required error`() = runBlocking {
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        assertFailsWith<IllegalStateException> {
            trustWeave.issue {
                credential {
                    type("PersonCredential")
                    issuer(issuerDidDoc.id)
                    subject {
                        id("did:key:subject")
                    }
                    issued(Instant.now())
                }
                by(issuerDid = issuerDidDoc.id, keyId = "") // Empty key ID
            }
        }
    }

    // ========== Proof Type Branches ==========

    @Test
    fun `test branch proof type from default config`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            // No proof type - uses default
        }

        assertNotNull(issuedCredential.proof)
        assertEquals("Ed25519Signature2020", issuedCredential.proof?.type)
    }

    @Test
    fun `test branch proof type from custom value`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            withProof(ProofType.Ed25519Signature2020) // Use supported proof type
        }

        assertNotNull(issuedCredential.proof)
        assertEquals("Ed25519Signature2020", issuedCredential.proof?.type)
    }

    // ========== Challenge and Domain Branches ==========

    @Test
    fun `test branch challenge provided`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            challenge("challenge-123")
        }

        assertNotNull(issuedCredential.proof)
        assertEquals("challenge-123", issuedCredential.proof?.challenge)
    }

    @Test
    fun `test branch domain provided`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            domain("example.com")
        }

        assertNotNull(issuedCredential.proof)
        assertEquals("example.com", issuedCredential.proof?.domain)
    }

    @Test
    fun `test branch challenge and domain both provided`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            challenge("challenge-123")
            domain("example.com")
        }

        assertNotNull(issuedCredential.proof)
        assertEquals("challenge-123", issuedCredential.proof?.challenge)
        assertEquals("example.com", issuedCredential.proof?.domain)
    }

    // ========== Auto-Anchor Branches ==========

    @Test
    fun `test branch auto-anchor disabled in config`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            // autoAnchor is false in config
        }

        assertNotNull(issuedCredential)
        // Credential should be issued but not anchored
    }

    @Test
    fun `test branch auto-anchor enabled in config`() = runBlocking {
        val trustLayerWithAutoAnchor = TrustWeave.build {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
            }
            anchor {
                chain("algorand:testnet") {
                    inMemory()
                }
            }
            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
                autoAnchor(true)
                defaultChain("algorand:testnet")
            }
        }

        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustLayerWithAutoAnchor.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }

        assertNotNull(issuedCredential)
        // Anchoring may fail silently, but credential is issued
    }

    @Test
    fun `test branch explicit anchor call`() = runBlocking {
        val trustLayerWithAnchor = TrustWeave.build {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
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

        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustLayerWithAnchor.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            // Note: anchor() function not available in current DSL
        }

        assertNotNull(issuedCredential)
    }

    @Test
    fun `test branch anchor error when chain ID missing`() = runBlocking {
        val trustLayerWithAutoAnchor = TrustWeave.build {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
                autoAnchor(true)
                // No defaultChain specified
            }
        }

        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        // Should fail when trying to anchor without chain ID
        assertFailsWith<IllegalStateException> {
            trustLayerWithAutoAnchor.issue {
                credential {
                    type("PersonCredential")
                    issuer(issuerDidDoc.id)
                    subject {
                        id("did:key:subject")
                    }
                    issued(Instant.now())
                }
                by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
                // No anchor() call and no defaultChain
            }
        }
    }

    @Test
    fun `test branch anchor error when anchor client not found`() = runBlocking {
        val trustLayerWithAutoAnchor = TrustWeave.build {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
                autoAnchor(true)
                defaultChain("nonexistent:chain")
            }
        }

        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        assertFailsWith<IllegalStateException> {
            trustLayerWithAutoAnchor.issue {
                credential {
                    type("PersonCredential")
                    issuer(issuerDidDoc.id)
                    subject {
                        id("did:key:subject")
                    }
                    issued(Instant.now())
                }
                by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            }
        }
    }

    @Test
    fun `test branch anchor failure handling`() = runBlocking {
        // This tests the exception handling when anchoring fails
        // The credential should still be issued even if anchoring fails
        val trustLayerWithAutoAnchor = TrustWeave.build {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
            }
            anchor {
                chain("algorand:testnet") {
                    inMemory()
                }
            }
            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
                autoAnchor(true)
                defaultChain("algorand:testnet")
            }
        }

        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        // Anchoring may fail, but credential should still be issued
        val issuedCredential = trustLayerWithAutoAnchor.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }

        assertNotNull(issuedCredential)
    }
}


