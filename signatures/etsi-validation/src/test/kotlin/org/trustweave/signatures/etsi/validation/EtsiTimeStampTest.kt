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
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustList
import org.trustweave.signatures.trustlists.TrustedTSP
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.tsa.TsaConfig
import java.security.cert.X509Certificate
import kotlin.time.Clock

/** The TIME_STAMP_TOKEN step only reports a `sigTst` as validated when a TSA trust anchor accepted it. */
class EtsiTimeStampTest {
    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private lateinit var tsa: TestTsa
    private val validator = DefaultEtsiSignatureValidator()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
        tsa = TestTsa()
    }

    private suspend fun stamped(): String {
        val keyId: KeyId =
            when (val r = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "ts-${System.nanoTime()}"))) {
                is GenerateKeyResult.Success -> r.keyHandle.id
                else -> error("KMS keygen failed: $r")
            }
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Stamped Signer")
        return DefaultJadesSigner(kms) { tsa.client() }
            .sign(
                buildJsonObject { put("k", JsonPrimitive("v")) },
                JadesSigningRequest(
                    profile = JadesProfile.B_T,
                    keyId = keyId,
                    signerCertificateChain = chain,
                    tsaConfig = TsaConfig(endpointUrl = "http://tsa.invalid/"),
                ),
            ).serializedFlattened
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

    private fun policy(
        anchors: List<X509Certificate>,
        require: Boolean,
    ) = EtsiSignaturePolicy(requireTimeStamp = require, timestampTrustAnchors = anchors)

    @Test
    fun `a time-stamp verified against a TSA anchor passes`() =
        runBlocking<Unit> {
            val report = validator.validate(stamped(), policy(listOf(tsa.cert), require = true), resolver())
            assertEquals(FinalVerdict.TOTAL_PASSED, report.finalVerdict)
            assertTrue(report.steps[EtsiValidationStep.TIME_STAMP_TOKEN] is StepOutcome.Passed)
        }

    @Test
    fun `a required time-stamp that no anchor accepts fails instead of passing`() =
        runBlocking<Unit> {
            val report = validator.validate(stamped(), policy(emptyList(), require = true), resolver())
            assertEquals(FinalVerdict.TOTAL_FAILED, report.finalVerdict)
            assertTrue(report.steps[EtsiValidationStep.TIME_STAMP_TOKEN] is StepOutcome.Failed)
        }

    @Test
    fun `an unverified optional time-stamp is inconclusive, never passed`() =
        runBlocking<Unit> {
            val report = validator.validate(stamped(), policy(emptyList(), require = false), resolver())
            assertEquals(FinalVerdict.INDETERMINATE, report.finalVerdict)
            assertTrue(report.steps[EtsiValidationStep.TIME_STAMP_TOKEN] is StepOutcome.Inconclusive)
        }

    @Test
    fun `a time-stamp from a TSA other than the configured one fails`() =
        runBlocking<Unit> {
            val report = validator.validate(stamped(), policy(listOf(TestTsa().cert), require = false), resolver())
            assertEquals(FinalVerdict.TOTAL_FAILED, report.finalVerdict)
            assertTrue(report.steps[EtsiValidationStep.TIME_STAMP_TOKEN] is StepOutcome.Failed)
        }
}
