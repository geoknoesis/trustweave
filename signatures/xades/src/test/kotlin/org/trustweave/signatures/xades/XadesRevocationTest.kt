package org.trustweave.signatures.xades

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder
import org.bouncycastle.cert.ocsp.CertificateID
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPRespBuilder
import org.bouncycastle.cert.ocsp.RespID
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.jupiter.api.Test
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.xades.XadesValidationResult.Invalid
import org.trustweave.signatures.xades.XadesValidationResult.Valid
import org.w3c.dom.Document
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/** CRL / OCSP evaluation: every evidence item is verified, freshness is enforced, and REQUIRED fails closed. */
class XadesRevocationTest {
    private val ca = TestCa()
    private val signerKey = ec()
    private val signerCert = ca.issue(signerKey.public, "CN=Signer")
    private val verifier = DefaultXadesVerifier()

    private fun ec(): KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()

    private val hourMs = 3_600_000L

    private fun ago(hours: Int) = Date(System.currentTimeMillis() - hours * hourMs)

    private fun ahead(hours: Int) = Date(System.currentTimeMillis() + hours * hourMs)

    private fun activeMatch() =
        TrustAnchorMatch.QualifiedActive(
            tspName = "Test TSP",
            territory = "EU",
            service =
                TspService(
                    serviceName = "Test CA",
                    serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                    status = TspServiceStatus.GRANTED,
                    statusStartingTime = Clock.System.now(),
                    serviceCertificates = listOf(ca.caCert),
                    qualifierUris = listOf(QualifierUris.QC_WITH_SSCD),
                ),
            qcWithSscd = true,
            qcForESig = true,
        )

    private val resolver =
        object : TrustAnchorResolver {
            override fun resolve(
                signerCert: X509Certificate,
                chain: List<X509Certificate>,
            ): TrustAnchorMatch = activeMatch()
        }

    private fun options(
        policy: XadesRevocationPolicy,
        evidence: XadesRevocationEvidence = XadesRevocationEvidence.NONE,
        issuers: List<X509Certificate> = emptyList(),
    ) = XadesVerificationOptions(
        XadesProfile.B_B,
        resolver,
        revocationPolicy = policy,
        revocationEvidence = evidence,
        revocationIssuerCertificates = issuers,
    )

    private fun signed(keyInfo: List<X509Certificate> = listOf(signerCert, ca.caCert)): Document =
        XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, keyInfo, signerCert)

    // ------------------------------------------------------------- evidence builders

    private fun crl(
        revoked: List<Pair<BigInteger, Date>> = emptyList(),
        thisUpdate: Date = ago(1),
        nextUpdate: Date? = ahead(24),
        signingKey: PrivateKey = ca.caKey.private,
        withCriticalExtension: Boolean = false,
    ): ByteArray {
        val builder = JcaX509v2CRLBuilder(ca.caCert.subjectX500Principal, thisUpdate)
        nextUpdate?.let { builder.setNextUpdate(it) }
        revoked.forEach { (serial, at) -> builder.addCRLEntry(serial, at, CRLReason.keyCompromise) }
        if (withCriticalExtension) builder.addExtension(Extension.issuingDistributionPoint, true, org.bouncycastle.asn1.DERSequence())
        val holder = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey))
        return JcaX509CRLConverter().getCRL(holder).encoded
    }

    private fun delegatedResponder(withOcspEku: Boolean): Pair<KeyPair, X509Certificate> {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val builder =
            JcaX509v3CertificateBuilder(
                X500Name(ca.caSubject),
                BigInteger.valueOf(System.nanoTime()),
                ago(24),
                ahead(24 * 30),
                X500Name("CN=OCSP Responder"),
                key.public,
            )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        if (withOcspEku) builder.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_OCSPSigning))
        val cert =
            JcaX509CertificateConverter().getCertificate(
                builder.build(JcaContentSignerBuilder("SHA256withRSA").build(ca.caKey.private)),
            )
        return key to cert
    }

    private fun ocsp(
        status: CertificateStatus? = CertificateStatus.GOOD,
        thisUpdate: Date = ago(1),
        nextUpdate: Date? = ahead(24),
        responder: Pair<KeyPair, X509Certificate>? = null,
        subject: X509Certificate = signerCert,
    ): ByteArray {
        val signingKey = responder?.first?.private ?: ca.caKey.private
        val responderCert = responder?.second ?: ca.caCert
        val calc = JcaDigestCalculatorProviderBuilder().build()
        val id = CertificateID(calc.get(CertificateID.HASH_SHA1), JcaX509CertificateHolder(ca.caCert), subject.serialNumber)
        val builder = BasicOCSPRespBuilder(RespID(JcaX509CertificateHolder(responderCert).subject))
        builder.addResponse(id, status, thisUpdate, nextUpdate, null)
        val chain = if (responder != null) arrayOf(JcaX509CertificateHolder(responderCert)) else emptyArray()
        val basic = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey), chain, Date())
        return OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic).encoded
    }

    private fun revokedStatus(at: Date) = RevokedStatus(at, CRLReason.keyCompromise)

    private fun embed(
        doc: Document,
        crls: List<ByteArray> = emptyList(),
        ocsp: List<ByteArray> = emptyList(),
    ): Document {
        val qp = doc.getElementsByTagNameNS(XadesForge.XADES, "QualifyingProperties").item(0)
        val unsigned = doc.createElementNS(XadesForge.XADES, "xades:UnsignedProperties")
        val usp = doc.createElementNS(XadesForge.XADES, "xades:UnsignedSignatureProperties")
        val values = doc.createElementNS(XadesForge.XADES, "xades:RevocationValues")
        if (crls.isNotEmpty()) {
            val holder = doc.createElementNS(XadesForge.XADES, "xades:CRLValues")
            crls.forEach { bytes ->
                holder.appendChild(
                    doc.createElementNS(XadesForge.XADES, "xades:EncapsulatedCRLValue").apply {
                        textContent = Base64.getEncoder().encodeToString(bytes)
                    },
                )
            }
            values.appendChild(holder)
        }
        if (ocsp.isNotEmpty()) {
            val holder = doc.createElementNS(XadesForge.XADES, "xades:OCSPValues")
            ocsp.forEach { bytes ->
                holder.appendChild(
                    doc.createElementNS(XadesForge.XADES, "xades:EncapsulatedOCSPValue").apply {
                        textContent = Base64.getEncoder().encodeToString(bytes)
                    },
                )
            }
            values.appendChild(holder)
        }
        usp.appendChild(values)
        unsigned.appendChild(usp)
        qp.appendChild(unsigned)
        return doc
    }

    private fun evidence(
        crls: List<ByteArray> = emptyList(),
        ocsp: List<ByteArray> = emptyList(),
    ) = XadesRevocationEvidence(crls, ocsp)

    // ------------------------------------------------------------------- verifier

    @Test
    fun `revocation is not evaluated by default`() =
        runTest {
            val revoked = crl(listOf(signerCert.serialNumber to ago(2)))
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.NOT_CHECKED, evidence(crls = listOf(revoked))))
            result.shouldBeInstanceOf<Valid>()
            result.revocationChecked shouldBe false
        }

    @Test
    fun `a good CRL satisfies REQUIRED and marks revocation as checked`() =
        runTest {
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(crls = listOf(crl()))))
            result.shouldBeInstanceOf<Valid>()
            result.revocationChecked shouldBe true
        }

    @Test
    fun `a good OCSP response satisfies REQUIRED`() =
        runTest {
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(ocsp = listOf(ocsp()))))
            result.shouldBeInstanceOf<Valid>()
            result.revocationChecked shouldBe true
        }

    @Test
    fun `a CRL listing the signer refuses the signature`() =
        runTest {
            val revoked = crl(listOf(signerCert.serialNumber to ago(2)))
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(crls = listOf(revoked))))
            result.shouldBeInstanceOf<Invalid.CertificateRevoked>().cert shouldBe signerCert
        }

    @Test
    fun `an OCSP response reporting the signer revoked refuses the signature`() =
        runTest {
            val response = ocsp(status = revokedStatus(ago(2)))
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.CHECK_IF_AVAILABLE, evidence(ocsp = listOf(response))))
            result.shouldBeInstanceOf<Invalid.CertificateRevoked>()
        }

    @Test
    fun `a revoked answer wins over a good one`() =
        runTest {
            val good = ocsp()
            val revoked = crl(listOf(signerCert.serialNumber to ago(2)))
            val result =
                verifier.verify(
                    signed(),
                    options(XadesRevocationPolicy.REQUIRED, evidence(crls = listOf(revoked), ocsp = listOf(good))),
                )
            result.shouldBeInstanceOf<Invalid.CertificateRevoked>()
        }

    @Test
    fun `REQUIRED fails closed when there is no evidence`() =
        runTest {
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED))
            result.shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `CHECK_IF_AVAILABLE accepts a signature with no evidence but does not claim it was checked`() =
        runTest {
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.CHECK_IF_AVAILABLE))
            result.shouldBeInstanceOf<Valid>()
            result.revocationChecked shouldBe false
        }

    @Test
    fun `a CRL signed by a different key is not trusted, even with the CA's name`() =
        runTest {
            val forger = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val forged = crl(signingKey = forger.private)
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(crls = listOf(forged))))
            result.shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `a forged revoked CRL cannot be used to deny a good signer, it is simply ignored`() =
        runTest {
            val forger = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val forged = crl(listOf(signerCert.serialNumber to ago(2)), signingKey = forger.private)
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.CHECK_IF_AVAILABLE, evidence(crls = listOf(forged))))
            result.shouldBeInstanceOf<Valid>()
            result.revocationChecked shouldBe false
        }

    @Test
    fun `a CRL past its nextUpdate is not fresh`() =
        runTest {
            val stale = crl(thisUpdate = ago(72), nextUpdate = ago(48))
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(crls = listOf(stale))))
            result.shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `a CRL with a critical extension is not used`() =
        runTest {
            val scoped = crl(withCriticalExtension = true)
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(crls = listOf(scoped))))
            result.shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `an OCSP answer of unknown is not good`() =
        runTest {
            val unknown =
                ocsp(
                    status =
                        org.bouncycastle.cert.ocsp
                            .UnknownStatus(),
                )
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(ocsp = listOf(unknown))))
            result.shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `an OCSP response for a different certificate does not cover the signer`() =
        runTest {
            val other = ca.issue(ec().public, "CN=Other")
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(ocsp = listOf(ocsp(subject = other)))))
            result.shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `an OCSP response from a delegated responder with the OCSP signing purpose is accepted`() =
        runTest {
            val response = ocsp(responder = delegatedResponder(withOcspEku = true))
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(ocsp = listOf(response))))
            result.shouldBeInstanceOf<Valid>()
        }

    @Test
    fun `an OCSP responder certificate without the OCSP signing purpose is rejected`() =
        runTest {
            val response = ocsp(responder = delegatedResponder(withOcspEku = false))
            val result = verifier.verify(signed(), options(XadesRevocationPolicy.REQUIRED, evidence(ocsp = listOf(response))))
            result.shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `evidence embedded in RevocationValues is used`() =
        runTest {
            val doc = embed(signed(), crls = listOf(crl()))
            val result = verifier.verify(doc, options(XadesRevocationPolicy.REQUIRED))
            result.shouldBeInstanceOf<Valid>().revocationChecked shouldBe true
        }

    @Test
    fun `embedded revocation of the signer is enforced`() =
        runTest {
            val doc = embed(signed(), ocsp = listOf(ocsp(status = revokedStatus(ago(3)))))
            verifier.verify(doc, options(XadesRevocationPolicy.REQUIRED)).shouldBeInstanceOf<Invalid.CertificateRevoked>()
        }

    @Test
    fun `garbage in RevocationValues is ignored and REQUIRED then fails closed`() =
        runTest {
            val doc = embed(signed(), crls = listOf("not a crl".toByteArray()), ocsp = listOf("nor this".toByteArray()))
            verifier.verify(doc, options(XadesRevocationPolicy.REQUIRED)).shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `an issuer missing from KeyInfo can be supplied through revocationIssuerCertificates`() =
        runTest {
            val doc = signed(keyInfo = listOf(signerCert))
            val noIssuer = verifier.verify(doc, options(XadesRevocationPolicy.REQUIRED, evidence(crls = listOf(crl()))))
            noIssuer.shouldBeInstanceOf<Invalid.RevocationUnavailable>()
            val withIssuer =
                verifier.verify(doc, options(XadesRevocationPolicy.REQUIRED, evidence(crls = listOf(crl())), issuers = listOf(ca.caCert)))
            withIssuer.shouldBeInstanceOf<Valid>().revocationChecked shouldBe true
        }

    // ------------------------------------------------- authenticated-time semantics (evaluator)

    private fun evaluate(
        evidence: XadesRevocationEvidence,
        authenticatedTime: Instant?,
    ) = XadesRevocation.evaluate(
        signer = signerCert,
        chain = listOf(ca.caCert),
        issuerCertificates = emptyList(),
        evidence = evidence,
        authenticatedTime = authenticatedTime,
        now = Clock.System.now(),
        skewMillis = 300_000,
    )

    @Test
    fun `a revocation after the authenticated signing time does not invalidate the signature`() {
        val signedAt = (Clock.System.now() - 10.hours)
        val laterRevocation = crl(listOf(signerCert.serialNumber to ago(2)), thisUpdate = ago(1))
        evaluate(evidence(crls = listOf(laterRevocation)), signedAt).single().shouldBeInstanceOf<XadesRevocation.Status.Good>()
    }

    @Test
    fun `a revocation before the authenticated signing time invalidates the signature`() {
        val signedAt = (Clock.System.now() - 1.hours)
        val earlier = crl(listOf(signerCert.serialNumber to ago(5)), thisUpdate = ago(1))
        val revoked = evaluate(evidence(crls = listOf(earlier)), signedAt).single().shouldBeInstanceOf<XadesRevocation.Status.Revoked>()
        (revoked.at < signedAt) shouldBe true
    }

    @Test
    fun `evidence issued before the authenticated signing time says nothing about it`() {
        val signedAt = (Clock.System.now() - 1.hours)
        val tooOld = crl(thisUpdate = ago(10), nextUpdate = ahead(24))
        evaluate(evidence(crls = listOf(tooOld)), signedAt).single().shouldBeInstanceOf<XadesRevocation.Status.Unavailable>()
    }

    @Test
    fun `a CRL whose nextUpdate has passed still covers an authenticated signature it postdates`() {
        val signedAt = (Clock.System.now() - 30.hours)
        val postdating = crl(thisUpdate = ago(24), nextUpdate = ago(1))
        evaluate(evidence(crls = listOf(postdating)), signedAt).single().shouldBeInstanceOf<XadesRevocation.Status.Good>()
    }

    @Test
    fun `revocation dates convert consistently`() {
        val d = Date()
        (d.toInstant().toKotlinInstant().toEpochMilliseconds()) shouldBe d.time
    }
}
