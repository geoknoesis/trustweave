package org.trustweave.signatures.cades

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import org.trustweave.signatures.revocation.CertificatePaths
import org.trustweave.signatures.revocation.CertificateRevocationEvaluator
import org.trustweave.signatures.revocation.RevocationPolicy
import org.trustweave.signatures.revocation.TimeStampTokenVerifier
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import java.security.MessageDigest
import java.security.Security
import java.security.cert.X509Certificate
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Verifier for CAdES B-B, B-T and B-LT profiles. Pure: never makes network calls.
 *
 * For detached signatures the caller must supply the original payload bytes via
 * [CadesVerificationOptions.detachedPayload]; for encapsulated signatures the verifier reads the
 * content from the CMS `encapContentInfo`.
 */
interface CadesVerifier {
    suspend fun verify(
        cmsBytes: ByteArray,
        options: CadesVerificationOptions,
    ): CadesValidationResult
}

/** Default [CadesVerifier] implementation using Bouncy Castle's `CMSSignedData`. */
class DefaultCadesVerifier : CadesVerifier {
    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(
                org.bouncycastle.jce.provider
                    .BouncyCastleProvider(),
            )
        }
    }

    override suspend fun verify(
        cmsBytes: ByteArray,
        options: CadesVerificationOptions,
    ): CadesValidationResult =
        withContext(Dispatchers.IO) {
            // 1. Parse CMS. First parse without a content provider to discover whether it's
            //    detached or encapsulated; then re-parse with detached payload if appropriate.
            val probe =
                try {
                    CMSSignedData(cmsBytes)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return@withContext Invalid.Malformed("not a valid CMS SignedData: ${t.message}")
                }
            val isDetached = probe.signedContent == null
            if (isDetached && options.detachedPayload == null) {
                return@withContext Invalid.MissingDetachedPayload(
                    "CMS is detached but verification options did not supply detachedPayload bytes",
                )
            }
            val cms =
                if (isDetached) {
                    try {
                        CMSSignedData(CMSProcessableByteArray(options.detachedPayload!!), cmsBytes)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        return@withContext Invalid.Malformed(
                            "CMS detached re-parse failed: ${t.message}",
                        )
                    }
                } else {
                    probe
                }

            // 3. Extract the (single) signer.
            val signerInfo: SignerInformation =
                try {
                    cms.signerInfos.signers.single()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return@withContext Invalid.Malformed("CMS does not contain exactly one signer")
                }

            // 4. Resolve signer cert from the embedded cert store via SID match.
            val signerCert =
                try {
                    findSignerCert(cms, signerInfo)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return@withContext Invalid.Malformed(
                        "cannot resolve signer certificate from CMS cert store: ${t.message}",
                    )
                } ?: return@withContext Invalid.Malformed("CMS does not embed the signer certificate")

            // 5. Cryptographic verification (BC handles digest, signed-attrs, and ECDSA / Ed25519).
            val verifier = JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signerCert)
            val signatureValid =
                try {
                    signerInfo.verify(verifier)
                } catch (cancelled: CancellationException) {
                    throw cancelled
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

            // 8. Signer certificate constraints (a CA certificate or one that cannot sign must not sign documents).
            CertificatePaths.signerProblem(signerCert)?.let { return@withContext Invalid.SignerCertificateInvalid(it) }

            // 9. Time-stamp. A token authenticates a time only once its TSA signature and certificate verify against
            //    `timestampTrustAnchors`; otherwise it is structurally checked and its time stays the signer's claim.
            var authenticatedTime: Instant? = null
            var hasSigTst = false
            when (val sigTstResult = validateSigTst(signerInfo, signingTime, options.maxClockSkew, options.timestampTrustAnchors)) {
                SigTstResult.None -> Unit
                is SigTstResult.Ok -> {
                    hasSigTst = true
                    if (sigTstResult.trusted) authenticatedTime = sigTstResult.genTime
                }
                is SigTstResult.Missing -> return@withContext Invalid.MissingTimeStamp(sigTstResult.reason)
                is SigTstResult.Mismatch -> return@withContext Invalid.TimeStampMismatch(sigTstResult.reason)
            }
            val timeStampProfile = if (authenticatedTime != null) CadesProfile.B_T else CadesProfile.B_B
            // B-LT additionally needs the embedded evidence; that is established below, after revocation.
            val earlyRequired = if (options.requiredProfile == CadesProfile.B_LT) CadesProfile.B_T else options.requiredProfile
            if (!timeStampProfile.atLeast(earlyRequired)) {
                return@withContext Invalid.WrongProfile(found = timeStampProfile, required = options.requiredProfile)
            }

            // 10. Trust anchor resolution over an ordered chain, validating the path as of the authenticated time
            //     when there is one (never the claimed signing time: that would let a signer back-date).
            val chain = CertificatePaths.walk(signerCert, collectAllCerts(cms))
            val trustMatch = options.trustAnchorResolver.resolve(signerCert, chain, authenticatedTime)
            // Exhaustive: a new TrustAnchorMatch subtype must be classified here, never pass by default.
            when (trustMatch) {
                is TrustAnchorMatch.NotTrusted -> return@withContext Invalid.UntrustedSigner(signerCert)
                is TrustAnchorMatch.QualifiedActive -> Unit
                is TrustAnchorMatch.QualifiedWithdrawn -> {
                    // A withdrawn service only vouches for signatures made before it was withdrawn.
                    if (authenticatedTime != null) {
                        if (authenticatedTime >= trustMatch.withdrawnAt) {
                            return@withContext Invalid.TrustWithdrawn(
                                signerCert,
                                trustMatch.withdrawnAt,
                                "time-stamped at $authenticatedTime, not before the withdrawal at ${trustMatch.withdrawnAt}",
                            )
                        }
                    } else {
                        if (!options.allowWithdrawnTrustWithoutAuthenticatedTime) {
                            return@withContext Invalid.TrustWithdrawn(
                                signerCert,
                                trustMatch.withdrawnAt,
                                "the trust-list service was withdrawn and the signing time is not authenticated by a " +
                                    "trusted time-stamp; set allowWithdrawnTrustWithoutAuthenticatedTime to accept",
                            )
                        }
                        if (signingTime != null && signingTime >= trustMatch.withdrawnAt) {
                            return@withContext Invalid.TrustWithdrawn(
                                signerCert,
                                trustMatch.withdrawnAt,
                                "claimed signing time $signingTime is not before the withdrawal at ${trustMatch.withdrawnAt}",
                            )
                        }
                    }
                }
            }

            // 11. Revocation (CRL / OCSP). Requesting B-LT implies REQUIRED: a long-term signature is only as good
            //     as the validation data it proves. Embedded data that is not well-formed is refused, never skipped.
            val embeddedEvidence =
                try {
                    CadesRevocationValues.embedded(cms, signerInfo)
                } catch (e: MalformedRevocationValuesException) {
                    return@withContext Invalid.Malformed(e.message ?: "embedded revocation data is malformed")
                }
            val revocationPolicy =
                if (options.requiredProfile == CadesProfile.B_LT && options.revocationPolicy == RevocationPolicy.NOT_CHECKED) {
                    RevocationPolicy.REQUIRED
                } else {
                    options.revocationPolicy
                }
            var revocationChecked = false
            var embeddedCoversChain = false
            if (revocationPolicy != RevocationPolicy.NOT_CHECKED) {
                val now =
                    kotlin.time.Clock.System
                        .now()
                val skewMillis = options.maxClockSkew.inWholeMilliseconds
                val statuses =
                    CertificateRevocationEvaluator.evaluate(
                        signer = signerCert,
                        candidates = chain,
                        issuerCertificates = options.revocationIssuerCertificates,
                        evidence = options.revocationEvidence + embeddedEvidence,
                        authenticatedTime = authenticatedTime,
                        now = now,
                        skewMillis = skewMillis,
                    )
                statuses.filterIsInstance<CertificateRevocationEvaluator.Status.Revoked>().firstOrNull()?.let {
                    return@withContext Invalid.CertificateRevoked(it.cert, it.at, it.reason)
                }
                val unavailable = statuses.filterIsInstance<CertificateRevocationEvaluator.Status.Unavailable>()
                if (revocationPolicy == RevocationPolicy.REQUIRED) {
                    if (statuses.isEmpty()) {
                        return@withContext Invalid.RevocationUnavailable(
                            "no certificate below a trust anchor was available to check for revocation",
                        )
                    }
                    if (unavailable.isNotEmpty()) {
                        return@withContext Invalid.RevocationUnavailable(unavailable.joinToString("; ") { it.reason })
                    }
                }
                revocationChecked = unavailable.isEmpty() && statuses.isNotEmpty()
                // B-LT: the evidence carried inside the signature must, on its own, cover the chain.
                if (revocationChecked && authenticatedTime != null && !embeddedEvidence.isEmpty) {
                    val own =
                        CertificateRevocationEvaluator.evaluate(
                            signer = signerCert,
                            candidates = chain,
                            issuerCertificates = options.revocationIssuerCertificates,
                            evidence = embeddedEvidence,
                            authenticatedTime = authenticatedTime,
                            now = now,
                            skewMillis = skewMillis,
                        )
                    embeddedCoversChain = own.isNotEmpty() && own.all { it is CertificateRevocationEvaluator.Status.Good }
                }
            }
            if (hasSigTst && authenticatedTime == null && options.requiredProfile.atLeast(CadesProfile.B_T)) {
                return@withContext Invalid.WrongProfile(found = CadesProfile.B_B, required = options.requiredProfile)
            }
            val foundProfile = if (embeddedCoversChain) CadesProfile.B_LT else timeStampProfile
            if (!foundProfile.atLeast(options.requiredProfile)) {
                return@withContext Invalid.WrongProfile(found = foundProfile, required = options.requiredProfile)
            }

            CadesValidationResult.Valid(
                signerCert = signerCert,
                trust = trustMatch,
                signingTime = signingTime,
                signatureTimeStamp = authenticatedTime,
                profile = foundProfile,
                revocationChecked = revocationChecked,
            )
        }

    // ---------------------------------------------------------------- signer-cert resolution

    @Suppress("UNCHECKED_CAST")
    private fun findSignerCert(
        cms: CMSSignedData,
        signerInfo: SignerInformation,
    ): X509Certificate? {
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

        data class Ok(
            val genTime: Instant,
            val trusted: Boolean,
        ) : SigTstResult()

        data class Missing(
            val reason: String,
        ) : SigTstResult()

        data class Mismatch(
            val reason: String,
        ) : SigTstResult()
    }

    private fun validateSigTst(
        signerInfo: SignerInformation,
        signingTime: Instant?,
        maxClockSkew: Duration,
        anchors: List<X509Certificate>,
    ): SigTstResult {
        val unsigned = signerInfo.unsignedAttributes ?: return SigTstResult.None
        val attr =
            unsigned.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken)
                ?: return SigTstResult.None
        val set =
            attr.attrValues as? ASN1Set
                ?: return SigTstResult.Mismatch("sigTst attribute has no values")
        if (set.size() == 0) return SigTstResult.Mismatch("sigTst attribute is empty")

        var earliest: Instant? = null
        for (i in 0 until set.size()) {
            val tokenBytes =
                try {
                    set.getObjectAt(i).toASN1Primitive().encoded
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return SigTstResult.Mismatch("sigTst token could not be re-encoded: ${t.message}")
                }
            val bcToken =
                try {
                    TimeStampToken(org.bouncycastle.cms.CMSSignedData(tokenBytes))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return SigTstResult.Mismatch("sigTst token is not a valid CMS SignedData: ${t.message}")
                }
            val info = bcToken.timeStampInfo
            val digest =
                TimeStampTokenVerifier.digestFor(info.messageImprintAlgOID.id)
                    ?: return SigTstResult.Mismatch("unsupported time-stamp digest ${info.messageImprintAlgOID.id}")
            val expectedImprint = MessageDigest.getInstance(digest).digest(signerInfo.signature)
            if (!MessageDigest.isEqual(expectedImprint, info.messageImprintDigest)) {
                return SigTstResult.Mismatch("sigTst messageImprint does not match $digest(signature)")
            }
            val tsaGenTime = info.genTime.toInstant().toKotlinInstant()
            // A time-stamp may legitimately come later than the claimed time; one that predates it does not.
            if (signingTime != null &&
                signingTime.toEpochMilliseconds() > tsaGenTime.toEpochMilliseconds() + maxClockSkew.inWholeMilliseconds
            ) {
                return SigTstResult.Mismatch("claimed signingTime ($signingTime) is later than the time-stamp ($tsaGenTime)")
            }
            if (anchors.isNotEmpty()) {
                val verified = TimeStampTokenVerifier.verify(tokenBytes, anchors)
                if (verified is TimeStampTokenVerifier.Result.Invalid) {
                    return SigTstResult.Mismatch("sigTst is not trusted: ${verified.reason}")
                }
            }
            if (earliest == null || tsaGenTime < earliest) earliest = tsaGenTime
        }
        return SigTstResult.Ok(earliest!!, trusted = anchors.isNotEmpty())
    }
}
