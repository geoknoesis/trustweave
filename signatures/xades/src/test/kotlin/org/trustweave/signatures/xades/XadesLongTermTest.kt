package org.trustweave.signatures.xades

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.bouncycastle.cert.X509CertificateHolder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.identifiers.KeyId
import org.trustweave.signatures.revocation.RevocationEvidence
import org.trustweave.signatures.revocation.RevocationPolicy
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustList
import org.trustweave.signatures.trustlists.TrustedTSP
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.tsa.TsaConfig
import org.trustweave.signatures.xades.XadesValidationResult.Invalid
import org.trustweave.signatures.xades.XadesValidationResult.Valid
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** XAdES B-T / B-LT: the signer produces them and the verifier reports the profile it can actually prove. */
class XadesLongTermTest {
    private val ca = TestCa()
    private val tsa = TestTsa()
    private val signerKey: KeyPair =
        KeyPairGenerator
            .getInstance(
                "EC",
            ).apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
    private val signerCert = ca.issue(signerKey.public, "CN=LT Signer")
    private val tsaConfig = TsaConfig(endpointUrl = "http://tsa.invalid/")
    private val verifier = DefaultXadesVerifier()
    private val signer = DefaultXadesSigner(TestKms(), signerKey.private) { tsa.client() }

    private val resolver =
        object : TrustAnchorResolver {
            override fun resolve(
                signerCert: X509Certificate,
                chain: List<X509Certificate>,
            ): TrustAnchorMatch =
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
        }

    private fun chain(): List<ByteArray> = listOf(signerCert.encoded, ca.caCert.encoded)

    private fun goodCrl(): ByteArray = RevocationFixtures.crl(ca, thisUpdate = Date())

    private fun revokedCrl(): ByteArray = RevocationFixtures.crl(ca, listOf(signerCert.serialNumber), thisUpdate = Date())

    private suspend fun sign(
        profile: XadesProfile,
        crl: ByteArray? = null,
    ) = signer
        .sign(
            XadesSigningRequest(
                profile = profile,
                keyId = KeyId("k"),
                document = XadesForge.sampleDocument(),
                signerCertificateChain = chain(),
                tsaConfig = tsaConfig.takeIf { profile.atLeast(XadesProfile.B_T) },
                validationData =
                    crl?.let {
                        XadesValidationData(
                            certificates = listOf(signerCert.encoded, ca.caCert.encoded, X509CertificateHolder(tsa.holder.encoded).encoded),
                            revocation = RevocationEvidence(crls = listOf(it)),
                        )
                    },
            ),
        )

    private fun options(
        required: XadesProfile,
        anchors: List<X509Certificate> = listOf(tsa.cert),
        policy: RevocationPolicy = RevocationPolicy.NOT_CHECKED,
        evidence: RevocationEvidence = RevocationEvidence.NONE,
    ) = XadesVerificationOptions(
        requiredProfile = required,
        trustAnchorResolver = resolver,
        timestampTrustAnchors = anchors,
        revocationPolicy = policy,
        revocationEvidence = evidence,
    )

    @Test
    fun `the signer produces a B-T signature that verifies as B-T`() =
        runTest {
            val signature = sign(XadesProfile.B_T)
            signature.profile shouldBe XadesProfile.B_T
            val result = verifier.verify(signature.document, options(XadesProfile.B_T)).shouldBeInstanceOf<Valid>()
            result.profile shouldBe XadesProfile.B_T
            result.signingTimeAuthenticated shouldBe true
        }

    @Test
    fun `the signer produces a B-LT signature that verifies as B-LT with revocation checked`() =
        runTest {
            val signature = sign(XadesProfile.B_LT, crl = goodCrl())
            signature.profile shouldBe XadesProfile.B_LT
            val result = verifier.verify(signature.document, options(XadesProfile.B_LT)).shouldBeInstanceOf<Valid>()
            result.profile shouldBe XadesProfile.B_LT
            result.revocationChecked shouldBe true
        }

    @Test
    fun `a B-LT signature is only reported as B-T when revocation is not evaluated`() =
        runTest {
            val signature = sign(XadesProfile.B_LT, crl = goodCrl())
            val result = verifier.verify(signature.document, options(XadesProfile.B_T)).shouldBeInstanceOf<Valid>()
            result.profile shouldBe XadesProfile.B_T
            result.revocationChecked shouldBe false
        }

    @Test
    fun `a B-LT signature whose embedded CRL revokes the signer is refused`() =
        runTest {
            val signature = sign(XadesProfile.B_LT, crl = revokedCrl())
            verifier.verify(signature.document, options(XadesProfile.B_LT)).shouldBeInstanceOf<Invalid.CertificateRevoked>()
        }

    @Test
    fun `requiring B-LT of a B-T signature fails closed on the missing evidence`() =
        runTest {
            val signature = sign(XadesProfile.B_T)
            verifier.verify(signature.document, options(XadesProfile.B_LT)).shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `evidence supplied only by the caller does not make a signature B-LT`() =
        runTest {
            val signature = sign(XadesProfile.B_T)
            val result =
                verifier.verify(
                    signature.document,
                    options(XadesProfile.B_LT, evidence = RevocationEvidence(crls = listOf(goodCrl()))),
                )
            val wrong = result.shouldBeInstanceOf<Invalid.WrongProfile>()
            wrong.found shouldBe XadesProfile.B_T
            wrong.required shouldBe XadesProfile.B_LT
        }

    @Test
    fun `B-LT needs a trusted time-stamp`() =
        runTest {
            val signature = sign(XadesProfile.B_LT, crl = goodCrl())
            verifier
                .verify(
                    signature.document,
                    options(XadesProfile.B_LT, anchors = emptyList()),
                ).shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `embedded evidence issued before the time-stamp is not accepted`() =
        runTest {
            val stale = RevocationFixtures.crl(ca, thisUpdate = Date(System.currentTimeMillis() - 3_600_000L))
            val signature = sign(XadesProfile.B_LT, crl = stale)
            verifier.verify(signature.document, options(XadesProfile.B_LT)).shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `signing requests are validated`() {
        val doc = XadesForge.sampleDocument()
        assertThrows<IllegalArgumentException> {
            XadesSigningRequest(XadesProfile.B_T, KeyId("k"), doc, chain())
        }
        assertThrows<IllegalArgumentException> {
            XadesSigningRequest(XadesProfile.B_LT, KeyId("k"), doc, chain(), tsaConfig = tsaConfig)
        }
    }

    @Test
    fun `the trust resolver validates the path as of the trusted time, not now`() {
        val now = System.currentTimeMillis()
        val day = 24L * 3_600_000L
        val expired = ca.issue(signerKey.public, "CN=Expired Signer", notBefore = Date(now - 10 * day), notAfter = Date(now - day))
        val service =
            TspService(
                serviceName = "Test CA",
                serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                status = TspServiceStatus.GRANTED,
                statusStartingTime = Clock.System.now(),
                serviceCertificates = listOf(ca.caCert),
                qualifierUris = listOf(QualifierUris.QC_WITH_SSCD),
            )
        val issued = Clock.System.now()
        val tsl =
            MemberStateTsl(
                territory = "EU",
                schemeOperator = "Test",
                sequenceNumber = 1,
                issuedAt = issued,
                trustedTsps = listOf(TrustedTSP(name = "Test TSP", tradeName = null, services = listOf(service))),
            )
        val resolver =
            DefaultTrustAnchorResolver(
                TrustList(schemeOperator = "Test", sequenceNumber = 1, issuedAt = issued, nextUpdateAt = null, memberStateLists = listOf(tsl)),
            )
        // Today the certificate has expired; as of five days ago, when a time-stamp vouches it was signed, it was valid.
        resolver.resolve(expired, listOf(ca.caCert)).shouldBeInstanceOf<TrustAnchorMatch.NotTrusted>()
        resolver.resolve(expired, listOf(ca.caCert), null).shouldBeInstanceOf<TrustAnchorMatch.NotTrusted>()
        resolver
            .resolve(expired, listOf(ca.caCert), Clock.System.now() - 5.days)
            .shouldBeInstanceOf<TrustAnchorMatch.QualifiedActive>()
    }
}
