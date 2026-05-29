package org.trustweave.contract

import org.trustweave.anchor.AnchorRef
import org.trustweave.contract.models.*
import org.trustweave.core.json.JsonDataBuilder
import org.trustweave.core.json.jsonData
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.Result
import kotlinx.serialization.json.JsonElement

/**
 * Service for managing SmartContracts.
 * Integrates with TrustWeave for verifiable credentials and blockchain anchoring.
 */
interface SmartContractService {

    /**
     * Create a contract draft.
     */
    suspend fun createDraft(
        request: ContractDraftRequest
    ): Result<SmartContract>

    /**
     * Issue verifiable credential for the contract.
     */
    suspend fun issueContractCredential(
        contract: SmartContract,
        issuerDid: String,
        issuerKeyId: String
    ): Result<VerifiableCredential>

    /**
     * Anchor contract to blockchain for audit trail.
     */
    suspend fun anchorContract(
        contract: SmartContract,
        credential: VerifiableCredential,
        chainId: String = "algorand:mainnet"
    ): Result<AnchorRef>

    /**
     * Bind a contract (issue credential and anchor to blockchain).
     */
    suspend fun bindContract(
        contractId: String,
        issuerDid: String,
        issuerKeyId: String,
        chainId: String = "algorand:mainnet"
    ): Result<BoundContract>

    /**
     * Activate a contract (move from PENDING to ACTIVE).
     */
    suspend fun activateContract(
        contractId: String
    ): Result<SmartContract>

    /**
     * Execute contract based on execution model.
     */
    suspend fun executeContract(
        contract: SmartContract,
        executionContext: ExecutionContext
    ): Result<ExecutionResult>

    /**
     * Evaluate contract conditions.
     */
    suspend fun evaluateConditions(
        contract: SmartContract,
        inputData: JsonElement
    ): Result<ConditionEvaluation>

    /**
     * Update contract status.
     */
    suspend fun updateStatus(
        contractId: String,
        newStatus: ContractStatus,
        reason: String? = null,
        metadata: JsonElement? = null
    ): Result<SmartContract>

    /**
     * Get contract by ID.
     */
    suspend fun getContract(contractId: String): Result<SmartContract>

    /**
     * Verify contract credential.
     */
    suspend fun verifyContract(
        credentialId: String
    ): Result<Boolean>
}

// ─── Convenience extension functions ────────────────────────────────────────

/**
 * Convenience alias for [SmartContractService.createDraft].
 *
 * Allows callers to write `service.draft(request)` instead of
 * `service.createDraft(request)`.
 */
suspend fun SmartContractService.draft(
    request: ContractDraftRequest
): Result<SmartContract> = createDraft(request)

/**
 * DSL form of [SmartContractService.createDraft].
 *
 * ```kotlin
 * trustWeave.contracts.draft {
 *     contractType = ContractType.Insurance
 *     executionModel = ExecutionModel.Parametric
 *     parties = ContractParties(insurerDid, insuredDid)
 *     terms = contractTerms
 *     effectiveDate = "2026-05-28T00:00:00Z"
 *     contractData {
 *         "domain" to "flood"
 *         "thresholds" { "floodDepthCm" to 50.0 }
 *     }
 * }
 * ```
 */
suspend fun SmartContractService.draft(
    block: ContractDraftBuilder.() -> Unit
): Result<SmartContract> = createDraft(contractDraft(block))

/**
 * Convenience alias for [SmartContractService.issueContractCredential].
 *
 * Allows callers to write `service.issueCredential(...)` instead of
 * `service.issueContractCredential(...)`.
 */
suspend fun SmartContractService.issueCredential(
    contract: SmartContract,
    issuerDid: String,
    issuerKeyId: String
): Result<VerifiableCredential> = issueContractCredential(contract, issuerDid, issuerKeyId)

/**
 * Execute a contract with parametric trigger data supplied as key/value pairs.
 *
 * Shortcut for `executeContract(contract, parametricContext(*triggerData))` — lets
 * callers stay in plain Kotlin types instead of building a [JsonElement]:
 *
 * ```kotlin
 * trustWeave.contracts.executeParametric(
 *     activeContract,
 *     "floodDepthCm" to 75.0,
 *     "stationId" to "STA-42"
 * )
 * ```
 */
suspend fun SmartContractService.executeParametric(
    contract: SmartContract,
    vararg triggerData: Pair<String, Any?>
): Result<ExecutionResult> = executeContract(contract, parametricContext(*triggerData))

/**
 * Execute a contract with event data supplied as key/value pairs.
 *
 * Shortcut for `executeContract(contract, eventContext(*eventData))`.
 */
suspend fun SmartContractService.executeEvent(
    contract: SmartContract,
    vararg eventData: Pair<String, Any?>
): Result<ExecutionResult> = executeContract(contract, eventContext(*eventData))

/**
 * DSL form of [SmartContractService.executeContract].
 *
 * ```kotlin
 * trustWeave.contracts.executeContract(activeContract) {
 *     trigger {
 *         "floodDepthCm" to 75.0
 *         "credentialId" to floodCredential.id
 *     }
 * }
 * ```
 */
suspend fun SmartContractService.executeContract(
    contract: SmartContract,
    block: ExecutionContextBuilder.() -> Unit
): Result<ExecutionResult> = executeContract(contract, executionContext(block))

/**
 * DSL form of [SmartContractService.evaluateConditions].
 *
 * ```kotlin
 * trustWeave.contracts.evaluateConditions(contract) {
 *     "floodDepthCm" to 75.0
 * }
 * ```
 */
suspend fun SmartContractService.evaluateConditions(
    contract: SmartContract,
    block: JsonDataBuilder.() -> Unit
): Result<ConditionEvaluation> = evaluateConditions(contract, jsonData(block))

/**
 * Contract draft request.
 */
data class ContractDraftRequest(
    val contractType: ContractType,
    val executionModel: ExecutionModel,
    val parties: ContractParties,
    val terms: ContractTerms,
    val effectiveDate: String, // ISO 8601 timestamp
    val expirationDate: String? = null, // ISO 8601 timestamp
    val contractData: JsonElement
)

