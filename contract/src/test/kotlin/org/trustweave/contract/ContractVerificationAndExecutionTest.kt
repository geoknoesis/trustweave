package org.trustweave.contract

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.anchor.DefaultBlockchainAnchorRegistry
import org.trustweave.contract.evaluation.ContractEvaluationEngine
import org.trustweave.contract.evaluation.EvaluationContext
import org.trustweave.contract.evaluation.EvaluationEngines
import org.trustweave.contract.models.ConditionType
import org.trustweave.contract.models.ContractCondition
import org.trustweave.contract.models.ContractParties
import org.trustweave.contract.models.ContractStatus
import org.trustweave.contract.models.ContractTerms
import org.trustweave.contract.models.ContractType
import org.trustweave.contract.models.ExecutionContext
import org.trustweave.contract.models.ExecutionModel
import org.trustweave.contract.models.Obligation
import org.trustweave.contract.models.ObligationType
import org.trustweave.contract.models.SmartContract
import org.trustweave.contract.models.TriggerType
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.CredentialSubject
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
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Regression tests for contract verification (must check the credential, not just that an ID
 * exists) and for execution (stored record, single execution under concurrency).
 */
class ContractVerificationAndExecutionTest {
    private val primaryDid = "did:key:z6MkPrimary"
    private val counterpartyDid = "did:key:z6MkCounter"
    private val chainId = "test:chain"

    /** Issues credentials by copying the request; verification outcome is configurable. */
    internal open class FakeCredentialService(
        var valid: Boolean = true,
        val tamper: (CredentialSubject) -> CredentialSubject = { it },
        val issuerOverride: ((org.trustweave.credential.model.vc.Issuer) -> org.trustweave.credential.model.vc.Issuer)? = null,
    ) : CredentialService {
        val verifyCalls = AtomicInteger()

        override suspend fun issue(request: IssuanceRequest): IssuanceResult =
            IssuanceResult.Success(
                VerifiableCredential(
                    id = request.id,
                    type = request.type,
                    issuer = issuerOverride?.invoke(request.issuer) ?: request.issuer,
                    issuanceDate = request.issuedAt,
                    credentialSubject = tamper(request.credentialSubject),
                ),
            )

        override suspend fun verify(
            credential: VerifiableCredential,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult {
            verifyCalls.incrementAndGet()
            return if (valid) {
                VerificationResult.Valid(credential, credential.issuer.id, credential.credentialSubject.id, Clock.System.now(), null)
            } else {
                VerificationResult.Invalid.InvalidProof(credential, "bad signature")
            }
        }

        override suspend fun createPresentation(
            credentials: List<VerifiableCredential>,
            request: PresentationRequest,
        ): VerifiablePresentation = error("not used")

        override suspend fun verifyPresentation(
            presentation: VerifiablePresentation,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult = error("not used")

        override suspend fun status(
            credential: VerifiableCredential,
            clockSkewTolerance: Duration,
        ): CredentialStatusInfo = error("not used")

        override fun supports(format: ProofSuiteId): Boolean = true

        override fun supportedFormats(): List<ProofSuiteId> = listOf(ProofSuiteId.VC_LD)

        override fun supportsCapability(
            format: ProofSuiteId,
            capability: ProofEngineCapabilities.() -> Boolean,
        ): Boolean = false
    }

    /** Always-satisfied engine that suspends, so concurrent executions really interleave. */
    private class SlowTrueEngine : ContractEvaluationEngine {
        val evaluations = AtomicInteger()
        override val engineId = "slow-true"
        override val version = "1.0"
        override val implementationHash = "hash"
        override val supportedConditionTypes = setOf(ConditionType.THRESHOLD)

        override suspend fun evaluateCondition(
            condition: ContractCondition,
            inputData: JsonElement,
            context: EvaluationContext,
        ): Boolean {
            evaluations.incrementAndGet()
            delay(20)
            return true
        }

        override suspend fun evaluateConditions(
            conditions: List<ContractCondition>,
            inputData: JsonElement,
            context: EvaluationContext,
        ): Map<String, Boolean> = conditions.associate { it.id to evaluateCondition(it, inputData, context) }
    }

    private fun request(
        executionModel: ExecutionModel = ExecutionModel.Manual,
        conditions: List<ContractCondition> = emptyList(),
    ) = ContractDraftRequest(
        contractType = ContractType.Insurance,
        executionModel = executionModel,
        parties = ContractParties(primaryDid, counterpartyDid),
        terms =
            ContractTerms(
                obligations = listOf(Obligation("ob-1", primaryDid, "Pay out", ObligationType.PAYMENT)),
                conditions = conditions,
            ),
        effectiveDate = "2025-01-01T00:00:00Z",
        expirationDate = "2099-01-01T00:00:00Z",
        contractData = buildJsonObject { put("domain", "test") },
    )

    private fun serviceWith(
        credentialService: CredentialService?,
        engines: EvaluationEngines = EvaluationEngines(),
    ) = DefaultSmartContractService(
        credentialService = credentialService,
        blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chainId, InMemoryBlockchainAnchorClient(chainId)) },
        engines = engines,
    )

    // ── verifyContract ───────────────────────────────────────────────────────

    @Test
    fun `verifyContract verifies the issued credential and returns true`() =
        runTest {
            val credentials = FakeCredentialService(valid = true)
            val service = serviceWith(credentials)
            val draft = service.createDraft(request()).getOrThrow()
            val bound = service.bindContract(draft.id, primaryDid, "$primaryDid#key-1", chainId).getOrThrow()

            assertEquals(true, service.verifyContract(bound.credentialId).getOrThrow())
            assertEquals(1, credentials.verifyCalls.get())
        }

    @Test
    fun `verifyContract returns false when the credential proof is invalid`() =
        runTest {
            val credentials = FakeCredentialService(valid = true)
            val service = serviceWith(credentials)
            val draft = service.createDraft(request()).getOrThrow()
            val bound = service.bindContract(draft.id, primaryDid, "$primaryDid#key-1", chainId).getOrThrow()
            credentials.valid = false

            assertEquals(false, service.verifyContract(bound.credentialId).getOrThrow())
        }

    @Test
    fun `verifyContract returns false when the credential does not bind the contract terms`() =
        runTest {
            val credentials =
                FakeCredentialService(valid = true, tamper = { subject ->
                    subject.copy(claims = subject.claims + ("contractNumber" to JsonPrimitive("CONTRACT-OTHER")))
                })
            val service = serviceWith(credentials)
            val draft = service.createDraft(request()).getOrThrow()
            val bound = service.bindContract(draft.id, primaryDid, "$primaryDid#key-1", chainId).getOrThrow()

            assertEquals(false, service.verifyContract(bound.credentialId).getOrThrow())
        }

    @Test
    fun `verifyContract fails for an unknown credential instead of reporting success`() =
        runTest {
            val service = serviceWith(FakeCredentialService())
            assertTrue(service.verifyContract("urn:uuid:unknown").isFailure)
        }

    @Test
    fun `verifyContract fails without a credential service`() =
        runTest {
            val service = DefaultSmartContractService()
            assertTrue(service.verifyContract("urn:uuid:any").isFailure)
        }

    // ── executeContract ──────────────────────────────────────────────────────

    private suspend fun activeConditionalContract(service: DefaultSmartContractService): SmartContract {
        val condition = ContractCondition("c-1", "always", ConditionType.THRESHOLD, "x > 0")
        val draft =
            service
                .createDraft(
                    request(ExecutionModel.Parametric(TriggerType.Weather, "slow-true"), listOf(condition)),
                ).getOrThrow()
        service.bindContract(draft.id, primaryDid, "$primaryDid#key-1", chainId).getOrThrow()
        return service.activateContract(draft.id).getOrThrow()
    }

    @Test
    fun `concurrent executions of one ACTIVE contract execute it exactly once`() =
        runTest {
            val engine = SlowTrueEngine()
            val service = serviceWith(FakeCredentialService(), EvaluationEngines().apply { plusAssign(engine) })
            val active = activeConditionalContract(service)

            val results =
                withContext(Dispatchers.Default) {
                    (1..16)
                        .map { async { service.executeContract(active, ExecutionContext(triggerData = buildJsonObject { put("x", 1) })) } }
                        .awaitAll()
                }

            assertEquals(1, results.count { it.isSuccess && it.getOrThrow().executed })
            assertEquals(15, results.count { it.isFailure })
            assertEquals(ContractStatus.EXECUTED, service.getContract(active.id).getOrThrow().status)
        }

    @Test
    fun `executeContract uses the stored record, not a stale ACTIVE snapshot`() =
        runTest {
            val service = serviceWith(FakeCredentialService(), EvaluationEngines().apply { plusAssign(SlowTrueEngine()) })
            val active = activeConditionalContract(service)
            service.updateStatus(active.id, ContractStatus.SUSPENDED).getOrThrow()

            val result = service.executeContract(active, ExecutionContext(triggerData = buildJsonObject { put("x", 1) }))

            assertTrue(result.isFailure)
            assertEquals(ContractStatus.SUSPENDED, service.getContract(active.id).getOrThrow().status)
        }

    @Test
    fun `executeContract ignores terms edited in the caller snapshot`() =
        runTest {
            val service = serviceWith(FakeCredentialService(), EvaluationEngines().apply { plusAssign(SlowTrueEngine()) })
            val active = activeConditionalContract(service)
            val forged =
                active.copy(
                    terms =
                        active.terms.copy(
                            obligations = listOf(Obligation("forged", primaryDid, "Pay me twice", ObligationType.PAYMENT)),
                        ),
                )

            val result = service.executeContract(forged, ExecutionContext(triggerData = buildJsonObject { put("x", 1) })).getOrThrow()

            assertTrue(result.executed)
            assertEquals(listOf("ob-1"), result.outcomes.map { it.obligationTriggered })
        }

    @Test
    fun `executeContract reports the binding credential as evidence`() =
        runTest {
            val service = serviceWith(FakeCredentialService(), EvaluationEngines().apply { plusAssign(SlowTrueEngine()) })
            val condition = ContractCondition("c-1", "always", ConditionType.THRESHOLD, "x > 0")
            val draft =
                service
                    .createDraft(
                        request(ExecutionModel.Parametric(TriggerType.Weather, "slow-true"), listOf(condition)),
                    ).getOrThrow()
            val bound = service.bindContract(draft.id, primaryDid, "$primaryDid#key-1", chainId).getOrThrow()
            val active = service.activateContract(draft.id).getOrThrow()

            val result = service.executeContract(active, ExecutionContext(triggerData = buildJsonObject { put("x", 1) })).getOrThrow()

            assertEquals(listOf(bound.credentialId), result.evidence)
        }

    @Test
    fun `executeContract fails for an unknown contract`() =
        runTest {
            val service = serviceWith(null)
            val ghost = service.createDraft(request()).getOrThrow().copy(id = "ghost", status = ContractStatus.ACTIVE)
            assertTrue(service.executeContract(ghost, ExecutionContext()).isFailure)
        }
}
