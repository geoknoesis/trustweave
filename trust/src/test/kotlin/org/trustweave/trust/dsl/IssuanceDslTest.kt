package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.services.TestkitDidMethodFactory
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.createTestCredentialService
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.credential.model.ProofType
import org.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Clock
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/**
 * Tests for IssuanceBuilder DSL.
 */
class IssuanceDslTest {

    private lateinit var trustWeave: TrustWeave
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()
        assertNotNull(kms) { "KMS must be initialized before creating trust layer" }

        // Capture KMS reference for closure
        val kmsRef = kms

        // Create DID resolver that uses the DID registry from TrustWeave
        // We'll create a temporary TrustWeave to get the DID registry, then rebuild with CredentialService
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
                method("key") {
                    // Empty block is OK
                }
            }

            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
            }
            // Set CredentialService as issuer for issuance builder
            issuer(credentialService)
        }
    }

    @Test
    fun `test issuance with inline credential builder`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        val issuerDidId = issuerDidDoc.id

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidId)
                subject {
                    id("did:key:subject")
                    "name" to "John Doe"
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidId, keyId = issuerKey.id.value)
        }.getOrFail()

        assertNotNull(issuedCredential)
        assertTrue(issuedCredential.type.any { it.value == "PersonCredential" })
        assertEquals(issuerDidId.value, issuedCredential.issuer.id.value)
        assertNotNull(issuedCredential.proof)
        assertTrue(issuedCredential.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = issuedCredential.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertEquals("Ed25519Signature2020", linkedDataProof.type)
    }

    @Test
    fun `test issuance with pre-built credential`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        val issuerDidId = issuerDidDoc.id

        val credential = credential {
            type("PersonCredential")
            issuer(issuerDidId)
            subject {
                id("did:key:subject")
                "name" to "John Doe"
            }
            issued(Clock.System.now())
        }

        val issuedCredential = trustWeave.issue {
            credential(credential)
            signedBy(issuerDid = issuerDidId, keyId = issuerKey.id.value)
        }.getOrFail()

        assertNotNull(issuedCredential)
        assertNotNull(issuedCredential.proof)
    }

    @Test
    fun `test issuance with custom proof type`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        val issuerDidId = issuerDidDoc.id

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidId)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidId, keyId = issuerKey.id.value)
            withProof(org.trustweave.credential.format.ProofSuiteId.VC_LD)
        }.getOrFail()

        assertNotNull(issuedCredential.proof)
        assertTrue(issuedCredential.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = issuedCredential.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertEquals("Ed25519Signature2020", linkedDataProof.type)
    }

    @Test
    fun `test issuance with challenge and domain`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        val issuerDidId = issuerDidDoc.id

        val issuedCredential = trustWeave.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidId)
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = issuerDidId, keyId = issuerKey.id.value)
            challenge("challenge-123")
            domain("example.com")
        }.getOrFail()

        assertNotNull(issuedCredential.proof)
        assertTrue(issuedCredential.proof is org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof)
        val linkedDataProof = issuedCredential.proof as org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof
        assertEquals("challenge-123", linkedDataProof.additionalProperties["challenge"]?.jsonPrimitive?.content)
        assertEquals("example.com", linkedDataProof.additionalProperties["domain"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test issuance requires credential`() = runBlocking {
        val issuerKey: KeyHandle = when (val result = kms.generateKey("Ed25519", emptyMap())) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        val issuerDidId = issuerDidDoc.id

        assertFailsWith<IllegalStateException> {
            trustWeave.issue {
                // Missing credential
                signedBy(issuerDid = issuerDidId, keyId = issuerKey.id.value)
            }
        }
    }

    @Test
    fun `test issuance requires issuer DID and key ID`() = runBlocking {
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
}

