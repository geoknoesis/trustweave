package com.trustweave.credential.dsl

import com.trustweave.credential.models.VerifiableCredential
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

/**
 * Tests for CredentialBuilder DSL.
 */
class CredentialDslTest {

    @Test
    fun `test credential builder with minimal fields`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "name" to "John Doe"
            }
            issued(Instant.now())
        }
        
        assertNotNull(credential)
        assertTrue(credential.type.contains("VerifiableCredential"))
        assertTrue(credential.type.contains("PersonCredential"))
        assertEquals("did:key:issuer", credential.issuer)
        assertEquals("did:key:subject", credential.credentialSubject.jsonObject["id"]?.jsonPrimitive?.content)
        assertEquals("John Doe", credential.credentialSubject.jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test credential builder with all fields`() {
        val now = Instant.now()
        val expires = now.plus(365 * 10, ChronoUnit.DAYS)
        
        val credential = credential {
            id("https://example.edu/credentials/123")
            type("DegreeCredential", "BachelorDegreeCredential")
            issuer("did:key:university")
            subject {
                id("did:key:student")
                "degree" {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science"
                    "university" to "Example University"
                }
            }
            issued(now)
            expires(expires)
            schema("https://example.edu/schemas/degree.json")
        }
        
        assertNotNull(credential)
        assertEquals("https://example.edu/credentials/123", credential.id)
        assertTrue(credential.type.contains("VerifiableCredential"))
        assertTrue(credential.type.contains("DegreeCredential"))
        assertTrue(credential.type.contains("BachelorDegreeCredential"))
        assertEquals("did:key:university", credential.issuer)
        assertNotNull(credential.expirationDate)
        assertNotNull(credential.credentialSchema)
        assertEquals("https://example.edu/schemas/degree.json", credential.credentialSchema?.id)
        
        val degree = credential.credentialSubject.jsonObject["degree"]?.jsonObject
        assertNotNull(degree)
        assertEquals("BachelorDegree", degree["type"]?.jsonPrimitive?.content)
        assertEquals("Bachelor of Science", degree["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test credential builder with expiration duration`() {
        val credential = credential {
            type("Credential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
            expires(365, ChronoUnit.DAYS)
        }
        
        assertNotNull(credential.expirationDate)
    }

    @Test
    fun `test credential builder with status`() {
        val credential = credential {
            type("Credential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
            status {
                id("https://example.com/status/1")
                type("StatusList2021Entry")
                statusPurpose("revocation")
                statusListIndex("1")
            }
        }
        
        assertNotNull(credential.credentialStatus)
        assertEquals("https://example.com/status/1", credential.credentialStatus?.id)
        assertEquals("StatusList2021Entry", credential.credentialStatus?.type)
        assertEquals("revocation", credential.credentialStatus?.statusPurpose)
    }

    @Test
    fun `test credential builder with evidence`() {
        val credential = credential {
            type("Credential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
            evidence {
                id("evidence-1")
                type("DocumentVerification")
                document {
                    "documentType" to "passport"
                    "verifiedBy" to "did:key:verifier"
                }
                verifier("did:key:verifier")
                date(Instant.now().toString())
            }
        }
        
        assertNotNull(credential.evidence)
        assertEquals(1, credential.evidence?.size)
        assertEquals("evidence-1", credential.evidence?.first()?.id)
        assertTrue(credential.evidence?.first()?.type?.contains("DocumentVerification") == true)
    }

    @Test
    fun `test credential builder requires issuer`() {
        assertFailsWith<IllegalStateException> {
            credential {
                type("Credential")
                subject {
                    id("did:key:subject")
                }
                issued(Instant.now())
                // Missing issuer
            }
        }
    }

    @Test
    fun `test credential builder requires subject`() {
        assertFailsWith<IllegalStateException> {
            credential {
                type("Credential")
                issuer("did:key:issuer")
                issued(Instant.now())
                // Missing subject
            }
        }
    }

    @Test
    fun `test credential builder requires issuance date`() {
        assertFailsWith<IllegalStateException> {
            credential {
                type("Credential")
                issuer("did:key:issuer")
                subject {
                    id("did:key:subject")
                }
                // Missing issued date
            }
        }
    }

    @Test
    fun `test credential builder automatically adds VerifiableCredential type`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Instant.now())
        }
        
        assertTrue(credential.type.contains("VerifiableCredential"))
        assertTrue(credential.type.contains("PersonCredential"))
    }

    @Test
    fun `test credential builder with nested subject properties`() {
        val credential = credential {
            type("Credential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "personalInfo" {
                    "firstName" to "John"
                    "lastName" to "Doe"
                    "email" to "john@example.com"
                }
                "address" {
                    "street" to "123 Main St"
                    "city" to "Anytown"
                }
            }
            issued(Instant.now())
        }
        
        val personalInfo = credential.credentialSubject.jsonObject["personalInfo"]?.jsonObject
        assertNotNull(personalInfo)
        assertEquals("John", personalInfo["firstName"]?.jsonPrimitive?.content)
        assertEquals("Doe", personalInfo["lastName"]?.jsonPrimitive?.content)
        
        val address = credential.credentialSubject.jsonObject["address"]?.jsonObject
        assertNotNull(address)
        assertEquals("123 Main St", address["street"]?.jsonPrimitive?.content)
    }
}

