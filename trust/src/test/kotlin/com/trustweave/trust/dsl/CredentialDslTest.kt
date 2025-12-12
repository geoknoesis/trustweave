package com.trustweave.trust.dsl

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.trust.dsl.credential.credential
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
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
            issued(Clock.System.now())
        }

        assertNotNull(credential)
        assertTrue(credential.type.contains(com.trustweave.credential.model.CredentialType.VerifiableCredential))
        assertTrue(credential.type.any { it.value == "PersonCredential" })
        assertEquals("did:key:issuer", credential.issuer.id.value)
        assertEquals("did:key:subject", credential.credentialSubject.id.value)
        assertEquals("John Doe", credential.credentialSubject.claims["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test credential builder with all fields`() {
        val now = Clock.System.now()
        val expires = now.plus((365 * 10).days)

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
        assertEquals("https://example.edu/credentials/123", credential.id?.value)
        assertTrue(credential.type.contains(com.trustweave.credential.model.CredentialType.VerifiableCredential))
        assertTrue(credential.type.any { it.value == "DegreeCredential" })
        assertTrue(credential.type.any { it.value == "BachelorDegreeCredential" })
        assertEquals("did:key:university", credential.issuer.id.value)
        assertNotNull(credential.expirationDate)
        assertNotNull(credential.credentialSchema)
        assertEquals("https://example.edu/schemas/degree.json", credential.credentialSchema?.id?.value)

        val degree = credential.credentialSubject.claims["degree"]?.jsonObject
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
            issued(Clock.System.now())
            expires(365.days)
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
            issued(Clock.System.now())
            status {
                id("https://example.com/status/1")
                type("StatusList2021Entry")
                statusPurpose("revocation")
                statusListIndex("1")
            }
        }

        assertNotNull(credential.credentialStatus)
        assertEquals("https://example.com/status/1", credential.credentialStatus?.id?.value)
        assertEquals("StatusList2021Entry", credential.credentialStatus?.type)
        assertEquals(com.trustweave.credential.model.StatusPurpose.REVOCATION, credential.credentialStatus?.statusPurpose)
    }

    @Test
    fun `test credential builder with evidence`() {
        val credential = credential {
            type("Credential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            evidence {
                id("evidence-1")
                type("DocumentVerification")
                document {
                    "documentType" to "passport"
                    "verifiedBy" to "did:key:verifier"
                }
                verifier("did:key:verifier")
                date(Clock.System.now().toString())
            }
        }

        assertNotNull(credential.evidence)
        assertEquals(1, credential.evidence?.size)
        assertEquals("evidence-1", credential.evidence?.first()?.id?.value)
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
                issued(Clock.System.now())
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
                issued(Clock.System.now())
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
            issued(Clock.System.now())
        }

        assertTrue(credential.type.contains(com.trustweave.credential.model.CredentialType.VerifiableCredential))
        assertTrue(credential.type.any { it.value == "PersonCredential" })
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
            issued(Clock.System.now())
        }

        val personalInfo = credential.credentialSubject.claims["personalInfo"]?.jsonObject
        assertNotNull(personalInfo)
        assertEquals("John", personalInfo["firstName"]?.jsonPrimitive?.content)
        assertEquals("Doe", personalInfo["lastName"]?.jsonPrimitive?.content)

        val address = credential.credentialSubject.claims["address"]?.jsonObject
        assertNotNull(address)
        assertEquals("123 Main St", address["street"]?.jsonPrimitive?.content)
    }
}

