package org.trustweave.credential.proof

import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest

/**
 * Extension functions for proof options to improve developer experience.
 */

/**
 * Create a copy with a different purpose.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForIssuance()
 *     .withPurpose(ProofPurpose.Authentication)
 * ```
 */
fun ProofOptions.withPurpose(purpose: ProofPurpose): ProofOptions {
    return copy(purpose = purpose)
}

/**
 * Create a copy with a challenge.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForIssuance()
 *     .withChallenge("nonce-123")
 * ```
 */
fun ProofOptions.withChallenge(challenge: String): ProofOptions {
    return copy(challenge = challenge)
}

/**
 * Create a copy with domain binding.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForIssuance()
 *     .withDomain("example.com")
 * ```
 */
fun ProofOptions.withDomain(domain: String): ProofOptions {
    return copy(domain = domain)
}

/**
 * Create a copy with verification method.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForIssuance()
 *     .withVerificationMethod("did:key:example#key-1")
 * ```
 */
fun ProofOptions.withVerificationMethod(verificationMethod: String): ProofOptions {
    return copy(verificationMethod = verificationMethod)
}


/**
 * Check if proof options are for authentication.
 */
fun ProofOptions.isForAuthentication(): Boolean {
    return purpose == ProofPurpose.Authentication
}

/**
 * Check if proof options are for issuance (assertion method).
 */
fun ProofOptions.isForIssuance(): Boolean {
    return purpose == ProofPurpose.AssertionMethod
}

/**
 * Check if proof options have a challenge.
 */
fun ProofOptions.hasChallenge(): Boolean {
    return challenge != null
}

/**
 * Check if proof options have domain binding.
 */
fun ProofOptions.hasDomain(): Boolean {
    return domain != null
}

/**
 * Builder extension for IssuanceRequest to add proof options.
 * 
 * **Example:**
 * ```kotlin
 * val request = IssuanceRequest(...)
 *     .withProofOptions {
 *         challenge = "nonce-123"
 *         domain = "example.com"
 *     }
 * ```
 */
fun IssuanceRequest.withProofOptions(block: ProofOptionsBuilder.() -> Unit = {}): IssuanceRequest {
    val options = proofOptions(block)
    return copy(proofOptions = options)
}

/**
 * Builder extension for IssuanceRequest to add proof options using convenience function.
 * 
 * **Example:**
 * ```kotlin
 * val request = IssuanceRequest(...)
 *     .withProofOptionsForIssuance()
 * 
 * // Or use the DSL builder
 * val request = IssuanceRequest(...)
 *     .withProofOptions {
 *         purpose = ProofPurpose.Authentication
 *         challenge = "nonce-123"
 *     }
 * ```
 */
fun IssuanceRequest.withProofOptionsForIssuance(
    verificationMethod: String? = null
): IssuanceRequest {
    return copy(proofOptions = proofOptionsForIssuance(verificationMethod))
}

/**
 * Builder extension for PresentationRequest to add proof options.
 * 
 * **Example:**
 * ```kotlin
 * val request = PresentationRequest(...)
 *     .withProofOptions {
 *         challenge = "nonce-123"
 *         domain = "example.com"
 *     }
 * ```
 */
fun PresentationRequest.withProofOptions(block: ProofOptionsBuilder.() -> Unit = {}): PresentationRequest {
    val options = proofOptions(block)
    return copy(proofOptions = options)
}

/**
 * Builder extension for PresentationRequest to add proof options using convenience function.
 * 
 * **Example:**
 * ```kotlin
 * val request = PresentationRequest(...)
 *     .withProofOptionsForPresentation(
 *         challenge = verifierChallenge,
 *         domain = "example.com"
 *     )
 * ```
 */
fun PresentationRequest.withProofOptionsForPresentation(
    challenge: String,
    domain: String? = null,
    verificationMethod: String? = null
): PresentationRequest {
    return copy(proofOptions = proofOptionsForPresentation(challenge, domain, verificationMethod))
}

