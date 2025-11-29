package com.trustweave.credential.proof

import com.trustweave.did.DidDocument
import com.trustweave.did.VerificationMethod
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
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
    private val didResolver: DidResolver
) {

    constructor(
        resolveDid: suspend (String) -> DidResolutionResult?
    ) : this(
        DidResolver { did -> resolveDid(did.value) }
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

        // Resolve issuer DID document
        val resolutionResult = try {
            didResolver.resolve(com.trustweave.core.types.Did(issuerDid))
        } catch (e: IllegalArgumentException) {
            // Invalid DID format
            return@withContext ProofPurposeValidationResult(
                valid = false,
                errors = listOf("Failed to resolve issuer DID: $issuerDid (${e.message})")
            )
        }
        val document = when (resolutionResult) {
            is DidResolutionResult.Success -> resolutionResult.document
            else -> {
                return@withContext ProofPurposeValidationResult(
                    valid = false,
                    errors = listOf("Failed to resolve issuer DID: $issuerDid")
                )
            }
        }

        // Normalize verification method reference
        val normalizedVmRef = normalizeVerificationMethodReference(verificationMethod, issuerDid)

        // Check if verification method exists in document
        val verificationMethods = document.verificationMethod

        // Check if VM exists in verificationMethod list
        val vmExistsInList = verificationMethods.any { vm ->
            vm.id == normalizedVmRef || vm.id == verificationMethod
        }

        // Check if proof purpose matches verification relationship
        val isValid = when (proofPurpose) {
            "assertionMethod" -> {
                document.assertionMethod.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "authentication" -> {
                document.authentication.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "keyAgreement" -> {
                document.keyAgreement.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "capabilityInvocation" -> {
                document.capabilityInvocation.any { ref ->
                    ref == normalizedVmRef || ref == verificationMethod ||
                    ref == "#${verificationMethod.substringAfterLast("#")}"
                }
            }
            "capabilityDelegation" -> {
                document.capabilityDelegation.any { ref ->
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
