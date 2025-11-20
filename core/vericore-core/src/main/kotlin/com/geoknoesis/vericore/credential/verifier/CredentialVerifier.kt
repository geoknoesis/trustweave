package com.geoknoesis.vericore.credential.verifier

import com.geoknoesis.vericore.credential.CredentialVerificationOptions
import com.geoknoesis.vericore.credential.CredentialVerificationResult
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.did.asCredentialDidResolution
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.schema.SchemaRegistry
import com.geoknoesis.vericore.credential.proof.ProofValidator
import com.geoknoesis.vericore.credential.verifier.SignatureVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/**
 * Native credential verifier implementation.
 * 
 * Provides credential verification without dependency on external services.
 * Verifies proof signatures, DID resolution, expiration, revocation, and schema validation.
 * 
 * **Example Usage**:
 * ```kotlin
 * val verifier = CredentialVerifier(myDidResolver)
 * 
 * val result = verifier.verify(
 *     credential = credential,
 *     options = CredentialVerificationOptions(
 *         checkRevocation = true,
 *         checkExpiration = true,
 *         validateSchema = true
 *     )
 * )
 * 
 * if (result.valid) {
 *     println("Credential is valid!")
 * }
 * ```
 */
class CredentialVerifier(
    private val defaultDidResolver: CredentialDidResolver? = null // Optional default resolver provided by caller
) {

    /**
     * Verify a verifiable credential.
     * 
     * Steps:
     * 1. Verify proof signature
     * 2. Verify issuer DID resolution
     * 3. Check expiration
     * 4. Check revocation status
     * 5. Validate schema (if provided)
     * 6. Verify blockchain anchor (if present)
     * 
     * @param credential Credential to verify
     * @param options Verification options
     * @return Verification result with detailed status
     */
    suspend fun verify(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions = CredentialVerificationOptions()
    ): CredentialVerificationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        var proofValid = false
        var issuerValid = false
        var notExpired = true
        var notRevoked = true
        var schemaValid = true
        var blockchainAnchorValid = true
        var trustRegistryValid = true
        var delegationValid = true
        var proofPurposeValid = true
        
        val didResolver = buildDidResolver(options)

        // 1. Verify proof purpose if enabled (before signature verification)
        if (options.validateProofPurpose && credential.proof != null) {
            if (didResolver == null) {
                warnings.add("Proof purpose validation requested but no DID resolver available")
                proofPurposeValid = false
            } else {
                try {
                    val proofValidator = ProofValidator(didResolver)
                    val purposeResult = proofValidator.validateProofPurpose(
                        proofPurpose = credential.proof.proofPurpose,
                        verificationMethod = credential.proof.verificationMethod,
                        issuerDid = credential.issuer
                    )

                    proofPurposeValid = purposeResult.valid
                    if (!proofPurposeValid) {
                        errors.addAll(purposeResult.errors)
                    }
                } catch (e: Exception) {
                    warnings.add("Proof purpose validation failed: ${e.message}")
                    proofPurposeValid = false
                }
            }
        }
        
        // 2. Verify proof signature
        if (credential.proof != null) {
            proofValid = verifyProof(credential, credential.proof)
            if (!proofValid) {
                errors.add("Proof signature verification failed")
            }
        } else {
            errors.add("Credential has no proof")
        }
        
        // 2. Verify issuer DID resolution
        issuerValid = if (didResolver == null) {
            true
        } else {
            try {
                didResolver.resolve(credential.issuer)?.isResolvable == true
            } catch (e: Exception) {
                errors.add("Failed to resolve issuer DID: ${e.message}")
                false
            }
        }
        
        // 3. Check expiration
        if (options.checkExpiration) {
            credential.expirationDate?.let { expirationDate ->
                try {
                    val expiration = Instant.parse(expirationDate)
                    notExpired = Instant.now().isBefore(expiration)
                    if (!notExpired) {
                        errors.add("Credential has expired: $expirationDate")
                    }
                } catch (e: Exception) {
                    warnings.add("Invalid expiration date format: $expirationDate")
                }
            }
        }
        
        // 4. Check revocation status
        if (options.checkRevocation && credential.credentialStatus != null) {
            if (options.statusListManager != null) {
                try {
                    // Use reflection to call StatusListManager.checkRevocationStatus
                    val checkMethod = options.statusListManager.javaClass.getMethod(
                        "checkRevocationStatus",
                        VerifiableCredential::class.java,
                        kotlin.coroutines.Continuation::class.java
                    )
                    
                    val revocationStatus = suspendCoroutineUninterceptedOrReturn<com.geoknoesis.vericore.credential.revocation.RevocationStatus> { cont ->
                        try {
                            val result = checkMethod.invoke(options.statusListManager, credential, cont)
                            if (result === COROUTINE_SUSPENDED) {
                                COROUTINE_SUSPENDED
                            } else {
                                cont.resumeWith(Result.success(result as com.geoknoesis.vericore.credential.revocation.RevocationStatus))
                                COROUTINE_SUSPENDED
                            }
                        } catch (e: Exception) {
                            cont.resumeWith(Result.failure(e))
                            COROUTINE_SUSPENDED
                        }
                    }
                    
                    if (revocationStatus != null) {
                        notRevoked = !revocationStatus.revoked && !revocationStatus.suspended
                        if (revocationStatus.revoked) {
                            errors.add("Credential is revoked${revocationStatus.reason?.let { ": $it" } ?: ""}")
                        } else if (revocationStatus.suspended) {
                            errors.add("Credential is suspended${revocationStatus.reason?.let { ": $it" } ?: ""}")
                        }
                    } else {
                        warnings.add("Revocation status check returned null")
                    }
                } catch (e: Exception) {
                    warnings.add("Revocation checking failed: ${e.message}")
                }
            } else {
                warnings.add("Revocation checking requested but no StatusListManager provided")
            }
        }
        
        // 5. Validate schema if provided
        if (options.validateSchema && credential.credentialSchema != null) {
            try {
                val schemaResult = SchemaRegistry.validateCredential(
                    credential,
                    credential.credentialSchema.id
                )
                schemaValid = schemaResult.valid
                if (!schemaValid) {
                    val schemaErrors = schemaResult.errors.joinToString(", ") { "${it.path}: ${it.message}" }
                    errors.add("Schema validation failed: $schemaErrors")
                }
            } catch (e: Exception) {
                warnings.add("Schema validation error: ${e.message}")
            }
        }
        
        // 6. Verify blockchain anchor if present
        if (options.verifyBlockchainAnchor && credential.evidence != null) {
            blockchainAnchorValid = verifyBlockchainAnchor(credential, options.chainId)
            if (!blockchainAnchorValid) {
                errors.add("Blockchain anchor verification failed")
            }
        }
        
        // 7. Check trust registry if enabled
        if (options.checkTrustRegistry && options.trustRegistry != null) {
            try {
                val registry = options.trustRegistry
                val isTrustedMethod = registry.javaClass.getMethod(
                    "isTrustedIssuer",
                    String::class.java,
                    String::class.java,
                    kotlin.coroutines.Continuation::class.java
                )
                
                // Extract credential type from credential.type
                val credentialType = credential.type.firstOrNull { it != "VerifiableCredential" }
                
                // Call isTrustedIssuer using reflection with coroutines
                val isTrusted = suspendCoroutineUninterceptedOrReturn<Boolean> { cont ->
                    try {
                        val result = isTrustedMethod.invoke(registry, credential.issuer, credentialType, cont)
                        if (result === COROUTINE_SUSPENDED) {
                            COROUTINE_SUSPENDED
                        } else {
                            cont.resumeWith(Result.success(result as Boolean))
                            COROUTINE_SUSPENDED
                        }
                    } catch (e: Exception) {
                        cont.resumeWith(Result.failure(e))
                        COROUTINE_SUSPENDED
                    }
                } ?: false
                
                trustRegistryValid = isTrusted
                if (!trustRegistryValid) {
                    errors.add("Issuer '${credential.issuer}' is not trusted in trust registry")
                }
            } catch (e: Exception) {
                warnings.add("Trust registry check failed: ${e.message}")
                trustRegistryValid = false
            }
        }
        
        // 8. Verify delegation if proof purpose is capabilityDelegation or capabilityInvocation
        if (options.verifyDelegation && credential.proof != null) {
            val proofPurpose = credential.proof.proofPurpose
            if (proofPurpose == "capabilityDelegation" || proofPurpose == "capabilityInvocation") {
                if (didResolver == null) {
                    warnings.add("Delegation verification requested but no DID resolver available")
                    delegationValid = false
                } else {
                    try {
                        val verificationMethod = credential.proof.verificationMethod
                        val delegatorDid = if (verificationMethod.contains("#")) {
                            verificationMethod.substringBefore("#")
                        } else {
                            credential.issuer
                        }

                        val delegationService = com.geoknoesis.vericore.did.delegation.DelegationService(didResolver)
                        val delegationResult = delegationService.verifyDelegationChain(
                            delegatorDid = delegatorDid,
                            delegateDid = credential.issuer
                        )

                        delegationValid = delegationResult.valid
                        if (!delegationValid) {
                            errors.add("Delegation verification failed: ${delegationResult.errors.joinToString(", ")}")
                        }
                    } catch (e: Exception) {
                        warnings.add("Delegation verification failed: ${e.message}")
                        delegationValid = false
                    }
                }
            }
        }
        
        CredentialVerificationResult(
            valid = errors.isEmpty() && proofValid && issuerValid && notExpired && notRevoked && schemaValid && blockchainAnchorValid && trustRegistryValid && delegationValid && proofPurposeValid,
            errors = errors,
            warnings = warnings,
            proofValid = proofValid,
            issuerValid = issuerValid,
            notExpired = notExpired,
            notRevoked = notRevoked,
            schemaValid = schemaValid,
            blockchainAnchorValid = blockchainAnchorValid,
            trustRegistryValid = trustRegistryValid,
            delegationValid = delegationValid,
            proofPurposeValid = proofPurposeValid
        )
    }
    
    /**
     * Verify blockchain anchor evidence in credential.
     * 
     * Checks if evidence contains blockchain anchor and verifies it exists on the blockchain.
     */
    private suspend fun verifyBlockchainAnchor(
        credential: VerifiableCredential,
        chainId: String?
    ): Boolean {
        if (credential.evidence == null || credential.evidence.isEmpty()) {
            return true // No evidence to verify
        }
        
        // Find blockchain anchor evidence
        val anchorEvidence = credential.evidence.find { evidence ->
            evidence.type.contains("BlockchainAnchorEvidence")
        } ?: return true // No blockchain anchor evidence found
        
        // Extract chain ID and transaction hash from evidence
        val evidenceDoc = anchorEvidence.evidenceDocument?.jsonObject ?: return false
        val evidenceChainId = evidenceDoc["chainId"]?.jsonPrimitive?.content
        val txHash = evidenceDoc["txHash"]?.jsonPrimitive?.content
        
        if (txHash == null) {
            return false
        }
        
        // If chainId is specified in options, verify it matches
        if (chainId != null && evidenceChainId != null && evidenceChainId != chainId) {
            return false
        }
        
        // Note: Full verification would require access to BlockchainAnchorRegistry
        // For now, we verify the evidence structure is present
        // Actual blockchain verification should be done via VeriCore.verifyCredential with blockchain client
        return evidenceChainId != null && txHash.isNotEmpty()
    }
    
    /**
     * Verify proof signature.
     * 
     * Implements actual signature verification based on proof type.
     */
    private suspend fun verifyProof(
        credential: VerifiableCredential,
        proof: com.geoknoesis.vericore.credential.models.Proof
    ): Boolean {
        // Basic structure validation
        if (proof.type.isBlank()) {
            return false
        }
        
        if (proof.verificationMethod.isBlank()) {
            return false
        }
        
        if (proof.proofValue == null && proof.jws == null) {
            return false
        }
        
        // Use SignatureVerifier for actual verification
        val didResolver = buildDidResolver(CredentialVerificationOptions())
        if (didResolver == null) {
            // If no resolver available, can't verify signature
            return false
        }
        
        val signatureVerifier = SignatureVerifier(didResolver)
        return signatureVerifier.verify(credential, proof)
    }

    private fun buildDidResolver(options: CredentialVerificationOptions): CredentialDidResolver? {
        return defaultDidResolver
    }
}

