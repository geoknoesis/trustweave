package org.trustweave.signatures.xades

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator
import org.trustweave.signatures.tsa.TimeStampToken
import org.trustweave.signatures.tsa.TsaClient
import org.trustweave.signatures.tsa.TsaHashAlgorithm
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.time.toKotlinInstant

/** A self-contained RFC 3161 TSA: real tokens, no network. */
internal class TestTsa {
    val key: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val holder: X509CertificateHolder
    val cert: X509Certificate

    init {
        val dn = X500Name("CN=Test TSA")
        val builder =
            JcaX509v3CertificateBuilder(
                dn,
                BigInteger.valueOf(System.nanoTime()),
                Date(System.currentTimeMillis() - 30L * 24 * 3600 * 1000),
                Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
                dn,
                key.public,
            )
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

    /** A [TsaClient] backed by this TSA. */
    fun client(): TsaClient =
        object : TsaClient {
            override suspend fun requestTimeStamp(
                digest: ByteArray,
                hashAlgorithm: TsaHashAlgorithm,
                nonce: ByteArray?,
            ): TimeStampToken {
                val now = Date()
                return TimeStampToken(
                    encoded = token(digest, now),
                    genTime = now.toInstant().toKotlinInstant(),
                    tsaSubject = "CN=Test TSA",
                    messageImprintAlgorithm = hashAlgorithm,
                    messageImprint = digest,
                    serialNumber = byteArrayOf(1),
                    policyOid = null,
                )
            }
        }
}
