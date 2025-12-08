package com.trustweave.contract

import com.trustweave.anchor.AnchorRef
import com.trustweave.contract.models.*
import com.trustweave.credential.model.vc.VerifiableCredential
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

