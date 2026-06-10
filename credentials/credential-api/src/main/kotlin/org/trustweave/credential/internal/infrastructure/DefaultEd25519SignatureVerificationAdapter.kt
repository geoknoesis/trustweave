package org.trustweave.credential.internal.infrastructure

import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.proof.internal.engines.ProofEngineUtils
import org.trustweave.credential.spi.proof.SignatureVerificationPort
import org.trustweave.did.model.VerificationMethod
import org.slf4j.LoggerFactory

/**
 * Default implementation of [SignatureVerificationPort] for Ed25519Signature2020 proofs.
 *
 * Uses [java.security.Signature] with the "Ed25519" algorithm (via BouncyCastle) to verify
 * signatures. Public key material is extracted from the [VerificationMethod] via
 * [ProofEngineUtils.extractPublicKey].
 */
internal class DefaultEd25519SignatureVerificationAdapter : SignatureVerificationPort {

    private val logger = LoggerFactory.getLogger(DefaultEd25519SignatureVerificationAdapter::class.java)

    override fun verify(
        documentBytes: ByteArray,
        signatureBytes: ByteArray,
        verificationMethod: VerificationMethod,
        proofType: String
    ): Boolean {
        if (proofType != CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020) {
            logger.warn("Unsupported proof type: {}", proofType)
            return false
        }

        val publicKey = ProofEngineUtils.extractPublicKey(verificationMethod)
        if (publicKey == null) {
            logger.warn(
                "Failed to extract public key from verification method: {}",
                verificationMethod.id.value
            )
            return false
        }

        return try {
            val sig = java.security.Signature.getInstance("Ed25519")
            sig.initVerify(publicKey)
            sig.update(documentBytes)
            val result = sig.verify(signatureBytes)
            logger.debug(
                "Ed25519 verification result: isValid={}, verificationMethod={}",
                result,
                verificationMethod.id.value
            )
            result
        } catch (e: Exception) {
            logger.error("Exception during Ed25519 signature verification: {}", e.message, e)
            false
        }
    }
}
