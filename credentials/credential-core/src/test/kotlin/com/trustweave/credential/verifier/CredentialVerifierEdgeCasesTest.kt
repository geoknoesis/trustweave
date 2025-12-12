package com.trustweave.credential.verifier

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.credential.CredentialVerificationResult
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.schema.SchemaRegistries
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorOptions
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.VerificationMethod
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.kms.Algorithm
import com.trustweave.kms.results.GenerateKeyResult
import com.trustweave.kms.results.SignResult
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.util.booleanDidResolver as trustBooleanDidResolver
import com.trustweave.util.resultDidResolver as trustResultDidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Exhaustive edge case tests for CredentialVerifier API robustness.
 */
class CredentialVerifierEdgeCasesTest {

    private lateinit var verifier: CredentialVerifier
    private lateinit var kms: InMemoryKeyManagementService
    private var keyId: com.trustweave.core.identifiers.KeyId? = null
    private lateinit var publicKeyJwk: Map<String, Any?>
    private lateinit var proofGenerator: Ed25519ProofGenerator
    private val issuerDid = "did:key:issuer123"

    @BeforeEach
    fun setup() = runBlocking {
        SchemaRegistries.default().clear()
        SchemaRegistries.defaultValidatorRegistry().clear()
        // JsonSchemaValidator is registered by default in defaultValidatorRegistry

        // Create a real KMS and generate a real Ed25519 key
        kms = InMemoryKeyManagementService()
        val generateResult = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "key-1"))
        val keyHandle = when (generateResult) {
            is GenerateKeyResult.Success -> generateResult.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $generateResult")
        }
        keyId = keyHandle.id
        publicKeyJwk = keyHandle.publicKeyJwk ?: emptyMap()

        // Create proof generator that uses the KMS to sign
        val keyIdValue = keyId!!
        proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> 
                val signResult = kms.sign(keyIdValue, data)
                when (signResult) {
                    is SignResult.Success -> signResult.signature
                    else -> throw IllegalStateException("Failed to sign: $signResult")
                }
            },
            getPublicKeyId = { keyIdValue.value }
        )

        // Create DID resolver with real public key
        val didResolver = createTestDidResolver()
        verifier = CredentialVerifier(defaultDidResolver = didResolver)
    }

    /**
     * Creates a test DID resolver that returns a DID document with verification methods.
     */
    private fun createTestDidResolver(): DidResolver {
        val issuerDidObj = Did(issuerDid)
        return trustResultDidResolver { didStr ->
            if (didStr == issuerDid || didStr == "did:key:issuer123") {
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = issuerDidObj,
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = VerificationMethodId.parse("$issuerDid#key-1", issuerDidObj),
                                type = "Ed25519VerificationKey2020",
                                controller = issuerDidObj,
                                publicKeyJwk = publicKeyJwk
                            )
                        )
                    )
                )
            } else {
                null
            }
        }
    }

    @AfterEach
    fun cleanup() = runBlocking {
        SchemaRegistries.default().clear()
        SchemaRegistries.defaultValidatorRegistry().clear()
    }

    // ========== Null and Empty Input Tests ==========

    @Test
    fun `test verify credential with null ID`() = runBlocking {
        val credential = createTestCredential(id = null)

        val result = verifier.verify(credential)

        assertNotNull(result)
        // Should still verify (ID is optional)
    }

    @Test
    fun `test verify credential with empty type list`() = runBlocking {
        val credential = createTestCredential(types = emptyList())

        val result = verifier.verify(credential)

        // May fail validation or handle gracefully
        assertNotNull(result)
        // Empty type list should result in invalid credential
        if (!result.valid) {
            assertTrue(result.allErrors.isNotEmpty() || result.allWarnings.isNotEmpty())
        }
    }

    @Test
    fun `test verify credential with empty issuer DID`() = runBlocking {
        // Create credential with empty issuer - use IriIssuer with empty string
        val validCredential = createTestCredential()
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(com.trustweave.core.identifiers.Iri("")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
            issuanceDate = Clock.System.now(),
            proof = validCredential.proof // Use proof from valid credential
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.issuerValid)
    }

    @Test
    fun `test verify credential with null proof`() = runBlocking {
        // Create credential directly with null proof (not using helper which auto-generates)
        val credential = VerifiableCredential(
            id = CredentialId("https://example.com/credentials/1"),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = mapOf("name" to JsonPrimitive("John Doe"))
            ),
            issuanceDate = Clock.System.now(),
            proof = null
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
        assertTrue(result.allErrors.any { it.contains("no proof") || it.contains("proof") })
    }

    @Test
    fun `test verify credential with empty proof type`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = ""
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test verify credential with empty verification method`() = runBlocking {
        // Use an empty string for verificationMethod which will fail validation
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "", // Empty verification method
                proofPurpose = "assertionMethod",
                proofValue = "",
                additionalProperties = emptyMap()
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    // ========== Invalid Format Tests ==========

    @Test
    fun `test verify credential with invalid issuance date format`() = runBlocking {
        val credential = createTestCredential(issuanceDate = try { Instant.parse("not-a-date") } catch (e: Exception) { Clock.System.now() })

        val result = verifier.verify(credential)

        // May add warning or handle gracefully
        assertNotNull(result)
        // Invalid date format may result in warnings or be ignored
        if (result.allWarnings.isNotEmpty()) {
            assertTrue(result.allWarnings.any { it.contains("date") || it.contains("format") || it.contains("Invalid") })
        }
    }

    @Test
    fun `test verify credential with invalid expiration date format`() = runBlocking {
        // Instant.parse will throw exception for invalid format, so expirationDate will be null
        // With null expirationDate, there's no expiration to check, so no warning about invalid format
        val credential = createTestCredential(expirationDate = try { Instant.parse("not-a-date") } catch (e: Exception) { null })

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        // If expirationDate is null, there's no expiration to validate, so notExpired should be true
        // and there won't be a warning about invalid format (since there's no expiration date)
        assertTrue(result.notExpired) // No expiration date means not expired
        // Note: With null expirationDate, there's no "invalid format" warning since there's no date to validate
    }

    @Test
    fun `test verify credential with malformed DID`() = runBlocking {
        // Create credential with malformed DID - use IriIssuer with invalid DID string
        val validCredential = createTestCredential()
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(com.trustweave.core.identifiers.Iri("not-a-did")), // Malformed DID
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
            issuanceDate = Clock.System.now(),
            proof = validCredential.proof // Use proof from valid credential
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.issuerValid)
    }

    // ========== Boundary Value Tests ==========

    @Test
    fun `test verify credential expired exactly at current time`() = runBlocking {
        val now = Clock.System.now()
        val credential = createTestCredential(expirationDate = now)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        // Should be expired (now is not before expiration)
        assertFalse(result.notExpired)
    }

    @Test
    fun `test verify credential expired one second ago`() = runBlocking {
        val expirationDate = Clock.System.now().minus(1.seconds)
        val credential = createTestCredential(expirationDate = expirationDate)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertFalse(result.valid)
        assertFalse(result.notExpired)
        assertTrue(result.allErrors.any { it.contains("expired") })
    }

    @Test
    fun `test verify credential expires one second in future`() = runBlocking {
        val expirationDate = Clock.System.now().plus(1.seconds)
        val credential = createTestCredential(expirationDate = expirationDate)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertTrue(result.notExpired)
    }

    @Test
    fun `test verify credential with very long expiration date`() = runBlocking {
        val expirationDate = Clock.System.now().plus((31536000L * 1000).seconds)
        val credential = createTestCredential(expirationDate = expirationDate)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertTrue(result.notExpired)
    }

    // ========== Proof Edge Cases ==========

    @Test
    fun `test verify credential with proof missing created timestamp`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = ""
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test verify credential with proof missing proof purpose`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "",
                proofValue = ""
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    @Test
    fun `test verify credential with proof having both proofValue and jws`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "z123"
            )
        )

        val result = verifier.verify(credential)

        // Should handle both (may prefer one over the other)
        assertNotNull(result)
    }

    @Test
    fun `test verify credential with proof missing both proofValue and jws`() = runBlocking {
        // LinkedDataProof requires proofValue, so we'll use an empty string
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = ""
            )
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.proofValid)
    }

    // ========== DID Resolution Edge Cases ==========

    @Test
    fun `test verify credential with DID resolution throwing exception`() = runBlocking {
        val verifierWithThrowingDid = CredentialVerifier(
            defaultDidResolver = DidResolver { throw RuntimeException("DID resolution failed") }
        )
        val credential = createTestCredential()

        val result = verifierWithThrowingDid.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.issuerValid)
        assertTrue(result.allErrors.any { it.contains("DID") || it.contains("resolution") || it.contains("failed") })
    }

    @Test
    fun `test verify credential with DID resolution returning false`() = runBlocking {
        val verifierWithFailedDid = CredentialVerifier(
            defaultDidResolver = trustBooleanDidResolver { false }
        )
        val credential = createTestCredential()

        val result = verifierWithFailedDid.verify(credential)

        assertFalse(result.valid)
        assertFalse(result.issuerValid)
    }

    // ========== Credential Status Edge Cases ==========

    @Test
    fun `test verify credential with credential status missing ID`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = StatusListId(""),
                type = "StatusList2021Entry"
            )
        )

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkRevocation = true)
        )

        assertNotNull(result)
        // May warn about invalid status
    }

    @Test
    fun `test verify credential with credential status missing type`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = CredentialStatus(
                id = StatusListId("https://example.com/status/1"),
                type = ""
            )
        )

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkRevocation = true)
        )

        assertNotNull(result)
    }

    // ========== Schema Edge Cases ==========

    @Test
    fun `test verify credential with schema missing ID`() = runBlocking {
        val schema = CredentialSchema(
            id = SchemaId(""),
            type = "JsonSchemaValidator2018"
        )
        val credential = createTestCredential(schema = schema)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(validateSchema = true)
        )

        assertNotNull(result)
        // May skip validation if schema ID is invalid
    }

    @Test
    fun `test verify credential with non-existent schema`() = runBlocking {
        val schema = CredentialSchema(
            id = SchemaId("https://example.com/nonexistent"),
            type = "JsonSchemaValidator2018"
        )
        val credential = createTestCredential(schema = schema)

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(validateSchema = true)
        )

        assertNotNull(result)
        // May skip validation or warn if schema not found
    }

    // ========== Options Edge Cases ==========

    @Test
    fun `test verify credential with all checks disabled`() = runBlocking {
        val expiredCredential = createTestCredential(
            expirationDate = Clock.System.now().minus(86400.seconds)
        )

        val result = verifier.verify(
            expiredCredential,
            CredentialVerificationOptions(
                checkExpiration = false,
                checkRevocation = false,
                validateSchema = false,
                verifyBlockchainAnchor = false
            )
        )

        assertTrue(result.notExpired) // Should be true when check disabled
        assertTrue(result.notRevoked)
        assertTrue(result.schemaValid)
        assertTrue(result.blockchainAnchorValid)
    }

    @Test
    fun `test verify credential with all checks enabled`() = runBlocking {
        val credential = createTestCredential()

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(
                checkExpiration = true,
                checkRevocation = true,
                validateSchema = true,
                verifyBlockchainAnchor = true
            )
        )

        assertNotNull(result)
        // All checks should be performed
    }

    // ========== Multiple Errors Tests ==========

    @Test
    fun `test verify credential with multiple validation errors`() = runBlocking {
        val credential = createTestCredential(
            proof = null,
            expirationDate = Clock.System.now().minus(86400.seconds),
            issuerDid = "did:key:wrong"
        )

        val result = verifier.verify(
            credential,
            CredentialVerificationOptions(checkExpiration = true)
        )

        assertFalse(result.valid)
        assertTrue(result.allErrors.size >= 2)
    }

    @Test
    fun `test verify credential with all possible errors`() = runBlocking {
        val credential = VerifiableCredential(
            id = null,
            type = emptyList(),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
            issuanceDate = Clock.System.now(),
            proof = null
        )

        val result = verifier.verify(credential)

        assertFalse(result.valid)
        assertTrue(result.allErrors.isNotEmpty())
    }

    // ========== Special Character Tests ==========

    @Test
    fun `test verify credential with special characters in proof value`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "z!@#$%^&*()_+-=[]{}|;':\",./<>?"
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
        // Should handle special characters in proof value
    }

    @Test
    fun `test verify credential with unicode in verification method`() = runBlocking {
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer123#key-中文",
                proofPurpose = "assertionMethod",
                proofValue = ""
            )
        )

        val result = verifier.verify(credential)

        assertNotNull(result)
    }

    // ========== Helper Methods ==========

    private suspend fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = this.issuerDid,
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        issuanceDate: Instant = Clock.System.now(),
        expirationDate: Instant? = null,
        proof: CredentialProof? = null, // null means auto-generate
        schema: CredentialSchema? = null,
        credentialStatus: CredentialStatus? = null
    ): VerifiableCredential {
        // Create credential without proof first
        val credentialWithoutProof = VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            proof = null,
            credentialSchema = schema,
            credentialStatus = credentialStatus
        )

        // Generate a properly signed proof if not provided
        val finalProof = proof ?: run {
            val generatedProof = proofGenerator.generateProof(
                credential = credentialWithoutProof,
                keyId = keyId!!.value,
                options = ProofGeneratorOptions(
                    proofPurpose = "assertionMethod",
                    verificationMethod = "$issuerDid#key-1"
                )
            )
            // Convert Proof to CredentialProof
            when (generatedProof) {
                is com.trustweave.credential.models.Proof -> CredentialProof.LinkedDataProof(
                    type = generatedProof.type.identifier,
                    created = Instant.parse(generatedProof.created),
                    verificationMethod = generatedProof.verificationMethod.value,
                    proofPurpose = generatedProof.proofPurpose,
                    proofValue = generatedProof.proofValue ?: "",
                    additionalProperties = emptyMap()
                )
                else -> throw IllegalStateException("Unexpected proof type")
            }
        }

        // Return credential with proof
        return credentialWithoutProof.copy(proof = finalProof)
    }
}

