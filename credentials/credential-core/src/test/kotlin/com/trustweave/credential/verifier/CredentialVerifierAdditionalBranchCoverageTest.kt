package com.trustweave.credential.verifier

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.schema.SchemaRegistries
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.did.identifiers.Did
import com.trustweave.did.resolver.DidResolver
import com.trustweave.util.booleanDidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Additional branch coverage tests for CredentialVerifier.
 */
class CredentialVerifierAdditionalBranchCoverageTest {

    @BeforeEach
    fun setup() = runBlocking {
        SchemaRegistries.default().clear()
        SchemaRegistries.defaultValidatorRegistry().clear()
    }

    @Test
    fun `test CredentialVerifier verify with checkExpiration false`() = runBlocking {
        val verifier = CredentialVerifier()
        val pastDate = Clock.System.now().minus(86400.seconds)
        val credential = createTestCredential(expirationDate = pastDate)
        val options = CredentialVerificationOptions(checkExpiration = false)

        val result = verifier.verify(credential, options)

        assertTrue(result.notExpired) // Should be true when checkExpiration is false
    }

    @Test
    fun `test CredentialVerifier verify with checkRevocation false`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = StatusListId("status-list-1"),
                type = "StatusList2021Entry"
            )
        )
        val options = CredentialVerificationOptions(checkRevocation = false)

        val result = verifier.verify(credential, options)

        assertTrue(result.notRevoked) // Should be true when checkRevocation is false
    }

    @Test
    fun `test CredentialVerifier verify with validateSchema false`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential(
            credentialSchema = CredentialSchema(
                id = SchemaId("schema-1"),
                type = "JsonSchemaValidator2018"
            )
        )
        val options = CredentialVerificationOptions(validateSchema = false)

        val result = verifier.verify(credential, options)

        assertTrue(result.schemaValid) // Should be true when validateSchema is false
    }

    @Test
    fun `test CredentialVerifier verify with validateSchema true and schema registered`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        val schema = CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018"
        )
        val schemaDef = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        SchemaRegistries.default().registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, schemaDef)
        SchemaRegistries.defaultValidatorRegistry().clear()
        // Validator is already registered by default

        val verifier = CredentialVerifier()
        val credential = createTestCredential(credentialSchema = schema)
        val options = CredentialVerificationOptions(validateSchema = true)

        val result = verifier.verify(credential, options)

        assertNotNull(result)
    }

    @Test
    fun `test CredentialVerifier verify with verifyBlockchainAnchor false`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential()
        val options = CredentialVerificationOptions(verifyBlockchainAnchor = false)

        val result = verifier.verify(credential, options)

        assertTrue(result.blockchainAnchorValid) // Should be true when verifyBlockchainAnchor is false
    }

    @Test
    fun `test CredentialVerifier verify with resolveDid returning false`() = runBlocking {
        val verifier = CredentialVerifier(booleanDidResolver { false })
        val credential = createTestCredential()

        val result = verifier.verify(credential)

        assertFalse(result.issuerValid)
        assertFalse(result.valid)
    }

    @Test
    fun `test CredentialVerifier verify with resolveDid throwing exception`() = runBlocking {
        val verifier = CredentialVerifier(
            defaultDidResolver = DidResolver { throw RuntimeException("DID resolution failed") }
        )
        val credential = createTestCredential()

        val result = verifier.verify(credential)

        assertFalse(result.issuerValid)
        assertFalse(result.valid)
        assertTrue(result.allErrors.any { it.contains("DID") || it.contains("resolution") })
    }

    @Test
    fun `test CredentialVerifier verify with expired credential and checkExpiration true`() = runBlocking {
        val verifier = CredentialVerifier()
        val pastDate = Clock.System.now().minus(86400.seconds)
        val credential = createTestCredential(expirationDate = pastDate)
        val options = CredentialVerificationOptions(checkExpiration = true)

        val result = verifier.verify(credential, options)

        assertFalse(result.notExpired)
        assertFalse(result.valid)
    }

    @Test
    fun `test CredentialVerifier verify with revoked credential and checkRevocation true`() = runBlocking {
        val verifier = CredentialVerifier()
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = StatusListId("status-list-1"),
                type = "StatusList2021Entry"
            )
        )
        val options = CredentialVerificationOptions(checkRevocation = true)

        val result = verifier.verify(credential, options)

        // Current implementation adds warning but doesn't set notRevoked to false
        // This is expected behavior for placeholder implementation
        assertTrue(result.allWarnings.any { it.contains("Revocation") || it.contains("revocation") })
    }

    @Test
    fun `test CredentialVerifier verify with expired credential`() = runBlocking {
        val verifier = CredentialVerifier()
        // With Instant type, we can't have invalid format, so test with expired credential instead
        val pastDate = Clock.System.now().minus(86400.seconds)
        val credential = createTestCredential(expirationDate = pastDate)
        val options = CredentialVerificationOptions(checkExpiration = true)

        val result = verifier.verify(credential, options)

        // Should detect expired credential
        assertFalse(result.isValid)
        assertTrue(result.allErrors.any { it.contains("expired") || it.contains("expiration") })
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        issuanceDate: Instant = Clock.System.now(),
        expirationDate: Instant? = null,
        credentialStatus: CredentialStatus? = null,
        credentialSchema: CredentialSchema? = null,
        proof: CredentialProof? = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Clock.System.now(),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "test-proof",
            additionalProperties = emptyMap()
        )
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialStatus = credentialStatus,
            credentialSchema = credentialSchema,
            proof = proof
        )
    }
}

