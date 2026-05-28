package org.trustweave.signatures.jades

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.util.Date

/**
 * Minimal in-process RFC 3161 TSA for the JAdES B-T test. Mirrors the InProcessTsa fixture in
 * `signatures:tsa-core` test sources, inlined here to avoid cross-module test-source visibility.
 */
internal class TestTsa private constructor(
    val certHolder: X509CertificateHolder,
    private val privateKey: PrivateKey,
    val defaultPolicyOid: String = "1.2.3.4.5",
) {
    fun stamp(requestBytes: ByteArray): ByteArray {
        val request = TimeStampRequest(requestBytes)
        val digestCalcProvider = JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
        val sha256 = digestCalcProvider.get(
            org.bouncycastle.asn1.x509.AlgorithmIdentifier(TSPAlgorithms.SHA256),
        )
        val signerInfoGen = JcaSignerInfoGeneratorBuilder(digestCalcProvider).build(
            JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(privateKey),
            certHolder,
        )
        val tokenGen = TimeStampTokenGenerator(
            signerInfoGen, sha256, ASN1ObjectIdentifier(request.reqPolicy?.id ?: defaultPolicyOid),
        )
        tokenGen.addCertificates(JcaCertStore(listOf(certHolder)))
        val responseGen = TimeStampResponseGenerator(tokenGen, TSPAlgorithms.ALLOWED)
        return responseGen.generate(request, BigInteger.valueOf(System.nanoTime()), Date()).encoded
    }

    companion object {
        init {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        fun generate(): TestTsa {
            val keyPair = KeyPairGenerator.getInstance("RSA", "BC").run {
                initialize(2048); generateKeyPair()
            }
            val cert = selfSign(keyPair)
            return TestTsa(cert, keyPair.private)
        }

        private fun selfSign(keyPair: KeyPair): X509CertificateHolder {
            val now = Date()
            val notAfter = Date(now.time + 365L * 24 * 3600 * 1000)
            val dn = X500Name("CN=JAdES Test TSA, O=TrustWeave, C=EU")
            val builder = JcaX509v3CertificateBuilder(
                dn, BigInteger.valueOf(System.nanoTime()), now, notAfter, dn, keyPair.public,
            )
            builder.addExtension(
                Extension.extendedKeyUsage, true,
                ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping),
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)
            return builder.build(signer)
        }
    }
}
