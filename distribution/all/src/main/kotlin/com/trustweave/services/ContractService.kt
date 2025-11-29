package com.trustweave.services

import com.trustweave.TrustWeaveContext
import com.trustweave.contract.*
import com.trustweave.contract.models.*
import com.trustweave.credential.CredentialService
import kotlin.Result
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.AnchorRef
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.serialization.json.JsonElement

/**
 * Service for SmartContract operations.
 *
 * Provides a clean, focused API for creating, binding, and executing smart contracts.
 *
 * **Example:**
 * ```kotlin
 * val TrustWeave = TrustWeave.create()
 * val contract = trustweave.contracts.draft(
 *     request = ContractDraftRequest(
 *         contractType = ContractType.Insurance,
 *         executionModel = ExecutionModel.Parametric(...),
 *         parties = ContractParties(...),
 *         terms = ContractTerms(...),
 *         effectiveDate = Instant.now().toString(),
 *         contractData = buildJsonObject { ... }
 *     )
 * )
 * ```
 */
class ContractService(
    private val context: TrustWeaveContext
) : SmartContractService {
    private val delegate: SmartContractService by lazy {
        // Get credential service from context (optional - null is allowed)
        val credentialService = context.credentialRegistry.get()

        // Pass blockchain registry for chain-specific client resolution
        DefaultSmartContractService(
            credentialService = credentialService,
            blockchainRegistry = context.blockchainRegistry
        )
    }

    /**
     * Creates a contract draft.
     */
    override suspend fun createDraft(
        request: ContractDraftRequest
    ): Result<SmartContract> {
        return delegate.createDraft(request)
    }

    /**
     * Issue verifiable credential for the contract.
     */
    override suspend fun issueContractCredential(
        contract: SmartContract,
        issuerDid: String,
        issuerKeyId: String
    ): Result<VerifiableCredential> {
        return delegate.issueContractCredential(contract, issuerDid, issuerKeyId)
    }

    /**
     * Anchor contract to blockchain for audit trail.
     */
    override suspend fun anchorContract(
        contract: SmartContract,
        credential: VerifiableCredential,
        chainId: String
    ): Result<AnchorRef> {
        return delegate.anchorContract(contract, credential, chainId)
    }

    /**
     * Binds a contract (issues credential and anchors to blockchain).
     */
    override suspend fun bindContract(
        contractId: String,
        issuerDid: String,
        issuerKeyId: String,
        chainId: String
    ): Result<BoundContract> {
        return delegate.bindContract(contractId, issuerDid, issuerKeyId, chainId)
    }

    /**
     * Activates a contract.
     */
    override suspend fun activateContract(
        contractId: String
    ): Result<SmartContract> {
        return delegate.activateContract(contractId)
    }

    /**
     * Executes a contract.
     */
    override suspend fun executeContract(
        contract: SmartContract,
        executionContext: ExecutionContext
    ): Result<ExecutionResult> {
        return delegate.executeContract(contract, executionContext)
    }

    /**
     * Evaluates contract conditions.
     */
    override suspend fun evaluateConditions(
        contract: SmartContract,
        inputData: JsonElement
    ): Result<ConditionEvaluation> {
        return delegate.evaluateConditions(contract, inputData)
    }

    /**
     * Updates contract status.
     */
    override suspend fun updateStatus(
        contractId: String,
        newStatus: ContractStatus,
        reason: String?,
        metadata: JsonElement?
    ): Result<SmartContract> {
        return delegate.updateStatus(contractId, newStatus, reason, metadata)
    }

    /**
     * Gets a contract by ID.
     */
    override suspend fun getContract(contractId: String): Result<SmartContract> {
        return delegate.getContract(contractId)
    }

    /**
     * Verifies a contract credential.
     */
    override suspend fun verifyContract(
        credentialId: String
    ): Result<Boolean> {
        return delegate.verifyContract(credentialId)
    }

    /**
     * Creates a contract draft (convenience method with simplified name).
     */
    suspend fun draft(
        request: ContractDraftRequest
    ): Result<SmartContract> = createDraft(request)

    /**
     * Issue verifiable credential for the contract (convenience method with simplified name).
     */
    suspend fun issueCredential(
        contract: SmartContract,
        issuerDid: String,
        issuerKeyId: String
    ): Result<VerifiableCredential> = issueContractCredential(contract, issuerDid, issuerKeyId)
}

