package org.trustweave.credential.mdl

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.mdl.engine.MdocProofEngine
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.RevocationFailurePolicy
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.Algorithm
import org.trustweave.testkit.kms.InMemoryKeyManagementService

/**
 * A status check that could not be completed used to be ignored ("warn but don't fail") with no
 * warning at all, so a credential whose revocation status was unknown verified as valid. It now
 * follows the verifier's [RevocationFailurePolicy] like the SD-JWT and VC-LD engines.
 */
class MdocRevocationPolicyTest {
    private val issuerDid = Did("did:example:issuer")
    private val holderDid = Did("did:example:holder")
    private val issuerKeyId = KeyId(issuerDid.value)
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var issuer: MdocProofEngine

    private val optIn = ProofEngineConfig(properties = mapOf(MdocProofEngine.OPTION_ALLOW_KMS_ISSUER_KEY_LOOKUP to true))

    @BeforeEach
    fun setup() =
        runBlocking<Unit> {
            kms = InMemoryKeyManagementService()
            kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to issuerKeyId.value))
            issuer = MdocProofEngine(kms, config = optIn)
        }

    private suspend fun statusBearingCredential(): VerifiableCredential =
        issuer
            .issue(
                IssuanceRequest(
                    format = ProofSuiteId.MDOC,
                    issuer = Issuer.fromDid(issuerDid),
                    issuerKeyId = VerificationMethodId(issuerDid, issuerKeyId),
                    credentialSubject = CredentialSubject.fromDid(holderDid, claims = mapOf("family_name" to JsonPrimitive("Smith"))),
                    type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString(MdlNamespace.MDL_DOC_TYPE)),
                    proofOptions =
                        ProofOptions(
                            additionalOptions =
                                mapOf(
                                    "namespace" to MdlNamespace.ISO_18013_5_1,
                                    "docType" to MdlNamespace.MDL_DOC_TYPE,
                                    "algorithm" to "Ed25519",
                                ),
                        ),
                ),
            ).copy(credentialStatus = CredentialStatus(StatusListId("https://status.example/1#5"), "StatusList2021Entry"))

    private fun engineWith(checker: CredentialStatusChecker) = MdocProofEngine(kms, checker, optIn)

    private fun checker(result: () -> CredentialStatusCheckResult) =
        object : CredentialStatusChecker {
            override suspend fun checkStatus(credential: VerifiableCredential) = result()
        }

    private val unavailable = checker { CredentialStatusCheckResult.CheckFailed("status list unreachable") }
    private val throwing = checker { error("boom") }

    @Test
    fun `an undeterminable status fails closed by default`() =
        runBlocking<Unit> {
            val vc = statusBearingCredential()
            engineWith(unavailable).verify(vc, VerificationOptions()).shouldBeInstanceOf<VerificationResult.Invalid>()
            engineWith(throwing).verify(vc, VerificationOptions()).shouldBeInstanceOf<VerificationResult.Invalid>()
        }

    @Test
    fun `FAIL_WITH_WARNING accepts but says so`() =
        runBlocking<Unit> {
            val result =
                engineWith(unavailable)
                    .verify(
                        statusBearingCredential(),
                        VerificationOptions(revocationFailurePolicy = RevocationFailurePolicy.FAIL_WITH_WARNING),
                    )
            result.shouldBeInstanceOf<VerificationResult.Valid>().warnings.shouldNotBeEmpty()
        }

    @Test
    fun `FAIL_OPEN accepts when the verifier chose it`() =
        runBlocking<Unit> {
            engineWith(unavailable)
                .verify(statusBearingCredential(), VerificationOptions(revocationFailurePolicy = RevocationFailurePolicy.FAIL_OPEN))
                .shouldBeInstanceOf<VerificationResult.Valid>()
        }

    @Test
    fun `a conclusive status is unaffected`() =
        runBlocking<Unit> {
            val vc = statusBearingCredential()
            engineWith(checker { CredentialStatusCheckResult.Valid })
                .verify(vc, VerificationOptions())
                .shouldBeInstanceOf<VerificationResult.Valid>()
            engineWith(checker { CredentialStatusCheckResult.Revoked("lost") })
                .verify(vc, VerificationOptions())
                .shouldBeInstanceOf<VerificationResult.Invalid.Revoked>()
            engineWith(checker { CredentialStatusCheckResult.Suspended("hold") })
                .verify(vc, VerificationOptions())
                .shouldBeInstanceOf<VerificationResult.Invalid>()
        }
}
