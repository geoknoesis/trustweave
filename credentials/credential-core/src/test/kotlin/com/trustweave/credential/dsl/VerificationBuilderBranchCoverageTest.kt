package com.trustweave.credential.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for VerificationBuilder DSL.
 * Tests all conditional branches, error paths, and edge cases.
 */
class VerificationBuilderBranchCoverageTest {

    private lateinit var trustLayer: TrustLayerConfig
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()
        trustLayer = trustLayer {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType("Ed25519Signature2020")
                defaultChain("algorand:testnet")
            }
        }
    }

    // ========== Credential Required Branches ==========

    @Test
    fun `test branch credential required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.verify {
                // Missing credential
                checkRevocation()
            }
        }
    }

    @Test
    fun `test branch credential provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
        }
        
        assertNotNull(result)
    }

    // ========== Revocation Check Branches ==========

    @Test
    fun `test branch revocation check enabled by default`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            // checkRevocation defaults to true
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch revocation check explicitly enabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            checkRevocation()
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch revocation check disabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            skipRevocationCheck()
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch revocation check toggle multiple times`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            checkRevocation()
            skipRevocationCheck() // Last call wins
        }
        
        assertNotNull(result)
    }

    // ========== Expiration Check Branches ==========

    @Test
    fun `test branch expiration check enabled by default`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            // checkExpiration defaults to true
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch expiration check explicitly enabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            checkExpiration()
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch expiration check disabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            skipExpirationCheck()
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch expiration check toggle multiple times`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            checkExpiration()
            skipExpirationCheck() // Last call wins
        }
        
        assertNotNull(result)
    }

    // ========== Schema Validation Branches ==========

    @Test
    fun `test branch schema validation disabled by default`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            // Schema validation defaults to false
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch schema validation enabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            validateSchema("https://example.com/schemas/person.json")
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch schema validation disabled after enabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            validateSchema("https://example.com/schemas/person.json")
            skipSchemaValidation()
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch schema validation with schema ID`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            validateSchema("https://example.com/schemas/person.json")
        }
        
        assertNotNull(result)
    }

    // ========== Anchor Verification Branches ==========

    @Test
    fun `test branch anchor verification disabled by default`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            // Anchor verification defaults to false
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch anchor verification enabled without chain ID`() = runBlocking {
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
                defaultChain("algorand:testnet")
            }
        }
        
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayerWithAnchor.verify {
            credential(credential)
            verifyAnchor() // Uses defaultChain from config
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch anchor verification enabled with explicit chain ID`() = runBlocking {
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
            }
        }
        
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayerWithAnchor.verify {
            credential(credential)
            verifyAnchor("algorand:testnet")
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch anchor verification error when no chain ID`() = runBlocking {
        val trustLayerNoAnchor = trustLayer {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType("Ed25519Signature2020")
                // No defaultChain
            }
        }
        
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        // Should still work - chainId is optional in options
        val result = trustLayerNoAnchor.verify {
            credential(credential)
            verifyAnchor() // No chain ID provided
        }
        
        assertNotNull(result)
    }

    // ========== Combined Options Branches ==========

    @Test
    fun `test branch all verification options enabled`() = runBlocking {
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
                defaultChain("algorand:testnet")
            }
        }
        
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayerWithAnchor.verify {
            credential(credential)
            checkRevocation()
            checkExpiration()
            validateSchema("https://example.com/schemas/person.json")
            verifyAnchor("algorand:testnet")
        }
        
        assertNotNull(result)
    }

    @Test
    fun `test branch all verification options disabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        val result = trustLayer.verify {
            credential(credential)
            skipRevocationCheck()
            skipExpirationCheck()
            skipSchemaValidation()
            // Anchor verification already disabled by default
        }
        
        assertNotNull(result)
    }
}

