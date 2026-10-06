package org.trustweave.signatures.xades

import kotlinx.coroutines.CancellationException
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.cert.ocsp.SingleResp
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * How [DefaultXadesVerifier] treats certificate revocation.
 *
 * Revocation evidence (CRLs and OCSP responses) comes from the signature's own
 * `<xades:RevocationValues>` (XAdES-B-LT) and from
 * [XadesVerificationOptions.revocationEvidence]. Every item is verified against the issuing CA's
 * key before it is used, so an attacker who edits the (unsigned) `RevocationValues` can only strip
 * evidence or replace it with something equally genuine; the freshness rules described on
 * [XadesRevocationEvidence] stop a stale "good" answer from standing in for a current one.
 */
enum class XadesRevocationPolicy {
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
     * ([XadesValidationResult.Invalid.RevocationUnavailable]).
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
class XadesRevocationEvidence
    @JvmOverloads
    constructor(
        val crls: List<ByteArray> = emptyList(),
        val ocspResponses: List<ByteArray> = emptyList(),
    ) {
        val isEmpty: Boolean get() = crls.isEmpty() && ocspResponses.isEmpty()

        internal operator fun plus(other: XadesRevocationEvidence): XadesRevocationEvidence =
            XadesRevocationEvidence(crls + other.crls, ocspResponses + other.ocspResponses)

        companion object {
            @JvmField
            val NONE = XadesRevocationEvidence()
        }
    }

/** Evaluates CRL and OCSP evidence for a certificate chain. */
internal object XadesRevocation {
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

    private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
    private const val MAX_ITEMS = 64
    private const val MAX_ITEM_BYTES = 4 * 1024 * 1024

    /** Evidence embedded in `<xades:UnsignedProperties>/<xades:UnsignedSignatureProperties>/<xades:RevocationValues>`. */
    fun embedded(qualifyingProperties: Element): XadesRevocationEvidence {
        val crls = mutableListOf<ByteArray>()
        val ocsp = mutableListOf<ByteArray>()
        val values =
            children(qualifyingProperties, "UnsignedProperties")
                .flatMap { children(it, "UnsignedSignatureProperties") }
                .flatMap { children(it, "RevocationValues") }
        for (rv in values) {
            children(rv, "CRLValues").flatMap { children(it, "EncapsulatedCRLValue") }.forEach { decode(it)?.let(crls::add) }
            children(rv, "OCSPValues").flatMap { children(it, "EncapsulatedOCSPValue") }.forEach { decode(it)?.let(ocsp::add) }
        }
        return XadesRevocationEvidence(crls.take(MAX_ITEMS), ocsp.take(MAX_ITEMS))
    }

    /**
     * Evaluate the signer ([chain] first element is the signer) and every CA below the trust anchor.
     * Returns one [Status] per certificate checked; a revoked certificate is reported first.
     */
    fun evaluate(
        signer: X509Certificate,
        chain: List<X509Certificate>,
        issuerCertificates: List<X509Certificate>,
        evidence: XadesRevocationEvidence,
        authenticatedTime: Instant?,
        now: Instant,
        skewMillis: Long,
    ): List<Status> {
        val crls = parseCrls(evidence.crls.take(MAX_ITEMS))
        val ocsp = parseOcsp(evidence.ocspResponses.take(MAX_ITEMS))
        val path = listOf(signer) + chain
        val results = mutableListOf<Status>()
        path.forEachIndexed { index, cert ->
            if (cert.subjectX500Principal == cert.issuerX500Principal) return@forEachIndexed // self-signed anchor
            val issuer =
                path.getOrNull(index + 1)?.takeIf { signedBy(cert, it) }
                    ?: issuerCertificates.firstOrNull { it.subjectX500Principal == cert.issuerX500Principal && signedBy(cert, it) }
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
            val single = singleFor(response, cert, issuer) ?: continue
            if (!ocspSignedByIssuer(response, issuer)) {
                detail = "an OCSP response for '$name' is not signed by its issuer or an authorised responder"
                continue
            }
            val thisUpdate = single.thisUpdate.toInstant().toKotlinInstant()
            val nextUpdate = single.nextUpdate?.toInstant()?.toKotlinInstant()
            if (!fresh(thisUpdate, nextUpdate, authenticatedTime, now, skewMillis)) {
                detail = "the OCSP response for '$name' is not fresh enough for this signature"
                continue
            }
            when (val s = single.certStatus) {
                null -> good = true
                is RevokedStatus -> {
                    val at = s.revocationTime.toInstant().toKotlinInstant()
                    if (revokedAtReference(at, authenticatedTime, now, skewMillis)) {
                        return Status.Revoked(cert, at, "OCSP reports '$name' revoked at $at")
                    }
                    good = true
                }
                else -> detail = "the OCSP responder does not know '$name'"
            }
        }

        for (crl in crls) {
            if (crl.issuerX500Principal != cert.issuerX500Principal) continue
            if (!crlUsable(crl, issuer)) {
                detail = "a CRL for '$name' is not a verifiable direct CRL from its issuer"
                continue
            }
            val thisUpdate = crl.thisUpdate.toInstant().toKotlinInstant()
            val nextUpdate = crl.nextUpdate?.toInstant()?.toKotlinInstant()
            if (!fresh(thisUpdate, nextUpdate, authenticatedTime, now, skewMillis)) {
                detail = "the CRL for '$name' is not fresh enough for this signature"
                continue
            }
            val entry = crl.getRevokedCertificate(cert)
            if (entry == null) {
                good = true
            } else {
                val at = entry.revocationDate.toInstant().toKotlinInstant()
                if (revokedAtReference(at, authenticatedTime, now, skewMillis)) {
                    return Status.Revoked(cert, at, "CRL lists '$name' as revoked at $at")
                }
                good = true
            }
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
            nextUpdate == null || nextUpdate.toEpochMilliseconds() >= now.toEpochMilliseconds() - skewMillis
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

    /**
     * A direct CRL signed by [issuer]. Critical extensions (delta CRL indicator, issuing
     * distribution point, ...) change what the list covers and are not interpreted here, so a CRL
     * carrying one is not used.
     */
    private fun crlUsable(
        crl: X509CRL,
        issuer: X509Certificate,
    ): Boolean {
        if (!crl.criticalExtensionOIDs.isNullOrEmpty()) return false
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

    private fun singleFor(
        response: BasicOCSPResp,
        cert: X509Certificate,
        issuer: X509Certificate,
    ): SingleResp? {
        val calculators = JcaDigestCalculatorProviderBuilder().build()
        val issuerHolder = JcaX509CertificateHolder(issuer)
        return response.responses.firstOrNull { single ->
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
        if (!signedBy(responder, issuer)) return false
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

    private fun signedBy(
        cert: X509Certificate,
        issuer: X509Certificate,
    ): Boolean =
        try {
            cert.verify(issuer.publicKey)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    private fun children(
        parent: Element,
        localName: String,
    ): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.namespaceURI == XADES_NS && node.localName == localName) out += node
        }
        return out
    }

    private fun decode(element: Element): ByteArray? =
        try {
            Base64.getMimeDecoder().decode(element.textContent.trim()).takeIf { it.size <= MAX_ITEM_BYTES }
        } catch (_: IllegalArgumentException) {
            null
        }

    private const val KU_DIGITAL_SIGNATURE = 0
    private const val KU_CRL_SIGN = 6
}
