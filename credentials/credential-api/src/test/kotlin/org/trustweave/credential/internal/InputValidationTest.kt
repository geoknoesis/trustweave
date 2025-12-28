package org.trustweave.credential.internal

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.CredentialSchema
import org.trustweave.credential.model.CredentialType
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Comprehensive tests for InputValidation utility.
 */
class InputValidationTest {
    
    @Test
    fun `test validateCredentialId with valid ID`() {
        val validId = CredentialId("https://example.com/credentials/123")
        // Should not throw
        InputValidation.validateCredentialId(validId)
    }
    
    @Test
    fun `test validateCredentialId with ID exceeding max length`() {
        val longId = CredentialId("a".repeat(SecurityConstants.MAX_CREDENTIAL_ID_LENGTH + 1))
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateCredentialId(longId)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
        assert(exception.message?.contains(SecurityConstants.MAX_CREDENTIAL_ID_LENGTH.toString()) == true)
    }
    
    @Test
    fun `test validateCredentialId with ID at max length`() {
        val maxLengthId = CredentialId("a".repeat(SecurityConstants.MAX_CREDENTIAL_ID_LENGTH))
        // Should not throw
        InputValidation.validateCredentialId(maxLengthId)
    }
    
    @Test
    fun `test validateDid with valid DID`() {
        val validDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        // Should not throw
        InputValidation.validateDid(validDid)
    }
    
    @Test
    fun `test validateDid with DID exceeding max length`() {
        val longDid = Did("did:key:${"a".repeat(SecurityConstants.MAX_DID_LENGTH)}")
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateDid(longDid)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validateIri with valid IRI`() {
        val validIri = Iri("https://example.com/subject")
        // Should not throw
        InputValidation.validateIri(validIri)
    }
    
    @Test
    fun `test validateIri with IRI exceeding max length`() {
        val longIri = Iri("https://example.com/${"a".repeat(SecurityConstants.MAX_DID_LENGTH)}")
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateIri(longIri)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validateSchemaId with valid schema ID`() {
        val validSchemaId = "https://example.com/schema/123"
        // Should not throw
        InputValidation.validateSchemaId(validSchemaId)
    }
    
    @Test
    fun `test validateSchemaId with schema ID exceeding max length`() {
        val longSchemaId = "a".repeat(SecurityConstants.MAX_SCHEMA_ID_LENGTH + 1)
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateSchemaId(longSchemaId)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validateVerificationMethodId with valid ID`() {
        val validId = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1"
        // Should not throw
        InputValidation.validateVerificationMethodId(validId)
    }
    
    @Test
    fun `test validateVerificationMethodId with ID exceeding max length`() {
        val longId = "a".repeat(SecurityConstants.MAX_VERIFICATION_METHOD_ID_LENGTH + 1)
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateVerificationMethodId(longId)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validateCredentialClaimsCount with valid count`() {
        val claims = (1..100).associate { "claim$it" to JsonPrimitive("value$it") }
        val credential = createTestCredential(claims = claims)
        // Should not throw
        InputValidation.validateCredentialClaimsCount(credential)
    }
    
    @Test
    fun `test validateCredentialClaimsCount exceeding max`() {
        val claims = (1..(SecurityConstants.MAX_CLAIMS_PER_CREDENTIAL + 1)).associate { 
            "claim$it" to JsonPrimitive("value$it") 
        }
        val credential = createTestCredential(claims = claims)
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateCredentialClaimsCount(credential)
        }
        assert(exception.message?.contains("exceeds maximum claims count") == true)
    }
    
    @Test
    fun `test validateCredentialClaimsCount at max count`() {
        val claims = (1..SecurityConstants.MAX_CLAIMS_PER_CREDENTIAL).associate { 
            "claim$it" to JsonPrimitive("value$it") 
        }
        val credential = createTestCredential(claims = claims)
        // Should not throw
        InputValidation.validateCredentialClaimsCount(credential)
    }
    
    @Test
    fun `test validatePresentationCredentialsCount with valid count`() {
        val credentials = (1..50).map { createTestCredential() }
        val presentation = createTestPresentation(credentials)
        // Should not throw
        InputValidation.validatePresentationCredentialsCount(presentation)
    }
    
    @Test
    fun `test validatePresentationCredentialsCount exceeding max`() {
        val credentials = (1..(SecurityConstants.MAX_CREDENTIALS_PER_PRESENTATION + 1)).map { 
            createTestCredential() 
        }
        val presentation = createTestPresentation(credentials)
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validatePresentationCredentialsCount(presentation)
        }
        assert(exception.message?.contains("exceeds maximum credentials count") == true)
    }
    
    @Test
    fun `test validatePresentationCredentialsCount at max count`() {
        val credentials = (1..SecurityConstants.MAX_CREDENTIALS_PER_PRESENTATION).map { 
            createTestCredential() 
        }
        val presentation = createTestPresentation(credentials)
        // Should not throw
        InputValidation.validatePresentationCredentialsCount(presentation)
    }
    
    @Test
    fun `test validateCredentialStructure with valid credential`() {
        val credential = createTestCredential()
        // Should not throw
        InputValidation.validateCredentialStructure(credential)
    }
    
    @Test
    fun `test validateCredentialStructure with invalid ID length`() {
        val longId = CredentialId("a".repeat(SecurityConstants.MAX_CREDENTIAL_ID_LENGTH + 1))
        val credential = createTestCredential(id = longId)
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateCredentialStructure(credential)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validateCredentialStructure with invalid issuer IRI length`() {
        val longIri = Iri("did:key:${"a".repeat(SecurityConstants.MAX_DID_LENGTH)}")
        val credential = createTestCredential(issuer = Issuer.IriIssuer(longIri))
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateCredentialStructure(credential)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validateCredentialStructure with invalid subject IRI length`() {
        val longIri = Iri("did:key:${"a".repeat(SecurityConstants.MAX_DID_LENGTH)}")
        val credential = createTestCredential(
            subject = CredentialSubject(
                id = longIri,
                claims = emptyMap()
            )
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateCredentialStructure(credential)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validateCredentialStructure with invalid schema ID length`() {
        val longSchemaId = "a".repeat(SecurityConstants.MAX_SCHEMA_ID_LENGTH + 1)
        val credential = createTestCredential(
            schemaId = CredentialSchema(
                id = SchemaId(longSchemaId),
                type = "JsonSchemaValidator2018"
            )
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validateCredentialStructure(credential)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validatePresentationStructure with valid presentation`() {
        val presentation = createTestPresentation(listOf(createTestCredential()))
        // Should not throw
        InputValidation.validatePresentationStructure(presentation)
    }
    
    @Test
    fun `test validatePresentationStructure with invalid holder IRI length`() {
        val longIri = Iri("did:key:${"a".repeat(SecurityConstants.MAX_DID_LENGTH)}")
        val presentation = createTestPresentation(
            listOf(createTestCredential()),
            holder = longIri
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validatePresentationStructure(presentation)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    @Test
    fun `test validatePresentationStructure with invalid credential in presentation`() {
        val invalidCredential = createTestCredential(
            id = CredentialId("a".repeat(SecurityConstants.MAX_CREDENTIAL_ID_LENGTH + 1))
        )
        val presentation = createTestPresentation(listOf(invalidCredential))
        val exception = assertFailsWith<IllegalArgumentException> {
            InputValidation.validatePresentationStructure(presentation)
        }
        assert(exception.message?.contains("exceeds maximum length") == true)
    }
    
    // Helper functions
    
    private fun createTestCredential(
        id: CredentialId? = CredentialId("https://example.com/credentials/123"),
        issuer: Issuer = Issuer.IriIssuer(Iri("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")),
        subject: CredentialSubject = CredentialSubject(
            id = Iri("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"),
            claims = mapOf("degree" to JsonPrimitive("B.S. Computer Science"))
        ),
        claims: Map<String, JsonElement> = emptyMap(),
        schemaId: CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("EducationCredential")),
            context = listOf(
                "https://www.w3.org/2018/credentials/v1",
                "https://w3id.org/security/suites/ed25519-2020/v1"
            ),
            issuer = issuer,
            issuanceDate = Clock.System.now(),
            credentialSubject = if (claims.isEmpty()) subject else CredentialSubject(subject.id, claims),
            credentialSchema = schemaId,
            proof = null // No proof needed for structure validation tests
        )
    }
    
    private fun createTestPresentation(
        credentials: List<VerifiableCredential>,
        holder: Iri = Iri("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
    ): VerifiablePresentation {
        return VerifiablePresentation(
            context = listOf(
                "https://www.w3.org/2018/credentials/v1",
                "https://w3id.org/security/suites/ed25519-2020/v1"
            ),
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = holder,
            verifiableCredential = credentials,
            proof = null // No proof needed for structure validation tests
        )
    }
}

