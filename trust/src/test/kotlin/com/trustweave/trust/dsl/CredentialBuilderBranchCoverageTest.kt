package com.trustweave.trust.dsl

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.model.CredentialType
import com.trustweave.trust.dsl.credential.credential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for CredentialBuilder DSL.
 * Tests all conditional branches, error paths, and edge cases.
 */
class CredentialBuilderBranchCoverageTest {

    // ========== Type Branches ==========

    @Test
    fun `test branch VerifiableCredential auto-added when types empty`() {
        val credential = credential {
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            // No types specified
        }

        assertTrue(credential.type.contains(CredentialType.VerifiableCredential))
    }

    @Test
    fun `test branch VerifiableCredential auto-added when not in types`() {
        val credential = credential {
            type("DegreeCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        assertTrue(credential.type.contains(CredentialType.VerifiableCredential))
        assertTrue(credential.type.any { it.value == "DegreeCredential" })
    }

    @Test
    fun `test branch VerifiableCredential not duplicated when already present`() {
        val credential = credential {
            type("VerifiableCredential", "DegreeCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        assertEquals(2, credential.type.size)
        assertTrue(credential.type.contains(CredentialType.VerifiableCredential))
        assertTrue(credential.type.any { it.value == "DegreeCredential" })
    }

    @Test
    fun `test branch multiple types provided`() {
        val credential = credential {
            type("DegreeCredential", "BachelorDegreeCredential", "EducationCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        assertTrue(credential.type.contains(CredentialType.VerifiableCredential))
        assertTrue(credential.type.any { it.value == "DegreeCredential" })
        assertTrue(credential.type.any { it.value == "BachelorDegreeCredential" })
        assertTrue(credential.type.any { it.value == "EducationCredential" })
    }

    // ========== Subject Required Branches ==========

    @Test
    fun `test branch subject required error`() {
        assertFailsWith<IllegalStateException> {
            credential {
                type("PersonCredential")
                issuer("did:key:issuer")
                issued(Clock.System.now())
                // Missing subject
            }
        }
    }

    @Test
    fun `test branch subject with ID only`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        assertNotNull(credential.credentialSubject)
        assertEquals("did:key:subject", credential.credentialSubject.id.value)
    }

    @Test
    fun `test branch subject with properties`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "name" to "John Doe"
                "email" to "john@example.com"
            }
            issued(Clock.System.now())
        }

        assertNotNull(credential.credentialSubject)
        assertEquals("John Doe", credential.credentialSubject.claims["name"]?.jsonPrimitive?.content)
        assertEquals("john@example.com", credential.credentialSubject.claims["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test branch subject with nested objects`() {
        val credential = credential {
            type("DegreeCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "degree" {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science"
                }
            }
            issued(Clock.System.now())
        }

        assertNotNull(credential.credentialSubject)
        val degree = credential.credentialSubject.claims["degree"]?.jsonObject
        assertNotNull(degree)
        assertEquals("BachelorDegree", degree!!["type"]?.jsonPrimitive?.content)
    }

    // ========== Issuance Date Required Branches ==========

    @Test
    fun `test branch issuance date defaults to now`() {
        // Issuance date is optional and defaults to Clock.System.now()
        val before = Clock.System.now()
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            // Missing issued() - should default to now
        }
        val after = Clock.System.now()
        
        // Verify issuance date was set to a time between before and after
        assertTrue(
            credential.issuanceDate >= before && credential.issuanceDate <= after,
            "Issuance date should be set to a time between before and after"
        )
    }

    @Test
    fun `test branch issuance date provided`() {
        val now = Clock.System.now()
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(now)
        }

        assertEquals(now, credential.issuanceDate)
    }

    // ========== Issuer Required Branches ==========

    @Test
    fun `test branch issuer required error`() {
        assertFailsWith<IllegalStateException> {
            credential {
                type("PersonCredential")
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
                // Missing issuer
            }
        }
    }

    @Test
    fun `test branch issuer provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        assertEquals("did:key:issuer", credential.issuer.id.value)
    }

    // ========== Expiration Branches ==========

    @Test
    fun `test branch expiration not provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            // No expiration
        }

        assertNull(credential.expirationDate)
    }

    @Test
    fun `test branch expiration with Instant`() {
        val expiration = Clock.System.now().plus(kotlin.time.Duration.parse("P365D"))
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            expires(expiration)
        }

        assertEquals(expiration, credential.expirationDate)
    }

    @Test
    fun `test branch expiration with duration`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            expires(365.days)
        }

        assertNotNull(credential.expirationDate)
    }

    // ========== ID Branches ==========

    @Test
    fun `test branch ID not provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            // No ID
        }

        assertNull(credential.id)
    }

    @Test
    fun `test branch ID provided`() {
        val credential = credential {
            id("https://example.edu/credentials/123")
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        assertEquals("https://example.edu/credentials/123", credential.id?.value)
    }

    // ========== Schema Branches ==========

    @Test
    fun `test branch schema not provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            // No schema
        }

        assertNull(credential.credentialSchema)
    }

    @Test
    fun `test branch schema with default type and format`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            schema("https://example.com/schemas/person.json")
        }

        assertNotNull(credential.credentialSchema)
        assertEquals("https://example.com/schemas/person.json", credential.credentialSchema?.id?.value)
        assertEquals("JsonSchemaValidator2018", credential.credentialSchema?.type)
        // Format is not directly accessible, but we can verify it's set
    }

    @Test
    fun `test branch schema with custom type and format`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            schema(
                schemaId = "https://example.com/schemas/person.json",
                type = "JsonSchemaValidator2020"
            )
        }

        assertNotNull(credential.credentialSchema)
        assertEquals("JsonSchemaValidator2020", credential.credentialSchema?.type)
        // Format is not directly accessible, but we can verify it's set
    }

    // ========== Status Branches ==========

    @Test
    fun `test branch status not provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            // No status
        }

        assertNull(credential.credentialStatus)
    }

    @Test
    fun `test branch status with all fields`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            status {
                id("https://example.com/status/123")
                type("StatusList2021Entry")
                statusPurpose("revocation")
                statusListIndex("123")
                statusListCredential("https://example.com/status-list")
            }
        }

        assertNotNull(credential.credentialStatus)
        assertEquals("https://example.com/status/123", credential.credentialStatus?.id?.value)
        assertEquals("StatusList2021Entry", credential.credentialStatus?.type)
    }

    @Test
    fun `test branch status error when ID missing`() {
        assertFailsWith<IllegalStateException> {
            credential {
                type("PersonCredential")
                issuer("did:key:issuer")
                subject {
                    id("did:key:subject")
                }
                issued(Clock.System.now())
                status {
                    // Missing ID
                    type("StatusList2021Entry")
                }
            }
        }
    }

    // ========== Evidence Branches ==========

    @Test
    fun `test branch evidence not provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            // No evidence
        }

        assertNull(credential.evidence)
    }

    @Test
    fun `test branch single evidence`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            evidence {
                id("https://example.com/evidence/1")
                type("DocumentVerification")
                verifier("did:key:verifier")
            }
        }

        assertNotNull(credential.evidence)
        assertEquals(1, credential.evidence?.size)
    }

    @Test
    fun `test branch multiple evidence`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            evidence {
                id("https://example.com/evidence/1")
                type("DocumentVerification")
            }
            evidence {
                id("https://example.com/evidence/2")
                type("IdentityVerification")
            }
        }

        assertNotNull(credential.evidence)
        assertEquals(2, credential.evidence?.size)
    }

    @Test
    fun `test branch evidence with default type when not specified`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            evidence {
                id("https://example.com/evidence/1")
                // No type - should default to "Evidence"
            }
        }

        assertNotNull(credential.evidence)
        assertEquals(1, credential.evidence?.size)
        assertTrue(credential.evidence?.first()?.type?.contains("Evidence") == true)
    }

    // ========== Terms of Use Branches ==========

    @Test
    fun `test branch terms of use not provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            // No terms of use
        }

        assertNull(credential.termsOfUse)
    }

    @Test
    fun `test branch terms of use with all fields`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            termsOfUse {
                id("https://example.com/terms")
                type("IssuerPolicy")
                terms {
                    "payment" to "required"
                }
            }
        }

        assertNotNull(credential.termsOfUse)
        assertEquals("https://example.com/terms", credential.termsOfUse?.firstOrNull()?.id)
    }

    @Test
    fun `test branch terms of use with empty terms`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            termsOfUse {
                id("https://example.com/terms")
                // No terms block - should create empty object
            }
        }

        assertNotNull(credential.termsOfUse)
        assertNotNull(credential.termsOfUse?.firstOrNull()?.id)
    }

    // ========== Refresh Service Branches ==========

    @Test
    fun `test branch refresh service not provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            // No refresh service
        }

        assertNull(credential.refreshService)
    }

    @Test
    fun `test branch refresh service provided`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
            refreshService(
                id = "https://example.com/refresh",
                type = "CredentialRefreshService2020",
                endpoint = "https://example.com/refresh-endpoint"
            )
        }

        assertNotNull(credential.refreshService)
        assertEquals("https://example.com/refresh", credential.refreshService?.id?.value)
        assertEquals("CredentialRefreshService2020", credential.refreshService?.type)
    }

    // ========== Subject Property Value Type Branches ==========

    @Test
    fun `test branch subject property with String value`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "name" to "John Doe"
            }
            issued(Clock.System.now())
        }

        val subjectObj = buildJsonObject {
            put("id", credential.credentialSubject.id.value)
            credential.credentialSubject.claims.forEach { (key, value) -> put(key, value) }
        }
        assertEquals("John Doe", subjectObj["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test branch subject property with Number value`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "age" to 30
            }
            issued(Clock.System.now())
        }

        val subjectObj = buildJsonObject {
            put("id", credential.credentialSubject.id.value)
            credential.credentialSubject.claims.forEach { (key, value) -> put(key, value) }
        }
        assertEquals(30, subjectObj["age"]?.jsonPrimitive?.int)
    }

    @Test
    fun `test branch subject property with Boolean value`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "verified" to true
            }
            issued(Clock.System.now())
        }

        assertEquals(true, credential.credentialSubject.claims["verified"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `test branch subject property with null value`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "optionalField" to null
            }
            issued(Clock.System.now())
        }

        // Null values are stored as JsonNull
        val optionalField = credential.credentialSubject.claims["optionalField"]
        assertTrue(optionalField is kotlinx.serialization.json.JsonNull || optionalField == null)
    }

    @Test
    fun `test branch subject property with JsonElement value`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "custom" to {
                    "key" to "value"
                }
            }
            issued(Clock.System.now())
        }

        assertNotNull(credential.credentialSubject.claims["custom"]?.jsonObject)
    }

    // ========== Nested Object Builder Branches ==========

    @Test
    fun `test branch nested object with multiple properties`() {
        val credential = credential {
            type("DegreeCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "degree" {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science"
                    "university" to "Example University"
                    "gpa" to 3.8
                }
            }
            issued(Clock.System.now())
        }

        val degree = credential.credentialSubject.claims["degree"]?.jsonObject
        assertNotNull(degree)
        assertEquals("BachelorDegree", degree!!["type"]?.jsonPrimitive?.content)
        assertEquals("Bachelor of Science", degree["name"]?.jsonPrimitive?.content)
        assertEquals(3.8, degree["gpa"]?.jsonPrimitive?.double)
    }

    @Test
    fun `test branch deeply nested objects`() {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "address" {
                    "street" {
                        "number" to 123
                        "name" to "Main St"
                    }
                }
            }
            issued(Clock.System.now())
        }

        val address = credential.credentialSubject.claims["address"]?.jsonObject
        assertNotNull(address)
        val street = address!!["street"]?.jsonObject
        assertNotNull(street)
        assertEquals(123, street!!["number"]?.jsonPrimitive?.int)
    }
}


