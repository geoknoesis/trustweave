package org.trustweave.signatures.xades

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.xades.XadesValidationResult.Invalid
import org.trustweave.signatures.xades.XadesValidationResult.Valid
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** Trust handling: which KeyInfo certificates reach the resolver, and what each result means. */
class XadesTrustChainTest {
    private val ca = TestCa()
    private val signerKey = ecKey()
    private val signerCert = ca.issue(signerKey.public, "CN=Signer")
    private val verifier = DefaultXadesVerifier()

    private fun ecKey(): KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()

    private class RecordingResolver(
        val answer: TrustAnchorMatch,
    ) : TrustAnchorResolver {
        var signer: X509Certificate? = null
        var chain: List<X509Certificate>? = null

        override fun resolve(
            signerCert: X509Certificate,
            chain: List<X509Certificate>,
        ): TrustAnchorMatch {
            this.signer = signerCert
            this.chain = chain
            return answer
        }
    }

    private fun activeMatch(): TrustAnchorMatch.QualifiedActive =
        TrustAnchorMatch.QualifiedActive(
            tspName = "Test TSP",
            territory = "EU",
            service =
                TspService(
                    serviceName = "Test CA",
                    serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                    status = TspServiceStatus.GRANTED,
                    statusStartingTime = Clock.System.now() - kotlin.time.Duration.parse("PT8760H"),
                    serviceCertificates = listOf(ca.caCert),
                    qualifierUris = listOf(QualifierUris.QC_WITH_SSCD),
                ),
            qcWithSscd = true,
            qcForESig = true,
        )

    private fun options(
        resolver: TrustAnchorResolver,
        requireSigningTime: Boolean = false,
    ) = XadesVerificationOptions(XadesProfile.B_B, resolver, requireSigningTime = requireSigningTime)

    private fun signed(
        keyInfoCerts: List<X509Certificate>,
        signer: X509Certificate = signerCert,
        signingTimeText: String? = Instant.now().toString(),
    ) = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, keyInfoCerts, signer, signingTimeText = signingTimeText)

    @Test
    fun `the resolver is offered the certificates that chain to the signer`() =
        runTest {
            val resolver = RecordingResolver(activeMatch())
            val result = verifier.verify(signed(listOf(signerCert, ca.caCert)), options(resolver))
            result.shouldBeInstanceOf<Valid>()
            resolver.chain!!.shouldContainExactly(ca.caCert)
        }

    @Test
    fun `unrelated certificates appended to KeyInfo are not passed to the resolver`() =
        runTest {
            val unrelated = TestCa(caSubject = "CN=Unrelated Root").caCert
            val unrelatedLeaf = TestCa(caSubject = "CN=Other Root").issue(ecKey().public, "CN=Bystander")
            val resolver = RecordingResolver(activeMatch())
            val result = verifier.verify(signed(listOf(unrelated, signerCert, unrelatedLeaf, ca.caCert)), options(resolver))
            result.shouldBeInstanceOf<Valid>()
            resolver.chain!!.shouldContainExactly(ca.caCert)
        }

    @Test
    fun `a forged root that copies the real CA subject but not its key is dropped`() =
        runTest {
            // Same DN as the genuine issuer, different key pair: subject linkage alone would pass,
            // the signature check must not.
            val impostor = TestCa(caSubject = ca.caSubject).caCert
            val resolver = RecordingResolver(TrustAnchorMatch.NotTrusted)
            val result = verifier.verify(signed(listOf(signerCert, impostor)), options(resolver))
            result.shouldBeInstanceOf<Invalid.UntrustedSigner>()
            resolver.chain!!.shouldContainExactly()
        }

    @Test
    fun `a signer whose real issuer is absent gets an empty chain, not the unrelated certificates`() =
        runTest {
            val bystander = TestCa(caSubject = "CN=Bystander Root").caCert
            val resolver = RecordingResolver(TrustAnchorMatch.NotTrusted)
            verifier.verify(signed(listOf(signerCert, bystander)), options(resolver))
            resolver.chain!!.shouldContainExactly()
        }

    @Test
    fun `QualifiedActive is accepted and carried on the result`() =
        runTest {
            val doc = signed(listOf(signerCert, ca.caCert))
            val result = verifier.verify(doc, options(RecordingResolver(activeMatch()))) as Valid
            result.trust.shouldBeInstanceOf<TrustAnchorMatch.QualifiedActive>()
            result.signingTimeAuthenticated shouldBe false
            result.revocationChecked shouldBe false
        }

    @Test
    fun `QualifiedWithdrawn without an authenticated signing time is refused by default`() =
        runTest {
            val withdrawn = TrustAnchorMatch.QualifiedWithdrawn("Test TSP", Clock.System.now())
            val result = verifier.verify(signed(listOf(signerCert, ca.caCert)), options(RecordingResolver(withdrawn)))
            result.shouldBeInstanceOf<Invalid.TrustWithdrawn>()
        }

    @Test
    fun `QualifiedWithdrawn is accepted without authenticated time only when explicitly allowed and the claim predates withdrawal`() =
        runTest {
            val withdrawnLater = TrustAnchorMatch.QualifiedWithdrawn("Test TSP", Clock.System.now().plus(1.hours))
            val lenient = options(RecordingResolver(withdrawnLater)).copy(allowWithdrawnTrustWithoutAuthenticatedTime = true)
            (verifier.verify(signed(listOf(signerCert, ca.caCert)), lenient) as Valid).trust shouldBe withdrawnLater

            // A claimed signing time at/after the withdrawal is refused even with the option set.
            val withdrawnEarlier = TrustAnchorMatch.QualifiedWithdrawn("Test TSP", Clock.System.now().minus(1.hours))
            verifier
                .verify(
                    signed(listOf(signerCert, ca.caCert)),
                    options(RecordingResolver(withdrawnEarlier)).copy(allowWithdrawnTrustWithoutAuthenticatedTime = true),
                ).shouldBeInstanceOf<Invalid.TrustWithdrawn>()
        }

    @Test
    fun `NotTrusted is refused`() =
        runTest {
            verifier
                .verify(signed(listOf(signerCert, ca.caCert)), options(RecordingResolver(TrustAnchorMatch.NotTrusted)))
                .shouldBeInstanceOf<Invalid.UntrustedSigner>()
        }

    @Test
    fun `a missing SigningTime is accepted by default and judged at verification time`() =
        runTest {
            val result =
                verifier.verify(
                    signed(listOf(signerCert, ca.caCert), signingTimeText = null),
                    options(RecordingResolver(activeMatch())),
                )
            result.shouldBeInstanceOf<Valid>()
            result.signingTime shouldBe null
        }

    @Test
    fun `requireSigningTime rejects a signature without SigningTime`() =
        runTest {
            val result =
                verifier.verify(
                    signed(listOf(signerCert, ca.caCert), signingTimeText = null),
                    options(RecordingResolver(activeMatch()), requireSigningTime = true),
                )
            result.shouldBeInstanceOf<Invalid.Malformed>()
            result.reason shouldContain "SigningTime"
        }

    @Test
    fun `requireSigningTime still accepts a signature that has one`() =
        runTest {
            verifier
                .verify(signed(listOf(signerCert, ca.caCert)), options(RecordingResolver(activeMatch()), requireSigningTime = true))
                .shouldBeInstanceOf<Valid>()
        }

    @Test
    fun `a since-expired certificate is rejected whatever SigningTime claims`() =
        runTest {
            val expired =
                ca.issue(
                    signerKey.public,
                    "CN=Expired Signer",
                    notBefore = Date.from(Instant.now().minus(30, ChronoUnit.DAYS)),
                    notAfter = Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                )
            val withoutTime = signed(listOf(expired, ca.caCert), signer = expired, signingTimeText = null)
            verifier
                .verify(withoutTime, options(RecordingResolver(activeMatch())))
                .shouldBeInstanceOf<Invalid.CertificateExpired>()

            // A back-dated claimed SigningTime inside the validity window does not rescue it.
            val withTime =
                signed(
                    listOf(expired, ca.caCert),
                    signer = expired,
                    signingTimeText = Instant.now().minus(10, ChronoUnit.DAYS).toString(),
                )
            verifier.verify(withTime, options(RecordingResolver(activeMatch()))).shouldBeInstanceOf<Invalid.CertificateExpired>()
        }
}
