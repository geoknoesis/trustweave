package org.trustweave.contract

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.contract.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [DefaultSmartContractService] lifecycle: DRAFT → PENDING → ACTIVE → (EXPIRED/EXECUTED).
 *
 * These tests use the no-arg constructor (no CredentialService, no blockchain registry)
 * to isolate state-machine behaviour.
 */
class DefaultSmartContractServiceTest {

    private val primaryDid = "did:key:z6MkPrimary"
    private val counterpartyDid = "did:key:z6MkCounter"

    private fun validRequest(
        effectiveDate: String = "2025-01-01T00:00:00Z",
        expirationDate: String? = "2099-01-01T00:00:00Z"
    ) = ContractDraftRequest(
        contractType = ContractType.Insurance,
        executionModel = ExecutionModel.Manual,
        parties = ContractParties(primaryDid, counterpartyDid),
        terms = ContractTerms(
            obligations = listOf(
                Obligation(
                    id = "ob-1",
                    partyDid = primaryDid,
                    description = "Pay premium",
                    obligationType = ObligationType.PAYMENT
                )
            ),
            conditions = emptyList()
        ),
        effectiveDate = effectiveDate,
        expirationDate = expirationDate,
        contractData = buildJsonObject { put("domain", "test") }
    )

    // ── createDraft ──────────────────────────────────────────────────────────

    @Test
    fun `createDraft returns DRAFT status`() = runTest {
        val service = DefaultSmartContractService()
        val result = service.createDraft(validRequest())
        assertTrue(result.isSuccess)
        val contract = result.getOrThrow()
        assertEquals(ContractStatus.DRAFT, contract.status)
        assertNotNull(contract.id)
        assertNotNull(contract.contractNumber)
    }

    @Test
    fun `createDraft stores contract retrievable via getContract`() = runTest {
        val service = DefaultSmartContractService()
        val contract = service.createDraft(validRequest()).getOrThrow()
        val retrieved = service.getContract(contract.id).getOrThrow()
        assertEquals(contract.id, retrieved.id)
        assertEquals(ContractStatus.DRAFT, retrieved.status)
    }

    @Test
    fun `createDraft fails with invalid parties`() = runTest {
        val service = DefaultSmartContractService()
        val badRequest = validRequest().copy(
            parties = ContractParties("", counterpartyDid)
        )
        val result = service.createDraft(badRequest)
        assertTrue(result.isFailure)
    }

    // ── updateStatus: DRAFT → PENDING ────────────────────────────────────────

    @Test
    fun `updateStatus DRAFT to PENDING succeeds`() = runTest {
        val service = DefaultSmartContractService()
        val contract = service.createDraft(validRequest()).getOrThrow()
        val updated = service.updateStatus(contract.id, ContractStatus.PENDING).getOrThrow()
        assertEquals(ContractStatus.PENDING, updated.status)
    }

    // ── activateContract: PENDING → ACTIVE ──────────────────────────────────

    @Test
    fun `activateContract transitions PENDING to ACTIVE`() = runTest {
        val service = DefaultSmartContractService()
        val contract = service.createDraft(validRequest()).getOrThrow()
        service.updateStatus(contract.id, ContractStatus.PENDING).getOrThrow()
        val activated = service.activateContract(contract.id).getOrThrow()
        assertEquals(ContractStatus.ACTIVE, activated.status)
    }

    @Test
    fun `activateContract fails when contract is in DRAFT status`() = runTest {
        val service = DefaultSmartContractService()
        val contract = service.createDraft(validRequest()).getOrThrow()
        val result = service.activateContract(contract.id)
        assertTrue(result.isFailure)
    }

    // ── updateStatus: ACTIVE → EXPIRED ──────────────────────────────────────

    @Test
    fun `updateStatus ACTIVE to EXPIRED succeeds`() = runTest {
        val service = DefaultSmartContractService()
        val contract = service.createDraft(validRequest()).getOrThrow()
        service.updateStatus(contract.id, ContractStatus.PENDING).getOrThrow()
        service.activateContract(contract.id).getOrThrow()
        val expired = service.updateStatus(contract.id, ContractStatus.EXPIRED).getOrThrow()
        assertEquals(ContractStatus.EXPIRED, expired.status)
    }

    // ── terminal states ──────────────────────────────────────────────────────

    @Test
    fun `cannot transition out of TERMINATED status`() = runTest {
        val service = DefaultSmartContractService()
        val contract = service.createDraft(validRequest()).getOrThrow()
        service.updateStatus(contract.id, ContractStatus.PENDING).getOrThrow()
        service.activateContract(contract.id).getOrThrow()
        service.updateStatus(contract.id, ContractStatus.TERMINATED).getOrThrow()
        val result = service.updateStatus(contract.id, ContractStatus.ACTIVE)
        assertTrue(result.isFailure)
    }

    @Test
    fun `cannot transition out of CANCELLED status`() = runTest {
        val service = DefaultSmartContractService()
        val contract = service.createDraft(validRequest()).getOrThrow()
        service.updateStatus(contract.id, ContractStatus.CANCELLED).getOrThrow()
        val result = service.updateStatus(contract.id, ContractStatus.DRAFT)
        assertTrue(result.isFailure)
    }

    // ── getContract ──────────────────────────────────────────────────────────

    @Test
    fun `getContract fails for unknown ID`() = runTest {
        val service = DefaultSmartContractService()
        val result = service.getContract("nonexistent-id")
        assertTrue(result.isFailure)
    }
}
