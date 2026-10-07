package org.trustweave.signatures.revocation

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
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
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date

/** A throwaway CA that issues certificates, CRLs and OCSP responses for the tests. */
internal class TestCa(
    val subject: String,
    parent: TestCa? = null,
    notBefore: Date = hours(-24 * 30),
    notAfter: Date = hours(24 * 365),
) {
    val key: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val cert: X509Certificate =
        build(
            issuerName = parent?.subject ?: subject,
            subjectName = subject,
            publicKey = key.public,
            signingKey = parent?.key ?: key,
            ca = true,
            notBefore = notBefore,
            notAfter = notAfter,
        )

    fun issue(
        name: String,
        publicKey: PublicKey,
        ca: Boolean = false,
        usage: Int? = KeyUsage.digitalSignature,
        notBefore: Date = hours(-24),
        notAfter: Date = hours(24 * 30),
    ): X509Certificate = build(subject, name, publicKey, key, ca, notBefore, notAfter, usage)

    fun crl(
        revoked: List<BigInteger> = emptyList(),
        thisUpdate: Date = Date(),
        nextUpdate: Date? = hours(24),
        revocationDate: Date = hours(-2),
        reason: Int = CRLReason.keyCompromise,
        critical: Boolean = false,
        signingKey: KeyPair = key,
    ): ByteArray {
        val builder = JcaX509v2CRLBuilder(cert.subjectX500Principal, thisUpdate)
        nextUpdate?.let { builder.setNextUpdate(it) }
        revoked.forEach { builder.addCRLEntry(it, revocationDate, reason) }
        if (critical) builder.addExtension(Extension.issuingDistributionPoint, true, DERSequence())
        return JcaX509CRLConverter().getCRL(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey.private))).encoded
    }

    fun ocsp(
        subject: X509Certificate,
        status: CertificateStatus? = CertificateStatus.GOOD,
        thisUpdate: Date = Date(),
        nextUpdate: Date? = hours(24),
    ): ByteArray {
        val calc = JcaDigestCalculatorProviderBuilder().build()
        val id = CertificateID(calc.get(CertificateID.HASH_SHA1), JcaX509CertificateHolder(cert), subject.serialNumber)
        val builder = BasicOCSPRespBuilder(RespID(JcaX509CertificateHolder(cert).subject))
        builder.addResponse(id, status, thisUpdate, nextUpdate, null)
        val basic = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(key.private), emptyArray(), Date())
        return OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic).encoded
    }

    fun revoked(at: Date = hours(-2)) = RevokedStatus(at, CRLReason.keyCompromise)

    private fun build(
        issuerName: String,
        subjectName: String,
        publicKey: PublicKey,
        signingKey: KeyPair,
        ca: Boolean,
        notBefore: Date,
        notAfter: Date,
        usage: Int? = if (ca) KeyUsage.keyCertSign or KeyUsage.cRLSign else KeyUsage.digitalSignature,
    ): X509Certificate {
        val builder =
            JcaX509v3CertificateBuilder(
                X500Name(issuerName),
                BigInteger.valueOf(System.nanoTime()),
                notBefore,
                notAfter,
                X500Name(subjectName),
                publicKey,
            )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        usage?.let { builder.addExtension(Extension.keyUsage, true, KeyUsage(it)) }
        val holder = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey.private))
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    companion object {
        fun hours(offset: Int) = Date(System.currentTimeMillis() + offset * 3_600_000L)

        fun leafKey(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}

/** A throwaway RFC 3161 time-stamp authority. */
internal class TestTsa(
    notBefore: Date = TestCa.hours(-24 * 30),
    notAfter: Date = TestCa.hours(24 * 365),
) {
    val key: KeyPair = TestCa.leafKey()
    private val holder: X509CertificateHolder
    val cert: X509Certificate

    init {
        val dn = X500Name("CN=Unit Test TSA")
        val builder = JcaX509v3CertificateBuilder(dn, BigInteger.valueOf(System.nanoTime()), notBefore, notAfter, dn, key.public)
        builder.addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
        holder = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(key.private))
        cert = JcaX509CertificateConverter().getCertificate(holder)
    }

    fun token(
        digest: ByteArray,
        genTime: Date = Date(),
    ): ByteArray {
        val request = TimeStampRequestGenerator().apply { setCertReq(true) }.generate(TSPAlgorithms.SHA256, digest)
        val calc = JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider()).build()
        val signerInfo = JcaSignerInfoGeneratorBuilder(calc).build(JcaContentSignerBuilder("SHA256withRSA").build(key.private), holder)
        val gen =
            TimeStampTokenGenerator(signerInfo, calc.get(AlgorithmIdentifier(TSPAlgorithms.SHA256)), ASN1ObjectIdentifier("1.2.3.4.5"))
        gen.addCertificates(JcaCertStore(listOf(holder)))
        val response =
            TimeStampResponseGenerator(
                gen,
                TSPAlgorithms.ALLOWED,
            ).generate(request, BigInteger.valueOf(System.nanoTime()), genTime)
        return TimeStampResponse(response.encoded).timeStampToken.encoded
    }
}
