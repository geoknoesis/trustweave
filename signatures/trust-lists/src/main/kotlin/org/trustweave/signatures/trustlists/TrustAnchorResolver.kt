package org.trustweave.signatures.trustlists

import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Resolves a signing certificate (plus its chain) against a parsed [TrustList], producing a
 * [TrustAnchorMatch] that tells the JAdES verifier whether the signature can be claimed as
 * eIDAS-qualified.
 */
interface TrustAnchorResolver {

    /**
     * Resolve [signerCert] against the configured trust graph.
     *
     * @param signerCert  The certificate that signed the JAdES payload.
     * @param chain       Intermediate certificates between [signerCert] and a root, in any order.
     *                    Implementations are expected to find a valid PKIX path themselves.
     *                    The list MAY include [signerCert] itself; it MAY be empty if [signerCert]
     *                    is directly issued by a root present in the trust list.
     *
     * @return [TrustAnchorMatch.QualifiedActive] when the chain anchors at a CA/QC service whose
     *         status is currently `GRANTED`; [TrustAnchorMatch.QualifiedWithdrawn] when the matched
     *         service has been withdrawn; [TrustAnchorMatch.NotTrusted] otherwise.
     */
    fun resolve(signerCert: X509Certificate, chain: List<X509Certificate>): TrustAnchorMatch

    /**
     * Resolve [signerCert] with the path validated as of [validationTime] instead of now. Verifiers pass the
     * time of a *trusted* time-stamp so that a signature made while its certificate was valid can still be
     * validated after the certificate expired. A time that is merely claimed by the signer must never be passed
     * here: it would let anyone back-date a signature made with an expired or compromised certificate.
     *
     * The default implementation ignores [validationTime] and validates as of now, which is the safe choice for
     * resolvers that cannot honour it.
     */
    fun resolve(
        signerCert: X509Certificate,
        chain: List<X509Certificate>,
        validationTime: Instant?,
    ): TrustAnchorMatch = resolve(signerCert, chain)
}

/**
 * Default [TrustAnchorResolver] backed by the JDK's [CertPathValidator] (`PKIX` algorithm).
 *
 * For each candidate CA/QC service in the supplied [TrustList], the resolver constructs a
 * [PKIXParameters] instance with that CA's certificates as the trust anchor set and asks the
 * validator to find a path from [signerCert] (via the supplied intermediates) to one of those
 * anchors. The first successful match wins; the resolver then synthesises the
 * [TrustAnchorMatch.QualifiedActive] or [TrustAnchorMatch.QualifiedWithdrawn] value using the
 * service-level metadata (qualifier URIs, current status).
 *
 * Revocation checking is **disabled** at this layer — CRL / OCSP lookups belong in the JAdES
 * verifier's "validation data" stage (ETSI EN 319 102-1 §5.4) and are not in MVP scope.
 *
 * The resolver is thread-safe: instances are immutable and the JDK PKIX validator is per-call.
 */
class DefaultTrustAnchorResolver(
    private val trustList: TrustList,
) : TrustAnchorResolver {

    /**
     * Flattened view: every CA/QC service across every Member State, paired with the territory
     * code so we can attribute matches accurately.
     */
    private val qualifiedCaServices: List<MatchedService> =
        trustList.memberStateLists.flatMap { ms ->
            ms.trustedTsps.flatMap { tsp ->
                tsp.services
                    .filter { it.serviceType == TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES }
                    .filter { it.serviceCertificates.isNotEmpty() }
                    .map { service ->
                        MatchedService(
                            territory = ms.territory,
                            tspName = tsp.name,
                            service = service,
                        )
                    }
            }
        }

    override fun resolve(
        signerCert: X509Certificate,
        chain: List<X509Certificate>,
    ): TrustAnchorMatch = resolve(signerCert, chain, null)

    override fun resolve(
        signerCert: X509Certificate,
        chain: List<X509Certificate>,
        validationTime: Instant?,
    ): TrustAnchorMatch {
        val candidates = qualifiedCaServices
        if (candidates.isEmpty()) return TrustAnchorMatch.NotTrusted

        // Build a CertPath from signer + chain (excluding the signer if it's also in `chain`).
        val intermediates = chain.filter { it != signerCert }
        val pathCerts = listOf(signerCert) + intermediates
        val certPath: CertPath = CERT_FACTORY.generateCertPath(pathCerts)

        for (candidate in candidates) {
            val match = tryValidate(certPath, candidate, validationTime)
            if (match != null) return match
        }
        return TrustAnchorMatch.NotTrusted
    }

    private fun tryValidate(
        certPath: CertPath,
        candidate: MatchedService,
        validationTime: Instant?,
    ): TrustAnchorMatch? {
        val anchors = candidate.service.serviceCertificates
            .map { TrustAnchor(it, null) }
            .toSet()
        if (anchors.isEmpty()) return null

        val params = try {
            PKIXParameters(anchors)
        } catch (t: Throwable) {
            // E.g. anchor certs flagged as non-CA. Don't let one malformed candidate fail the rest.
            return null
        }
        params.isRevocationEnabled = false
        // Validity periods are checked as of now unless the caller supplied the time of a trusted time-stamp.
        validationTime?.let { params.date = java.util.Date(it.toEpochMilliseconds()) }

        val validator = CertPathValidator.getInstance("PKIX")
        try {
            validator.validate(certPath, params)
        } catch (t: Throwable) {
            return null
        }

        return buildMatch(candidate)
    }

    private fun buildMatch(matched: MatchedService): TrustAnchorMatch {
        val service = matched.service
        return when (service.status) {
            TspServiceStatus.GRANTED -> TrustAnchorMatch.QualifiedActive(
                tspName = matched.tspName,
                territory = matched.territory,
                service = service,
                qcWithSscd = service.qualifierUris.any {
                    it == QualifierUris.QC_WITH_SSCD ||
                        it == QualifierUris.QC_SSCD_STATUS_AS_IN_CERT
                },
                qcForESig = service.qualifierUris.any { it == QualifierUris.QC_FOR_ESIG },
            )

            TspServiceStatus.WITHDRAWN -> TrustAnchorMatch.QualifiedWithdrawn(
                tspName = matched.tspName,
                withdrawnAt = service.statusStartingTime,
            )

            else -> null ?: TrustAnchorMatch.NotTrusted
        }
    }

    private data class MatchedService(
        val territory: String,
        val tspName: String,
        val service: TspService,
    )

    /**
     * Convert a [java.util.Date] to a [kotlin.time.Instant] without dropping precision.
     * Currently unused in MVP but reserved for future "validation at a specific signing time"
     * support in JAdES B-LT.
     */
    @Suppress("unused")
    internal fun toKxInstant(d: java.util.Date): Instant = d.toInstant().toKotlinInstant()

    companion object {
        private val CERT_FACTORY: CertificateFactory = CertificateFactory.getInstance("X.509")
    }
}
