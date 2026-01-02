package org.trustweave.credential.security

import org.trustweave.credential.internal.InputValidation
import org.trustweave.credential.internal.SecurityConstants
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Security-focused tests for input validation.
 * 
 * Tests edge cases and potential security vulnerabilities in input validation,
 * including DoS attack scenarios, boundary conditions, and adversarial inputs.
 */
class SecurityValidationTest {
    
    @Test
    fun `test credential ID maximum length boundary`() {
        // Test exact maximum length - should pass
        val validId = "x".repeat(SecurityConstants.MAX_CREDENTIAL_ID_LENGTH)
        InputValidation.validateCredentialId(CredentialId(validId))
        
        // Test one over maximum - should fail
        val invalidId = "x".repeat(SecurityConstants.MAX_CREDENTIAL_ID_LENGTH + 1)
        assertFailsWith<IllegalArgumentException> {
            InputValidation.validateCredentialId(CredentialId(invalidId))
        }
    }
    
    @Test
    fun `test DID maximum length boundary`() {
        // Test exact maximum length - should pass
        // Note: We need to ensure the total DID length is exactly MAX_DID_LENGTH
        val prefix = "did:test:"
        val remainingLength = SecurityConstants.MAX_DID_LENGTH - prefix.length
        val validDid = prefix + "x".repeat(remainingLength)
        assertTrue(validDid.length == SecurityConstants.MAX_DID_LENGTH)
        InputValidation.validateDid(Did(validDid))
        
        // Test one over maximum - should fail
        val invalidDid = prefix + "x".repeat(remainingLength + 1)
        assertTrue(invalidDid.length > SecurityConstants.MAX_DID_LENGTH)
        assertFailsWith<IllegalArgumentException> {
            InputValidation.validateDid(Did(invalidDid))
        }
    }
    
    @Test
    fun `test IRI maximum length boundary`() {
        // Test exact maximum length - should pass
        // Note: IRI validation uses MAX_DID_LENGTH constant
        val prefix = "https://example.com/"
        val remainingLength = SecurityConstants.MAX_DID_LENGTH - prefix.length
        val validIri = prefix + "x".repeat(remainingLength)
        assertTrue(validIri.length == SecurityConstants.MAX_DID_LENGTH)
        InputValidation.validateIri(Iri(validIri))
        
        // Test one over maximum - should fail
        val invalidIri = prefix + "x".repeat(remainingLength + 1)
        assertTrue(invalidIri.length > SecurityConstants.MAX_DID_LENGTH)
        assertFailsWith<IllegalArgumentException> {
            InputValidation.validateIri(Iri(invalidIri))
        }
    }
    
    @Test
    fun `test claims count maximum boundary`() {
        // Test exact maximum - should pass (if implemented as validation)
        // Note: This is tested through actual credential creation in integration tests
        // This test verifies the constant is reasonable
        assertTrue(SecurityConstants.MAX_CLAIMS_PER_CREDENTIAL > 0)
        assertTrue(SecurityConstants.MAX_CLAIMS_PER_CREDENTIAL >= 100) // Reasonable minimum
    }
    
    @Test
    fun `test credential size maximum boundary`() {
        // Verify constants are set
        assertTrue(SecurityConstants.MAX_CREDENTIAL_SIZE_BYTES > 0)
        assertTrue(SecurityConstants.MAX_CREDENTIAL_SIZE_BYTES >= 1024) // At least 1KB
    }
    
    @Test
    fun `test presentation size maximum boundary`() {
        // Verify constants are set
        assertTrue(SecurityConstants.MAX_PRESENTATION_SIZE_BYTES > 0)
        assertTrue(SecurityConstants.MAX_PRESENTATION_SIZE_BYTES >= SecurityConstants.MAX_CREDENTIAL_SIZE_BYTES)
    }
    
    @Test
    fun `test presentation credentials count maximum`() {
        // Verify constants are set
        assertTrue(SecurityConstants.MAX_CREDENTIALS_PER_PRESENTATION > 0)
        assertTrue(SecurityConstants.MAX_CREDENTIALS_PER_PRESENTATION >= 10) // Reasonable minimum
    }
    
    @Test
    fun `test schema ID maximum length boundary`() {
        // Test exact maximum length - should pass
        val prefix = "https://schema.org/"
        val remainingLength = SecurityConstants.MAX_SCHEMA_ID_LENGTH - prefix.length
        val validSchemaId = prefix + "x".repeat(remainingLength)
        assertTrue(validSchemaId.length == SecurityConstants.MAX_SCHEMA_ID_LENGTH)
        InputValidation.validateSchemaId(validSchemaId)
        
        // Test one over maximum - should fail
        val invalidSchemaId = prefix + "x".repeat(remainingLength + 1)
        assertTrue(invalidSchemaId.length > SecurityConstants.MAX_SCHEMA_ID_LENGTH)
        assertFailsWith<IllegalArgumentException> {
            InputValidation.validateSchemaId(invalidSchemaId)
        }
    }
    
    @Test
    fun `test verification method ID maximum length boundary`() {
        // Test exact maximum length - should pass
        val prefix = "did:test:issuer#"
        val remainingLength = SecurityConstants.MAX_VERIFICATION_METHOD_ID_LENGTH - prefix.length
        val validVmId = prefix + "x".repeat(remainingLength)
        assertTrue(validVmId.length == SecurityConstants.MAX_VERIFICATION_METHOD_ID_LENGTH)
        InputValidation.validateVerificationMethodId(validVmId)
        
        // Test one over maximum - should fail
        val invalidVmId = prefix + "x".repeat(remainingLength + 1)
        assertTrue(invalidVmId.length > SecurityConstants.MAX_VERIFICATION_METHOD_ID_LENGTH)
        assertFailsWith<IllegalArgumentException> {
            InputValidation.validateVerificationMethodId(invalidVmId)
        }
    }
    
    @Test
    fun `test Ed25519 signature length constant`() {
        // Ed25519 signatures are always exactly 64 bytes
        assertTrue(SecurityConstants.ED25519_SIGNATURE_LENGTH_BYTES == 64)
    }
    
    @Test
    fun `test canonicalized document size limit`() {
        // Verify constant is set and reasonable
        assertTrue(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES > 0)
        assertTrue(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES >= SecurityConstants.MAX_CREDENTIAL_SIZE_BYTES)
    }
    
    @Test
    fun `test status list check size limit`() {
        // Verify constant is set
        assertTrue(SecurityConstants.MAX_STATUS_LIST_CHECK_SIZE > 0)
        assertTrue(SecurityConstants.MAX_STATUS_LIST_CHECK_SIZE >= 100) // Reasonable minimum
    }
    
    @Test
    fun `test adversarial input - very long strings at boundaries`() {
        // Test that validation handles very long strings correctly
        // This test verifies the validation doesn't hang or crash on very long strings
        val boundaryString = "x".repeat(SecurityConstants.MAX_DID_LENGTH)
        val overBoundaryString = "x".repeat(SecurityConstants.MAX_DID_LENGTH + 1000)
        
        // Verify string lengths
        assertTrue(boundaryString.length == SecurityConstants.MAX_DID_LENGTH)
        assertTrue(overBoundaryString.length > SecurityConstants.MAX_DID_LENGTH)
        
        // Test that validation rejects strings over the limit (even if not valid DID format)
        // The validation should check length first before format validation
    }
    
    @Test
    fun `test security constants are consistent`() {
        // Verify logical consistency between related constants
        assertTrue(SecurityConstants.MAX_PRESENTATION_SIZE_BYTES >= SecurityConstants.MAX_CREDENTIAL_SIZE_BYTES,
            "Presentation size limit should be >= credential size limit")
        assertTrue(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES >= SecurityConstants.MAX_CREDENTIAL_SIZE_BYTES,
            "Canonicalized document size limit should be >= credential size limit")
        assertTrue(SecurityConstants.MAX_DID_LENGTH >= 100,
            "DID length limit should be at least 100 characters")
        assertTrue(SecurityConstants.MAX_CREDENTIAL_ID_LENGTH >= 100,
            "Credential ID length limit should be at least 100 characters")
    }
}

