package org.trustweave.signatures.xades

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
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
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** B-T time-stamp verification, withdrawn-trust timing and structural chain validation. */
class XadesTimestampAndChainTest {
    private val verifier = DefaultXadesVerifier()
    private val day = 24L * 3600 * 1000

    private fun ecKey(): KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()

    private fun match(withdrawnAt: kotlin.time.Instant? = null): TrustAnchorMatch =
        if (withdrawnAt != null) {
            TrustAnchorMatch.QualifiedWithdrawn("Test TSP", withdrawnAt)
        } else {
            TrustAnchorMatch.QualifiedActive(
                "Test TSP",
                "EU",
                TspService(
                    "CA",
                    TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                    TspServiceStatus.GRANTED,
                    Clock.System.now(),
                    emptyList(),
                    listOf(QualifierUris.QC_WITH_SSCD),
                ),
                qcWithSscd = true,
                qcForESig = true,
            )
        }

    private fun resolver(answer: TrustAnchorMatch) =
        object : TrustAnchorResolver {
            override fun resolve(
                signerCert: X509Certificate,
                chain: List<X509Certificate>,
            ) = answer
        }

    // ------------------------------------------------------------------ time-stamp support

    private class Tsa {
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
            genTime: Date,
        ): ByteArray {
            val request = TimeStampRequestGenerator().apply { setCertReq(true) }.generate(TSPAlgorithms.SHA256, digest)
            val calc = JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider()).build()
            val signerInfo =
                JcaSignerInfoGeneratorBuilder(calc).build(
                    JcaContentSignerBuilder("SHA256withRSA").build(key.private),
                    holder,
                )
            val gen =
                TimeStampTokenGenerator(
                    signerInfo,
                    calc.get(AlgorithmIdentifier(TSPAlgorithms.SHA256)),
                    ASN1ObjectIdentifier("1.2.3.4.5"),
                )
            gen.addCertificates(JcaCertStore(listOf(holder)))
            val response =
                TimeStampResponseGenerator(gen, TSPAlgorithms.ALLOWED)
                    .generate(request, BigInteger.valueOf(System.nanoTime()), genTime)
            return TimeStampResponse(response.encoded).timeStampToken.encoded
        }
    }

    private val ca = TestCa()
    private val signerKey = ecKey()
    private val signerCert = ca.issue(signerKey.public, "CN=Signer")

    private fun stamped(
        tsa: Tsa,
        genTime: Date = Date(),
        signingTimeText: String = Instant.now().toString(),
        tamperDigest: Boolean = false,
    ): Document {
        val doc =
            XadesForge.sign(
                XadesForge.sampleDocument(),
                signerKey.private,
                listOf(signerCert, ca.caCert),
                signerCert,
                signingTimeText = signingTimeText,
            )
        val sigValue = doc.getElementsByTagNameNS(XadesForge.DS, "SignatureValue").item(0) as org.w3c.dom.Element
        var digest = MessageDigest.getInstance("SHA-256").digest(XadesTimestamps.imprintInput(sigValue))
        if (tamperDigest) digest = MessageDigest.getInstance("SHA-256").digest("other".toByteArray())
        val token = tsa.token(digest, genTime)
        val qp = doc.getElementsByTagNameNS(XadesForge.XADES, "QualifyingProperties").item(0)
        val unsigned = doc.createElementNS(XadesForge.XADES, "xades:UnsignedProperties")
        val usp = doc.createElementNS(XadesForge.XADES, "xades:UnsignedSignatureProperties")
        val st = doc.createElementNS(XadesForge.XADES, "xades:SignatureTimeStamp")
        st.appendChild(
            doc.createElementNS(XadesForge.XADES, "xades:EncapsulatedTimeStamp").apply {
                textContent = Base64.getEncoder().encodeToString(token)
            },
        )
        usp.appendChild(st)
        unsigned.appendChild(usp)
        qp.appendChild(unsigned)
        return doc
    }

    private fun opts(
        answer: TrustAnchorMatch = match(),
        anchors: List<X509Certificate> = emptyList(),
        require: Boolean = false,
    ) = XadesVerificationOptions(
        XadesProfile.B_B,
        resolver(answer),
        requireSignatureTimestamp = require,
        timestampTrustAnchors = anchors,
    )

    @Test
    fun `a valid trusted time-stamp authenticates the signing time and yields B-T`() =
        runTest {
            val tsa = Tsa()
            val result = verifier.verify(stamped(tsa), opts(anchors = listOf(tsa.cert), require = true))
            result.shouldBeInstanceOf<Valid>()
            result.signingTimeAuthenticated shouldBe true
            result.profile shouldBe XadesProfile.B_T
            (result.signatureTimeStamp != null) shouldBe true
        }

    @Test
    fun `an untimestamped signature reports an unauthenticated signing time`() =
        runTest {
            val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, listOf(signerCert, ca.caCert), signerCert)
            val result = verifier.verify(doc, opts())
            result.shouldBeInstanceOf<Valid>()
            result.signingTimeAuthenticated shouldBe false
            result.signatureTimeStamp shouldBe null
        }

    @Test
    fun `requireSignatureTimestamp fails when there is no time-stamp`() =
        runTest {
            val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, listOf(signerCert, ca.caCert), signerCert)
            val result = verifier.verify(doc, opts(anchors = listOf(Tsa().cert), require = true))
            result.shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `requireSignatureTimestamp fails closed when no TSA anchors are configured`() =
        runTest {
            val result = verifier.verify(stamped(Tsa()), opts(require = true))
            result.shouldBeInstanceOf<Invalid.TimeStampInvalid>()
            result.reason shouldContain "timestampTrustAnchors"
        }

    @Test
    fun `a time-stamp from an untrusted TSA is rejected`() =
        runTest {
            val result = verifier.verify(stamped(Tsa()), opts(anchors = listOf(Tsa().cert)))
            result.shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `a time-stamp over different data is rejected`() =
        runTest {
            val tsa = Tsa()
            val result = verifier.verify(stamped(tsa, tamperDigest = true), opts(anchors = listOf(tsa.cert)))
            result.shouldBeInstanceOf<Invalid.TimeStampInvalid>()
            result.reason shouldContain "imprint"
        }

    @Test
    fun `a claimed signing time later than the time-stamp is rejected`() =
        runTest {
            val tsa = Tsa()
            val doc = stamped(tsa, genTime = Date(System.currentTimeMillis() - 2 * 3600_000), signingTimeText = Instant.now().toString())
            verifier.verify(doc, opts(anchors = listOf(tsa.cert))).shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `withdrawn trust is accepted for a signature time-stamped before the withdrawal and refused after`() =
        runTest {
            val tsa = Tsa()
            val withdrawnAt = Clock.System.now().plus(1.hours)
            verifier
                .verify(stamped(tsa), opts(match(withdrawnAt), listOf(tsa.cert)))
                .shouldBeInstanceOf<Valid>()
            val earlier = Clock.System.now().minus(30.minutes)
            verifier
                .verify(stamped(tsa), opts(match(earlier), listOf(tsa.cert)))
                .shouldBeInstanceOf<Invalid.TrustWithdrawn>()
        }

    // ------------------------------------------------------------------ chain validation

    private class Node(
        val cert: X509Certificate,
        val key: KeyPair,
        val name: String,
    )

    private fun issue(
        subject: String,
        subjectKey: PublicKeyHolder,
        issuer: Node?,
        ca: Boolean?,
        pathLen: Int? = null,
        keyUsage: Int? = null,
        notBefore: Date = Date(System.currentTimeMillis() - 30 * day),
        notAfter: Date = Date(System.currentTimeMillis() + 365 * day),
    ): Node {
        val issuerKey = issuer?.key ?: subjectKey.pair!!
        val issuerName = X500Name(issuer?.name ?: subject)
        val builder =
            JcaX509v3CertificateBuilder(
                issuerName,
                BigInteger.valueOf(System.nanoTime()),
                notBefore,
                notAfter,
                X500Name(subject),
                subjectKey.public,
            )
        if (ca != null) {
            builder.addExtension(
                Extension.basicConstraints,
                true,
                if (ca && pathLen != null) BasicConstraints(pathLen) else BasicConstraints(ca),
            )
        }
        if (keyUsage != null) builder.addExtension(Extension.keyUsage, true, KeyUsage(keyUsage))
        val cert =
            JcaX509CertificateConverter().getCertificate(
                builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey.private)),
            )
        return Node(cert, subjectKey.pair ?: issuerKey, subject)
    }

    private class PublicKeyHolder(
        val pair: KeyPair?,
        val public: java.security.PublicKey,
    )

    private fun fresh(): PublicKeyHolder = ecKey().let { PublicKeyHolder(it, it.public) }

    private fun leafHolder() = PublicKeyHolder(null, signerKey.public)

    private suspend fun verifyChain(
        chain: List<X509Certificate>,
        leaf: X509Certificate,
    ): XadesValidationResult {
        val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, chain, leaf)
        return verifier.verify(doc, opts())
    }

    private val ku = KeyUsage.digitalSignature
    private val caKu = KeyUsage.keyCertSign or KeyUsage.cRLSign

    @Test
    fun `a well-formed three-level chain is accepted`() =
        runTest {
            val root = issue("CN=Root", fresh(), null, true, 1, caKu)
            val mid = issue("CN=Mid", fresh(), root, true, 0, caKu)
            val leaf = issue("CN=Leaf", leafHolder(), mid, false, null, ku)
            verifyChain(listOf(leaf.cert, mid.cert, root.cert), leaf.cert).shouldBeInstanceOf<Valid>()
        }

    @Test
    fun `an intermediate that is not a CA cannot issue the signer`() =
        runTest {
            val root = issue("CN=Root", fresh(), null, true, null, caKu)
            val mid = issue("CN=Mid", fresh(), root, false, null, ku)
            val leaf = issue("CN=Leaf", leafHolder(), mid, false, null, ku)
            val result = verifyChain(listOf(leaf.cert, mid.cert, root.cert), leaf.cert)
            result.shouldBeInstanceOf<Invalid.CertificateChainInvalid>()
            result.reason shouldContain "not a CA"
        }

    @Test
    fun `an issuer without basicConstraints is not a CA`() =
        runTest {
            val root = issue("CN=Root", fresh(), null, null, null, caKu)
            val leaf = issue("CN=Leaf", leafHolder(), root, false, null, ku)
            verifyChain(listOf(leaf.cert, root.cert), leaf.cert).shouldBeInstanceOf<Invalid.CertificateChainInvalid>()
        }

    @Test
    fun `a CA without keyCertSign is refused`() =
        runTest {
            val root = issue("CN=Root", fresh(), null, true, null, KeyUsage.digitalSignature)
            val leaf = issue("CN=Leaf", leafHolder(), root, false, null, ku)
            val result = verifyChain(listOf(leaf.cert, root.cert), leaf.cert)
            result.shouldBeInstanceOf<Invalid.CertificateChainInvalid>()
            result.reason shouldContain "keyCertSign"
        }

    @Test
    fun `pathLenConstraint is enforced`() =
        runTest {
            val root = issue("CN=Root", fresh(), null, true, 0, caKu)
            val mid = issue("CN=Mid", fresh(), root, true, null, caKu)
            val leaf = issue("CN=Leaf", leafHolder(), mid, false, null, ku)
            val result = verifyChain(listOf(leaf.cert, mid.cert, root.cert), leaf.cert)
            result.shouldBeInstanceOf<Invalid.CertificateChainInvalid>()
            result.reason shouldContain "pathLenConstraint"
        }

    @Test
    fun `an expired CA is refused`() =
        runTest {
            val root =
                issue(
                    "CN=Root",
                    fresh(),
                    null,
                    true,
                    null,
                    caKu,
                    notBefore = Date(System.currentTimeMillis() - 60 * day),
                    notAfter = Date(System.currentTimeMillis() - day),
                )
            val leaf = issue("CN=Leaf", leafHolder(), root, false, null, ku)
            val result = verifyChain(listOf(leaf.cert, root.cert), leaf.cert)
            result.shouldBeInstanceOf<Invalid.CertificateChainInvalid>()
            result.reason shouldContain "expired"
        }

    @Test
    fun `a signer whose keyUsage forbids signing is refused`() =
        runTest {
            val root = issue("CN=Root", fresh(), null, true, null, caKu)
            val leaf = issue("CN=Leaf", leafHolder(), root, false, null, KeyUsage.keyEncipherment)
            val result = verifyChain(listOf(leaf.cert, root.cert), leaf.cert)
            result.shouldBeInstanceOf<Invalid.CertificateChainInvalid>()
            result.reason shouldContain "keyUsage"
        }
}
