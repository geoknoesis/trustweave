package org.trustweave.contract

import org.trustweave.contract.models.*
import org.trustweave.core.util.ValidationResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractValidatorTest {

    private val validPrimaryDid = "did:key:z6MkPrimaryParty"
    private val validCounterpartyDid = "did:key:z6MkCounterparty"
    private val validEffectiveDate = "2025-01-01T00:00:00Z"
    private val validExpirationDate = "2026-01-01T00:00:00Z"

    private fun validParties() = ContractParties(
        primaryPartyDid = validPrimaryDid,
        counterpartyDid = validCounterpartyDid
    )

    private fun validTerms() = ContractTerms(
        obligations = listOf(
            Obligation(
                id = "ob-1",
                partyDid = validPrimaryDid,
                description = "Pay premium",
                obligationType = ObligationType.PAYMENT
            )
        ),
        conditions = emptyList()
    )

    // ── validateParties ──────────────────────────────────────────────────────

    @Test
    fun `validateParties accepts valid distinct DIDs`() {
        val result = ContractValidator.validateParties(validParties())
        assertTrue(result.isValid(), "Expected valid parties")
    }

    @Test
    fun `validateParties rejects duplicate DIDs`() {
        val result = ContractValidator.validateParties(
            ContractParties(
                primaryPartyDid = validPrimaryDid,
                counterpartyDid = validPrimaryDid
            )
        )
        assertFalse(result.isValid())
    }

    @Test
    fun `validateParties rejects blank primary DID`() {
        val result = ContractValidator.validateParties(
            ContractParties(primaryPartyDid = "", counterpartyDid = validCounterpartyDid)
        )
        assertFalse(result.isValid())
    }

    @Test
    fun `validateParties rejects invalid counterparty DID format`() {
        val result = ContractValidator.validateParties(
            ContractParties(
                primaryPartyDid = validPrimaryDid,
                counterpartyDid = "not-a-did"
            )
        )
        assertFalse(result.isValid())
    }

    // ── validateDates ────────────────────────────────────────────────────────

    @Test
    fun `validateDates accepts valid date range`() {
        val result = ContractValidator.validateDates(validEffectiveDate, validExpirationDate)
        assertTrue(result.isValid())
    }

    @Test
    fun `validateDates accepts null expiration`() {
        val result = ContractValidator.validateDates(validEffectiveDate, null)
        assertTrue(result.isValid())
    }

    @Test
    fun `validateDates rejects blank effective date`() {
        val result = ContractValidator.validateDates("", validExpirationDate)
        assertFalse(result.isValid())
    }

    @Test
    fun `validateDates rejects expiration before effective`() {
        val result = ContractValidator.validateDates(
            "2025-06-01T00:00:00Z",
            "2025-01-01T00:00:00Z"
        )
        assertFalse(result.isValid())
    }

    @Test
    fun `validateDates rejects malformed dates`() {
        val result = ContractValidator.validateDates("not-a-date", null)
        assertFalse(result.isValid())
    }

    // ── validateStateTransition ──────────────────────────────────────────────

    @Test
    fun `DRAFT to PENDING is valid`() {
        val result = ContractValidator.validateStateTransition(ContractStatus.DRAFT, ContractStatus.PENDING)
        assertTrue(result.isValid())
    }

    @Test
    fun `DRAFT to CANCELLED is valid`() {
        val result = ContractValidator.validateStateTransition(ContractStatus.DRAFT, ContractStatus.CANCELLED)
        assertTrue(result.isValid())
    }

    @Test
    fun `DRAFT to ACTIVE is invalid`() {
        val result = ContractValidator.validateStateTransition(ContractStatus.DRAFT, ContractStatus.ACTIVE)
        assertFalse(result.isValid())
    }

    @Test
    fun `PENDING to ACTIVE is valid`() {
        val result = ContractValidator.validateStateTransition(ContractStatus.PENDING, ContractStatus.ACTIVE)
        assertTrue(result.isValid())
    }

    @Test
    fun `ACTIVE to EXPIRED is valid`() {
        val result = ContractValidator.validateStateTransition(ContractStatus.ACTIVE, ContractStatus.EXPIRED)
        assertTrue(result.isValid())
    }

    @Test
    fun `ACTIVE to EXECUTED is valid`() {
        val result = ContractValidator.validateStateTransition(ContractStatus.ACTIVE, ContractStatus.EXECUTED)
        assertTrue(result.isValid())
    }

    @Test
    fun `EXECUTED to any state is invalid (terminal)`() {
        ContractStatus.entries.filter { it != ContractStatus.EXECUTED }.forEach { target ->
            val result = ContractValidator.validateStateTransition(ContractStatus.EXECUTED, target)
            assertFalse(result.isValid(), "EXECUTED -> $target should be invalid (terminal state)")
        }
    }

    @Test
    fun `EXPIRED to any state is invalid (terminal)`() {
        ContractStatus.entries.filter { it != ContractStatus.EXPIRED }.forEach { target ->
            val result = ContractValidator.validateStateTransition(ContractStatus.EXPIRED, target)
            assertFalse(result.isValid(), "EXPIRED -> $target should be invalid (terminal state)")
        }
    }

    @Test
    fun `CANCELLED to any state is invalid (terminal)`() {
        ContractStatus.entries.filter { it != ContractStatus.CANCELLED }.forEach { target ->
            val result = ContractValidator.validateStateTransition(ContractStatus.CANCELLED, target)
            assertFalse(result.isValid(), "CANCELLED -> $target should be invalid (terminal state)")
        }
    }

    // ── validateTerms ────────────────────────────────────────────────────────

    @Test
    fun `validateTerms accepts valid terms`() {
        val result = ContractValidator.validateTerms(validTerms())
        assertTrue(result.isValid())
    }

    @Test
    fun `validateTerms rejects duplicate obligation IDs`() {
        val dup = validTerms().copy(
            obligations = listOf(
                Obligation("ob-1", validPrimaryDid, "First", ObligationType.PAYMENT),
                Obligation("ob-1", validPrimaryDid, "Duplicate", ObligationType.PAYMENT)
            )
        )
        val result = ContractValidator.validateTerms(dup)
        assertFalse(result.isValid())
    }
}
