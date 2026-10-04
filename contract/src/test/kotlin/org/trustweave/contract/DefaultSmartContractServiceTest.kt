package org.trustweave.contract

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.anchor.DefaultBlockchainAnchorRegistry
import org.trustweave.contract.ContractVerificationAndExecutionTest.FakeCredentialService
import org.trustweave.contract.models.ContractParties
import org.trustweave.contract.models.ContractStatus
import org.trustweave.contract.models.ContractTerms
import org.trustweave.contract.models.ContractType
import org.trustweave.contract.models.ExecutionContext
import org.trustweave.contract.models.ExecutionModel
import org.trustweave.contract.models.Obligation
import org.trustweave.contract.models.ObligationType
import org.trustweave.contract.models.SmartContract
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [DefaultSmartContractService] lifecycle: DRAFT → PENDING → ACTIVE → (EXPIRED/EXECUTED).
 *
 * Tests without binding use the no-arg constructor (no CredentialService, no blockchain registry) to
 * isolate state-machine behaviour. A contract can only become ACTIVE after [bindContract] bound a
 * credential and anchor, so lifecycle tests past PENDING use [boundService].
 */
class DefaultSmartContractServiceTest {
    private val primaryDid = "did:key:z6MkPrimary"
    private val counterpartyDid = "did:key:z6MkCounter"

    private fun validRequest(
        effectiveDate: String = "2025-01-01T00:00:00Z",
        expirationDate: String? = "2099-01-01T00:00:00Z",
    ) = ContractDraftRequest(
        contractType = ContractType.Insurance,
        executionModel = ExecutionModel.Manual,
        parties = ContractParties(primaryDid, counterpartyDid),
        terms =
            ContractTerms(
                obligations =
                    listOf(
                        Obligation(
                            id = "ob-1",
                            partyDid = primaryDid,
                            description = "Pay premium",
                            obligationType = ObligationType.PAYMENT,
                        ),
                    ),
                conditions = emptyList(),
            ),
        effectiveDate = effectiveDate,
        expirationDate = expirationDate,
        contractData = buildJsonObject { put("domain", "test") },
    )

    private val chain = "test:chain"

    private fun boundService() =
        DefaultSmartContractService(
            credentialService = FakeCredentialService(),
            blockchainRegistry = DefaultBlockchainAnchorRegistry().apply { register(chain, InMemoryBlockchainAnchorClient(chain)) },
        )

    private suspend fun DefaultSmartContractService.draftAndBind(): SmartContract {
        val draft = createDraft(validRequest()).getOrThrow()
        bindContract(draft.id, primaryDid, "$primaryDid#key-1", chain).getOrThrow()
        return getContract(draft.id).getOrThrow()
    }

    // ── createDraft ──────────────────────────────────────────────────────────

    @Test
    fun `createDraft returns DRAFT status`() =
        runTest {
            val service = DefaultSmartContractService()
            val result = service.createDraft(validRequest())
            assertTrue(result.isSuccess)
            val contract = result.getOrThrow()
            assertEquals(ContractStatus.DRAFT, contract.status)
            assertNotNull(contract.id)
            assertNotNull(contract.contractNumber)
        }

    @Test
    fun `createDraft stores contract retrievable via getContract`() =
        runTest {
            val service = DefaultSmartContractService()
            val contract = service.createDraft(validRequest()).getOrThrow()
            val retrieved = service.getContract(contract.id).getOrThrow()
            assertEquals(contract.id, retrieved.id)
            assertEquals(ContractStatus.DRAFT, retrieved.status)
        }

    @Test
    fun `createDraft fails with invalid parties`() =
        runTest {
            val service = DefaultSmartContractService()
            val badRequest =
                validRequest().copy(
                    parties = ContractParties("", counterpartyDid),
                )
            val result = service.createDraft(badRequest)
            assertTrue(result.isFailure)
        }

    // ── updateStatus: DRAFT → PENDING ────────────────────────────────────────

    @Test
    fun `updateStatus DRAFT to PENDING succeeds`() =
        runTest {
            val service = DefaultSmartContractService()
            val contract = service.createDraft(validRequest()).getOrThrow()
            val updated = service.updateStatus(contract.id, ContractStatus.PENDING).getOrThrow()
            assertEquals(ContractStatus.PENDING, updated.status)
        }

    // ── activateContract: PENDING → ACTIVE ──────────────────────────────────

    @Test
    fun `activateContract transitions PENDING to ACTIVE`() =
        runTest {
            val service = boundService()
            val contract = service.draftAndBind()
            val activated = service.activateContract(contract.id).getOrThrow()
            assertEquals(ContractStatus.ACTIVE, activated.status)
        }

    @Test
    fun `activateContract fails when contract is in DRAFT status`() =
        runTest {
            val service = DefaultSmartContractService()
            val contract = service.createDraft(validRequest()).getOrThrow()
            val result = service.activateContract(contract.id)
            assertTrue(result.isFailure)
        }

    // ── updateStatus: ACTIVE → EXPIRED ──────────────────────────────────────

    @Test
    fun `updateStatus ACTIVE to EXPIRED succeeds`() =
        runTest {
            val service = boundService()
            val contract = service.draftAndBind()
            service.activateContract(contract.id).getOrThrow()
            val expired = service.updateStatus(contract.id, ContractStatus.EXPIRED).getOrThrow()
            assertEquals(ContractStatus.EXPIRED, expired.status)
        }

    // ── terminal states ──────────────────────────────────────────────────────

    @Test
    fun `cannot transition out of TERMINATED status`() =
        runTest {
            val service = boundService()
            val contract = service.draftAndBind()
            service.activateContract(contract.id).getOrThrow()
            service.updateStatus(contract.id, ContractStatus.TERMINATED).getOrThrow()
            val result = service.updateStatus(contract.id, ContractStatus.ACTIVE)
            assertTrue(result.isFailure)
        }

    @Test
    fun `cannot transition out of CANCELLED status`() =
        runTest {
            val service = DefaultSmartContractService()
            val contract = service.createDraft(validRequest()).getOrThrow()
            service.updateStatus(contract.id, ContractStatus.CANCELLED).getOrThrow()
            val result = service.updateStatus(contract.id, ContractStatus.DRAFT)
            assertTrue(result.isFailure)
        }

    // ── activation and execution need a bound credential and the execute path ──

    @Test
    fun `a contract cannot reach ACTIVE through updateStatus or activateContract without a bound credential`() =
        runTest {
            val service = boundService()
            val contract = service.createDraft(validRequest()).getOrThrow()
            service.updateStatus(contract.id, ContractStatus.PENDING).getOrThrow()

            assertTrue(service.updateStatus(contract.id, ContractStatus.ACTIVE).isFailure)
            assertTrue(service.activateContract(contract.id).isFailure)
            assertEquals(ContractStatus.PENDING, service.getContract(contract.id).getOrThrow().status)
        }

    @Test
    fun `updateStatus can activate a contract once it is bound`() =
        runTest {
            val service = boundService()
            val contract = service.draftAndBind()
            assertEquals(ContractStatus.ACTIVE, service.updateStatus(contract.id, ContractStatus.ACTIVE).getOrThrow().status)
        }

    @Test
    fun `a service without an anchor layer cannot bind and therefore never activates a contract`() =
        runTest {
            // No registry: bindContract cannot anchor, so such a service can never activate a contract.
            val service = DefaultSmartContractService(credentialService = FakeCredentialService())
            val contract = service.createDraft(validRequest()).getOrThrow()
            assertTrue(service.bindContract(contract.id, primaryDid, "$primaryDid#key-1", chain).isFailure)
            service.updateStatus(contract.id, ContractStatus.PENDING).getOrThrow()
            assertTrue(service.activateContract(contract.id).isFailure)
        }

    @Test
    fun `updateStatus refuses EXECUTED and leaves the contract untouched`() =
        runTest {
            val service = boundService()
            val contract = service.draftAndBind()
            service.activateContract(contract.id).getOrThrow()

            val result = service.updateStatus(contract.id, ContractStatus.EXECUTED)

            assertTrue(result.isFailure)
            assertEquals(ContractStatus.ACTIVE, service.getContract(contract.id).getOrThrow().status)
        }

    @Test
    fun `executeContract is the way to EXECUTED for a manual contract without conditions`() =
        runTest {
            val service = boundService()
            val contract = service.draftAndBind()
            val active = service.activateContract(contract.id).getOrThrow()

            val result = service.executeContract(active, ExecutionContext()).getOrThrow()

            assertTrue(result.executed)
            assertEquals(ContractStatus.EXECUTED, service.getContract(contract.id).getOrThrow().status)
        }

    // ── getContract ──────────────────────────────────────────────────────────

    @Test
    fun `getContract fails for unknown ID`() =
        runTest {
            val service = DefaultSmartContractService()
            val result = service.getContract("nonexistent-id")
            assertTrue(result.isFailure)
        }
}
