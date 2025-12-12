package com.trustweave.credential.issuer

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.proof.ProofGeneratorOptions
import com.trustweave.credential.schema.SchemaRegistry
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*

/**
 * Native credential issuer implementation.
 *
 * Provides credential issuance without dependency on external services.
 * Uses pluggable proof generators and integrates with schema systems.
 *
 * **Example Usage**:
 * ```kotlin
 * val issuer = CredentialIssuer(
 *     proofGenerator = Ed25519ProofGenerator { data, keyId -> kms.sign(keyId, data) },
 *     resolveDid = { did -> didRegistry.resolve(did) }
 * )
 *
 * val credential = issuer.issue(
 *     credential = credentialWithoutProof,
 *     issuerDid = "did:key:...",
 *     keyId = "key-1",
 *     options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
 * )
 * ```
 */
class CredentialIssuer(
    private val proofGenerator: ProofGenerator? = null,
    private val resolveDid: suspend (String) -> Boolean = { true }, // Default: assume DID is valid
    private val proofRegistry: ProofGeneratorRegistry = ProofGeneratorRegistry()
) {
    /**
     * Issue a verifiable credential.
     *
     * Steps:
     * 1. Validate credential structure
     * 2. Resolve issuer DID (if needed)
     * 3. Validate schema (if provided)
     * 4. Generate proof
     * 5. Add proof to credential
     * 6. Optionally anchor digest to blockchain
     *
     * @param credential Credential to issue (without proof)
     * @param issuerDid DID of the issuer
     * @param keyId Key ID for signing
     * @param options Issuance options
     * @return Issued credential with proof
     */
    suspend fun issue(
        credential: VerifiableCredential,
        issuerDid: String,
        keyId: String,
        options: CredentialIssuanceOptions = CredentialIssuanceOptions()
    ): VerifiableCredential = withContext(Dispatchers.IO) {
        // 1. Validate credential structure
        validateCredentialStructure(credential)

        // 2. Verify issuer DID matches
        if (credential.issuer.id.value != issuerDid) {
            throw IllegalArgumentException("Credential issuer '${credential.issuer.id.value}' does not match provided issuerDid '$issuerDid'")
        }

        // 3. Resolve issuer DID to verify it exists
        resolveIssuerDid(issuerDid)

        // 4. Validate schema if provided
        credential.credentialSchema?.let { schema ->
            validateSchema(credential, schema.id)
        }

        // 5. Get proof generator
        val generator = proofGenerator ?: getProofGenerator(options.proofType)

        // 6. Generate proof
        val proof = generator.generateProof(
            credential = credential,
            keyId = keyId,
            options = ProofGeneratorOptions(
                proofPurpose = "assertionMethod",
                challenge = options.challenge,
                domain = options.domain,
                verificationMethod = options.additionalOptions["verificationMethod"] as? String
            )
        )

        // 7. Convert Proof to CredentialProof and add to credential
        val credentialProof = convertProofToCredentialProof(proof)
        credential.copy(proof = credentialProof)

        // 8. Optionally anchor to blockchain (handled by caller or separate service)
    }

    /**
     * Validate credential structure.
     */
    private fun validateCredentialStructure(credential: VerifiableCredential) {
        if (!credential.type.any { it.value == "VerifiableCredential" }) {
            throw IllegalArgumentException("Credential type must include 'VerifiableCredential'")
        }

        if (credential.issuer.id.value.isBlank()) {
            throw IllegalArgumentException("Credential issuer is required")
        }
        // issuanceDate is Instant, not String, so no need to check isBlank
    }

    /**
     * Resolve issuer DID to verify it exists.
     */
    private suspend fun resolveIssuerDid(issuerDid: String): Boolean {
        return try {
            resolveDid(issuerDid)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to resolve issuer DID: $issuerDid", e)
        }
    }

    /**
     * Validate credential against schema.
     */
    private suspend fun validateSchema(credential: VerifiableCredential, schemaId: SchemaId) {
        val result = com.trustweave.credential.schema.SchemaRegistries.default().validate(credential, schemaId)
        if (!result.valid) {
            val errors = result.errors.joinToString(", ") { error -> "${error.path}: ${error.message}" }
            throw IllegalArgumentException("Credential validation failed against schema '${schemaId.value}': $errors")
        }
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
                val additionalProperties = buildMap<String, kotlinx.serialization.json.JsonElement> {
                    proof.challenge?.let { put("challenge", kotlinx.serialization.json.JsonPrimitive(it)) }
                    proof.domain?.let { put("domain", kotlinx.serialization.json.JsonPrimitive(it)) }
                }
                CredentialProof.LinkedDataProof(
                    type = proof.type.identifier,
                    created = Instant.parse(proof.created),
                    verificationMethod = proof.verificationMethod.value,
                    proofPurpose = proof.proofPurpose,
                    proofValue = proof.proofValue,
                    additionalProperties = additionalProperties
                )
            }
            else -> {
                throw IllegalArgumentException("Proof must have either proofValue or jws")
            }
        }
    }
}

