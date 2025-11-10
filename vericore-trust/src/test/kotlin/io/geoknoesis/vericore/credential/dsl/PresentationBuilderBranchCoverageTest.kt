package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.presentation.PresentationService
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for PresentationBuilder DSL.
 * Tests all conditional branches, error paths, and edge cases.
 */
class PresentationBuilderBranchCoverageTest {

    private lateinit var presentationService: PresentationService

    @BeforeEach
    fun setup() {
        val proofGenerator = Ed25519ProofGenerator(
            signer = { _, _ -> "mock-signature-${UUID.randomUUID()}".toByteArray() },
            getPublicKeyId = { "did:key:holder#key-1" }
        )
        val proofRegistry = ProofGeneratorRegistry().apply { register(proofGenerator) }
        presentationService = PresentationService(
            proofGenerator = proofGenerator,
            proofRegistry = proofRegistry
        )
    }

    // ========== Credentials Required Branches ==========

    @Test
    fun `test branch credentials required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            presentation(presentationService) {
                holder("did:key:holder")
                // Missing credentials
            }
        }
    }

    @Test
    fun `test branch single credential provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
                "name" to "John Doe"
            }
            issued(Instant.now())
        }
        
        val presentation = presentation(presentationService) {
            credentials(credential)
            holder("did:key:holder")
        }
        
        assertNotNull(presentation)
        assertEquals(1, presentation.verifiableCredential.size)
    }

    @Test
    fun `test branch multiple credentials provided`() = runBlocking {
        val credential1 = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val credential2 = credential {
            type("DegreeCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation(presentationService) {
            credentials(credential1, credential2)
            holder("did:key:holder")
        }
        
        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test branch credentials from list`() = runBlocking {
        val credentials = listOf(
            credential {
                type("PersonCredential")
                issuer("did:key:issuer")
                subject {
                    id("did:key:holder")
                }
                issued(Instant.now())
            },
            credential {
                type("DegreeCredential")
                issuer("did:key:issuer")
                subject {
                    id("did:key:holder")
                }
                issued(Instant.now())
            }
        )
        
        val presentation = presentation(presentationService) {
            credentials(credentials)
            holder("did:key:holder")
        }
        
        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    // ========== Holder DID Required Branches ==========

    @Test
    fun `test branch holder DID required error`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        assertFailsWith<IllegalStateException> {
            presentation(presentationService) {
                credentials(credential)
                // Missing holder
            }
        }
    }

    @Test
    fun `test branch holder DID provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation(presentationService) {
            credentials(credential)
            holder("did:key:holder")
        }
        
        assertNotNull(presentation)
        assertEquals("did:key:holder", presentation.holder)
    }

    // ========== Challenge Branches ==========

    @Test
    fun `test branch challenge not provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            // No challenge
        }
        
        assertNotNull(presentation)
        assertNull(presentation.challenge)
    }

    @Test
    fun `test branch challenge provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            challenge("verification-challenge-123")
        }
        
        assertNotNull(presentation)
        assertEquals("verification-challenge-123", presentation.challenge)
    }

    // ========== Domain Branches ==========

    @Test
    fun `test branch domain not provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            // No domain
        }
        
        assertNotNull(presentation)
        assertNull(presentation.domain)
    }

    @Test
    fun `test branch domain provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            domain("example.com")
        }
        
        assertNotNull(presentation)
        assertEquals("example.com", presentation.domain)
    }

    // ========== Proof Type Branches ==========

    @Test
    fun `test branch proof type default`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            // Uses default proof type
        }
        
        assertNotNull(presentation)
    }

    @Test
    fun `test branch proof type custom`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            proofType("JsonWebSignature2020")
        }
        
        assertNotNull(presentation)
    }

    // ========== Key ID Branches ==========

    @Test
    fun `test branch key ID not provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            // No keyId
        }
        
        assertNotNull(presentation)
    }

    @Test
    fun `test branch key ID provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            keyId("key-1")
        }
        
        assertNotNull(presentation)
    }

    // ========== Selective Disclosure Branches ==========

    @Test
    fun `test branch selective disclosure disabled by default`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
                "name" to "John Doe"
                "email" to "john@example.com"
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            // No selective disclosure
        }
        
        assertNotNull(presentation)
    }

    @Test
    fun `test branch selective disclosure enabled with reveal`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
                "name" to "John Doe"
                "email" to "john@example.com"
                "ssn" to "123-45-6789"
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            selectiveDisclosure {
                reveal("name", "email")
            }
        }
        
        assertNotNull(presentation)
    }

    @Test
    fun `test branch selective disclosure with hide`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
                "name" to "John Doe"
                "email" to "john@example.com"
                "ssn" to "123-45-6789"
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            selectiveDisclosure {
                reveal("name", "email")
                hide("ssn")
            }
        }
        
        assertNotNull(presentation)
    }

    // ========== Combined Options Branches ==========

    @Test
    fun `test branch all presentation options provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
                "name" to "John Doe"
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential)
            holder("did:key:holder")
            challenge("challenge-123")
            domain("example.com")
            proofType("Ed25519Signature2020")
            keyId("key-1")
            selectiveDisclosure {
                reveal("name")
            }
        }
        
        assertNotNull(presentation)
        assertEquals("challenge-123", presentation.challenge)
        assertEquals("example.com", presentation.domain)
    }
}

