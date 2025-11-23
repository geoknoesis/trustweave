package com.trustweave.services

import com.trustweave.TrustWeaveContext
import com.trustweave.core.*
import com.trustweave.core.normalizeKeyId
import com.trustweave.core.types.ProofType
import com.trustweave.credential.*
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation
import com.trustweave.credential.issuer.CredentialIssuer
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.verifier.CredentialVerifier
import com.trustweave.credential.did.CredentialDidResolver
import com.trustweave.credential.presentation.PresentationService
import com.trustweave.did.toCredentialDidResolution
import com.trustweave.kms.KeyManagementService
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Focused service for credential operations.
 * 
 * Provides a clean, focused API for issuing, verifying credentials and creating presentations.
 * 
 * **Example:**
 * ```kotlin
 * val TrustWeave = TrustWeave.create()
 * val credential = trustweave.credentials.issue(
 *     issuer = "did:key:issuer",
 *     subject = buildJsonObject { put("name", "Alice") },
 *     config = IssuanceConfig(proofType = ProofType.Ed25519Signature2020, keyId = "key-1")
 * )
 * ```
 */
class CredentialService(
    private val context: TrustWeaveContext
) {
    /**
     * Issues a verifiable credential.
     * 
     * **Example:**
     * ```kotlin
     * val credential = trustweave.credentials.issue(
     *     issuer = "did:key:issuer",
     *     subject = buildJsonObject {
     *         put("id", "did:key:subject")
     *         put("name", "Alice")
     *     },
     *     config = IssuanceConfig(
     *         proofType = ProofType.Ed25519Signature2020,
     *         keyId = "key-1",
     *         issuerDid = "did:key:issuer"
     *     )
     * )
     * ```
     * 
     * @param issuer DID of the credential issuer
     * @param subject The credential subject as JSON
     * @param config Issuance configuration
     * @param types Credential types (VerifiableCredential is added automatically)
     * @param expirationDate Optional expiration date (ISO 8601)
     * @return The issued credential with proof
     */
    suspend fun issue(
        issuer: String,
        subject: JsonElement,
        config: IssuanceConfig,
        types: List<String> = listOf("VerifiableCredential"),
        expirationDate: String? = null
    ): VerifiableCredential {
        // Validate issuer DID format
        DidValidator.validateFormat(issuer).let {
            if (!it.isValid()) {
                throw TrustWeaveError.InvalidDidFormat(
                    did = issuer,
                    reason = it.errorMessage() ?: "Invalid issuer DID format"
                )
            }
        }
        
        // Validate issuer DID method is registered
        val availableMethods = context.getAvailableDidMethods()
        DidValidator.validateMethod(issuer, availableMethods).let {
            if (!it.isValid()) {
                throw TrustWeaveError.DidMethodNotRegistered(
                    method = DidValidator.extractMethod(issuer) ?: "unknown",
                    availableMethods = availableMethods
                )
            }
        }
        
        val normalizedKeyId = normalizeKeyId(config.keyId)
        
        // Build verification method ID - use the original keyId if it's a full URL, otherwise construct from issuer DID
        val verificationMethodId = if (config.keyId.contains("#")) {
            // Key ID is already a full verification method URL (e.g., "did:key:z6Mk...#key-1")
            config.keyId
        } else if (config.keyId.startsWith("did:")) {
            // Key ID is a full DID URL (unlikely but handle it)
            config.keyId
        } else {
            // Key ID is just a fragment (e.g., "key-1"), construct full URL from issuer DID
            "$issuer#${normalizedKeyId}"
        }
        
        val credentialId = "urn:uuid:${UUID.randomUUID()}"
        val credential = VerifiableCredential(
            id = credentialId,
            type = if ("VerifiableCredential" in types) types else listOf("VerifiableCredential") + types,
            issuer = issuer,
            credentialSubject = subject,
            issuanceDate = java.time.Instant.now().toString(),
            expirationDate = expirationDate
        )
        
        // Validate credential structure
        CredentialValidator.validateStructure(credential).let {
            if (!it.isValid()) {
                throw TrustWeaveError.CredentialInvalid(
                    reason = it.errorMessage() ?: "Invalid credential structure",
                    credentialId = credentialId,
                    field = (it as? ValidationResult.Invalid)?.field
                )
            }
        }
        
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, keyId -> context.kms.sign(normalizeKeyId(keyId), data) }
        )
        
        val issuerService = CredentialIssuer(proofGenerator)
        
        // Create issuance options with verification method ID
        val issuanceOptions = CredentialIssuanceOptions(
            proofType = config.proofType.identifier,
            keyId = normalizedKeyId,
            issuerDid = issuer,
            challenge = config.challenge,
            domain = config.domain,
            anchorToBlockchain = config.anchorToBlockchain,
            chainId = config.chainId,
            additionalOptions = config.additionalOptions + mapOf("verificationMethod" to verificationMethodId)
        )
        
        return issuerService.issue(credential, issuer, normalizedKeyId, issuanceOptions)
    }
    
    /**
     * Verifies a verifiable credential.
     * 
     * **Example:**
     * ```kotlin
     * val result = trustweave.credentials.verify(credential)
     * if (result.valid) {
     *     println("Credential is valid")
     * } else {
     *     println("Errors: ${result.errors}")
     * }
     * ```
     * 
     * @param credential The credential to verify
     * @param config Verification configuration
     * @return Verification result with detailed status
     */
    suspend fun verify(
        credential: VerifiableCredential,
        config: VerificationConfig = VerificationConfig()
    ): CredentialVerificationResult {
        val effectiveResolver = CredentialDidResolver { did ->
            runCatching { context.resolveDid(did) }
                .getOrNull()
                ?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(defaultDidResolver = effectiveResolver)
        
        val options = CredentialVerificationOptions(
            checkRevocation = config.checkRevocation,
            checkExpiration = config.checkExpiration,
            validateSchema = config.validateSchema,
            schemaId = config.schemaId,
            verifyBlockchainAnchor = config.verifyBlockchainAnchor,
            chainId = config.chainId,
            additionalOptions = config.additionalOptions
        )
        
        var result = verifier.verify(credential, options)
        
        // Enhance blockchain anchor verification if enabled
        val chainId = config.chainId
        if (config.verifyBlockchainAnchor && chainId != null && credential.evidence != null) {
            result = verifyBlockchainAnchorEvidence(credential, chainId, result)
        }
        
        return result
    }
    
    private suspend fun verifyBlockchainAnchorEvidence(
        credential: VerifiableCredential,
        chainId: String,
        currentResult: CredentialVerificationResult
    ): CredentialVerificationResult {
        val anchorEvidence = credential.evidence?.find { evidence ->
            evidence.type.contains("BlockchainAnchorEvidence")
        } ?: return currentResult
        
        val evidenceDoc = anchorEvidence.evidenceDocument?.jsonObject ?: return currentResult
        val evidenceChainId = evidenceDoc["chainId"]?.jsonPrimitive?.content
        val txHash = evidenceDoc["txHash"]?.jsonPrimitive?.content
        
        if (evidenceChainId != chainId || txHash == null) {
            return currentResult.copy(
                valid = false,
                blockchainAnchorValid = false,
                errors = currentResult.errors + "Blockchain anchor evidence chainId mismatch or missing txHash"
            )
        }
        
        val client = context.getBlockchainClient(chainId)
        if (client == null) {
            return currentResult.copy(
                warnings = currentResult.warnings + "Blockchain client not available for chain: $chainId",
                blockchainAnchorValid = false
            )
        }
        
        return try {
            val anchorRef = com.trustweave.anchor.AnchorRef(
                chainId = chainId,
                txHash = txHash
            )
            client.readPayload(anchorRef)
            
            currentResult.copy(blockchainAnchorValid = true)
        } catch (e: Exception) {
            currentResult.copy(
                valid = false,
                blockchainAnchorValid = false,
                errors = currentResult.errors + "Blockchain anchor verification failed: ${e.message}"
            )
        }
    }
}

/**
 * Type-safe issuance configuration.
 */
data class IssuanceConfig(
    val proofType: ProofType = ProofType.Ed25519Signature2020,
    val keyId: String,
    val issuerDid: String? = null,
    val challenge: String? = null,
    val domain: String? = null,
    val anchorToBlockchain: Boolean = false,
    val chainId: String? = null,
    val additionalOptions: Map<String, Any?> = emptyMap()
)

/**
 * Type-safe verification configuration.
 */
data class VerificationConfig(
    val checkRevocation: Boolean = true,
    val checkExpiration: Boolean = true,
    val validateSchema: Boolean = false,
    val schemaId: String? = null,
    val verifyBlockchainAnchor: Boolean = false,
    val chainId: String? = null,
    val checkTrustRegistry: Boolean = false,
    val additionalOptions: Map<String, Any?> = emptyMap()
)

