package org.trustweave.signatures.trustlists

import kotlinx.datetime.Instant
import java.security.cert.X509Certificate

/**
 * Parsed European trust graph: one root LoTL (List of Trusted Lists) plus N per-Member-State
 * Trusted Service Lists (TSLs).
 *
 * Built by [TrustListParser] from ETSI TS 119 612 XML bytes. Consumed by
 * [TrustAnchorResolver] to decide whether a signing certificate chains to a qualified
 * Trust Service Provider (QTSP).
 *
 * @property schemeOperator      Top-level scheme operator name, typically "European Commission".
 * @property sequenceNumber      LoTL sequence number from `SchemeInformation.TSLSequenceNumber`.
 * @property issuedAt            LoTL issue date.
 * @property nextUpdateAt        Next-update hint from the LoTL; null when the field was absent.
 * @property memberStateLists    One entry per Member State whose TSL XML was supplied to the
 *                               parser. Entries are ordered as the LoTL pointers appear; missing
 *                               TSLs (LoTL references a territory whose XML was not provided) are
 *                               silently omitted.
 */
data class TrustList(
    val schemeOperator: String,
    val sequenceNumber: Int,
    val issuedAt: Instant,
    val nextUpdateAt: Instant?,
    val memberStateLists: List<MemberStateTsl>,
)

/**
 * One Member State's Trusted Service List, parsed from ETSI TS 119 612 XML.
 *
 * @property territory       ISO 3166-1 alpha-2 country code (e.g. `"DE"`, `"FR"`, `"EU"` for the
 *                           European Commission's own list).
 * @property schemeOperator  National scheme operator name.
 * @property sequenceNumber  TSL sequence number.
 * @property issuedAt        TSL issue date.
 * @property trustedTsps     The Trust Service Providers listed as currently or historically
 *                           recognised under this Member State's regulatory regime.
 */
data class MemberStateTsl(
    val territory: String,
    val schemeOperator: String,
    val sequenceNumber: Int,
    val issuedAt: Instant,
    val trustedTsps: List<TrustedTSP>,
)

/**
 * A Trust Service Provider listed inside a [MemberStateTsl].
 *
 * @property name       TSP legal name from `TSPInformation.TSPName`.
 * @property tradeName  Optional trade name from `TSPInformation.TSPTradeName`.
 * @property services   The TSP's individual qualified services.
 */
data class TrustedTSP(
    val name: String,
    val tradeName: String?,
    val services: List<TspService>,
)

/**
 * A single Trust Service published by a [TrustedTSP].
 *
 * Status history is intentionally not modelled in the MVP — only the current
 * [status] and [statusStartingTime] are exposed. A future revision will add the
 * `ServiceHistoryInstance` chain so that signatures can be validated against the
 * service status that was in force at the asserted signing time.
 *
 * @property serviceName          Service name from `ServiceInformation.ServiceName`.
 * @property serviceType          Mapped from `ServiceTypeIdentifier`.
 * @property status               Mapped from `ServiceStatus`.
 * @property statusStartingTime   Mapped from `StatusStartingTime`.
 * @property serviceCertificates  All X.509 certificates extracted from `ServiceDigitalIdentity`.
 *                                Typically a single CA certificate; a non-empty list otherwise.
 * @property qualifierUris        URIs from
 *                                `ServiceInformationExtensions/Qualifications/QualifiersList` that
 *                                define the service's regulatory qualifiers, e.g.
 *                                `http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCWithSSCD`
 *                                or `…/QCForESig`.
 */
data class TspService(
    val serviceName: String,
    val serviceType: TspServiceType,
    val status: TspServiceStatus,
    val statusStartingTime: Instant,
    val serviceCertificates: List<X509Certificate>,
    val qualifierUris: List<String>,
)

/**
 * Mapped form of the ETSI `ServiceTypeIdentifier` URI.
 *
 * Only the categories relevant to qualified electronic signatures are modelled. Anything else
 * collapses to [OTHER] — the MVP JAdES verifier does not consume the long tail of niche service
 * categories (national-eID providers, registered-mail, archival services, etc.).
 */
enum class TspServiceType(val uri: String) {
    /** `http://uri.etsi.org/TrstSvc/Svctype/CA/QC` — CA for qualified certificates. */
    CA_FOR_QUALIFIED_CERTIFICATES("http://uri.etsi.org/TrstSvc/Svctype/CA/QC"),

    /** `http://uri.etsi.org/TrstSvc/Svctype/TSA/QTST` — qualified time-stamp authority. */
    QUALIFIED_TIMESTAMP("http://uri.etsi.org/TrstSvc/Svctype/TSA/QTST"),

    /** `http://uri.etsi.org/TrstSvc/Svctype/AdESValidation/Q` — qualified validation service. */
    QUALIFIED_VALIDATION_SERVICE("http://uri.etsi.org/TrstSvc/Svctype/AdESValidation/Q"),

    /** Any service-type URI not in the enum above. */
    OTHER(""),
    ;

    companion object {
        /** Map a service-type URI to the enum; falls back to [OTHER] when unknown. */
        fun fromUri(uri: String): TspServiceType =
            entries.firstOrNull { it.uri == uri } ?: OTHER
    }
}

/**
 * Mapped form of the ETSI `ServiceStatus` URI.
 *
 * The four values cover the status URIs published in production EU TSLs as of TS 119 612 v2.3.1.
 * Less common values (`recognisedatnationallevel`, deprecated `inaccord` etc.) collapse to
 * [RECOGNISEDATNATIONALLEVEL] or [OTHER] as appropriate.
 */
enum class TspServiceStatus(val uri: String) {
    /** `…/svcstatus/granted` — service is in force. */
    GRANTED("http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted"),

    /** `…/svcstatus/withdrawn` — service has been withdrawn. */
    WITHDRAWN("http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/withdrawn"),

    /** `…/svcstatus/recognisedatnationallevel` — recognised under national law but not eIDAS-qualified. */
    RECOGNISEDATNATIONALLEVEL(
        "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/recognisedatnationallevel",
    ),

    /** Any other status URI. */
    OTHER(""),
    ;

    companion object {
        fun fromUri(uri: String): TspServiceStatus =
            entries.firstOrNull { it.uri == uri } ?: OTHER
    }
}

/**
 * Well-known qualifier URIs from `ServiceInformationExtensions`. Listed here as constants for
 * convenience — these are the strings the JAdES verifier looks for to decide between AdES and
 * QES qualification, and between QCForESig / QCForESeal / QCForWSA.
 */
object QualifierUris {
    const val QC_WITH_SSCD = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCWithSSCD"
    const val QC_NO_SSCD = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCNoSSCD"
    const val QC_SSCD_STATUS_AS_IN_CERT =
        "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCSSCDStatusAsInCert"
    const val QC_FOR_ESIG = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCForESig"
    const val QC_FOR_ESEAL = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCForESeal"
    const val QC_FOR_WSA = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCForWSA"
}
