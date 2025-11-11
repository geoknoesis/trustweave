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
 * Tests for IssuanceBuilder DSL.
 */
class IssuanceDslTest {

    private lateinit var trustLayer: TrustLayerConfig
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()
        assertNotNull(kms) { "KMS must be initialized before creating trust layer" }
        
        // Capture KMS reference for closure
        val kmsRef = kms
        
        trustLayer = trustLayer {
            keys {
                custom(kmsRef as Any)
                // Provide signer function directly to avoid reflection
                signer { data, keyId ->
                    kmsRef.sign(keyId, data)
                }
            }
            
            did {
                method("key") {
                    // Empty block is OK
                }
            }
            
            credentials {
                defaultProofType("Ed25519Signature2020")
            }
        }
    }

    @Test
    fun `test issuance with inline credential builder`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        val issuerDidId = issuerDidDoc.id
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidId)
                subject {
                    id("did:key:subject")
                    "name" to "John Doe"
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidId, keyId = issuerKey.id)
        }
        
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
        
        val issuedCredential = trustLayer.issue {
            credential(credential)
            by(issuerDid = issuerDidId, keyId = issuerKey.id)
        }
        
        assertNotNull(issuedCredential)
        assertNotNull(issuedCredential.proof)
    }

    @Test
    fun `test issuance with custom proof type`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        val issuerDidId = issuerDidDoc.id
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidId)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidId, keyId = issuerKey.id)
            withProof("Ed25519Signature2020")
        }
        
        assertNotNull(issuedCredential.proof)
        assertEquals("Ed25519Signature2020", issuedCredential.proof?.type)
    }

    @Test
    fun `test issuance with challenge and domain`() = runBlocking {
        val issuerKey: KeyHandle = kms.generateKey("Ed25519", emptyMap())
        val didMethod = DidKeyMockMethod(kms)
        val issuerDidDoc: DidDocument = didMethod.createDid()
        val issuerDidId = issuerDidDoc.id
        
        val issuedCredential = trustLayer.issue {
            credential {
                type("PersonCredential")
                issuer(issuerDidId)
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
            }
            by(issuerDid = issuerDidId, keyId = issuerKey.id)
            challenge("challenge-123")
            domain("example.com")
        }
        
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
            trustLayer.issue {
                // Missing credential
                by(issuerDid = issuerDidId, keyId = issuerKey.id)
            }
        }
    }

    @Test
    fun `test issuance requires issuer DID and key ID`() = runBlocking {
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
}
