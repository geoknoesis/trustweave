package org.trustweave.signatures.revocation

import kotlinx.coroutines.CancellationException
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.tsp.TimeStampToken
import java.security.GeneralSecurityException
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Verifies an RFC 3161 time-stamp token against caller-configured TSA trust anchors: the token's CMS
 * signature, the TSA certificate's validity at `genTime` (and its time-stamping purpose, which BouncyCastle
 * enforces), and that the TSA certificate is one of, or is issued by, an anchor.
 *
 * A token that has not passed this check proves nothing about *when* something was signed: anyone can build
 * a structurally valid token with an arbitrary `genTime`. Callers must therefore treat the time of an
 * unverified token as the signer's own claim.
 */
object TimeStampTokenVerifier {
    sealed class Result {
        /**
         * @property genTime         The TSA-asserted time.
         * @property imprintOid      OID of the message-imprint digest algorithm.
         * @property imprintDigest   The message imprint the token covers; the caller compares it with its own digest.
         */
        class Valid(
            val genTime: Instant,
            val imprintOid: String,
            val imprintDigest: ByteArray,
        ) : Result()

        class Invalid(
            val reason: String,
        ) : Result()
    }

    /** JCA digest name for the message-imprint algorithm [oid] (SHA-256, SHA-384 or SHA-512), or `null`. */
    fun digestFor(oid: String): String? =
        when (oid) {
            "2.16.840.1.101.3.4.2.1" -> "SHA-256"
            "2.16.840.1.101.3.4.2.2" -> "SHA-384"
            "2.16.840.1.101.3.4.2.3" -> "SHA-512"
            else -> null
        }

    fun verify(
        tokenDer: ByteArray,
        anchors: List<X509Certificate>,
    ): Result {
        if (anchors.isEmpty()) return Result.Invalid("no TSA trust anchors are configured")
        val token =
            try {
                TimeStampToken(CMSSignedData(tokenDer))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                return Result.Invalid("not an RFC 3161 time-stamp token: ${t.message}")
            }
        val info = token.timeStampInfo

        @Suppress("UNCHECKED_CAST")
        val holder =
            (
                token.certificates.getMatches(
                    token.sid as org.bouncycastle.util.Selector<X509CertificateHolder>,
                ) as Collection<X509CertificateHolder>
            ).firstOrNull() ?: return Result.Invalid("time-stamp carries no TSA certificate")
        val tsaCert: X509Certificate =
            try {
                token.validate(JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider()).build(holder))
                JcaX509CertificateConverter().getCertificate(holder)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                return Result.Invalid("time-stamp signature is invalid: ${t.message}")
            }
        if (!validAt(tsaCert, info.genTime)) return Result.Invalid("TSA certificate was not valid at the time-stamp's genTime")
        val trusted = anchors.any { anchor -> (anchor == tsaCert || issuedBy(tsaCert, anchor)) && validAt(anchor, info.genTime) }
        if (!trusted) return Result.Invalid("TSA certificate is neither one of nor issued by the configured TSA trust anchors")
        return Result.Valid(info.genTime.toInstant().toKotlinInstant(), info.messageImprintAlgOID.id, info.messageImprintDigest)
    }

    private fun validAt(
        cert: X509Certificate,
        at: Date,
    ): Boolean =
        try {
            cert.checkValidity(at)
            true
        } catch (_: GeneralSecurityException) {
            false
        }

    private fun issuedBy(
        cert: X509Certificate,
        issuer: X509Certificate,
    ): Boolean =
        try {
            cert.verify(issuer.publicKey)
            cert.issuerX500Principal == issuer.subjectX500Principal
        } catch (_: GeneralSecurityException) {
            false
        }
}
