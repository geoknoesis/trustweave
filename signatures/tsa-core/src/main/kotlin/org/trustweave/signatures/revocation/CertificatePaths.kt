package org.trustweave.signatures.revocation

import kotlinx.coroutines.CancellationException
import java.security.cert.X509Certificate

/** Certificate-path helpers shared by the signature verifiers and the revocation evaluator. */
object CertificatePaths {
    private const val KU_DIGITAL_SIGNATURE = 0
    private const val KU_NON_REPUDIATION = 1

    /** Self-signed means signed by its own key: a matching name alone also describes a re-keyed self-issued certificate. */
    fun isSelfSigned(cert: X509Certificate): Boolean = cert.subjectX500Principal == cert.issuerX500Principal && signedBy(cert, cert)

    /** `true` when [cert]'s signature verifies under [issuer]'s key and the names link (issuer DN == subject DN). */
    fun signedBy(
        cert: X509Certificate,
        issuer: X509Certificate,
    ): Boolean =
        try {
            cert.verify(issuer.publicKey)
            cert.issuerX500Principal == issuer.subjectX500Principal
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    /**
     * The certificates of [candidates] that form a chain upwards from [signer], nearest issuer first: each is the
     * issuer of the previous (matching names AND a verifying signature). Walking stops at a self-signed certificate.
     * Unrelated, duplicate or out-of-order candidates are ignored, which gives the PKIX validator the ordered path
     * it needs and stops a stray certificate from being treated as part of the chain.
     */
    fun walk(
        signer: X509Certificate,
        candidates: List<X509Certificate>,
    ): List<X509Certificate> {
        val chain = mutableListOf<X509Certificate>()
        var current = signer
        val remaining = candidates.filter { it != signer }.distinct().toMutableList()
        while (!isSelfSigned(current)) {
            val issuer = remaining.firstOrNull { signedBy(current, it) } ?: break
            remaining.remove(issuer)
            chain.add(issuer)
            current = issuer
        }
        return chain
    }

    /**
     * Why [cert] may not be used to sign documents, or `null` when it may: a certificate that is a CA, or whose
     * `keyUsage` (when present) allows neither `digitalSignature` nor `nonRepudiation`.
     */
    fun signerProblem(cert: X509Certificate): String? {
        if (cert.basicConstraints >= 0) return "signer certificate is a CA certificate (basicConstraints cA is set)"
        val usage = cert.keyUsage ?: return null
        val signs = usage.getOrElse(KU_DIGITAL_SIGNATURE) { false } || usage.getOrElse(KU_NON_REPUDIATION) { false }
        return if (signs) null else "signer certificate keyUsage permits neither digitalSignature nor nonRepudiation"
    }
}
