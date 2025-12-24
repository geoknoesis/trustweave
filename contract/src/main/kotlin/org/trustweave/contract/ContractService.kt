package org.trustweave.contract

import org.trustweave.contract.models.*
import org.trustweave.credential.CredentialService
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.Result

/**
 * Service for SmartContract operations.
 *
 * Provides a clean, focused API for creating, binding, and executing smart contracts.
 * This is a convenience wrapper around DefaultSmartContractService that integrates
 * with TrustWeave's registries.
 *
 * **Example:**
 * ```kotlin
 * val contractService = ContractService(
 *     credentialRegistry = credentialRegistry,
 *     blockchainRegistry = blockchainRegistry
 * )
 * val contract = contractService.draft(
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
    credentialService: CredentialService? = null,
    blockchainRegistry: BlockchainAnchorRegistry? = null
) : SmartContractService {
    private val delegate: SmartContractService by lazy {
        // Create DefaultSmartContractService with the service
        DefaultSmartContractService(
            credentialService = credentialService,
            blockchainRegistry = blockchainRegistry
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
    ): Result<org.trustweave.anchor.AnchorRef> {
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
        inputData: kotlinx.serialization.json.JsonElement
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
        metadata: kotlinx.serialization.json.JsonElement?
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


