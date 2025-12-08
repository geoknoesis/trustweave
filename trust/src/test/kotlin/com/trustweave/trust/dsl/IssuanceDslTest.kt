package com.trustweave.trust.dsl

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.did.DidDocument
import com.trustweave.kms.KeyHandle
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.credential
import com.trustweave.credential.model.ProofType
import com.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
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

        trustWeave = TrustWeave.build {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kmsRef)
                // Provide signer function directly to avoid reflection
                signer { data, keyId ->
                    kmsRef.sign(com.trustweave.core.identifiers.KeyId(keyId), data)
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
        }
    }

    @Test
    fun `test issuance with inline credential builder`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
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
        assertTrue(issuedCredential.type.contains("PersonCredential"))
        assertEquals(issuerDidId, issuedCredential.issuer)
        assertNotNull(issuedCredential.proof)
        assertEquals("Ed25519Signature2020", issuedCredential.proof?.type)
    }

    @Test
    fun `test issuance with pre-built credential`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
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
            issued(Instant.now())
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
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
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
            withProof(ProofType.Ed25519Signature2020)
        }.getOrFail()

        assertNotNull(issuedCredential.proof)
        assertEquals("Ed25519Signature2020", issuedCredential.proof?.type)
    }

    @Test
    fun `test issuance with challenge and domain`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
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
        assertEquals("challenge-123", issuedCredential.proof?.challenge)
        assertEquals("example.com", issuedCredential.proof?.domain)
    }

    @Test
    fun `test issuance requires credential`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
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

