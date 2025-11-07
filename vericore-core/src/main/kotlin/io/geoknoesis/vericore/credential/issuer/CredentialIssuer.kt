package io.geoknoesis.vericore.credential.issuer

import io.geoknoesis.vericore.credential.CredentialIssuanceOptions
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.proof.ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.credential.proof.ProofOptions
import io.geoknoesis.vericore.credential.schema.SchemaRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val resolveDid: suspend (String) -> Boolean = { true } // Default: assume DID is valid
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
        if (credential.issuer != issuerDid) {
            throw IllegalArgumentException("Credential issuer '${credential.issuer}' does not match provided issuerDid '$issuerDid'")
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
            options = ProofOptions(
                proofPurpose = "assertionMethod",
                challenge = options.challenge,
                domain = options.domain,
                verificationMethod = options.additionalOptions["verificationMethod"] as? String
            )
        )
        
        // 7. Add proof to credential
        credential.copy(proof = proof)
        
        // 8. Optionally anchor to blockchain (handled by caller or separate service)
    }
    
    /**
     * Validate credential structure.
     */
    private fun validateCredentialStructure(credential: VerifiableCredential) {
        if (!credential.type.contains("VerifiableCredential")) {
            throw IllegalArgumentException("Credential type must include 'VerifiableCredential'")
        }
        
        if (credential.issuer.isBlank()) {
            throw IllegalArgumentException("Credential issuer is required")
        }
        
        if (credential.issuanceDate.isBlank()) {
            throw IllegalArgumentException("Credential issuanceDate is required")
        }
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
    private suspend fun validateSchema(credential: VerifiableCredential, schemaId: String) {
        val result = SchemaRegistry.validateCredential(credential, schemaId)
        if (!result.valid) {
            val errors = result.errors.joinToString(", ") { "${it.path}: ${it.message}" }
            throw IllegalArgumentException("Credential validation failed against schema '$schemaId': $errors")
        }
    }
    
    /**
     * Get proof generator by type.
     */
    private fun getProofGenerator(proofType: String): ProofGenerator {
        return ProofGeneratorRegistry.get(proofType)
            ?: throw IllegalArgumentException("No proof generator registered for type: $proofType")
    }
}

