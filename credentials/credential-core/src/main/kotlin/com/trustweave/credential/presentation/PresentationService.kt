package com.trustweave.credential.presentation

import com.trustweave.credential.CredentialVerificationResult
import com.trustweave.credential.PresentationOptions
import com.trustweave.credential.PresentationVerificationOptions
import com.trustweave.credential.PresentationVerificationResult
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.proof.ProofOptions
import com.trustweave.credential.verifier.CredentialVerifier
import com.trustweave.core.identifiers.Iri
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Presentation request model.
 *
 * Used to request verifiable presentations from holders.
 *
 * @param id Unique request identifier
 * @param query Presentation query specifying what credentials are requested
 * @param challenge Challenge string for authentication
 * @param domain Optional domain string for authentication
 * @param expires Optional expiration timestamp
 */
data class PresentationRequest(
    val id: String = UUID.randomUUID().toString(),
    val query: PresentationQuery,
    val challenge: String,
    val domain: String? = null,
    val expires: String? = null
)

/**
 * Presentation query specifying credential requirements.
 *
 * @param type Query type (e.g., "DIDAuthentication", "CredentialQuery")
 * @param credentialTypes List of required credential types
 * @param requiredFields List of required fields in credentialSubject
 * @param issuer Optional required issuer DID
 */
data class PresentationQuery(
    val type: String, // "DIDAuthentication", "CredentialQuery"
    val credentialTypes: List<String>? = null,
    val requiredFields: List<String>? = null,
    val issuer: String? = null
)

/**
 * Presentation service for creating and verifying verifiable presentations.
 *
 * Handles presentation creation, selective disclosure, and verification.
 *
 * **Example Usage**:
 * ```kotlin
 * val service = PresentationService(
 *     proofGenerator = Ed25519ProofGenerator { data, keyId -> kms.sign(keyId, data) },
 *     credentialVerifier = CredentialVerifier { did -> didRegistry.resolve(did).document != null }
 * )
 *
 * // Create presentation
 * val presentation = service.createPresentation(
 *     credentials = listOf(credential1, credential2),
 *     holderDid = "did:key:...",
 *     options = PresentationOptions(
 *         holderDid = "did:key:...",
 *         challenge = "challenge-123",
 *         domain = "example.com"
 *     )
 * )
 *
 * // Verify presentation
 * val result = service.verifyPresentation(
 *     presentation = presentation,
 *     options = PresentationVerificationOptions(
 *         verifyChallenge = true,
 *         expectedChallenge = "challenge-123"
 *     )
 * )
 * ```
 */
class PresentationService(
    private val proofGenerator: ProofGenerator? = null,
    private val credentialVerifier: CredentialVerifier? = null,
    private val proofRegistry: ProofGeneratorRegistry = ProofGeneratorRegistry()
) {
    /**
     * Create a verifiable presentation from credentials.
     *
     * @param credentials List of credentials to include in presentation
     * @param holderDid DID of the presentation holder
     * @param options Presentation options (proof type, challenge, domain, etc.)
     * @return Verifiable presentation with proof
     */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        holderDid: String,
        options: PresentationOptions
    ): VerifiablePresentation = withContext(Dispatchers.IO) {
        require(credentials.isNotEmpty()) { "At least one credential is required" }
        require(holderDid.isNotBlank()) { "Holder DID is required" }

        // Build presentation without proof
        val holderIri: Iri = try {
            Did(holderDid) // Try as DID first
        } catch (e: Exception) {
            Iri(holderDid) // Fallback to IRI
        }
        
        val presentation = VerifiablePresentation(
            id = com.trustweave.credential.identifiers.CredentialId(UUID.randomUUID().toString()),
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = credentials,
            holder = holderIri,
            proof = null,
            challenge = options.challenge,
            domain = options.domain
        )

        // Generate proof if proof type is specified
        val proof: CredentialProof? = if (options.proofType.isNotBlank() && options.keyId != null) {
            val generator = proofGenerator ?: getProofGenerator(options.proofType)
            val generatedProof = generator.generateProof(
                credential = credentials.first(), // Use first credential for proof generation context
                keyId = options.keyId,
                options = ProofOptions(
                    proofPurpose = "authentication",
                    challenge = options.challenge,
                    domain = options.domain
                )
            )
            convertProofToCredentialProof(generatedProof)
        } else {
            null
        }

        presentation.copy(proof = proof)
    }

    /**
     * Verify a verifiable presentation.
     *
     * @param presentation Presentation to verify
     * @param options Verification options
     * @return Verification result
     */
    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        options: PresentationVerificationOptions = PresentationVerificationOptions()
    ): PresentationVerificationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        var presentationProofValid = false
        var challengeValid = true
        var domainValid = true

        // 1. Verify presentation proof
        if (presentation.proof != null) {
            // TODO: Implement actual proof verification
            // For now, check proof structure
            presentationProofValid = when (val proof = presentation.proof) {
                is com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof -> {
                    proof.type.isNotBlank() && proof.verificationMethod.isNotBlank()
                }
                is com.trustweave.credential.model.vc.CredentialProof.JwtProof -> {
                    proof.jwt.isNotBlank()
                }
                is com.trustweave.credential.model.vc.CredentialProof.SdJwtVcProof -> {
                    proof.sdJwtVc.isNotBlank()
                }
            }
            if (!presentationProofValid) {
                errors.add("Presentation proof is invalid")
            }
        } else {
            errors.add("Presentation has no proof")
        }

        // 2. Verify challenge
        if (options.verifyChallenge) {
            if (options.expectedChallenge != null) {
                challengeValid = presentation.challenge == options.expectedChallenge
                if (!challengeValid) {
                    errors.add("Challenge mismatch: expected '${options.expectedChallenge}', got '${presentation.challenge}'")
                }
            } else if (presentation.challenge == null) {
                warnings.add("No challenge provided in presentation")
            }
        }

        // 3. Verify domain
        if (options.verifyDomain && options.expectedDomain != null) {
            domainValid = presentation.domain == options.expectedDomain
            if (!domainValid) {
                errors.add("Domain mismatch: expected '${options.expectedDomain}', got '${presentation.domain}'")
            }
        }

        // 4. Verify all credentials in presentation
        val credentialResults = mutableListOf<CredentialVerificationResult>()
        if (options.checkRevocation && credentialVerifier != null) {
            for (credential in presentation.verifiableCredential) {
                val result = credentialVerifier.verify(
                    credential,
                    com.trustweave.credential.CredentialVerificationOptions(
                        checkRevocation = options.checkRevocation
                    )
                )
                credentialResults.add(result)

                if (!result.isValid) {
                    errors.add("Credential ${credential.id ?: "unknown"} verification failed: ${result.allErrors.joinToString(", ")}")
                }
            }
        }

        PresentationVerificationResult(
            valid = errors.isEmpty() && presentationProofValid && challengeValid && domainValid,
            errors = errors,
            warnings = warnings,
            presentationProofValid = presentationProofValid,
            challengeValid = challengeValid,
            domainValid = domainValid,
            credentialResults = credentialResults
        )
    }

    /**
     * Create a selective disclosure presentation.
     *
     * Creates a presentation that only discloses specific fields from credentials.
     * Requires BBS+ proof type for zero-knowledge selective disclosure.
     *
     * @param credentials List of credentials
     * @param disclosedFields List of field paths to disclose (e.g., ["credentialSubject.name", "credentialSubject.email"])
     * @param holderDid DID of the holder
     * @param options Presentation options
     * @return Presentation with selective disclosure
     */
    suspend fun createSelectiveDisclosure(
        credentials: List<VerifiableCredential>,
        disclosedFields: List<String>,
        holderDid: String,
        options: PresentationOptions
    ): VerifiablePresentation {
        // Check if BBS+ proof generator is available
        val bbsGenerator = proofRegistry.get("BbsBlsSignature2020")

        if (bbsGenerator != null && bbsGenerator is com.trustweave.credential.proof.BbsProofGenerator) {
            // Use BBS+ generator for selective disclosure
            val derivedCredentials = credentials.map { credential ->
                // Create derived credential with only disclosed fields
                createDerivedCredentialForDisclosure(credential, disclosedFields)
            }

            // Create presentation with derived credentials
            // In a full implementation, this would use BBS+ selective disclosure proofs
            return createPresentation(derivedCredentials, holderDid, options.copy(proofType = "BbsBlsSignature2020"))
        } else {
            // Fallback: create presentation with filtered fields
            val filteredCredentials = credentials.map { credential ->
                createDerivedCredentialForDisclosure(credential, disclosedFields)
            }
            return createPresentation(filteredCredentials, holderDid, options)
        }
    }

    /**
     * Create a derived credential with only disclosed fields.
     */
    private fun createDerivedCredentialForDisclosure(
        credential: VerifiableCredential,
        disclosedFields: List<String>
    ): VerifiableCredential {
        val json = kotlinx.serialization.json.Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

        // Serialize credential to JSON
        val credentialJson = json.encodeToJsonElement(
            com.trustweave.credential.model.vc.VerifiableCredential.serializer(),
            credential
        ).jsonObject

        // Create derived credential with only disclosed fields
        val derivedJson = buildJsonObject {
            // Always include core fields
            credentialJson["id"]?.let { put("id", it) }
            put("type", credentialJson["type"] ?: kotlinx.serialization.json.buildJsonArray { })
            put("issuer", credentialJson["issuer"] ?: kotlinx.serialization.json.JsonPrimitive(""))
            put("issuanceDate", credentialJson["issuanceDate"] ?: kotlinx.serialization.json.JsonPrimitive(""))

            // Include disclosed fields from credentialSubject
            val subjectJson = credentialJson["credentialSubject"]?.jsonObject
            if (subjectJson != null) {
                val derivedSubject = buildJsonObject {
                    // Always include id
                    subjectJson["id"]?.let { put("id", it) }

                    // Include only disclosed fields
                    for (fieldPath in disclosedFields) {
                        val fieldName = if (fieldPath.contains(".")) {
                            fieldPath.substringAfterLast(".")
                        } else {
                            fieldPath
                        }

                        // Check if this field should be disclosed
                        if (fieldPath.startsWith("credentialSubject.") || fieldPath == fieldName) {
                            subjectJson[fieldName]?.let { put(fieldName, it) }
                        }
                    }
                }
                put("credentialSubject", derivedSubject)
            } else {
                // If no subject, include it as-is
                credentialJson["credentialSubject"]?.let { put("credentialSubject", it) }
            }
        }

        // Parse back to VerifiableCredential
        return json.decodeFromJsonElement(
            com.trustweave.credential.model.vc.VerifiableCredential.serializer(),
            derivedJson
        )
    }

    /**
     * Get proof generator by type.
     */
    private fun getProofGenerator(proofType: String): ProofGenerator =
        proofRegistry.get(proofType)
            ?: throw IllegalArgumentException("No proof generator registered for type: $proofType")
    
    /**
     * Convert Proof (from CredentialModels) to CredentialProof (from model.vc).
     */
    private fun convertProofToCredentialProof(proof: com.trustweave.credential.models.Proof): CredentialProof {
        return when {
            proof.jws != null -> {
                // JWT proof
                CredentialProof.JwtProof(jwt = proof.jws)
            }
            proof.proofValue != null -> {
                // Linked Data Proof
                CredentialProof.LinkedDataProof(
                    type = proof.type.identifier,
                    created = Instant.parse(proof.created),
                    verificationMethod = proof.verificationMethod.value,
                    proofPurpose = proof.proofPurpose,
                    proofValue = proof.proofValue,
                    additionalProperties = emptyMap()
                )
            }
            else -> {
                throw IllegalArgumentException("Proof must have either proofValue or jws")
            }
        }
    }
}

