package org.trustweave.signatures.cades

import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder
import org.bouncycastle.cert.ocsp.CertificateID
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPRespBuilder
import org.bouncycastle.cert.ocsp.RespID
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.math.BigInteger
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date

/** Builds real CRLs and OCSP responses signed by a test CA. */
internal object RevocationFixtures {
    private const val HOUR_MS = 3_600_000L

    fun ago(hours: Int) = Date(System.currentTimeMillis() - hours * HOUR_MS)

    fun ahead(hours: Int) = Date(System.currentTimeMillis() + hours * HOUR_MS)

    fun crl(
        ca: X509Certificate,
        caKey: PrivateKey,
        revoked: List<BigInteger> = emptyList(),
        thisUpdate: Date = Date(),
        nextUpdate: Date = ahead(24),
    ): ByteArray {
        val builder = JcaX509v2CRLBuilder(ca.subjectX500Principal, thisUpdate).setNextUpdate(nextUpdate)
        revoked.forEach { builder.addCRLEntry(it, ago(2), CRLReason.keyCompromise) }
        return JcaX509CRLConverter().getCRL(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(caKey))).encoded
    }

    fun ocsp(
        ca: X509Certificate,
        caKey: PrivateKey,
        subject: X509Certificate,
        revoked: Boolean = false,
    ): ByteArray {
        val calc = JcaDigestCalculatorProviderBuilder().build()
        val id = CertificateID(calc.get(CertificateID.HASH_SHA1), JcaX509CertificateHolder(ca), subject.serialNumber)
        val status: CertificateStatus? = if (revoked) RevokedStatus(ago(2), CRLReason.keyCompromise) else CertificateStatus.GOOD
        val builder = BasicOCSPRespBuilder(RespID(JcaX509CertificateHolder(ca).subject))
        builder.addResponse(id, status, Date(), ahead(24), null)
        val basic = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(caKey), emptyArray(), Date())
        return OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic).encoded
    }
}
