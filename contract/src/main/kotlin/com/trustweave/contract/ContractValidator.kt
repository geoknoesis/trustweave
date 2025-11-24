package com.trustweave.contract

import com.trustweave.contract.models.*
import com.trustweave.core.util.ValidationResult
import com.trustweave.core.util.DidValidator
import java.time.Instant

/**
 * Validates contract-related data.
 */
object ContractValidator {
    
    /**
     * Validates a contract draft request.
     */
    fun validateDraftRequest(request: ContractDraftRequest): ValidationResult {
        // Validate parties
        val partiesResult = validateParties(request.parties)
        if (!partiesResult.isValid()) {
            return partiesResult
        }
        
        // Validate dates
        val datesResult = validateDates(request.effectiveDate, request.expirationDate)
        if (!datesResult.isValid()) {
            return datesResult
        }
        
        // Validate terms
        val termsResult = validateTerms(request.terms)
        if (!termsResult.isValid()) {
            return termsResult
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates contract parties.
     */
    fun validateParties(parties: ContractParties): ValidationResult {
        // Validate primary party DID
        val primaryResult = DidValidator.validateFormat(parties.primaryPartyDid)
        if (!primaryResult.isValid()) {
            return ValidationResult.Invalid(
                code = "INVALID_PRIMARY_PARTY_DID",
                message = "Primary party DID is invalid: ${primaryResult.errorMessage()}",
                field = "parties.primaryPartyDid",
                value = parties.primaryPartyDid
            )
        }
        
        // Validate counterparty DID
        val counterpartyResult = DidValidator.validateFormat(parties.counterpartyDid)
        if (!counterpartyResult.isValid()) {
            return ValidationResult.Invalid(
                code = "INVALID_COUNTERPARTY_DID",
                message = "Counterparty DID is invalid: ${counterpartyResult.errorMessage()}",
                field = "parties.counterpartyDid",
                value = parties.counterpartyDid
            )
        }
        
        // Ensure parties are different
        if (parties.primaryPartyDid == parties.counterpartyDid) {
            return ValidationResult.Invalid(
                code = "DUPLICATE_PARTIES",
                message = "Primary party and counterparty must be different",
                field = "parties",
                value = parties.primaryPartyDid
            )
        }
        
        // Validate additional parties
        parties.additionalParties.forEach { (role, did) ->
            val result = DidValidator.validateFormat(did)
            if (!result.isValid()) {
                return ValidationResult.Invalid(
                    code = "INVALID_ADDITIONAL_PARTY_DID",
                    message = "Additional party DID for role '$role' is invalid: ${result.errorMessage()}",
                    field = "parties.additionalParties.$role",
                    value = did
                )
            }
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates date fields.
     */
    fun validateDates(effectiveDate: String, expirationDate: String?): ValidationResult {
        if (effectiveDate.isBlank()) {
            return ValidationResult.Invalid(
                code = "EMPTY_EFFECTIVE_DATE",
                message = "Effective date is required",
                field = "effectiveDate",
                value = effectiveDate
            )
        }
        
        // Validate effective date format
        val effective = try {
            Instant.parse(effectiveDate)
        } catch (e: Exception) {
            return ValidationResult.Invalid(
                code = "INVALID_EFFECTIVE_DATE_FORMAT",
                message = "Effective date must be in ISO 8601 format: ${e.message}",
                field = "effectiveDate",
                value = effectiveDate
            )
        }
        
        // Validate expiration date if provided
        if (expirationDate != null && expirationDate.isNotBlank()) {
            val expiration = try {
                Instant.parse(expirationDate)
            } catch (e: Exception) {
                return ValidationResult.Invalid(
                    code = "INVALID_EXPIRATION_DATE_FORMAT",
                    message = "Expiration date must be in ISO 8601 format: ${e.message}",
                    field = "expirationDate",
                    value = expirationDate
                )
            }
            
            // Ensure expiration is after effective date
            if (!expiration.isAfter(effective)) {
                return ValidationResult.Invalid(
                    code = "INVALID_DATE_RANGE",
                    message = "Expiration date must be after effective date",
                    field = "expirationDate",
                    value = expirationDate
                )
            }
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates contract terms.
     */
    fun validateTerms(terms: ContractTerms): ValidationResult {
        // Validate obligations have unique IDs
        val obligationIds = terms.obligations.map { it.id }.toSet()
        if (obligationIds.size != terms.obligations.size) {
            return ValidationResult.Invalid(
                code = "DUPLICATE_OBLIGATION_IDS",
                message = "Obligation IDs must be unique",
                field = "terms.obligations",
                value = obligationIds.toString()
            )
        }
        
        // Validate conditions have unique IDs
        val conditionIds = terms.conditions.map { it.id }.toSet()
        if (conditionIds.size != terms.conditions.size) {
            return ValidationResult.Invalid(
                code = "DUPLICATE_CONDITION_IDS",
                message = "Condition IDs must be unique",
                field = "terms.conditions",
                value = conditionIds.toString()
            )
        }
        
        // Validate obligation party DIDs
        terms.obligations.forEach { obligation ->
            val result = DidValidator.validateFormat(obligation.partyDid)
            if (!result.isValid()) {
                return ValidationResult.Invalid(
                    code = "INVALID_OBLIGATION_PARTY_DID",
                    message = "Obligation '${obligation.id}' has invalid party DID: ${result.errorMessage()}",
                    field = "terms.obligations.${obligation.id}.partyDid",
                    value = obligation.partyDid
                )
            }
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates state transition.
     */
    fun validateStateTransition(from: ContractStatus, to: ContractStatus): ValidationResult {
        val validTransitions = mapOf(
            ContractStatus.DRAFT to setOf(
                ContractStatus.PENDING,
                ContractStatus.CANCELLED
            ),
            ContractStatus.PENDING to setOf(
                ContractStatus.ACTIVE,
                ContractStatus.CANCELLED
            ),
            ContractStatus.ACTIVE to setOf(
                ContractStatus.EXECUTED,
                ContractStatus.SUSPENDED,
                ContractStatus.EXPIRED,
                ContractStatus.TERMINATED,
                ContractStatus.CANCELLED
            ),
            ContractStatus.SUSPENDED to setOf(
                ContractStatus.ACTIVE,
                ContractStatus.TERMINATED,
                ContractStatus.CANCELLED
            ),
            ContractStatus.EXECUTED to emptySet(), // Terminal state
            ContractStatus.EXPIRED to emptySet(), // Terminal state
            ContractStatus.CANCELLED to emptySet(), // Terminal state
            ContractStatus.TERMINATED to emptySet() // Terminal state
        )
        
        val allowed = validTransitions[from] ?: emptySet()
        
        if (to !in allowed) {
            return ValidationResult.Invalid(
                code = "INVALID_STATE_TRANSITION",
                message = "Cannot transition from $from to $to. Allowed transitions: $allowed",
                field = "status",
                value = "$from -> $to"
            )
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Checks if contract is expired.
     */
    fun isExpired(contract: SmartContract): Boolean {
        if (contract.expirationDate == null) {
            return false
        }
        
        return try {
            val expiration = Instant.parse(contract.expirationDate)
            Instant.now().isAfter(expiration)
        } catch (e: Exception) {
            false // If date is invalid, don't consider expired
        }
    }
}


