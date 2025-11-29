package com.trustweave.credential.proof

/**
 * Type-safe proof type representation.
 *
 * Use sealed classes instead of strings for compile-time type safety.
 *
 * **Example:**
 * ```kotlin
 * val proofType: ProofType = ProofType.Ed25519Signature2020
 * ```
 */
sealed class ProofType {
    /**
     * The proof type identifier string used in VC proofs.
     */
    abstract val identifier: String

    /**
     * Ed25519 Signature 2020 proof type (recommended).
     */
    object Ed25519Signature2020 : ProofType() {
        override val identifier = "Ed25519Signature2020"
    }

    /**
     * JSON Web Signature 2020 proof type.
     */
    object JsonWebSignature2020 : ProofType() {
        override val identifier = "JsonWebSignature2020"
    }

    /**
     * BBS+ Bls Signature 2020 proof type (for selective disclosure).
     */
    object BbsBlsSignature2020 : ProofType() {
        override val identifier = "BbsBlsSignature2020"
    }

    /**
     * Custom proof type with arbitrary identifier.
     */
    data class Custom(override val identifier: String) : ProofType()

    override fun toString(): String = identifier
}

/**
 * Common proof type constants for convenience.
 */
object ProofTypes {
    val Ed25519 = ProofType.Ed25519Signature2020
    val JWT = ProofType.JsonWebSignature2020
    val BBS = ProofType.BbsBlsSignature2020

    fun fromString(identifier: String): ProofType = when (identifier) {
        "Ed25519Signature2020" -> ProofType.Ed25519Signature2020
        "JsonWebSignature2020" -> ProofType.JsonWebSignature2020
        "BbsBlsSignature2020" -> ProofType.BbsBlsSignature2020
        else -> ProofType.Custom(identifier)
    }
}

