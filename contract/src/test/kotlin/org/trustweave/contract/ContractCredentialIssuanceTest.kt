package org.trustweave.contract

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import org.trustweave.contract.models.*
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.CredentialStatusInfo
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.core.identifiers.Iri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that [DefaultSmartContractService.issueContractCredential] correctly delegates to
 * [CredentialService.issue] and propagates results.
 */
class ContractCredentialIssuanceTest {

    private val primaryDid = "did:key:z6MkIssuer"
    private val counterpartyDid = "did:key:z6MkHolder"
    private val issuerKeyId = "did:key:z6MkIssuer#key-1"

    private fun makeDraftContract(service: DefaultSmartContractService): SmartContract {
        val request = ContractDraftRequest(
            contractType = ContractType.Insurance,
            executionModel = ExecutionModel.Manual,
            parties = ContractParties(primaryDid, counterpartyDid),
            terms = ContractTerms(
                obligations = listOf(
                    Obligation("ob-1", primaryDid, "Pay premium", ObligationType.PAYMENT)
                ),
                conditions = emptyList()
            ),
            effectiveDate = "2025-01-01T00:00:00Z",
            expirationDate = "2099-01-01T00:00:00Z",
            contractData = buildJsonObject { put("domain", "test") }
        )
        var contract: SmartContract? = null
        kotlinx.coroutines.runBlocking {
            contract = service.createDraft(request).getOrThrow()
        }
        return contract!!
    }

    /** Minimal CredentialService stub that records [issue] call arguments. */
    private class CapturingCredentialService(
        private val issueResponse: IssuanceResult
    ) : CredentialService {
        val capturedRequests = mutableListOf<IssuanceRequest>()

        override suspend fun issue(request: IssuanceRequest): IssuanceResult {
            capturedRequests += request
            return issueResponse
        }

        override suspend fun verify(credential: VerifiableCredential, trustPolicy: TrustEvaluator?, options: VerificationOptions): VerificationResult =
            error("not used")
        override suspend fun createPresentation(credentials: List<VerifiableCredential>, request: PresentationRequest): VerifiablePresentation =
            error("not used")
        override suspend fun verifyPresentation(presentation: VerifiablePresentation, trustPolicy: TrustEvaluator?, options: VerificationOptions): VerificationResult =
            error("not used")
        override suspend fun status(credential: VerifiableCredential, clockSkewTolerance: Duration): CredentialStatusInfo =
            error("not used")
        override fun supports(format: ProofSuiteId): Boolean = false
        override fun supportedFormats(): List<ProofSuiteId> = emptyList()
        override fun supportsCapability(format: ProofSuiteId, capability: ProofEngineCapabilities.() -> Boolean): Boolean = false
    }

    private fun stubCredential() = VerifiableCredential(
        id = CredentialId("urn:uuid:stub-credential"),
        type = listOf(
            CredentialType.fromString("VerifiableCredential"),
            CredentialType.fromString("SmartContractCredential")
        ),
        issuer = Issuer.from(Iri(primaryDid)),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject.fromIri(Iri("urn:contract:stub"))
    )

    // ── issueContractCredential delegates to CredentialService.issue ─────────

    @Test
    fun `issueContractCredential calls credentialService issue once`() = runTest {
        val stubCred = stubCredential()
        val capturingService = CapturingCredentialService(IssuanceResult.Success(stubCred))
        val service = DefaultSmartContractService(credentialService = capturingService)

        val contract = makeDraftContract(service)
        val result = service.issueContractCredential(contract, primaryDid, issuerKeyId)

        assertTrue(result.isSuccess)
        assertEquals(1, capturingService.capturedRequests.size)
    }

    @Test
    fun `issueContractCredential returns credential from credentialService`() = runTest {
        val stubCred = stubCredential()
        val capturingService = CapturingCredentialService(IssuanceResult.Success(stubCred))
        val service = DefaultSmartContractService(credentialService = capturingService)

        val contract = makeDraftContract(service)
        val issued = service.issueContractCredential(contract, primaryDid, issuerKeyId).getOrThrow()

        assertEquals(stubCred.id, issued.id)
    }

    @Test
    fun `issueContractCredential propagates issuance failure`() = runTest {
        val failureResponse = IssuanceResult.Failure.InvalidRequest("issuer", "not found")
        val capturingService = CapturingCredentialService(failureResponse)
        val service = DefaultSmartContractService(credentialService = capturingService)

        val contract = makeDraftContract(service)
        val result = service.issueContractCredential(contract, primaryDid, issuerKeyId)

        assertTrue(result.isFailure)
    }

    @Test
    fun `issueContractCredential fails when no credentialService provided`() = runTest {
        val service = DefaultSmartContractService() // no CredentialService
        val contract = makeDraftContract(service)
        val result = service.issueContractCredential(contract, primaryDid, issuerKeyId)

        assertTrue(result.isFailure)
    }

    @Test
    fun `issueContractCredential embeds contract type in issuance request`() = runTest {
        val stubCred = stubCredential()
        val capturingService = CapturingCredentialService(IssuanceResult.Success(stubCred))
        val service = DefaultSmartContractService(credentialService = capturingService)

        val contract = makeDraftContract(service)
        service.issueContractCredential(contract, primaryDid, issuerKeyId)

        val request = capturingService.capturedRequests.first()
        assertTrue(request.type.any { it.value == "SmartContractCredential" })
    }
}
