package org.trustweave.credential.spi.proof

import org.trustweave.did.model.VerificationMethod

/**
 * Port interface for cryptographic signature verification.
 *
 * Abstracts the signature verification infrastructure from the credential domain.
 * Implementations handle the cryptographic details of verifying digital signatures
 * over canonicalized documents using keys from DID verification methods.
 */
interface SignatureVerificationPort {
    /**
     * Verify a digital signature over raw document bytes.
     *
     * @param documentBytes The canonicalized document bytes that were signed
     * @param signatureBytes The raw signature bytes (decoded from proof value)
     * @param verificationMethod The verification method containing the public key
     * @param proofType The proof type identifier (e.g., "Ed25519Signature2020")
     * @return true if the signature is cryptographically valid, false otherwise
     */
    fun verify(
        documentBytes: ByteArray,
        signatureBytes: ByteArray,
        verificationMethod: VerificationMethod,
        proofType: String
    ): Boolean
}
