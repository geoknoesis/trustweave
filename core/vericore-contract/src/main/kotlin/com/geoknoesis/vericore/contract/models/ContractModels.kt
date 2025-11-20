package com.geoknoesis.vericore.contract.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Core SmartContract models for VeriCore.
 * 
 * These models provide a domain-agnostic abstraction for executable contracts
 * with verifiable credentials and blockchain anchoring support.
 */

/**
 * Contract lifecycle status.
 */
@Serializable
enum class ContractStatus {
    DRAFT,          // Being created/negotiated
    PENDING,        // Awaiting signatures/approval
    ACTIVE,         // In effect and executable
    SUSPENDED,      // Temporarily paused
    EXECUTED,       // Conditions met, executed
    EXPIRED,        // Past expiration date
    CANCELLED,      // Cancelled by parties
    TERMINATED      // Terminated by breach or agreement
}

/**
 * Contract type classification.
 */
@Serializable
sealed class ContractType {
    @Serializable
    object Insurance : ContractType()
    
    @Serializable
    object Legal : ContractType()
    
    @Serializable
    object Financial : ContractType()
    
    @Serializable
    object ServiceLevelAgreement : ContractType()
    
    @Serializable
    object SupplyChain : ContractType()
    
    @Serializable
    data class Custom(val name: String, val domain: String) : ContractType()
}

/**
 * Execution model - how the contract executes.
 */
@Serializable
sealed class ExecutionModel {
    /**
     * Parametric execution - triggers based on external data.
     */
    @Serializable
    data class Parametric(
        val triggerType: TriggerType,
        val evaluationEngine: String, // e.g., "parametric-insurance", "weather-derivative"
        val engineVersion: String? = null, // Engine version for compatibility checks
        val engineHash: String? = null // Engine implementation hash for tamper detection
    ) : ExecutionModel()
    
    /**
     * Conditional execution - if/then logic.
     */
    @Serializable
    data class Conditional(
        val conditions: List<ContractCondition>,
        val evaluationEngine: String, // e.g., "rule-engine", "smart-contract-vm"
        val engineVersion: String? = null, // Engine version for compatibility checks
        val engineHash: String? = null // Engine implementation hash for tamper detection
    ) : ExecutionModel()
    
    /**
     * Time-based execution - scheduled actions.
     */
    @Serializable
    data class Scheduled(
        val schedule: ScheduleDefinition,
        val evaluationEngine: String,
        val engineVersion: String? = null, // Engine version for compatibility checks
        val engineHash: String? = null // Engine implementation hash for tamper detection
    ) : ExecutionModel()
    
    /**
     * Event-driven execution - responds to events.
     */
    @Serializable
    data class EventDriven(
        val eventTypes: List<String>,
        val evaluationEngine: String,
        val engineVersion: String? = null, // Engine version for compatibility checks
        val engineHash: String? = null // Engine implementation hash for tamper detection
    ) : ExecutionModel()
    
    /**
     * Manual execution - requires human intervention.
     */
    @Serializable
    object Manual : ExecutionModel()
}

/**
 * Trigger types for parametric execution.
 */
@Serializable
sealed class TriggerType {
    @Serializable
    object EarthObservation : TriggerType() // EO data triggers
    
    @Serializable
    object Weather : TriggerType()           // Weather data
    
    @Serializable
    object Financial : TriggerType()         // Market data
    
    @Serializable
    object IoT : TriggerType()               // IoT sensor data
    
    @Serializable
    object API : TriggerType()               // External API data
    
    @Serializable
    data class Custom(val name: String) : TriggerType()
}

/**
 * Contract condition.
 */
@Serializable
data class ContractCondition(
    val id: String,
    val description: String,
    val conditionType: ConditionType,
    val expression: String, // e.g., JSONPath, CEL, or domain-specific DSL
    val evaluationContext: JsonElement? = null
)

/**
 * Condition types.
 */
@Serializable
enum class ConditionType {
    THRESHOLD,      // Value exceeds threshold
    RANGE,          // Value within range
    COMPARISON,     // Compare two values
    TEMPORAL,       // Time-based condition
    COMPOSITE       // Multiple conditions combined
}

/**
 * Schedule definition for scheduled execution.
 */
@Serializable
data class ScheduleDefinition(
    val cronExpression: String? = null,
    val interval: String? = null, // ISO 8601 duration
    val timezone: String? = null
)

/**
 * Contract parties identified by DIDs.
 */
@Serializable
data class ContractParties(
    val primaryPartyDid: String,      // Main party (e.g., insurer, service provider)
    val counterpartyDid: String,      // Other party (e.g., insured, client)
    val additionalParties: Map<String, String> = emptyMap() // Role -> DID
)

/**
 * Contract terms.
 */
@Serializable
data class ContractTerms(
    val obligations: List<Obligation>,
    val conditions: List<ContractCondition>,
    val penalties: List<Penalty>? = null,
    val rewards: List<Reward>? = null,
    val jurisdiction: String? = null,
    val governingLaw: String? = null,
    val disputeResolution: DisputeResolution? = null
)

/**
 * Obligation.
 */
@Serializable
data class Obligation(
    val id: String,
    val partyDid: String,
    val description: String,
    val obligationType: ObligationType,
    val deadline: String? = null, // ISO 8601 timestamp
    val metadata: JsonElement? = null
)

/**
 * Obligation types.
 */
@Serializable
enum class ObligationType {
    PAYMENT,
    DELIVERY,
    PERFORMANCE,
    NOTIFICATION,
    CUSTOM
}

/**
 * Penalty.
 */
@Serializable
data class Penalty(
    val id: String,
    val description: String,
    val penaltyType: PenaltyType,
    val amount: MonetaryAmount? = null,
    val metadata: JsonElement? = null
)

/**
 * Penalty types.
 */
@Serializable
enum class PenaltyType {
    MONETARY,
    TERMINATION,
    SUSPENSION,
    CUSTOM
}

/**
 * Reward.
 */
@Serializable
data class Reward(
    val id: String,
    val description: String,
    val rewardType: RewardType,
    val amount: MonetaryAmount? = null,
    val metadata: JsonElement? = null
)

/**
 * Reward types.
 */
@Serializable
enum class RewardType {
    MONETARY,
    DISCOUNT,
    BONUS,
    CUSTOM
}

/**
 * Monetary amount.
 */
@Serializable
data class MonetaryAmount(
    val amount: Double,
    val currency: String = "USD"
)

/**
 * Dispute resolution.
 */
@Serializable
data class DisputeResolution(
    val method: String, // e.g., "arbitration", "mediation", "court"
    val jurisdiction: String? = null,
    val rules: String? = null // e.g., "UNCITRAL", "AAA"
)

/**
 * Generic SmartContract abstraction.
 * 
 * Represents an executable agreement between parties with:
 * - Verifiable identity (DIDs)
 * - Cryptographic proof (Verifiable Credentials)
 * - Immutable audit trail (Blockchain anchoring)
 * - Pluggable execution models
 */
@Serializable
data class SmartContract(
    val id: String,
    val contractNumber: String,
    val status: ContractStatus,
    val contractType: ContractType,
    val executionModel: ExecutionModel,
    val parties: ContractParties,
    val terms: ContractTerms,
    val effectiveDate: String, // ISO 8601 timestamp
    val expirationDate: String? = null, // ISO 8601 timestamp
    val createdAt: String,
    val updatedAt: String,
    
    // VeriCore integration
    val credentialId: String? = null, // Verifiable Credential ID
    val anchorRef: AnchorRefData? = null // Blockchain anchor reference
    
    // Contract-specific data (domain-specific)
    val contractData: JsonElement
)

