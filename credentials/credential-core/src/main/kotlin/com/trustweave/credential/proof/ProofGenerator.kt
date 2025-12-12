package com.trustweave.credential.proof

import com.trustweave.credential.models.Proof
import com.trustweave.credential.model.vc.VerifiableCredential
import java.util.concurrent.ConcurrentHashMap

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
        options: ProofGeneratorOptions
    ): Proof
}

/**
 * Proof generation options for proof generators.
 * 
 * Note: This is separate from the ProofOptions in credential-api to avoid classpath conflicts.
 * This class is used by the ProofGenerator interface and implementations.
 */
data class ProofGeneratorOptions(
    val proofPurpose: String = "assertionMethod",
    val challenge: String? = null,
    val domain: String? = null,
    val verificationMethod: String? = null,
    val additionalOptions: Map<String, Any?> = emptyMap()
)

interface ProofGeneratorRegistry {
    fun register(generator: ProofGenerator)
    fun unregister(proofType: String)
    fun get(proofType: String): ProofGenerator?
    fun getRegisteredTypes(): List<String>
    fun isSupported(proofType: String): Boolean
    fun snapshot(): ProofGeneratorRegistry
    fun clear()
}

fun ProofGeneratorRegistry(): ProofGeneratorRegistry = DefaultProofGeneratorRegistry()

class DefaultProofGeneratorRegistry internal constructor(
    private val generators: MutableMap<String, ProofGenerator> = ConcurrentHashMap()
) : ProofGeneratorRegistry {
    override fun register(generator: ProofGenerator) {
        generators[generator.proofType] = generator
    }

    override fun unregister(proofType: String) {
        generators.remove(proofType)
    }

    override fun get(proofType: String): ProofGenerator? = generators[proofType]

    override fun getRegisteredTypes(): List<String> = generators.keys.toList()

    override fun isSupported(proofType: String): Boolean = generators.containsKey(proofType)

    override fun snapshot(): ProofGeneratorRegistry =
        DefaultProofGeneratorRegistry(ConcurrentHashMap(generators))

    override fun clear() {
        generators.clear()
    }
}

