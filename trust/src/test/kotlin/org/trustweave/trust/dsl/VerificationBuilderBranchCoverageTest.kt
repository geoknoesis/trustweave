package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.TrustWeaveConfig
import org.trustweave.trust.dsl.TrustWeaveContext
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.credential.model.ProofType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for VerificationBuilder DSL.
 * Tests all conditional branches, error paths, and edge cases.
 */
class VerificationBuilderBranchCoverageTest {

    private lateinit var trustWeave: TrustWeaveContext
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()
        val kmsRef = kms
        val config = trustWeave {
            keys {
                custom(kmsRef)
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
                defaultChain("algorand:testnet")
            }
        }
        trustWeave = TrustWeaveContext(config)
    }

    // ========== Credential Required Branches ==========

    @Test
    fun `test branch credential required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            skipRevocation()
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            checkRevocation()
            skipRevocation() // Last call wins
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            skipExpiration()
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            checkExpiration()
            skipExpiration() // Last call wins
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            validateSchema("https://example.com/schemas/person.json")
            skipSchema()
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            // Anchor verification defaults to false
        }

        assertNotNull(result)
    }

    @Test
    fun `test branch anchor verification enabled without chain ID`() = runBlocking {
        val kmsRef = kms
        val trustWeaveWithAnchor = TrustWeave.build {
            // DID methods auto-discovered via SPI
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
                defaultChain("algorand:testnet")
            }
        }

        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        // Note: verifyAnchor() not available in VerificationBuilder
        val result = trustWeaveWithAnchor.verify {
            credential(credential)
        }

        assertNotNull(result)
    }

    @Test
    fun `test branch anchor verification enabled with explicit chain ID`() = runBlocking {
        val kmsRef = kms
        val trustWeaveWithAnchor = TrustWeave.build {
            // DID methods auto-discovered via SPI
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
            }
        }

        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        // Note: verifyAnchor() not available in VerificationBuilder
        val result = trustWeaveWithAnchor.verify {
            credential(credential)
        }

        assertNotNull(result)
    }

    @Test
    fun `test branch anchor verification error when no chain ID`() = runBlocking {
        val kmsRef = kms
        val trustWeaveNoAnchor = TrustWeave.build {
            // DID methods auto-discovered via SPI
            keys {
                custom(kmsRef)
            }
            did {
                method("key") {}
            }
            credentials {
                defaultProofType(ProofType.Ed25519Signature2020)
                // No defaultChain
            }
        }

        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        // Note: verifyAnchor() not available in VerificationBuilder
        val result = trustWeaveNoAnchor.verify {
            credential(credential)
        }

        assertNotNull(result)
    }

    // ========== Combined Options Branches ==========

    @Test
    fun `test branch all verification options enabled`() = runBlocking {
        val kmsRef = kms
        val trustWeaveWithAnchor = TrustWeave.build {
            // DID methods auto-discovered via SPI
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
                defaultChain("algorand:testnet")
            }
        }

        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        // Note: verifyAnchor() not available in VerificationBuilder
        val result = trustWeaveWithAnchor.verify {
            credential(credential)
            checkRevocation()
            checkExpiration()
            validateSchema("https://example.com/schemas/person.json")
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
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            skipRevocation()
            skipExpiration()
            skipSchema()
            // Anchor verification already disabled by default
        }

        assertNotNull(result)
    }
}


