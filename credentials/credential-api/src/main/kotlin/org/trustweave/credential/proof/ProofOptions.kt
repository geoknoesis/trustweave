package org.trustweave.credential.proof

/**
 * Proof generation options - cross-proof-suite parameters.
 * 
 * These options apply to proof generation across different proof suites
 * (VC-LD, SD-JWT-VC presentations, etc.) and control the cryptographic
 * proof parameters, not the proof suite encoding details.
 * 
 * **Proof Suite-Specific Options:**
 * Use [additionalOptions] for proof suite-specific parameters (e.g., canonicalization algorithm,
 * hash algorithm, proof type). These are interpreted by the proof engine according to the proof suite.
 * 
 * **Example Usage:**
 * ```kotlin
 * // Simple - defaults to assertionMethod
 * val options = proofOptions()
 * 
 * // With challenge for presentation
 * val options = proofOptions {
 *     challenge = "random-nonce-123"
 *     domain = "example.com"
 * }
 * 
 * // For authentication
 * val options = proofOptions {
 *     purpose = ProofPurpose.Authentication
 *     challenge = generateChallenge()
 * }
 * 
 * // Using convenience functions
 * val options = proofOptionsForAuthentication(challenge = "nonce-123")
 * val options = proofOptionsForPresentation(challenge = "nonce-123", domain = "example.com")
 * ```
 */
data class ProofOptions(
    /**
     * Proof purpose (why the proof exists).
     * 
     * Common values:
     * - `assertionMethod` - proof that the issuer is asserting the credential claims
     * - `authentication` - proof for authentication purposes
     * - `keyAgreement` - proof for key agreement
     * - `capabilityInvocation` - proof for invoking a capability
     * - `capabilityDelegation` - proof for delegating a capability
     */
    val purpose: ProofPurpose = ProofPurpose.AssertionMethod,
    
    /**
     * Challenge (nonce) for non-repudiation.
     * 
     * Used to prevent replay attacks. Typically a random string provided
     * by the verifier during presentation flows.
     */
    val challenge: String? = null,
    
    /**
     * Domain binding string.
     * 
     * Used to bind the proof to a specific domain, preventing cross-domain
     * attacks. Common in OID4VP flows.
     */
    val domain: String? = null,
    
    /**
     * Verification method identifier (optional).
     * 
     * Explicitly specifies which verification method/key to use.
     * If null, the engine will select an appropriate method based on
     * the issuer's DID Document or other context.
     */
    val verificationMethod: String? = null,
    
    /**
     * Additional proof suite-specific options.
     * 
     * Use this for proof suite-specific parameters (e.g., "proofType", "canonicalizationAlgorithm")
     * that are interpreted by the proof engine according to the proof suite requirements.
     * 
     * **Example:**
     * ```kotlin
     * val options = proofOptions {
     *     purpose = ProofPurpose.AssertionMethod
     *     option("proofType", "Ed25519Signature2020")
     *     option("canonicalizationAlgorithm", "urdna2015")
     * }
     * ```
     */
    val additionalOptions: Map<String, Any?> = emptyMap()
) {
    /**
     * Copy with additional options merged.
     */
    fun withAdditionalOption(key: String, value: Any?): ProofOptions {
        return copy(additionalOptions = additionalOptions + (key to value))
    }
    
    /**
     * Copy with additional options merged.
     */
    fun withAdditionalOptions(options: Map<String, Any?>): ProofOptions {
        return copy(additionalOptions = additionalOptions + options)
    }
}

/**
 * Proof purpose enumeration.
 * 
 * Based on W3C Verifiable Credentials proof purpose values.
 * 
 * **Parsing from string:**
 * ```kotlin
 * val purpose = ProofPurpose.fromString("assertionMethod")
 * ```
 */
enum class ProofPurpose {
    /**
     * Assertion method - proof that the issuer is asserting the credential claims.
     * This is the default for credential issuance.
     */
    AssertionMethod,
    
    /**
     * Authentication - proof for authentication purposes.
     * Used when the credential holder proves their identity.
     */
    Authentication,
    
    /**
     * Key agreement - proof for establishing a shared secret.
     */
    KeyAgreement,
    
    /**
     * Capability invocation - proof for invoking a capability.
     */
    CapabilityInvocation,
    
    /**
     * Capability delegation - proof for delegating a capability.
     */
    CapabilityDelegation;
    
    /**
     * Get the standard string representation.
     */
    val standardValue: String
        get() = when (this) {
            AssertionMethod -> "assertionMethod"
            Authentication -> "authentication"
            KeyAgreement -> "keyAgreement"
            CapabilityInvocation -> "capabilityInvocation"
            CapabilityDelegation -> "capabilityDelegation"
        }
    
    companion object {
        /**
         * Parse proof purpose from standard string value.
         * 
         * **Example:**
         * ```kotlin
         * val purpose = ProofPurpose.fromString("assertionMethod")
         * ```
         * 
         * @return ProofPurpose if found, null otherwise
         */
        fun fromString(value: String): ProofPurpose? {
            return when (value) {
                "assertionMethod" -> AssertionMethod
                "authentication" -> Authentication
                "keyAgreement" -> KeyAgreement
                "capabilityInvocation" -> CapabilityInvocation
                "capabilityDelegation" -> CapabilityDelegation
                else -> null
            }
        }
    }
}

/**
 * Builder DSL for creating proof options.
 * 
 * **Example:**
 * ```kotlin
 * // Default (assertionMethod)
 * val options = proofOptions()
 * 
 * // With DSL
 * val options = proofOptions {
 *     purpose = ProofPurpose.Authentication
 *     challenge = "nonce-123"
 *     domain = "example.com"
 *     verificationMethod = "did:key:example#key-1"
 * }
 * ```
 */
fun proofOptions(block: ProofOptionsBuilder.() -> Unit = {}): ProofOptions {
    val builder = ProofOptionsBuilder()
    builder.block()
    return builder.build()
}

/**
 * Builder class for proof options.
 * 
 * Properties can be set directly in the DSL block:
 * ```kotlin
 * proofOptions {
 *     purpose = ProofPurpose.Authentication
 *     challenge = "nonce-123"
 *     domain = "example.com"
 * }
 * ```
 */
class ProofOptionsBuilder {
    /** Proof purpose (defaults to AssertionMethod). */
    var purpose: ProofPurpose = ProofPurpose.AssertionMethod
    
    /** Challenge (nonce) for non-repudiation. */
    var challenge: String? = null
    
    /** Domain binding string. */
    var domain: String? = null
    
    /** Verification method identifier. */
    var verificationMethod: String? = null
    
    private val additionalOptions: MutableMap<String, Any?> = mutableMapOf()
    
    /**
     * Add additional option.
     */
    fun option(key: String, value: Any?) {
        additionalOptions[key] = value
    }
    
    /**
     * Build the proof options.
     */
    fun build(): ProofOptions {
        return ProofOptions(
            purpose = purpose,
            challenge = challenge,
            domain = domain,
            verificationMethod = verificationMethod,
            additionalOptions = additionalOptions
        )
    }
}

/**
 * Create proof options for credential issuance (default).
 * 
 * Uses `assertionMethod` purpose, which is the standard for credential issuance.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForIssuance()
 * ```
 */
fun proofOptionsForIssuance(
    verificationMethod: String? = null
): ProofOptions = ProofOptions(
    purpose = ProofPurpose.AssertionMethod,
    verificationMethod = verificationMethod
)

/**
 * Create proof options for authentication.
 * 
 * Typically used in presentation flows where the holder authenticates.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForAuthentication(
 *     challenge = generateChallenge(),
 *     domain = "example.com"
 * )
 * ```
 */
fun proofOptionsForAuthentication(
    challenge: String,
    domain: String? = null,
    verificationMethod: String? = null
): ProofOptions = ProofOptions(
    purpose = ProofPurpose.Authentication,
    challenge = challenge,
    domain = domain,
    verificationMethod = verificationMethod
)

/**
 * Create proof options for presentation.
 * 
 * Alias for [proofOptionsForAuthentication] - presentations use authentication purpose.
 * Used when creating verifiable presentations. Requires a challenge to prevent replay attacks.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForPresentation(
 *     challenge = verifierChallenge,
 *     domain = "example.com"
 * )
 * ```
 */
fun proofOptionsForPresentation(
    challenge: String,
    domain: String? = null,
    verificationMethod: String? = null
): ProofOptions = proofOptionsForAuthentication(challenge, domain, verificationMethod)

/**
 * Create proof options for key agreement.
 * 
 * Used when establishing secure channels or key exchange.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForKeyAgreement(
 *     verificationMethod = "did:key:example#key-agreement-1"
 * )
 * ```
 */
fun proofOptionsForKeyAgreement(
    verificationMethod: String? = null
): ProofOptions = ProofOptions(
    purpose = ProofPurpose.KeyAgreement,
    verificationMethod = verificationMethod
)

/**
 * Create proof options for capability invocation.
 * 
 * Used when invoking capabilities or permissions.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForCapabilityInvocation(
 *     challenge = generateChallenge()
 * )
 * ```
 */
fun proofOptionsForCapabilityInvocation(
    challenge: String? = null,
    verificationMethod: String? = null
): ProofOptions = ProofOptions(
    purpose = ProofPurpose.CapabilityInvocation,
    challenge = challenge,
    verificationMethod = verificationMethod
)

/**
 * Create proof options for capability delegation.
 * 
 * Used when delegating capabilities or permissions.
 * 
 * **Example:**
 * ```kotlin
 * val options = proofOptionsForCapabilityDelegation(
 *     verificationMethod = "did:key:example#delegation-key"
 * )
 * ```
 */
fun proofOptionsForCapabilityDelegation(
    verificationMethod: String? = null
): ProofOptions = ProofOptions(
    purpose = ProofPurpose.CapabilityDelegation,
    verificationMethod = verificationMethod
)

/**
 * Generate a random challenge (nonce) for proof options.
 * 
 * **Example:**
 * ```kotlin
 * val challenge = generateChallenge()
 * val options = proofOptionsForPresentation(challenge = challenge)
 * ```
 */
fun generateChallenge(): String {
    return java.util.UUID.randomUUID().toString()
}
