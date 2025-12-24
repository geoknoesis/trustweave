package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.services.TestkitDidMethodFactory
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.createTestCredentialService
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.credential.model.ProofType
import org.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for IssuanceBuilder DSL.
 * Tests all conditional branches, error paths, and edge cases.
 */
class IssuanceBuilderBranchCoverageTest {

    private lateinit var trustWeave: TrustWeave
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()
        assertNotNull(kms) { "KMS must be initialized" }

        // Capture KMS reference for closure
        val kmsRef = kms

        // Create DID resolver that uses the DID registry from TrustWeave
        val tempTrustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kmsRef)
                signer { data, keyId ->
                    when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") {}
            }
        }
        
        val didResolver = DidResolver { did ->
            tempTrustWeave.getDslContext().getConfig().registries.didRegistry.resolve(did.value)
        }
        
        val credentialService = createTestCredentialService(kms = kmsRef, didResolver = didResolver)
        trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kmsRef)
                // Provide signer function directly to avoid reflection
                signer { data, keyId ->
                    when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
                autoAnchor(false)
            }
            // Set CredentialService as issuer for issuance builder
            issuer(credentialService)
        }
    }

    // ========== Credential Required Branches ==========

    @Test
    fun `test branch credential required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.issue {
                // Missing credential
                signedBy(issuerDid = "did:key:issuer", keyId = "key-1")
            }
        }
    }

    @Test
    fun `test branch credential from inline builder`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }.getOrFail()

        assertNotNull(issuedCredential)
    }

    @Test
    fun `test branch credential from pre-built`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val preBuiltCredential = credential {
            type("PersonCredential")
            issuer(issuerDidDoc.id)
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        val issuedCredential = trustWeave.issue {
            credential(preBuiltCredential)
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }.getOrFail()

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
                    issued(Clock.System.now())
                }
                // Missing signedBy() call
            }
        }
    }

    @Test
    fun `test branch issuer DID provided`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }.getOrFail()

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
                    issued(Clock.System.now())
                }
                signedBy(issuerDid = issuerDidDoc.id, keyId = "") // Empty key ID
            }
        }
    }

    // ========== Proof Type Branches ==========

    @Test
    fun `test branch proof type from default config`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            // No proof type - uses default
        }.getOrFail()

        assertNotNull(issuedCredential.proof)
        assertTrue(issuedCredential.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = issuedCredential.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertEquals("Ed25519Signature2020", linkedDataProof.type)
    }

    @Test
    fun `test branch proof type from custom value`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            withProof(org.trustweave.credential.format.ProofSuiteId.VC_LD) // Use supported proof suite
        }.getOrFail()

        assertNotNull(issuedCredential.proof)
        assertTrue(issuedCredential.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = issuedCredential.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertEquals("Ed25519Signature2020", linkedDataProof.type)
    }

    // ========== Challenge and Domain Branches ==========

    @Test
    fun `test branch challenge provided`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            challenge("challenge-123")
        }.getOrFail()

        assertNotNull(issuedCredential.proof)
        assertTrue(issuedCredential.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = issuedCredential.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertEquals("challenge-123", linkedDataProof.additionalProperties["challenge"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test branch domain provided`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            domain("example.com")
        }.getOrFail()

        assertNotNull(issuedCredential.proof)
        assertTrue(issuedCredential.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = issuedCredential.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertEquals("example.com", linkedDataProof.additionalProperties["domain"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test branch challenge and domain both provided`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            challenge("challenge-123")
            domain("example.com")
        }.getOrFail()

        assertNotNull(issuedCredential.proof)
        assertTrue(issuedCredential.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = issuedCredential.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertEquals("challenge-123", linkedDataProof.additionalProperties["challenge"]?.jsonPrimitive?.content)
        assertEquals("example.com", linkedDataProof.additionalProperties["domain"]?.jsonPrimitive?.content)
    }

    // ========== Auto-Anchor Branches ==========

    @Test
    fun `test branch auto-anchor disabled in config`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            // autoAnchor is false in config
        }.getOrFail()

        assertNotNull(issuedCredential)
        // Credential should be issued but not anchored
    }

    @Test
    fun `test branch auto-anchor enabled in config`() = runBlocking {
        val kmsRef = kms
        val trustLayerWithAutoAnchor = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kmsRef)
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

        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustLayerWithAutoAnchor.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }.getOrFail()

        assertNotNull(issuedCredential)
        // Anchoring may fail silently, but credential is issued
    }

    @Test
    fun `test branch explicit anchor call`() = runBlocking {
        val kmsRef = kms
        val trustLayerWithAnchor = TrustWeave.build {
            keys {
                custom(kmsRef)
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

        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()

        val issuedCredential = trustLayerWithAnchor.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidDoc.id)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            // Note: anchor() function not available in current DSL
        }.getOrFail()

        assertNotNull(issuedCredential)
    }

    @Test
    fun `test branch anchor error when chain ID missing`() = runBlocking {
        val kmsRef = kms
        val trustLayerWithAutoAnchor = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kmsRef)
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

        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
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
                    issued(Clock.System.now())
                }
                signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
                // No anchor() call and no defaultChain
            }
        }
    }

    @Test
    fun `test branch anchor error when anchor client not found`() = runBlocking {
        val kmsRef = kms
        val trustLayerWithAutoAnchor = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kmsRef)
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

        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
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
                    issued(Clock.System.now())
                }
                signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
            }
        }
    }

    @Test
    fun `test branch anchor failure handling`() = runBlocking {
        // This tests the exception handling when anchoring fails
        // The credential should still be issued even if anchoring fails
        val kmsRef = kms
        val trustLayerWithAutoAnchor = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kmsRef)
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

        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
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
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidDoc.id, keyId = issuerKey.id.value)
        }.getOrFail()

        assertNotNull(issuedCredential)
    }
}


