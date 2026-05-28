package org.trustweave.signatures.cades

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date

/** Mirror of `signatures/jades/src/test/.../TestCerts.kt` for CAdES tests. */
internal class TestCa(
    val caSubject: String = "CN=TrustWeave CAdES Test CA, O=TrustWeave, C=EU",
) {
    private val caKey: KeyPair = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }.generateKeyPair()
    val caCert: X509Certificate

    init {
        val now = Date()
        val notAfter = Date(now.time + 365L * 24 * 3600 * 1000)
        val dn = X500Name(caSubject)
        val builder = JcaX509v3CertificateBuilder(
            dn, BigInteger.valueOf(System.nanoTime()), now, notAfter, dn, caKey.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKey.private)
        caCert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    fun issue(
        subjectPublicKey: PublicKey,
        subject: String,
        notBefore: Date = Date(System.currentTimeMillis() - 60_000),
        notAfter: Date = Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            X500Name(caSubject),
            BigInteger.valueOf(System.nanoTime()),
            notBefore,
            notAfter,
            X500Name(subject),
            subjectPublicKey,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKey.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    fun issueChainBytes(subjectPublicKey: PublicKey, subject: String): List<ByteArray> {
        val endEntity = issue(subjectPublicKey, subject)
        return listOf(endEntity.encoded, caCert.encoded)
    }
}
