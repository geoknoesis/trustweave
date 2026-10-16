package org.trustweave.signatures.etsi.validation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.etsi.validation.EtsiValidationReport.FinalVerdict
import org.trustweave.signatures.jades.DefaultJadesSigner
import org.trustweave.signatures.jades.JadesProfile
import org.trustweave.signatures.jades.JadesSigningRequest
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

/** The REVOCATION step of the ETSI pipeline. */
class EtsiRevocationTest {
    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private val validator = DefaultEtsiSignatureValidator()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
    }

    private suspend fun signed(): Pair<String, X509Certificate> {
        val keyId = generateKey()
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=ETSI Revocation Signer")
        val signature =
            DefaultJadesSigner(kms).sign(
                payloadJson = buildJsonObject { put("hello", JsonPrimitive("world")) },
                request = JadesSigningRequest(profile = JadesProfile.B_B, keyId = keyId, signerCertificateChain = chain),
            )
        val signer = CertificateFactory.getInstance("X.509").generateCertificate(chain.first().inputStream()) as X509Certificate
        return signature.serializedFlattened to signer
    }

    private fun policy(
        revocationPolicy: RevocationPolicy,
        evidence: RevocationEvidence = RevocationEvidence.NONE,
    ) = EtsiSignaturePolicy(revocationPolicy = revocationPolicy, revocationEvidence = evidence)

    @Test
    fun `the REVOCATION step is not applicable by default`() =
        runBlocking<Unit> {
            val (envelope, _) = signed()
            val report = validator.validate(envelope, EtsiSignaturePolicy(), resolver())
            assertEquals(FinalVerdict.TOTAL_PASSED, report.finalVerdict)
            assertEquals(StepOutcome.NotApplicable, report.steps[EtsiValidationStep.REVOCATION])
        }

    @Test
    fun `good evidence passes the REVOCATION step`() =
        runBlocking<Unit> {
            val (envelope, _) = signed()
            val good = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey())
            val report =
                validator.validate(
                    envelope,
                    policy(RevocationPolicy.REQUIRED, RevocationEvidence(crls = listOf(good))),
                    resolver(),
                )
            assertEquals(FinalVerdict.TOTAL_PASSED, report.finalVerdict)
            assertTrue(
                report.steps[EtsiValidationStep.REVOCATION] is StepOutcome.Passed,
                "got ${report.steps[EtsiValidationStep.REVOCATION]}",
            )
        }

    @Test
    fun `a revoked signer fails the REVOCATION step and the verdict`() =
        runBlocking<Unit> {
            val (envelope, signer) = signed()
            val revoked = RevocationFixtures.ocsp(ca.caCert, ca.caPrivateKey(), signer, revoked = true)
            val report =
                validator.validate(
                    envelope,
                    policy(RevocationPolicy.REQUIRED, RevocationEvidence(ocspResponses = listOf(revoked))),
                    resolver(),
                )
            assertEquals(FinalVerdict.TOTAL_FAILED, report.finalVerdict)
            assertTrue(report.steps[EtsiValidationStep.REVOCATION] is StepOutcome.Failed)
        }

    @Test
    fun `REQUIRED without evidence fails the REVOCATION step`() =
        runBlocking<Unit> {
            val (envelope, _) = signed()
            val report = validator.validate(envelope, policy(RevocationPolicy.REQUIRED), resolver())
            assertEquals(FinalVerdict.TOTAL_FAILED, report.finalVerdict)
            assertTrue(report.steps[EtsiValidationStep.REVOCATION] is StepOutcome.Failed)
        }

    @Test
    fun `CHECK_IF_AVAILABLE without evidence is inconclusive rather than passed`() =
        runBlocking<Unit> {
            val (envelope, _) = signed()
            val report = validator.validate(envelope, policy(RevocationPolicy.CHECK_IF_AVAILABLE), resolver())
            assertEquals(FinalVerdict.INDETERMINATE, report.finalVerdict)
            assertTrue(report.steps[EtsiValidationStep.REVOCATION] is StepOutcome.Inconclusive)
        }

    private suspend fun generateKey(): KeyId =
        when (val r = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "etsi-rev-${System.nanoTime()}"))) {
            is GenerateKeyResult.Success -> r.keyHandle.id
            else -> error("KMS keygen failed: $r")
        }

    private fun resolver(): DefaultTrustAnchorResolver {
        val service =
            TspService(
                serviceName = "Test CA",
                serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                status = TspServiceStatus.GRANTED,
                statusStartingTime = Clock.System.now() - kotlin.time.Duration.parse("PT8760H"),
                serviceCertificates = listOf(ca.caCert),
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
