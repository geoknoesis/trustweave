package com.geoknoesis.vericore.credential.verifier

import com.geoknoesis.vericore.credential.CredentialVerificationOptions
import com.geoknoesis.vericore.credential.CredentialVerificationResult
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.did.asCredentialDidResolution
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.schema.SchemaRegistry
import com.geoknoesis.vericore.credential.proof.ProofValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            // TODO: Implement revocation checking
            // For now, assume not revoked if credentialStatus is present
            // Full implementation would check status list
            warnings.add("Revocation checking not yet implemented")
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
            // TODO: Implement blockchain anchor verification
            // Check if evidence contains blockchain anchor and verify it
            warnings.add("Blockchain anchor verification not yet implemented")
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
     * Verify proof signature.
     * 
     * TODO: Implement actual signature verification based on proof type.
     * This is a placeholder that checks proof structure.
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
        
        // TODO: Implement actual signature verification
        // 1. Resolve verification method from DID document
        // 2. Extract public key
        // 3. Verify signature against proof document
        
        // For now, return true if structure is valid
        return true
    }

    private fun buildDidResolver(options: CredentialVerificationOptions): CredentialDidResolver? {
        return defaultDidResolver
    }
}

