package com.trustweave.credential.proof

import com.trustweave.did.resolution.DidResolver
import com.trustweave.did.services.DidDocumentAccess
import com.trustweave.did.services.VerificationMethodAccess
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
 * val validator = ProofValidator(didResolver)
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
    private val didResolver: DidResolver,
    private val documentAccess: DidDocumentAccess,
    private val verificationMethodAccess: VerificationMethodAccess
) {

    constructor(
        resolveDid: suspend (String) -> com.trustweave.did.DidResolutionResult?,
        documentAccess: DidDocumentAccess,
        verificationMethodAccess: VerificationMethodAccess
    ) : this(
        DidResolver { did -> resolveDid(did) },
        documentAccess,
        verificationMethodAccess
    )

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
        
        // Services are required and provided via constructor
        val docAccess = documentAccess
        val vmAccess = verificationMethodAccess
        
        // Resolve issuer DID document
        val resolutionResult = didResolver.resolve(issuerDid)
        if (resolutionResult == null || resolutionResult.document == null) {
            return@withContext ProofPurposeValidationResult(
                valid = false,
                errors = listOf("Failed to resolve issuer DID: $issuerDid")
            )
        }
        
        val document = resolutionResult.document
        
        // Normalize verification method reference
        val normalizedVmRef = normalizeVerificationMethodReference(verificationMethod, issuerDid)
        
        // Check if verification method exists in document
        val verificationMethods = docAccess.getVerificationMethod(document)
        
        // Check if VM exists in verificationMethod list
        val vmExistsInList = verificationMethods.any { vm ->
            try {
                val vmId = vmAccess.getId(vm)
                vmId == normalizedVmRef || vmId == verificationMethod
            } catch (e: Exception) {
                false
            }
        }
        
        // Check if proof purpose matches verification relationship
        val isValid = when (proofPurpose) {
            "assertionMethod" -> {
                val assertionMethod = docAccess.getAssertionMethod(document)
                assertionMethod.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod || 
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "authentication" -> {
                val authentication = docAccess.getAuthentication(document)
                authentication.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "keyAgreement" -> {
                val keyAgreement = docAccess.getKeyAgreement(document)
                keyAgreement.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "capabilityInvocation" -> {
                val capabilityInvocation = docAccess.getCapabilityInvocation(document)
                capabilityInvocation.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "capabilityDelegation" -> {
                val capabilityDelegation = docAccess.getCapabilityDelegation(document)
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
