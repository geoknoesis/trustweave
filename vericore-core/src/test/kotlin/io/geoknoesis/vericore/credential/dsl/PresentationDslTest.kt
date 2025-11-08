package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Tests for PresentationBuilder DSL.
 */
class PresentationDslTest {

    @Test
    fun `test presentation creation with single credential`() = runBlocking {
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
        }
        
        assertNotNull(presentation)
        assertEquals("did:key:holder", presentation.holder)
        assertEquals(1, presentation.verifiableCredential.size)
        assertTrue(presentation.type.contains("VerifiablePresentation"))
    }

    @Test
    fun `test presentation creation with multiple credentials`() = runBlocking {
        val credential1 = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
                "name" to "John Doe"
            }
            issued(Instant.now())
        }
        
        val credential2 = credential {
            type("DegreeCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
                "degree" {
                    "type" to "BachelorDegree"
                }
            }
            issued(Instant.now())
        }
        
        val presentation = presentation {
            credentials(credential1, credential2)
            holder("did:key:holder")
        }
        
        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test presentation creation with challenge`() = runBlocking {
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

    @Test
    fun `test presentation creation with domain`() = runBlocking {
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

    @Test
    fun `test presentation creation with custom proof type`() = runBlocking {
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
            proofType("Ed25519Signature2020")
        }
        
        assertNotNull(presentation)
        // Proof type is used during presentation creation
    }

    @Test
    fun `test presentation creation requires credentials`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            presentation {
                holder("did:key:holder")
                // Missing credentials
            }
        }
    }

    @Test
    fun `test presentation creation requires holder`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Instant.now())
        }
        
        assertFailsWith<IllegalStateException> {
            presentation {
                credentials(credential)
                // Missing holder
            }
        }
    }

    @Test
    fun `test presentation with selective disclosure`() = runBlocking {
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
        // Selective disclosure fields are configured
    }
}

