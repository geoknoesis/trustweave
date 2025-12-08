package com.trustweave.credential.requests

import com.trustweave.credential.proof.ProofOptions
import kotlinx.serialization.json.JsonElement

/**
 * Request for credential presentation.
 * 
 * This is the data class only. Builder DSL is available via extension functions in the trust module.
 * 
 * Use `proofOptions.challenge` for proof nonce/challenge configuration.
 */
data class PresentationRequest(
    val disclosedClaims: Set<String>? = null,  // null = all
    val predicates: List<Predicate> = emptyList(),
    val proofOptions: ProofOptions? = null  // Proof-specific options (includes proof suite-specific options in additionalOptions)
)

enum class PredicateOperator {
    GREATER_THAN,
    LESS_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN_OR_EQUAL,
    EQUAL,
    NOT_EQUAL
}

data class Predicate(
    val claim: String,
    val operator: PredicateOperator,
    val value: JsonElement
)

