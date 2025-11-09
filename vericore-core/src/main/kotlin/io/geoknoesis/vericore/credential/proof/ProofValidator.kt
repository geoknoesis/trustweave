package io.geoknoesis.vericore.credential.proof

import io.geoknoesis.vericore.core.VeriCoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Validates proof purposes against DID Document verification relationships.
 * 
 * Ensures that proofs are used only for their intended purposes as defined
 * in the DID Document's verification relationships.
 * 
 * **Example Usage**:
 * ```kotlin
 * val validator = ProofValidator(
 *     resolveDid = { did -> didRegistry.resolve(did) }
 * )
 * 
 * val result = validator.validateProofPurpose(
 *     proofPurpose = "assertionMethod",
 *     verificationMethod = "did:key:issuer#key-1",
 *     issuerDid = "did:key:issuer"
 * )
 * 
 * if (result.valid) {
 *     println("Proof purpose is valid")
 * }
 * ```
 */
class ProofValidator(
    private val resolveDid: suspend (String) -> Any? // DidResolutionResult? - using Any to avoid dependency
) {
    /**
     * Validates that a proof purpose matches the verification relationship in the DID Document.
     * 
     * @param proofPurpose The proof purpose (e.g., "assertionMethod", "capabilityInvocation")
     * @param verificationMethod The verification method reference (DID URL or relative reference)
     * @param issuerDid The DID of the issuer
     * @return ProofPurposeValidationResult with validity and errors
     */
    suspend fun validateProofPurpose(
        proofPurpose: String,
        verificationMethod: String,
        issuerDid: String
    ): ProofPurposeValidationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        
        // Resolve issuer DID document using reflection
        val resolutionResult = resolveDid(issuerDid) as? Any
        if (resolutionResult == null) {
            return@withContext ProofPurposeValidationResult(
                valid = false,
                errors = listOf("Failed to resolve issuer DID: $issuerDid")
            )
        }
        
        val document = try {
            val getDocumentMethod = resolutionResult.javaClass.getMethod("getDocument")
            getDocumentMethod.invoke(resolutionResult) as? Any
        } catch (e: Exception) {
            null
        }
        
        if (document == null) {
            return@withContext ProofPurposeValidationResult(
                valid = false,
                errors = listOf("Issuer DID document not found: $issuerDid")
            )
        }
        
        // Normalize verification method reference
        val normalizedVmRef = normalizeVerificationMethodReference(verificationMethod, issuerDid)
        
        // Check if verification method exists in document using reflection
        // Note: We check verificationMethod list, but if a relationship references it, that's also valid
        val verificationMethods = try {
            val getVerificationMethodMethod = document.javaClass.getMethod("getVerificationMethod")
            val result = getVerificationMethodMethod.invoke(document) as? List<*>
            result ?: emptyList<Any>()
        } catch (e: Exception) {
            emptyList<Any>()
        }
        
        // Check if VM exists in verificationMethod list OR if it's referenced in relationships
        // This allows relationships to reference VMs even if not explicitly in verificationMethod
        val vmExistsInList = verificationMethods.any { vm ->
            try {
                val getIdMethod = vm?.javaClass?.getMethod("getId")
                val vmId = getIdMethod?.invoke(vm)?.toString()
                vmId == normalizedVmRef || vmId == verificationMethod
            } catch (e: Exception) {
                false
            }
        }
        
        // Check if proof purpose matches verification relationship using reflection
        // If the relationship matches, we consider it valid even if VM not in verificationMethod list
        val isValid = when (proofPurpose) {
            "assertionMethod" -> {
                val assertionMethod = try {
                    val getMethod = document.javaClass.getMethod("getAssertionMethod")
                    val result = getMethod.invoke(document) as? List<*>
                    result?.mapNotNull { it?.toString() } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                assertionMethod.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod || 
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "authentication" -> {
                val authentication = try {
                    val getMethod = document.javaClass.getMethod("getAuthentication")
                    val result = getMethod.invoke(document) as? List<*>
                    result?.mapNotNull { it?.toString() } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                authentication.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "keyAgreement" -> {
                val keyAgreement = try {
                    val getMethod = document.javaClass.getMethod("getKeyAgreement")
                    val result = getMethod.invoke(document) as? List<*>
                    result?.mapNotNull { it?.toString() } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                keyAgreement.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "capabilityInvocation" -> {
                val capabilityInvocation = try {
                    val getMethod = document.javaClass.getMethod("getCapabilityInvocation")
                    val result = getMethod.invoke(document) as? List<*>
                    result?.mapNotNull { it?.toString() } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                capabilityInvocation.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "capabilityDelegation" -> {
                val capabilityDelegation = try {
                    val getMethod = document.javaClass.getMethod("getCapabilityDelegation")
                    val result = getMethod.invoke(document) as? List<*>
                    result?.mapNotNull { it?.toString() } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                capabilityDelegation.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            else -> {
                errors.add("Unknown proof purpose: $proofPurpose")
                false
            }
        }
        
        if (!isValid) {
            errors.add("Proof purpose '$proofPurpose' does not match verification relationship in DID document")
        }
        
        // If VM not in verificationMethod list but relationship matches, that's acceptable
        // However, if VM not in list AND relationship doesn't match, add warning
        if (!vmExistsInList && isValid && verificationMethods.isNotEmpty()) {
            // VM referenced in relationship but not in verificationMethod list - this is valid but not ideal
        }
        
        ProofPurposeValidationResult(
            valid = isValid,
            errors = errors
        )
    }
    
    /**
     * Normalizes a verification method reference to a full DID URL.
     */
    private fun normalizeVerificationMethodReference(ref: String, issuerDid: String): String {
        return when {
            ref.startsWith("did:") -> ref // Already a full DID URL
            ref.startsWith("#") -> "$issuerDid$ref" // Relative reference
            else -> {
                // Try to construct full reference
                if (ref.contains("#")) {
                    ref
                } else {
                    "$issuerDid#$ref"
                }
            }
        }
    }
}

/**
 * Result of proof purpose validation.
 * 
 * @param valid Whether the proof purpose is valid
 * @param errors List of error messages if validation failed
 */
data class ProofPurposeValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList()
)
