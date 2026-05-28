package org.trustweave.signatures.cades

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.tsp.TimeStampToken
import org.trustweave.signatures.cades.CadesValidationResult.Invalid
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import java.security.MessageDigest
import java.security.Security
import java.security.cert.X509Certificate
import kotlin.time.Duration

/**
 * Verifier for CAdES B-B and B-T profiles. Pure: never makes network calls.
 *
 * For detached signatures the caller must supply the original payload bytes via
 * [CadesVerificationOptions.detachedPayload]; for encapsulated signatures the verifier reads the
 * content from the CMS `encapContentInfo`.
 */
interface CadesVerifier {
    suspend fun verify(cmsBytes: ByteArray, options: CadesVerificationOptions): CadesValidationResult
}

/** Default [CadesVerifier] implementation using Bouncy Castle's `CMSSignedData`. */
class DefaultCadesVerifier : CadesVerifier {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        }
    }

    override suspend fun verify(
        cmsBytes: ByteArray,
        options: CadesVerificationOptions,
    ): CadesValidationResult = withContext(Dispatchers.IO) {
        // 1. Parse CMS. First parse without a content provider to discover whether it's
        //    detached or encapsulated; then re-parse with detached payload if appropriate.
        val probe = try {
            CMSSignedData(cmsBytes)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("not a valid CMS SignedData: ${t.message}")
        }
        val isDetached = probe.signedContent == null
        if (isDetached && options.detachedPayload == null) {
            return@withContext Invalid.MissingDetachedPayload(
                "CMS is detached but verification options did not supply detachedPayload bytes",
            )
        }
        val cms = if (isDetached) {
            try {
                CMSSignedData(CMSProcessableByteArray(options.detachedPayload!!), cmsBytes)
            } catch (t: Throwable) {
                return@withContext Invalid.Malformed(
                    "CMS detached re-parse failed: ${t.message}",
                )
            }
        } else {
            probe
        }

        // 3. Extract the (single) signer.
        val signerInfo: SignerInformation = try {
            cms.signerInfos.signers.single()
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("CMS does not contain exactly one signer")
        }

        // 4. Resolve signer cert from the embedded cert store via SID match.
        val signerCert = try {
            findSignerCert(cms, signerInfo)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed(
                "cannot resolve signer certificate from CMS cert store: ${t.message}",
            )
        } ?: return@withContext Invalid.Malformed("CMS does not embed the signer certificate")

        // 5. Cryptographic verification (BC handles digest, signed-attrs, and ECDSA / Ed25519).
        val verifier = JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signerCert)
        val signatureValid = try {
            signerInfo.verify(verifier)
        } catch (t: Throwable) {
            return@withContext Invalid.BadSignature("verification threw: ${t.message}")
        }
        if (!signatureValid) {
            return@withContext Invalid.BadSignature("cryptographic verification failed")
        }

        // 6. Extract signing-time (optional but expected for CAdES B-B).
        val signingTime = extractSigningTime(signerInfo)

        // 7. Cert validity at signing time.
        if (!options.allowExpiredCertificateAtSigningTime && signingTime != null) {
            val skewMs = options.maxClockSkew.inWholeMilliseconds
            val notAfter = signerCert.notAfter.toInstant().toKotlinInstant()
            val notBefore = signerCert.notBefore.toInstant().toKotlinInstant()
            if (signingTime.toEpochMilliseconds() > notAfter.toEpochMilliseconds() + skewMs) {
                return@withContext Invalid.CertificateExpired(notAfter)
            }
            if (signingTime.toEpochMilliseconds() + skewMs < notBefore.toEpochMilliseconds()) {
                return@withContext Invalid.CertificateExpired(notAfter)
            }
        }

        // 8. Trust anchor resolution.
        val otherCerts = collectAllCerts(cms).filter { it != signerCert }
        val trustMatch = options.trustAnchorResolver.resolve(signerCert, otherCerts)
        if (trustMatch is TrustAnchorMatch.NotTrusted) {
            return@withContext Invalid.UntrustedSigner(signerCert)
        }

        // 9. Profile detection + sigTst handling (B-T).
        val sigTstResult = validateSigTst(signerInfo, signingTime, options.maxClockSkew)
        val foundProfile = if (sigTstResult is SigTstResult.None) CadesProfile.B_B else CadesProfile.B_T
        if (options.requiredProfile == CadesProfile.B_T && foundProfile == CadesProfile.B_B) {
            return@withContext Invalid.WrongProfile(found = foundProfile, required = options.requiredProfile)
        }
        val sigTstInstant = when (sigTstResult) {
            is SigTstResult.Ok -> sigTstResult.genTime
            is SigTstResult.Missing -> return@withContext Invalid.MissingTimeStamp(sigTstResult.reason)
            is SigTstResult.Mismatch -> return@withContext Invalid.TimeStampMismatch(sigTstResult.reason)
            SigTstResult.None -> null
        }

        CadesValidationResult.Valid(
            signerCert = signerCert,
            trust = trustMatch,
            signingTime = signingTime,
            signatureTimeStamp = sigTstInstant,
            profile = foundProfile,
        )
    }

    // ---------------------------------------------------------------- signer-cert resolution

    @Suppress("UNCHECKED_CAST")
    private fun findSignerCert(cms: CMSSignedData, signerInfo: SignerInformation): X509Certificate? {
        val converter = JcaX509CertificateConverter().setProvider("BC")
        val store = cms.certificates as org.bouncycastle.util.Store<X509CertificateHolder>
        val selector = signerInfo.sid as org.bouncycastle.util.Selector<X509CertificateHolder>
        val matches = store.getMatches(selector)
        val holder = matches.firstOrNull() ?: return null
        return converter.getCertificate(holder)
    }

    private fun collectAllCerts(cms: CMSSignedData): List<X509Certificate> {
        val converter = JcaX509CertificateConverter().setProvider("BC")
        @Suppress("UNCHECKED_CAST")
        val all = cms.certificates.getMatches(null) as Collection<X509CertificateHolder>
        return all.map { converter.getCertificate(it) }
    }

    // ---------------------------------------------------------------- signing-time

    private fun extractSigningTime(signerInfo: SignerInformation): Instant? {
        val attr = signerInfo.signedAttributes?.get(CMSAttributes.signingTime) ?: return null
        val set = attr.attrValues as? ASN1Set ?: return null
        if (set.size() == 0) return null
        val time = Time.getInstance(set.getObjectAt(0)) ?: return null
        return time.date.toInstant().toKotlinInstant()
    }

    // ---------------------------------------------------------------- sigTst

    private sealed class SigTstResult {
        data object None : SigTstResult()
        data class Ok(val genTime: Instant) : SigTstResult()
        data class Missing(val reason: String) : SigTstResult()
        data class Mismatch(val reason: String) : SigTstResult()
    }

    private fun validateSigTst(
        signerInfo: SignerInformation,
        signingTime: Instant?,
        maxClockSkew: Duration,
    ): SigTstResult {
        val unsigned = signerInfo.unsignedAttributes ?: return SigTstResult.None
        val attr = unsigned.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken)
            ?: return SigTstResult.None
        val set = attr.attrValues as? ASN1Set
            ?: return SigTstResult.Mismatch("sigTst attribute has no values")
        if (set.size() == 0) return SigTstResult.Mismatch("sigTst attribute is empty")

        val tokenBytes = try {
            set.getObjectAt(0).toASN1Primitive().encoded
        } catch (t: Throwable) {
            return SigTstResult.Mismatch("sigTst token could not be re-encoded: ${t.message}")
        }
        val bcToken = try {
            TimeStampToken(org.bouncycastle.cms.CMSSignedData(tokenBytes))
        } catch (t: Throwable) {
            return SigTstResult.Mismatch("sigTst token is not a valid CMS SignedData: ${t.message}")
        }
        val expectedImprint = MessageDigest.getInstance("SHA-256").digest(signerInfo.signature)
        if (!expectedImprint.contentEquals(bcToken.timeStampInfo.messageImprintDigest)) {
            return SigTstResult.Mismatch("sigTst messageImprint does not match SHA-256(signature)")
        }
        val tsaGenTime = bcToken.timeStampInfo.genTime.toInstant().toKotlinInstant()
        if (signingTime != null) {
            val deltaMs = kotlin.math.abs(
                tsaGenTime.toEpochMilliseconds() - signingTime.toEpochMilliseconds(),
            )
            if (deltaMs > maxClockSkew.inWholeMilliseconds) {
                return SigTstResult.Mismatch(
                    "TSA genTime ($tsaGenTime) is more than $maxClockSkew away from signingTime ($signingTime)",
                )
            }
        }
        return SigTstResult.Ok(tsaGenTime)
    }
}
