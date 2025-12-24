package org.trustweave.testkit.proof

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.proof.ProofPurpose
import org.trustweave.credential.proof.proofOptions
import org.trustweave.credential.proof.proofOptionsForIssuance
import org.trustweave.credential.proof.proofOptionsForAuthentication
import org.trustweave.credential.proof.proofOptionsForPresentation
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import kotlinx.serialization.json.*
import kotlin.time.Duration as KotlinDuration
import java.time.Duration
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.*

/**
 * Convenience methods for testing proof engines.
 * 
 * Provides fluent builders and helper functions for creating test data
 * needed to test proof engine implementations.
 * 
 * **Example Usage:**
 * ```kotlin
 * val fixture = TrustWeaveTestFixture.minimal()
 * val helpers = ProofEngineTestHelpers(fixture)
 * 
 * // Create test issuance request
 * val request = helpers.createIssuanceRequest(
 *     format = CredentialFormats.VC_LD,
 *     credentialType = "PersonCredential"
 * )
 * 
 * // Create test credential
 * val credential = helpers.createTestCredential(
 *     format = CredentialFormats.VC_LD
 * )
 * ```
 */
class ProofEngineTestHelpers(
    private val fixture: org.trustweave.testkit.TrustWeaveTestFixture
) {
    
    /**
     * Gets the KMS from the fixture for use in proof engines.
     */
    fun getKms(): KeyManagementService {
        return fixture.getKms()
    }
    
    /**
     * Creates a ProofEngineConfig with KMS integration for testing.
     * 
     * This allows proof engines to use the testkit's KMS for signing.
     * 
     * **Example:**
     * ```kotlin
     * val config = helpers.createProofEngineConfigWithKms()
     * val engine = VcLdProofEngine(config)
     * ```
     */
    fun createProofEngineConfigWithKms(): ProofEngineConfig {
        return ProofEngineConfig(
            properties = mapOf(
                "kms" to fixture.getKms()
            )
        )
    }
    
    /**
     * Creates a test key in the KMS and returns its KeyId.
     * 
     * **Example:**
     * ```kotlin
     * val keyId = helpers.createTestKey(Algorithm.Ed25519)
     * val request = helpers.createIssuanceRequest(
     *     format = CredentialFormats.VC_LD,
     *     issuerKeyId = VerificationMethodId(keyId.value)
     * )
     * ```
     */
    suspend fun createTestKey(algorithm: Algorithm = Algorithm.Ed25519): KeyId {
        val kms = fixture.getKms()
        val result = kms.generateKey(algorithm)
        val keyHandle = when (result) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalArgumentException("Failed to generate key: $result")
        }
        return keyHandle.id
    }
    
    /**
     * Creates a test IssuanceRequest with sensible defaults.
     * 
     * **Example:**
     * ```kotlin
     * val request = helpers.createIssuanceRequest(
     *     format = CredentialFormats.VC_LD,
     *     credentialType = "PersonCredential",
     *     claims = mapOf("name" to "John Doe", "age" to 30)
     * )
     * ```
     */
    suspend fun createIssuanceRequest(
        format: ProofSuiteId,
        credentialType: String = "TestCredential",
        issuerDid: Did? = null,
        subjectDid: Did? = null,
        claims: Map<String, Any> = emptyMap(),
        issuedAt: Instant = Clock.System.now(),
        validUntil: Instant? = null,
        issuerKeyId: VerificationMethodId? = null,
        proofOptions: ProofOptions? = null,
        autoCreateKey: Boolean = true
    ): IssuanceRequest {
        val issuer = issuerDid ?: fixture.createIssuerDid().id
        val subject = subjectDid ?: fixture.createIssuerDid().id
        
        // Auto-create key if requested and not provided
        val finalIssuerKeyId = issuerKeyId ?: if (autoCreateKey) {
            val keyId = createTestKey()
            VerificationMethodId.parse("${issuer.value}#${keyId.value}")
        } else {
            null
        }
        
        val jsonClaims = claims.mapValues { (_, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }
        
        val types = if (credentialType == "VerifiableCredential") {
            listOf(CredentialType.VerifiableCredential)
        } else {
            listOf(CredentialType.VerifiableCredential, CredentialType.Custom(credentialType))
        }
        
        return IssuanceRequest(
            format = format,
            issuer = Issuer.fromDid(issuer),
            issuerKeyId = finalIssuerKeyId,
            credentialSubject = CredentialSubject.fromDid(subject, claims = jsonClaims),
            type = types,
            issuedAt = issuedAt,
            validUntil = validUntil ?: issuedAt.plus(KotlinDuration.parse("PT8760H")), // Default: 1 year (365 days * 24 hours)
            proofOptions = proofOptions
        )
    }
    
    /**
     * Creates a test VerifiableCredential with sensible defaults.
     * 
     * **Example:**
     * ```kotlin
     * val credential = helpers.createTestCredential(
     *     format = CredentialFormats.VC_LD,
     *     credentialType = "PersonCredential",
     *     claims = mapOf("name" to "John Doe")
     * )
     * ```
     */
    suspend fun createTestCredential(
        format: ProofSuiteId,
        credentialType: String = "TestCredential",
        issuerDid: Did? = null,
        subjectDid: Did? = null,
        claims: Map<String, Any> = emptyMap(),
        issuedAt: Instant = Clock.System.now(),
        expirationDate: Instant? = null,
        proof: CredentialProof? = null
    ): VerifiableCredential {
        val issuer = issuerDid ?: fixture.createIssuerDid().id
        val subject = subjectDid ?: fixture.createIssuerDid().id
        
        val jsonClaims = claims.mapValues { (_, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }
        
        val types = if (credentialType == "VerifiableCredential") {
            listOf(CredentialType.VerifiableCredential)
        } else {
            listOf(CredentialType.VerifiableCredential, CredentialType.Custom(credentialType))
        }
        
        return VerifiableCredential(
            id = CredentialId("urn:uuid:${UUID.randomUUID()}"),
            type = types,
            issuer = Issuer.fromDid(issuer),
            issuanceDate = issuedAt,
            expirationDate = expirationDate ?: issuedAt.plus(KotlinDuration.parse("PT8760H")), // Default: 1 year
            credentialSubject = CredentialSubject.fromDid(subject, claims = jsonClaims),
            proof = proof
        )
    }
    
    /**
     * Creates a test Issuer from a DID.
     */
    suspend fun createTestIssuer(did: Did? = null): Issuer {
        val issuerDid = did ?: fixture.createIssuerDid().id
        return Issuer.fromDid(issuerDid)
    }
    
    /**
     * Creates a test CredentialSubject with claims.
     */
    suspend fun createTestSubject(
        did: Did? = null,
        claims: Map<String, Any> = emptyMap()
    ): CredentialSubject {
        val subjectDid = did ?: fixture.createIssuerDid().id
        val jsonClaims = claims.mapValues { (_, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }
        return CredentialSubject.fromDid(subjectDid, claims = jsonClaims)
    }
    
    /**
     * Creates test VerificationOptions with sensible defaults.
     */
    fun createVerificationOptions(
        checkRevocation: Boolean = true,
        checkExpiration: Boolean = true,
        checkNotBefore: Boolean = true,
        resolveIssuerDid: Boolean = true,
        validateSchema: Boolean = false,
        clockSkewTolerance: Duration = Duration.ofMinutes(5)
    ): VerificationOptions {
        return VerificationOptions(
            checkRevocation = checkRevocation,
            checkExpiration = checkExpiration,
            checkNotBefore = checkNotBefore,
            resolveIssuerDid = resolveIssuerDid,
            validateSchema = validateSchema,
            clockSkewTolerance = clockSkewTolerance
        )
    }
    
    /**
     * Creates test PresentationRequest with sensible defaults.
     */
    fun createPresentationRequest(
        disclosedClaims: Set<String>? = null,
        predicates: List<org.trustweave.credential.requests.Predicate> = emptyList(),
        proofOptions: ProofOptions? = null
    ): PresentationRequest {
        return PresentationRequest(
            disclosedClaims = disclosedClaims,
            predicates = predicates,
            proofOptions = proofOptions
        )
    }
    
    /**
     * Creates test ProofOptions for issuance (assertionMethod).
     */
    fun createIssuanceProofOptions(
        verificationMethod: String? = null
    ): ProofOptions {
        return proofOptionsForIssuance(verificationMethod)
    }
    
    /**
     * Creates test ProofOptions for authentication.
     */
    fun createAuthenticationProofOptions(
        challenge: String,
        domain: String? = null,
        verificationMethod: String? = null
    ): ProofOptions {
        return proofOptionsForAuthentication(challenge, domain, verificationMethod)
    }
    
    /**
     * Creates test ProofOptions for presentation.
     */
    fun createPresentationProofOptions(
        challenge: String,
        domain: String? = null,
        verificationMethod: String? = null
    ): ProofOptions {
        return proofOptionsForPresentation(challenge, domain, verificationMethod)
    }
    
    /**
     * Creates a test DID for use in tests.
     */
    suspend fun createTestDid(): Did {
        return fixture.createIssuerDid().id
    }
    
    /**
     * Creates a test VerificationMethodId.
     */
    suspend fun createTestVerificationMethodId(did: Did? = null): VerificationMethodId {
        val issuerDid = did ?: createTestDid()
        val keyId = createTestKey()
        return VerificationMethodId.parse("${issuerDid.value}#${keyId.value}")
    }
    
    /**
     * Creates a ProofEngineConfig with KMS and signer function for testing.
     * 
     * This provides a complete configuration that proof engines can use
     * to actually sign credentials during testing.
     * 
     * **Example:**
     * ```kotlin
     * val config = helpers.createTestableProofEngineConfig()
     * val engine = VcLdProofEngine(config)
     * engine.initialize(config)
     * ```
     */
    fun createTestableProofEngineConfig(): ProofEngineConfig {
        val kms = fixture.getKms()
        return ProofEngineConfig(
            properties = mapOf(
                "kms" to kms,
                "signer" to { data: ByteArray, keyId: String ->
                    kotlinx.coroutines.runBlocking {
                        kms.sign(KeyId(keyId), data)
                    }
                }
            )
        )
    }
    
    /**
     * Creates an expired credential for testing expiration scenarios.
     */
    suspend fun createExpiredCredential(
        format: ProofSuiteId,
        expiredBy: KotlinDuration = KotlinDuration.parse("PT1H")
    ): VerifiableCredential {
        return createTestCredential(
            format = format,
            issuedAt = Clock.System.now().minus(KotlinDuration.parse("PT48H")), // Issued 2 days ago
            expirationDate = Clock.System.now().minus(expiredBy) // Expired
        )
    }
    
    /**
     * Creates a credential that will expire soon for testing expiration scenarios.
     */
    suspend fun createExpiringSoonCredential(
        format: ProofSuiteId,
        expiresIn: KotlinDuration = KotlinDuration.parse("PT1H")
    ): VerifiableCredential {
        return createTestCredential(
            format = format,
            expirationDate = Clock.System.now().plus(expiresIn)
        )
    }
    
    /**
     * Creates a credential without proof for testing missing proof scenarios.
     */
    suspend fun createCredentialWithoutProof(
        format: ProofSuiteId
    ): VerifiableCredential {
        return createTestCredential(
            format = format,
            proof = null
        )
    }
    
    /**
     * Creates a credential with invalid proof for testing proof validation.
     */
    suspend fun createCredentialWithInvalidProof(
        format: ProofSuiteId
    ): VerifiableCredential {
        val invalidProof = when (format.value) {
            "vc-ld" -> CredentialProof.LinkedDataProof(
                type = "InvalidProofType",
                created = Clock.System.now(),
                verificationMethod = "invalid:verification:method",
                proofPurpose = "assertionMethod",
                proofValue = "invalid-signature",
                additionalProperties = emptyMap()
            )
            "sd-jwt-vc" -> CredentialProof.SdJwtVcProof(
                sdJwtVc = "invalid.jwt.token",
                disclosures = null
            )
            else -> CredentialProof.LinkedDataProof(
                type = "InvalidProofType",
                created = Clock.System.now(),
                verificationMethod = "invalid:verification:method",
                proofPurpose = "assertionMethod",
                proofValue = "invalid-signature",
                additionalProperties = emptyMap()
            )
        }
        
        return createTestCredential(
            format = format,
            proof = invalidProof
        )
    }
}

/**
 * Extension function to get ProofEngineTestHelpers from TrustWeaveTestFixture.
 */
suspend fun org.trustweave.testkit.TrustWeaveTestFixture.proofEngineHelpers(): ProofEngineTestHelpers {
    return ProofEngineTestHelpers(this)
}

/**
 * Convenience object for creating proof engine test data without a fixture.
 * 
 * Use this when you don't need DIDs or other fixture-dependent resources.
 */
object ProofEngineTestData {
    
    /**
     * Creates a test IssuanceRequest with minimal setup.
     */
    fun createMinimalIssuanceRequest(
        format: ProofSuiteId,
        issuerDid: String = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
        subjectDid: String = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
        credentialType: String = "TestCredential",
        claims: Map<String, Any> = emptyMap()
    ): IssuanceRequest {
        val jsonClaims = claims.mapValues { (_, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }
        
        val types = if (credentialType == "VerifiableCredential") {
            listOf(CredentialType.VerifiableCredential)
        } else {
            listOf(CredentialType.VerifiableCredential, CredentialType.Custom(credentialType))
        }
        
        return IssuanceRequest(
            format = format,
            issuer = Issuer.from(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromIri(subjectDid, claims = jsonClaims),
            type = types,
            issuedAt = Clock.System.now(),
            validUntil = Clock.System.now().plus(KotlinDuration.parse("PT8760H")) // 1 year
        )
    }
    
    /**
     * Creates a test VerifiableCredential with minimal setup.
     */
    fun createMinimalCredential(
        format: ProofSuiteId,
        issuerDid: String = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
        subjectDid: String = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
        credentialType: String = "TestCredential",
        claims: Map<String, Any> = emptyMap(),
        proof: CredentialProof? = null
    ): VerifiableCredential {
        val jsonClaims = claims.mapValues { (_, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }
        
        val types = if (credentialType == "VerifiableCredential") {
            listOf(CredentialType.VerifiableCredential)
        } else {
            listOf(CredentialType.VerifiableCredential, CredentialType.Custom(credentialType))
        }
        
        return VerifiableCredential(
            id = CredentialId("urn:uuid:${UUID.randomUUID()}"),
            type = types,
            issuer = Issuer.from(Did(issuerDid)),
            issuanceDate = Clock.System.now(),
            expirationDate = Clock.System.now().plus(KotlinDuration.parse("PT8760H")), // 1 year
            credentialSubject = CredentialSubject.fromIri(subjectDid, claims = jsonClaims),
            proof = proof
        )
    }
    
    /**
     * Creates default VerificationOptions.
     */
    fun defaultVerificationOptions(): VerificationOptions {
        return VerificationOptions()
    }
    
    /**
     * Creates default PresentationRequest.
     */
    fun defaultPresentationRequest(): PresentationRequest {
        return PresentationRequest()
    }
}

