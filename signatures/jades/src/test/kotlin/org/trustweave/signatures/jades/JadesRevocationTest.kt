package org.trustweave.signatures.jades

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.jades.JadesValidationResult.Invalid
import org.trustweave.signatures.jades.JadesValidationResult.Valid
import org.trustweave.signatures.revocation.RevocationEvidence
import org.trustweave.signatures.revocation.RevocationPolicy
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustList
import org.trustweave.signatures.trustlists.TrustedTSP
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.time.Clock

/** JAdES verification of CRL / OCSP evidence supplied by the caller. */
class JadesRevocationTest {
    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private val verifier = DefaultJadesVerifier()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
    }

    private data class Signed(
        val envelope: String,
        val signer: X509Certificate,
    )

    private suspend fun signed(): Signed {
        val keyId = generateKey()
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Revocation Signer")
        val signature =
            DefaultJadesSigner(kms).sign(
                payloadJson = buildJsonObject { put("hello", JsonPrimitive("world")) },
                request = JadesSigningRequest(profile = JadesProfile.B_B, keyId = keyId, signerCertificateChain = chain),
            )
        val signer = CertificateFactory.getInstance("X.509").generateCertificate(chain.first().inputStream()) as X509Certificate
        return Signed(signature.serializedFlattened, signer)
    }

    private fun options(
        policy: RevocationPolicy,
        evidence: RevocationEvidence = RevocationEvidence.NONE,
    ) = JadesVerificationOptions(
        requiredProfile = JadesProfile.B_B,
        trustAnchorResolver = resolverFor(ca.caCert),
        revocationPolicy = policy,
        revocationEvidence = evidence,
    )

    @Test
    fun `revocation is not evaluated by default`() =
        runBlocking<Unit> {
            val s = signed()
            val revoked = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey(), listOf(s.signer.serialNumber))
            val result = verifier.verify(s.envelope, options(RevocationPolicy.NOT_CHECKED, RevocationEvidence(crls = listOf(revoked))))
            assertTrue(result is Valid, "got $result")
            assertFalse((result as Valid).revocationChecked)
        }

    @Test
    fun `a good CRL satisfies REQUIRED`() =
        runBlocking<Unit> {
            val s = signed()
            val good = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey())
            val result = verifier.verify(s.envelope, options(RevocationPolicy.REQUIRED, RevocationEvidence(crls = listOf(good))))
            assertTrue(result is Valid, "got $result")
            assertTrue((result as Valid).revocationChecked)
        }

    @Test
    fun `a good OCSP response satisfies REQUIRED`() =
        runBlocking<Unit> {
            val s = signed()
            val good = RevocationFixtures.ocsp(ca.caCert, ca.caPrivateKey(), s.signer)
            val result = verifier.verify(s.envelope, options(RevocationPolicy.REQUIRED, RevocationEvidence(ocspResponses = listOf(good))))
            assertTrue(result is Valid, "got $result")
            assertTrue((result as Valid).revocationChecked)
        }

    @Test
    fun `a CRL listing the signer refuses the signature`() =
        runBlocking<Unit> {
            val s = signed()
            val revoked = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey(), listOf(s.signer.serialNumber))
            val result =
                verifier.verify(
                    s.envelope,
                    options(RevocationPolicy.CHECK_IF_AVAILABLE, RevocationEvidence(crls = listOf(revoked))),
                )
            assertTrue(result is Invalid.CertificateRevoked, "got $result")
            assertEquals(s.signer, (result as Invalid.CertificateRevoked).cert)
        }

    @Test
    fun `an OCSP response reporting the signer revoked refuses the signature`() =
        runBlocking<Unit> {
            val s = signed()
            val revoked = RevocationFixtures.ocsp(ca.caCert, ca.caPrivateKey(), s.signer, revoked = true)
            val result =
                verifier.verify(
                    s.envelope,
                    options(RevocationPolicy.REQUIRED, RevocationEvidence(ocspResponses = listOf(revoked))),
                )
            assertTrue(result is Invalid.CertificateRevoked, "got $result")
        }

    @Test
    fun `REQUIRED fails closed without evidence and CHECK_IF_AVAILABLE does not claim a check`() =
        runBlocking<Unit> {
            val s = signed()
            assertTrue(verifier.verify(s.envelope, options(RevocationPolicy.REQUIRED)) is Invalid.RevocationUnavailable)
            val lenient = verifier.verify(s.envelope, options(RevocationPolicy.CHECK_IF_AVAILABLE))
            assertTrue(lenient is Valid)
            assertFalse((lenient as Valid).revocationChecked)
        }

    @Test
    fun `a CRL signed by another key is ignored`() =
        runBlocking<Unit> {
            val s = signed()
            val other = TestCa()
            val forged = RevocationFixtures.crl(ca.caCert, other.caPrivateKey())
            val result = verifier.verify(s.envelope, options(RevocationPolicy.REQUIRED, RevocationEvidence(crls = listOf(forged))))
            assertTrue(result is Invalid.RevocationUnavailable, "got $result")
        }

    private suspend fun generateKey(): KeyId {
        val result = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "rev-${System.nanoTime()}"))
        return when (result) {
            is GenerateKeyResult.Success -> result.keyHandle.id
            else -> error("KMS keygen failed: $result")
        }
    }

    private fun resolverFor(trustedCa: X509Certificate): DefaultTrustAnchorResolver {
        val service =
            TspService(
                serviceName = "Test CA",
                serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                status = TspServiceStatus.GRANTED,
                statusStartingTime = Clock.System.now() - kotlin.time.Duration.parse("PT8760H"),
                serviceCertificates = listOf(trustedCa),
                qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
            )
        val now = Clock.System.now()
        val tsl =
            MemberStateTsl(
                territory = "EU",
                schemeOperator = "Test",
                sequenceNumber = 1,
                issuedAt = now,
                trustedTsps = listOf(TrustedTSP(name = "Test TSP", tradeName = null, services = listOf(service))),
            )
        return DefaultTrustAnchorResolver(
            TrustList(schemeOperator = "Test", sequenceNumber = 1, issuedAt = now, nextUpdateAt = null, memberStateLists = listOf(tsl)),
        )
    }
}
