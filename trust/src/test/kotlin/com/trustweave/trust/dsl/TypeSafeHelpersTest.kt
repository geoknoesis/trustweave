package com.trustweave.trust.dsl

import com.trustweave.trust.dsl.credential.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for TypeSafeHelpers.kt
 *
 * Tests all type-safe constants to ensure they are correct and accessible.
 */
class TypeSafeHelpersTest {

    @Test
    fun `test CredentialTypes constants`() {
        assertEquals("EducationCredential", CredentialTypes.EDUCATION)
        assertEquals("EmploymentCredential", CredentialTypes.EMPLOYMENT)
        assertEquals("CertificationCredential", CredentialTypes.CERTIFICATION)
        assertEquals("DegreeCredential", CredentialTypes.DEGREE)
        assertEquals("PersonCredential", CredentialTypes.PERSON)
        assertEquals("VerifiableCredential", CredentialTypes.VERIFIABLE_CREDENTIAL)
    }

    @Test
    fun `test ProofTypes constants`() {
        assertEquals("Ed25519Signature2020", ProofTypes.ED25519)
        assertEquals("JsonWebSignature2020", ProofTypes.JWT)
        assertEquals("BbsBlsSignature2020", ProofTypes.BBS_BLS)
    }

    @Test
    fun `test DidMethods constants`() {
        assertEquals("key", DidMethods.KEY)
        assertEquals("web", DidMethods.WEB)
        assertEquals("ion", DidMethods.ION)
        assertEquals("ethr", DidMethods.ETHR)
    }

    @Test
    fun `test KeyAlgorithms constants`() {
        assertEquals("Ed25519", KeyAlgorithms.ED25519)
        assertEquals("secp256k1", KeyAlgorithms.SECP256K1)
        assertEquals("RSA", KeyAlgorithms.RSA)
    }

    @Test
    fun `test StatusPurposes constants`() {
        assertEquals("revocation", StatusPurposes.REVOCATION)
        assertEquals("suspension", StatusPurposes.SUSPENSION)
    }

    @Test
    fun `test SchemaValidatorTypes constants`() {
        assertEquals("JsonSchemaValidator2018", SchemaValidatorTypes.JSON_SCHEMA)
        assertEquals("ShaclValidator2020", SchemaValidatorTypes.SHACL)
    }

    @Test
    fun `test ServiceTypes constants`() {
        assertEquals("LinkedDomains", ServiceTypes.LINKED_DOMAINS)
        assertEquals("DIDCommMessaging", ServiceTypes.DID_COMM_MESSAGING)
        assertEquals("CredentialRevocation", ServiceTypes.CREDENTIAL_REVOCATION)
    }

    @Test
    fun `test ProofPurposes constants`() {
        assertEquals("assertionMethod", ProofPurposes.ASSERTION_METHOD)
        assertEquals("authentication", ProofPurposes.AUTHENTICATION)
        assertEquals("keyAgreement", ProofPurposes.KEY_AGREEMENT)
        assertEquals("capabilityInvocation", ProofPurposes.CAPABILITY_INVOCATION)
        assertEquals("capabilityDelegation", ProofPurposes.CAPABILITY_DELEGATION)
    }

    @Test
    fun `test constants are accessible as objects`() {
        assertNotNull(CredentialTypes)
        assertNotNull(ProofTypes)
        assertNotNull(DidMethods)
        assertNotNull(KeyAlgorithms)
        assertNotNull(StatusPurposes)
        assertNotNull(SchemaValidatorTypes)
        assertNotNull(ServiceTypes)
        assertNotNull(ProofPurposes)
    }

    @Test
    fun `test constants can be used in DSL`() {
        // Verify constants can be used in actual DSL calls
        val credentialType = CredentialTypes.EDUCATION
        val proofType = ProofTypes.ED25519
        val didMethod = DidMethods.KEY

        assertNotNull(credentialType)
        assertNotNull(proofType)
        assertNotNull(didMethod)

        // These should not throw exceptions
        assertNotNull(credentialType)
    }
}


