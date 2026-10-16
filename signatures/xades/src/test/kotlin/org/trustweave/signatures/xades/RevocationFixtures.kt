package org.trustweave.signatures.xades

import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.util.Date

/** Builds a real CRL signed by a [TestCa]. */
internal object RevocationFixtures {
    fun crl(
        ca: TestCa,
        revoked: List<BigInteger> = emptyList(),
        thisUpdate: Date = Date(),
        nextUpdate: Date = Date(System.currentTimeMillis() + 24L * 3_600_000L),
    ): ByteArray {
        val builder = JcaX509v2CRLBuilder(ca.caCert.subjectX500Principal, thisUpdate).setNextUpdate(nextUpdate)
        revoked.forEach { builder.addCRLEntry(it, Date(System.currentTimeMillis() - 7_200_000L), CRLReason.keyCompromise) }
        return JcaX509CRLConverter().getCRL(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(ca.caKey.private))).encoded
    }
}
