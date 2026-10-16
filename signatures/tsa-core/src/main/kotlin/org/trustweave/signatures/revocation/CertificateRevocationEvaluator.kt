package org.trustweave.signatures.revocation

import kotlinx.coroutines.CancellationException
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.cert.ocsp.SingleResp
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * How a verifier treats certificate revocation.
 *
 * Revocation evidence (CRLs and OCSP responses) comes from the signature itself (XAdES
 * `<xades:RevocationValues>`, JAdES `rVals`) and from
 * the verifier options' `revocationEvidence`. Every item is verified against the issuing CA's
 * key before it is used, so an attacker who edits the (unsigned) `RevocationValues` can only strip
 * evidence or replace it with something equally genuine; the freshness rules described on
 * [RevocationEvidence] stop a stale "good" answer from standing in for a current one.
 */
enum class RevocationPolicy {
    /** Revocation is not evaluated (the default). `Valid.revocationChecked` stays `false`. */
    NOT_CHECKED,

    /**
     * Evaluate whatever evidence is available. A certificate found revoked is refused. A
     * certificate with no usable evidence is accepted, and `Valid.revocationChecked` is `false`
     * unless every certificate in the chain was shown good.
     */
    CHECK_IF_AVAILABLE,

    /**
     * Fail closed: the signer and every CA certificate below the trust anchor must have usable
     * evidence showing it good, otherwise the signature is refused
     * (the verifier's `RevocationUnavailable` result).
     */
    REQUIRED,
}

/**
 * DER-encoded revocation evidence supplied by the caller (for example fetched from the CA just
 * before verification, which turns a B-B or B-T signature into one with current status).
 *
 * # Freshness
 *
 * - When the signing time is authenticated by a trusted time-stamp, evidence must have been issued
 *   at or after that time (a status from before the signature says nothing about it). A
 *   revocation dated after the time-stamp does not invalidate the signature.
 * - Otherwise evidence must be current at verification time: `thisUpdate` not in the future and
 *   `nextUpdate` (when present) not in the past; any revocation counts.
 *
 * @property crls          DER-encoded X.509 CRLs. Only direct CRLs signed by the certificate's
 *                         issuer are used; delta CRLs and CRLs with critical extensions are ignored.
 * @property ocspResponses DER-encoded `OCSPResponse` structures (RFC 6960).
 */
class RevocationEvidence
    @JvmOverloads
    constructor(
        val crls: List<ByteArray> = emptyList(),
        val ocspResponses: List<ByteArray> = emptyList(),
    ) {
        val isEmpty: Boolean get() = crls.isEmpty() && ocspResponses.isEmpty()

        operator fun plus(other: RevocationEvidence): RevocationEvidence =
            RevocationEvidence(crls + other.crls, ocspResponses + other.ocspResponses)

        companion object {
            @JvmField
            val NONE = RevocationEvidence()
        }
    }

/**
 * Evaluates CRL and OCSP evidence for a certificate chain. Shared by the XAdES and JAdES verifiers and
 * the ETSI validation pipeline; format-specific code only has to extract the evidence.
 */
object CertificateRevocationEvaluator {
    sealed class Status {
        object Good : Status()

        class Revoked(
            val cert: X509Certificate,
            val at: Instant,
            val reason: String,
        ) : Status()

        class Unavailable(
            val reason: String,
        ) : Status()
    }

    /** Most CRLs / OCSP responses considered per signature, and the largest accepted item. */
    const val MAX_ITEMS = 64
    const val MAX_ITEM_BYTES = 4 * 1024 * 1024

    /**
     * How old a CRL or OCSP response without `nextUpdate` may be before it stops counting as current. RFC 6960
     * reads an absent `nextUpdate` as "newer information is always available", so such a response vouches for
     * the present only while it is recent: without a bound, a years-old "good" would stand in for today's status.
     */
    const val MAX_AGE_WITHOUT_NEXT_UPDATE_MILLIS = 24L * 60 * 60 * 1000

    /**
     * Evaluate the signer and every CA below the trust anchor. [candidates] are the certificates the
     * signature carries (any order, unrelated ones are ignored): the chain is walked upwards from [signer]
     * by issuer name and signature, so a stray certificate cannot add a failing "unavailable" entry.
     * [issuerCertificates] are TRUST ANCHORS: the CAs the caller already trusts. A carried certificate that is
     * one of them is not itself evaluated (an anchor is trusted, not revocation-checked), nothing above it is
     * either, and the certificate it issued is verified with it. An intermediate CA must therefore be carried in
     * the signature (or be an anchor); one that is only "supplied" would be trusted without being checked.
     * Returns one [Status] per certificate checked; a revoked certificate is reported first.
     */
    fun evaluate(
        signer: X509Certificate,
        candidates: List<X509Certificate>,
        issuerCertificates: List<X509Certificate>,
        evidence: RevocationEvidence,
        authenticatedTime: Instant?,
        now: Instant,
        skewMillis: Long,
    ): List<Status> {
        val crls = parseCrls(evidence.crls.take(MAX_ITEMS))
        val ocsp = parseOcsp(evidence.ocspResponses.take(MAX_ITEMS))
        val full = listOf(signer) + CertificatePaths.walk(signer, candidates)
        // The chain ends at the first certificate the caller trusts as an anchor; it and everything above it are not checked.
        val path = full.takeWhile { it == signer || it !in issuerCertificates }
        val results = mutableListOf<Status>()
        path.forEachIndexed { index, cert ->
            if (CertificatePaths.isSelfSigned(cert)) return@forEachIndexed // self-signed anchor
            val issuer =
                full.getOrNull(index + 1)?.takeIf { CertificatePaths.signedBy(cert, it) }
                    ?: issuerCertificates.firstOrNull {
                        it.subjectX500Principal == cert.issuerX500Principal &&
                            CertificatePaths.signedBy(cert, it)
                    }
            results +=
                if (issuer == null) {
                    Status.Unavailable("the issuer of '${cert.subjectX500Principal.name}' is not available to verify revocation evidence")
                } else {
                    statusOf(cert, issuer, crls, ocsp, authenticatedTime, now, skewMillis)
                }
        }
        return results.sortedBy { if (it is Status.Revoked) 0 else 1 }
    }

    private fun statusOf(
        cert: X509Certificate,
        issuer: X509Certificate,
        crls: List<X509CRL>,
        ocsp: List<BasicOCSPResp>,
        authenticatedTime: Instant?,
        now: Instant,
        skewMillis: Long,
    ): Status {
        val name = cert.subjectX500Principal.name
        var good = false
        var detail = "no CRL or OCSP response covers '$name'"

        for (response in ocsp) {
            val singles = singlesFor(response, cert, issuer)
            if (singles.isEmpty()) continue
            if (!ocspSignedByIssuer(response, issuer)) {
                detail = "an OCSP response for '$name' is not signed by its issuer or an authorised responder"
                continue
            }
            // Revocation is monotonic: a genuine "revoked" statement counts however old it is, so freshness
            // is only required before a response may vouch that the certificate is good.
            for (single in singles) {
                val s = single.certStatus
                if (s is RevokedStatus) {
                    val at = s.revocationTime.toInstant().toKotlinInstant()
                    if (revokedAtReference(at, authenticatedTime, now, skewMillis)) {
                        return Status.Revoked(cert, at, "OCSP reports '$name' revoked at $at")
                    }
                }
            }
            val vouching = singles.filter { it.certStatus == null || it.certStatus is RevokedStatus }
            val single =
                vouching.firstOrNull { single ->
                    fresh(
                        single.thisUpdate.toInstant().toKotlinInstant(),
                        single.nextUpdate?.toInstant()?.toKotlinInstant(),
                        authenticatedTime,
                        now,
                        skewMillis,
                    )
                }
            when {
                single == null && singles.any { it.certStatus == null || it.certStatus is RevokedStatus } ->
                    detail = "the OCSP response for '$name' is not fresh enough for this signature"
                single != null -> good = true
                else -> detail = "the OCSP responder does not know '$name'"
            }
        }

        for (crl in crls) {
            if (crl.issuerX500Principal != cert.issuerX500Principal) continue
            if (!crlVerified(crl, issuer)) {
                detail = "a CRL for '$name' is not signed by its issuer"
                continue
            }
            // A signature-verified "revoked" entry counts whatever else the CRL says it covers: a delta or
            // partitioned CRL (critical extensions) cannot vouch that a certificate is good, but it can still
            // prove one is revoked. `removeFromCRL` (delta CRLs) means the opposite and is not a revocation.
            val entry = crl.getRevokedCertificate(cert)
            if (entry != null && entry.revocationReason != java.security.cert.CRLReason.REMOVE_FROM_CRL) {
                val at = entry.revocationDate.toInstant().toKotlinInstant()
                if (revokedAtReference(at, authenticatedTime, now, skewMillis)) {
                    return Status.Revoked(cert, at, "CRL lists '$name' as revoked at $at")
                }
            }
            if (!crl.criticalExtensionOIDs.isNullOrEmpty()) {
                detail = "a CRL for '$name' carries critical extensions (delta, partitioned or scoped) and cannot vouch that it is good"
                continue
            }
            val thisUpdate = crl.thisUpdate.toInstant().toKotlinInstant()
            val nextUpdate = crl.nextUpdate?.toInstant()?.toKotlinInstant()
            if (!fresh(thisUpdate, nextUpdate, authenticatedTime, now, skewMillis)) {
                detail = "the CRL for '$name' is not fresh enough for this signature"
                continue
            }
            good = true
        }
        return if (good) Status.Good else Status.Unavailable(detail)
    }

    private fun fresh(
        thisUpdate: Instant,
        nextUpdate: Instant?,
        authenticatedTime: Instant?,
        now: Instant,
        skewMillis: Long,
    ): Boolean {
        if (thisUpdate.toEpochMilliseconds() > now.toEpochMilliseconds() + skewMillis) return false
        return if (authenticatedTime != null) {
            thisUpdate.toEpochMilliseconds() >= authenticatedTime.toEpochMilliseconds() - skewMillis
        } else {
            if (nextUpdate == null) {
                thisUpdate.toEpochMilliseconds() >= now.toEpochMilliseconds() - MAX_AGE_WITHOUT_NEXT_UPDATE_MILLIS - skewMillis
            } else {
                nextUpdate.toEpochMilliseconds() >= now.toEpochMilliseconds() - skewMillis
            }
        }
    }

    private fun revokedAtReference(
        revokedAt: Instant,
        authenticatedTime: Instant?,
        now: Instant,
        skewMillis: Long,
    ): Boolean {
        val reference = authenticatedTime ?: now
        return revokedAt.toEpochMilliseconds() <= reference.toEpochMilliseconds() + skewMillis
    }

    // ----------------------------------------------------------------- CRL

    private fun parseCrls(items: List<ByteArray>): List<X509CRL> {
        val factory = CertificateFactory.getInstance("X.509")
        return items.mapNotNull { bytes ->
            if (bytes.size > MAX_ITEM_BYTES) return@mapNotNull null
            try {
                factory.generateCRL(ByteArrayInputStream(bytes)) as? X509CRL
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    /** A CRL whose signature verifies under [issuer], which may sign CRLs. Says nothing about what it covers. */
    private fun crlVerified(
        crl: X509CRL,
        issuer: X509Certificate,
    ): Boolean {
        issuer.keyUsage?.let { if (it.size <= KU_CRL_SIGN || !it[KU_CRL_SIGN]) return false }
        return try {
            crl.verify(issuer.publicKey)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- OCSP

    private fun parseOcsp(items: List<ByteArray>): List<BasicOCSPResp> =
        items.mapNotNull { bytes ->
            if (bytes.size > MAX_ITEM_BYTES) return@mapNotNull null
            try {
                val response = OCSPResp(bytes)
                if (response.status != OCSPResp.SUCCESSFUL) null else response.responseObject as? BasicOCSPResp
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    private fun singlesFor(
        response: BasicOCSPResp,
        cert: X509Certificate,
        issuer: X509Certificate,
    ): List<SingleResp> {
        val calculators = JcaDigestCalculatorProviderBuilder().build()
        val issuerHolder = JcaX509CertificateHolder(issuer)
        return response.responses.filter { single ->
            try {
                single.certID.serialNumber == cert.serialNumber && single.certID.matchesIssuer(issuerHolder, calculators)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Signed by the issuing CA itself, or by a responder certificate that CA issued for OCSP signing. */
    private fun ocspSignedByIssuer(
        response: BasicOCSPResp,
        issuer: X509Certificate,
    ): Boolean {
        val producedAt = response.producedAt
        val candidates = mutableListOf(issuer)
        for (holder in response.certs) {
            val responder =
                try {
                    org.bouncycastle.cert.jcajce
                        .JcaX509CertificateConverter()
                        .getCertificate(holder)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
            if (responder != issuer && isDelegatedResponder(responder, issuer, producedAt)) candidates += responder
        }
        return candidates.any { candidate ->
            try {
                response.isSignatureValid(JcaContentVerifierProviderBuilder().build(candidate))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun isDelegatedResponder(
        responder: X509Certificate,
        issuer: X509Certificate,
        producedAt: java.util.Date,
    ): Boolean {
        if (!CertificatePaths.signedBy(responder, issuer)) return false
        try {
            responder.checkValidity(producedAt)
        } catch (_: java.security.cert.CertificateException) {
            return false
        }
        val usage =
            try {
                responder.extendedKeyUsage
            } catch (_: java.security.cert.CertificateParsingException) {
                return false
            } ?: return false
        if (KeyPurposeId.id_kp_OCSPSigning.id !in usage) return false
        responder.keyUsage?.let { if (it.isEmpty() || !it[KU_DIGITAL_SIGNATURE]) return false }
        return true
    }

    // ------------------------------------------------------------- helpers

    private const val KU_DIGITAL_SIGNATURE = 0
    private const val KU_CRL_SIGN = 6
}
