package io.geoknoesis.vericore.credential.proof

import io.geoknoesis.vericore.credential.did.CredentialDidResolver
import io.geoknoesis.vericore.credential.did.asCredentialDidResolution
import io.geoknoesis.vericore.spi.services.DidDocumentAccess
import io.geoknoesis.vericore.spi.services.VerificationMethodAccess
import io.geoknoesis.vericore.spi.services.AdapterLoader
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
    private val didResolver: CredentialDidResolver,
    private val documentAccess: DidDocumentAccess? = null, // Optional - will attempt reflective adapter if not provided
    private val verificationMethodAccess: VerificationMethodAccess? = null // Optional - will attempt reflective adapter if not provided
) {

    constructor(
        resolveDid: suspend (String) -> Any?,
        documentAccess: DidDocumentAccess? = null,
        verificationMethodAccess: VerificationMethodAccess? = null
    ) : this(
        CredentialDidResolver { did -> resolveDid(did).asCredentialDidResolution() },
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
        
        // Get required service instances
        val docAccess = documentAccess ?: AdapterLoader.didDocumentAccess()
        val vmAccess = verificationMethodAccess ?: AdapterLoader.verificationMethodAccess()
        
        // If services not available, throw error with helpful message
        if (docAccess == null || vmAccess == null) {
            throw IllegalStateException(
                "DidDocumentAccess or VerificationMethodAccess not available. " +
                "Ensure vericore-did module is on classpath by importing a class from it " +
                "(e.g., io.geoknoesis.vericore.did.DidDocument) to trigger service registration."
            )
        }
        
        // Resolve issuer DID document
        val resolutionResult = didResolver.resolve(issuerDid)
        if (resolutionResult == null || !resolutionResult.isResolvable) {
            return@withContext ProofPurposeValidationResult(
                valid = false,
                errors = listOf("Failed to resolve issuer DID: $issuerDid")
            )
        }
        
        val document = resolutionResult.document
            ?: resolutionResult.raw?.let { runCatching { docAccess.getDocument(it) }.getOrNull() }
            ?: docAccess.getDocument(resolutionResult)
        if (document == null) {
            return@withContext ProofPurposeValidationResult(
                valid = false,
                errors = listOf("Issuer DID document not found: $issuerDid")
            )
        }
        
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
