package io.geoknoesis.vericore.credential.proof

import io.geoknoesis.vericore.credential.models.Proof
import io.geoknoesis.vericore.credential.models.VerifiableCredential

/**
 * Pluggable proof generator interface.
 * 
 * Implementations generate cryptographic proofs for verifiable credentials.
 * Supports multiple proof types: Ed25519, JWT, BBS+, etc.
 * 
 * **Example Usage**:
 * ```kotlin
 * val generator = Ed25519ProofGenerator { data, keyId ->
 *     kms.sign(keyId, data)
 * }
 * ProofGeneratorRegistry.register(generator)
 * 
 * val proof = generator.generateProof(credential, keyId, options)
 * ```
 */
interface ProofGenerator {
    /**
     * Proof type identifier (e.g., "Ed25519Signature2020", "JsonWebSignature2020").
     */
    val proofType: String
    
    /**
     * Generate a proof for a credential.
     * 
     * @param credential Credential to sign (without proof)
     * @param keyId Key ID for signing
     * @param options Proof generation options
     * @return Generated proof
     */
    suspend fun generateProof(
        credential: VerifiableCredential,
        keyId: String,
        options: ProofOptions
    ): Proof
}

/**
 * Proof generation options.
 */
data class ProofOptions(
    val proofPurpose: String = "assertionMethod",
    val challenge: String? = null,
    val domain: String? = null,
    val verificationMethod: String? = null,
    val additionalOptions: Map<String, Any?> = emptyMap()
)

/**
 * Registry for proof generators.
 * 
 * Allows registration and selection of proof generators by type.
 * 
 * **Thread Safety**: This registry is thread-safe for concurrent access.
 */
object ProofGeneratorRegistry {
    private val generators = mutableMapOf<String, ProofGenerator>()
    
    /**
     * Register a proof generator.
     * 
     * @param generator Proof generator to register
     */
    fun register(generator: ProofGenerator) {
        generators[generator.proofType] = generator
    }
    
    /**
     * Get a proof generator by type.
     * 
     * @param proofType Proof type identifier
     * @return Proof generator, or null if not found
     */
    fun get(proofType: String): ProofGenerator? {
        return generators[proofType]
    }
    
    /**
     * Get all registered proof types.
     * 
     * @return List of proof type identifiers
     */
    fun getRegisteredTypes(): List<String> {
        return generators.keys.toList()
    }
    
    /**
     * Check if a proof type is supported.
     * 
     * @param proofType Proof type identifier
     * @return true if generator is registered
     */
    fun isSupported(proofType: String): Boolean {
        return generators.containsKey(proofType)
    }
    
    /**
     * Clear all registered generators.
     * Useful for testing.
     */
    fun clear() {
        generators.clear()
    }
}

