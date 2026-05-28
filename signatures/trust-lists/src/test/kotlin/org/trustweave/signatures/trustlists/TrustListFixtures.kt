package org.trustweave.signatures.trustlists

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date

/**
 * Test-side helpers that synthesise minimal but standards-shaped trust-list inputs.
 *
 * Two artefacts are produced:
 * - A throwaway CA + end-entity certificate pair via Bouncy Castle ([generateCaAndSigner]).
 * - Minimal LoTL + TSL XML documents that embed the CA cert and reference the right ETSI
 *   service-type / status / qualifier URIs ([renderLotlXml], [renderTslXml]).
 */
object TrustListFixtures {

    private const val PROVIDER = "BC"

    init {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // ---------------------------------------------------------------- certificates

    data class CaAndSigner(
        val caCert: X509Certificate,
        val signerCert: X509Certificate,
        val caKey: KeyPair,
        val signerKey: KeyPair,
    ) {
        val caCertBase64: String by lazy { Base64.getEncoder().encodeToString(caCert.encoded) }
    }

    /**
     * Generate an in-memory self-signed CA and an end-entity certificate signed by that CA.
     *
     * Both keys are RSA-2048. The CA carries basic-constraints CA=true and keyCertSign usage;
     * the signer carries digitalSignature usage. Validity is one year.
     */
    fun generateCaAndSigner(
        caSubject: String = "CN=TrustWeave Test CA, O=TrustWeave, C=EU",
        signerSubject: String = "CN=TrustWeave Test Signer, O=TrustWeave, C=EU",
    ): CaAndSigner {
        val caKey = rsa2048()
        val signerKey = rsa2048()

        val notBefore = Date(System.currentTimeMillis() - 60_000) // backdate one minute
        val notAfter = Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000)

        val caHolder = buildCaCertificate(caKey, caSubject, notBefore, notAfter)
        val signerHolder = buildEndEntityCertificate(
            issuerKey = caKey,
            issuerHolder = caHolder,
            subjectKey = signerKey,
            subjectDn = signerSubject,
            notBefore = notBefore,
            notAfter = notAfter,
        )

        return CaAndSigner(
            caCert = CONVERTER.getCertificate(caHolder),
            signerCert = CONVERTER.getCertificate(signerHolder),
            caKey = caKey,
            signerKey = signerKey,
        )
    }

    private fun rsa2048(): KeyPair = KeyPairGenerator.getInstance("RSA", PROVIDER).run {
        initialize(2048)
        generateKeyPair()
    }

    private fun buildCaCertificate(
        keyPair: KeyPair,
        subject: String,
        notBefore: Date,
        notAfter: Date,
    ): X509CertificateHolder {
        val dn = X500Name(subject)
        val builder = JcaX509v3CertificateBuilder(
            dn,
            BigInteger.valueOf(System.currentTimeMillis()),
            notBefore,
            notAfter,
            dn,
            keyPair.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(PROVIDER)
            .build(keyPair.private)
        return builder.build(signer)
    }

    private fun buildEndEntityCertificate(
        issuerKey: KeyPair,
        issuerHolder: X509CertificateHolder,
        subjectKey: KeyPair,
        subjectDn: String,
        notBefore: Date,
        notAfter: Date,
    ): X509CertificateHolder {
        val builder = JcaX509v3CertificateBuilder(
            issuerHolder.subject,
            BigInteger.valueOf(System.nanoTime()),
            notBefore,
            notAfter,
            X500Name(subjectDn),
            subjectKey.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(PROVIDER)
            .build(issuerKey.private)
        return builder.build(signer)
    }

    // ---------------------------------------------------------------- XML rendering

    /**
     * Minimal but well-shaped LoTL XML. The MVP parser only consumes top-level metadata, so we
     * skip the `OtherTSLPointers` block (the test passes the per-MS TSL directly via the map).
     */
    fun renderLotlXml(
        schemeOperator: String = "European Commission",
        sequenceNumber: Int = 456,
        issuedAt: String = "2026-01-01T00:00:00Z",
        nextUpdateAt: String? = "2026-07-01T00:00:00Z",
    ): ByteArray {
        val nextUpdate = nextUpdateAt
            ?.let { "<NextUpdate><dateTime>$it</dateTime></NextUpdate>" }
            ?: "<NextUpdate/>"
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
              <SchemeInformation>
                <TSLVersionIdentifier>5</TSLVersionIdentifier>
                <TSLSequenceNumber>$sequenceNumber</TSLSequenceNumber>
                <SchemeOperatorName>
                  <Name xml:lang="en">$schemeOperator</Name>
                </SchemeOperatorName>
                <ListIssueDateTime>$issuedAt</ListIssueDateTime>
                $nextUpdate
              </SchemeInformation>
            </TrustServiceStatusList>
        """.trimIndent().trim()
        return xml.toByteArray(Charsets.UTF_8)
    }

    data class TslServiceSpec(
        val serviceName: String,
        val serviceTypeUri: String,
        val statusUri: String,
        val statusStartingTime: String,
        val caCertBase64: String,
        val qualifierUris: List<String>,
    )

    /**
     * Minimal TSL XML carrying a single TSP with one or more services.
     */
    fun renderTslXml(
        territory: String,
        schemeOperator: String,
        tspName: String,
        services: List<TslServiceSpec>,
        sequenceNumber: Int = 1,
        issuedAt: String = "2026-01-01T00:00:00Z",
    ): ByteArray {
        val servicesXml = services.joinToString(separator = "\n") { svc -> renderService(svc) }
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
              <SchemeInformation>
                <TSLVersionIdentifier>5</TSLVersionIdentifier>
                <TSLSequenceNumber>$sequenceNumber</TSLSequenceNumber>
                <SchemeOperatorName>
                  <Name xml:lang="en">$schemeOperator</Name>
                </SchemeOperatorName>
                <SchemeTerritory>$territory</SchemeTerritory>
                <ListIssueDateTime>$issuedAt</ListIssueDateTime>
              </SchemeInformation>
              <TrustServiceProviderList>
                <TrustServiceProvider>
                  <TSPInformation>
                    <TSPName><Name xml:lang="en">$tspName</Name></TSPName>
                  </TSPInformation>
                  <TSPServices>
                    $servicesXml
                  </TSPServices>
                </TrustServiceProvider>
              </TrustServiceProviderList>
            </TrustServiceStatusList>
        """.trimIndent().trim()
        return xml.toByteArray(Charsets.UTF_8)
    }

    private fun renderService(svc: TslServiceSpec): String {
        val qualifiers = if (svc.qualifierUris.isEmpty()) "" else """
            <ServiceInformationExtensions>
              <Extension Critical="false">
                <Qualifications>
                  <QualificationElement>
                    <Qualifiers>
                      ${svc.qualifierUris.joinToString("\n") { "<Qualifier uri=\"$it\"/>" }}
                    </Qualifiers>
                  </QualificationElement>
                </Qualifications>
              </Extension>
            </ServiceInformationExtensions>
        """.trimIndent()

        return """
            <TSPService>
              <ServiceInformation>
                <ServiceTypeIdentifier>${svc.serviceTypeUri}</ServiceTypeIdentifier>
                <ServiceName><Name xml:lang="en">${svc.serviceName}</Name></ServiceName>
                <ServiceDigitalIdentity>
                  <DigitalId>
                    <X509Certificate>${svc.caCertBase64}</X509Certificate>
                  </DigitalId>
                </ServiceDigitalIdentity>
                <ServiceStatus>${svc.statusUri}</ServiceStatus>
                <StatusStartingTime>${svc.statusStartingTime}</StatusStartingTime>
                $qualifiers
              </ServiceInformation>
            </TSPService>
        """.trimIndent()
    }

    private val CONVERTER = JcaX509CertificateConverter().setProvider(PROVIDER)
}
