package org.trustweave.signatures.tsa

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.X509CertificateHolder
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
 * Pure in-process RFC 3161 TSA used by tests.
 *
 * Generates an RSA-2048 self-signed certificate with the TSA Extended-Key-Usage
 * (OID `1.3.6.1.5.5.7.3.8`) and uses Bouncy Castle's [TimeStampTokenGenerator] +
 * [TimeStampResponseGenerator] to produce real `TimeStampResp` byte streams.
 *
 * Default issuance policy is `1.2.3.4.5` so tests can pin or mismatch it.
 */
class InProcessTsa private constructor(
    val certHolder: X509CertificateHolder,
    private val privateKey: PrivateKey,
    val defaultPolicyOid: String = "1.2.3.4.5",
) {

    /** DER bytes of the TSA signing certificate (useful for pin tests). */
    val certEncoded: ByteArray get() = certHolder.encoded

    /**
     * Build a complete `TimeStampResp` for [requestBytes].
     *
     * @param policyOidOverride if non-null, the token is issued under that policy regardless of
     *   what the request asked for (useful for policy-mismatch tests).
     * @param genTime override for the token's gen-time; defaults to "now".
     */
    fun stamp(
        requestBytes: ByteArray,
        policyOidOverride: String? = null,
        genTime: Date = Date(),
    ): ByteArray {
        val request = TimeStampRequest(requestBytes)
        val policyOid = policyOidOverride ?: request.reqPolicy?.id ?: defaultPolicyOid

        val digestCalcProvider = JcaDigestCalculatorProviderBuilder()
            .setProvider(PROVIDER)
            .build()
        val sha256 = digestCalcProvider.get(
            org.bouncycastle.asn1.x509.AlgorithmIdentifier(TSPAlgorithms.SHA256),
        )
        val signerBuilder = JcaSignerInfoGeneratorBuilder(digestCalcProvider)
        val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(PROVIDER)
            .build(privateKey)

        val tokenGen = TimeStampTokenGenerator(
            signerBuilder.build(contentSigner, certHolder),
            sha256,
            ASN1ObjectIdentifier(policyOid),
        )
        tokenGen.addCertificates(
            org.bouncycastle.cert.jcajce.JcaCertStore(listOf(certHolder)),
        )

        val responseGen = TimeStampResponseGenerator(tokenGen, TSPAlgorithms.ALLOWED)
        val serial = BigInteger.valueOf(System.nanoTime())
        val response = responseGen.generate(request, serial, genTime)
        return response.encoded
    }

    companion object {
        private const val PROVIDER = "BC"

        init {
            if (Security.getProvider(PROVIDER) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        fun generate(subject: String = "CN=TrustWeave Test TSA, O=TrustWeave, C=EU"): InProcessTsa {
            val keyPair = generateRsaKeyPair()
            val certHolder = selfSign(keyPair, subject)
            return InProcessTsa(certHolder, keyPair.private)
        }

        private fun generateRsaKeyPair(): KeyPair {
            val kg = KeyPairGenerator.getInstance("RSA", PROVIDER)
            kg.initialize(2048)
            return kg.generateKeyPair()
        }

        private fun selfSign(keyPair: KeyPair, subject: String): X509CertificateHolder {
            val now = Date()
            val notAfter = Date(now.time + 365L * 24 * 3600 * 1000)
            val dn = X500Name(subject)
            val builder = JcaX509v3CertificateBuilder(
                dn,
                BigInteger.valueOf(System.currentTimeMillis()),
                now,
                notAfter,
                dn,
                keyPair.public,
            )
            // RFC 3161 §2.3: TSA signer certs must carry id-kp-timeStamping EKU (1.3.6.1.5.5.7.3.8)
            builder.addExtension(
                Extension.extendedKeyUsage,
                true,
                ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping),
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(PROVIDER)
                .build(keyPair.private)
            return builder.build(signer)
        }
    }
}
