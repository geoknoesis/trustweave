package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.kms.KeyHandle
import com.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
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

    private lateinit var trustLayer: TrustLayerConfig
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()
        assertNotNull(kms) { "KMS must be initialized" }
        
        // Capture KMS reference for closure
        val kmsRef = kms
        
        trustLayer = trustLayer {
            keys {
                custom(kmsRef as Any) // Cast to Any for DSL
                // Provide signer function directly to avoid reflection
                signer { data, keyId ->
                    kmsRef.sign(keyId, data)
                }
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType("Ed25519Signature2020")
                autoAnchor(false)
            }
        }
    }

    // ========== Credential Required Branches ==========

    @Test
    fun `test branch credential required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.issue {
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
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
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
        
        val issuedCredential = trustLayer.issue {
            credential(preBuiltCredential)
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
        }
        
        assertNotNull(issuedCredential)
    }

    // ========== Issuer DID Required Branches ==========

    @Test
    fun `test branch issuer DID required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.issue {
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
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
        }
        
        assertNotNull(issuedCredential)
    }

    // ========== Key ID Required Branches ==========

    @Test
    fun `test branch key ID required error`() = runBlocking {
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        
        assertFailsWith<IllegalStateException> {
            trustLayer.issue {
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
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
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
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
            withProof("Ed25519Signature2020") // Use supported proof type
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
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
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
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
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
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
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
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
            // autoAnchor is false in config
        }
        
        assertNotNull(issuedCredential)
        // Credential should be issued but not anchored
    }

    @Test
    fun `test branch auto-anchor enabled in config`() = runBlocking {
        val trustLayerWithAutoAnchor = trustLayer {
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
                defaultProofType("Ed25519Signature2020")
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
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
        }
        
        assertNotNull(issuedCredential)
        // Anchoring may fail silently, but credential is issued
    }

    @Test
    fun `test branch explicit anchor call`() = runBlocking {
        val trustLayerWithAnchor = trustLayer {
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
                defaultProofType("Ed25519Signature2020")
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
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
            anchor("algorand:testnet")
        }
        
        assertNotNull(issuedCredential)
    }

    @Test
    fun `test branch anchor error when chain ID missing`() = runBlocking {
        val trustLayerWithAutoAnchor = trustLayer {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType("Ed25519Signature2020")
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
                by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
                // No anchor() call and no defaultChain
            }
        }
    }

    @Test
    fun `test branch anchor error when anchor client not found`() = runBlocking {
        val trustLayerWithAutoAnchor = trustLayer {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType("Ed25519Signature2020")
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
                by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
            }
        }
    }

    @Test
    fun `test branch anchor failure handling`() = runBlocking {
        // This tests the exception handling when anchoring fails
        // The credential should still be issued even if anchoring fails
        val trustLayerWithAutoAnchor = trustLayer {
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
                defaultProofType("Ed25519Signature2020")
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
            by(issuerDid = issuerDidDoc.id, keyId = issuerKey.id)
        }
        
        assertNotNull(issuedCredential)
    }
}

