package org.trustweave.signatures.xades

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustList
import org.trustweave.signatures.trustlists.TrustedTSP
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.xades.XadesValidationResult.Invalid
import org.trustweave.signatures.xades.XadesValidationResult.Valid
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

class XadesAntiWrappingTest {
    private val ca = TestCa()
    private val signerKey = ecKey()
    private val signerCert = ca.issue(signerKey.public, "CN=Signer")
    private val verifier = DefaultXadesVerifier()
    private val options = XadesVerificationOptions(XadesProfile.B_B, resolverFor(ca.caCert))

    private fun ecKey(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private suspend fun verify(doc: org.w3c.dom.Document) = verifier.verify(doc, options)

    @Test
    fun `well-formed forged signature verifies (baseline for the forge helper)`() =
        runTest {
            val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, listOf(signerCert, ca.caCert), signerCert)
            val result = verify(doc)
            result.shouldBeInstanceOf<Valid>()
            result.signerCert shouldBe signerCert
        }

    @Test
    fun `signature that only references an Id-addressed element is rejected`() =
        runTest {
            val doc =
                XadesForge.sign(
                    XadesForge.sampleDocument(),
                    signerKey.private,
                    listOf(signerCert),
                    signerCert,
                    coverWholeDocument = false,
                    extraReferenceUris = listOf("#total"),
                )
            val result = verify(doc)
            result.shouldBeInstanceOf<Invalid.Malformed>()
            result.reason shouldContain "whole document"
        }

    @Test
    fun `SignedProperties reference pointing outside the signature is rejected`() =
        runTest {
            val doc =
                XadesForge.sign(
                    XadesForge.sampleDocument(),
                    signerKey.private,
                    listOf(signerCert),
                    signerCert,
                    signedPropertiesUri = "#forged-props",
                    beforeSign = { d ->
                        val forged = d.createElementNS(XadesForge.XADES, "xades:SignedProperties")
                        forged.setAttribute("Id", "forged-props")
                        d.documentElement.appendChild(forged)
                    },
                )
            val result = verify(doc)
            result.shouldBeInstanceOf<Invalid.Malformed>()
            result.reason shouldContain "does not point at"
        }

    @Test
    fun `duplicate Id attributes are rejected`() =
        runTest {
            val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, listOf(signerCert), signerCert)
            val dup = doc.createElementNS(XadesForge.XADES, "xades:SignedProperties")
            dup.setAttribute("Id", XadesForge.SP_ID)
            doc.documentElement.insertBefore(dup, doc.documentElement.firstChild)
            val result = verify(doc)
            result.shouldBeInstanceOf<Invalid.Malformed>()
            result.reason shouldContain "duplicate Id"
        }

    @Test
    fun `tampered content fails the signature`() =
        runTest {
            val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, listOf(signerCert), signerCert)
            doc.getElementsByTagName("Total").item(0).textContent = "0.01"
            verify(doc).shouldBeInstanceOf<Invalid.BadSignature>()
        }

    @Test
    fun `SigningCertificateV2 digest that does not match any KeyInfo cert is rejected`() =
        runTest {
            val otherCert = ca.issue(ecKey().public, "CN=Other")
            val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, listOf(signerCert), otherCert)
            val result = verify(doc)
            result.shouldBeInstanceOf<Invalid.BadSignature>()
            result.reason shouldContain "digest"
        }

    @Test
    fun `missing SigningCertificateV2 is rejected`() =
        runTest {
            val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, listOf(signerCert), null)
            verify(doc).shouldBeInstanceOf<Invalid.Malformed>()
        }

    @Test
    fun `signer cert is chosen by SigningCertificateV2 digest, not KeyInfo order`() =
        runTest {
            val doc = XadesForge.sign(XadesForge.sampleDocument(), signerKey.private, listOf(ca.caCert, signerCert), signerCert)
            val result = verify(doc)
            result.shouldBeInstanceOf<Valid>()
            result.signerCert shouldBe signerCert
        }

    @Test
    fun `attacker key cannot sign under a victim certificate`() =
        runTest {
            val attacker = ecKey()
            val doc = XadesForge.sign(XadesForge.sampleDocument(), attacker.private, listOf(signerCert), signerCert)
            verify(doc).shouldBeInstanceOf<Invalid.BadSignature>()
        }

    @Test
    fun `malformed SigningTime is Malformed`() =
        runTest {
            val doc =
                XadesForge.sign(
                    XadesForge.sampleDocument(),
                    signerKey.private,
                    listOf(signerCert),
                    signerCert,
                    signingTimeText = "yesterday",
                )
            val result = verify(doc)
            result.shouldBeInstanceOf<Invalid.Malformed>()
            result.reason shouldContain "SigningTime"
        }

    @Test
    fun `missing SigningTime falls back to the current time for the validity window`() =
        runTest {
            val okDoc =
                XadesForge.sign(
                    XadesForge.sampleDocument(),
                    signerKey.private,
                    listOf(signerCert),
                    signerCert,
                    signingTimeText = null,
                )
            val ok = verify(okDoc)
            ok.shouldBeInstanceOf<Valid>()
            ok.signingTime shouldBe null

            val expired =
                ca.issue(
                    signerKey.public,
                    "CN=Expired",
                    notBefore = Date(System.currentTimeMillis() - 10_000_000),
                    notAfter = Date(System.currentTimeMillis() - 1_000_000),
                )
            val expiredDoc =
                XadesForge.sign(
                    XadesForge.sampleDocument(),
                    signerKey.private,
                    listOf(expired),
                    expired,
                    signingTimeText = null,
                )
            verify(expiredDoc).shouldBeInstanceOf<Invalid.CertificateExpired>()
        }

    @Test
    fun `signing time before notBefore is CertificateNotYetValid`() =
        runTest {
            val doc =
                XadesForge.sign(
                    XadesForge.sampleDocument(),
                    signerKey.private,
                    listOf(signerCert),
                    signerCert,
                    signingTimeText = "2001-01-01T00:00:00Z",
                )
            verify(doc).shouldBeInstanceOf<Invalid.CertificateNotYetValid>()
        }

    @Test
    fun `SigningTime outside SignedSignatureProperties is ignored`() =
        runTest {
            val doc =
                XadesForge.sign(
                    XadesForge.sampleDocument(),
                    signerKey.private,
                    listOf(signerCert),
                    signerCert,
                    signingTimeText = null,
                    beforeSign = { d ->
                        d.documentElement.appendChild(
                            d.createElementNS(XadesForge.XADES, "xades:SigningTime").apply { textContent = "2001-01-01T00:00:00Z" },
                        )
                    },
                )
            val result = verify(doc)
            result.shouldBeInstanceOf<Valid>()
            result.signingTime shouldBe null
        }

    private fun resolverFor(trustedCa: X509Certificate): DefaultTrustAnchorResolver {
        val service =
            TspService(
                serviceName = "Test CA",
                serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                status = TspServiceStatus.GRANTED,
                statusStartingTime = Clock.System.now(),
                serviceCertificates = listOf(trustedCa),
                qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
            )
        return DefaultTrustAnchorResolver(
            TrustList(
                schemeOperator = "Test",
                sequenceNumber = 1,
                issuedAt = Clock.System.now(),
                nextUpdateAt = null,
                memberStateLists =
                    listOf(
                        MemberStateTsl(
                            territory = "EU",
                            schemeOperator = "Test",
                            sequenceNumber = 1,
                            issuedAt = Clock.System.now(),
                            trustedTsps = listOf(TrustedTSP(name = "Test TSP", tradeName = null, services = listOf(service))),
                        ),
                    ),
            ),
        )
    }
}
