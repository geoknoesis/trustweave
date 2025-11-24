package com.trustweave.contract

import com.trustweave.anchor.AnchorRef
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.contract.evaluation.*
import com.trustweave.contract.models.*
import com.trustweave.core.trustweaveCatching
import kotlin.Result
import com.trustweave.core.exception.InvalidOperationException
import com.trustweave.core.exception.NotFoundException
import com.trustweave.core.util.ValidationResult
import com.trustweave.credential.CredentialService as CredentialServiceInterface
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Default in-memory implementation of SmartContractService.
 * 
 * This is a simple implementation suitable for testing and development.
 * Production implementations should use persistent storage.
 */
class DefaultSmartContractService(
    private val credentialService: CredentialServiceInterface? = null,
    private val blockchainRegistry: BlockchainAnchorRegistry? = null,
    private val engines: EvaluationEngines = EvaluationEngines()
) : SmartContractService {
    
    private val contracts = ConcurrentHashMap<String, SmartContract>()
    
    // JSON serializer for encoding execution model and terms
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    
    override suspend fun createDraft(
        request: ContractDraftRequest
    ): Result<SmartContract> = trustweaveCatching {
        // Validate request
        val validation = ContractValidator.validateDraftRequest(request)
        if (!validation.isValid()) {
            throw InvalidOperationException(
                validation.errorMessage() ?: "Invalid contract draft request"
            )
        }
        
        val contractId = UUID.randomUUID().toString()
        val contractNumber = "CONTRACT-${Instant.now().toEpochMilli()}"
        val now = Instant.now().toString()
        
        val contract = SmartContract(
            id = contractId,
            contractNumber = contractNumber,
            status = ContractStatus.DRAFT,
            contractType = request.contractType,
            executionModel = request.executionModel,
            parties = request.parties,
            terms = request.terms,
            effectiveDate = request.effectiveDate,
            expirationDate = request.expirationDate,
            createdAt = now,
            updatedAt = now,
            credentialId = null,
            anchorRef = null,
            contractData = request.contractData
        )
        
        contracts[contractId] = contract
        contract
    }
    
    override suspend fun issueContractCredential(
        contract: SmartContract,
        issuerDid: String,
        issuerKeyId: String
    ): Result<VerifiableCredential> = trustweaveCatching {
        requireNotNull(credentialService) {
            "CredentialService is required for issuing contract credentials"
        }
        
        // Extract and capture engine hash if engine is registered
        val executionModelWithHash = contract.executionModel.withEngineHash(engines)
        
        val credentialSubject = buildJsonObject {
            put("id", contract.id)
            put("contractNumber", contract.contractNumber)
            put("contractType", contract.contractType.toString())
            put("status", contract.status.name)
            put("parties", buildJsonObject {
                put("primaryPartyDid", contract.parties.primaryPartyDid)
                put("counterpartyDid", contract.parties.counterpartyDid)
                contract.parties.additionalParties.forEach { (role, did) ->
                    put(role, did)
                }
            })
            put("effectiveDate", contract.effectiveDate)
            put("expirationDate", contract.expirationDate ?: "")
            put("contractData", contract.contractData)
            // Include execution model and terms in credential for tamper protection
            put("executionModel", json.encodeToJsonElement(ExecutionModel.serializer(), executionModelWithHash))
            put("terms", json.encodeToJsonElement(ContractTerms.serializer(), contract.terms))
        }
        
        val credential = VerifiableCredential(
            id = "urn:uuid:${java.util.UUID.randomUUID()}",
            type = listOf("VerifiableCredential", "SmartContractCredential"),
            issuer = issuerDid,
            credentialSubject = credentialSubject,
            issuanceDate = java.time.Instant.now().toString()
        )
        
        credentialService.issueCredential(
            credential = credential,
            options = com.trustweave.credential.CredentialIssuanceOptions(
                proofType = "Ed25519Signature2020",
                keyId = issuerKeyId
            )
        )
    }
    
    override suspend fun anchorContract(
        contract: SmartContract,
        credential: VerifiableCredential,
        chainId: String
    ): Result<AnchorRef> = trustweaveCatching {
        val blockchainClient = blockchainRegistry?.get(chainId)
            ?: throw IllegalStateException("No blockchain client available for chain: $chainId")
        
        val payload = buildJsonObject {
            put("contractId", contract.id)
            put("credentialId", credential.id ?: throw IllegalStateException(
                "Credential must have an ID after issuance"
            ))
            put("contractNumber", contract.contractNumber)
            put("status", contract.status.name)
        }
        
        val anchorResult = blockchainClient.writePayload(payload)
        anchorResult.ref
    }
    
    override suspend fun bindContract(
        contractId: String,
        issuerDid: String,
        issuerKeyId: String,
        chainId: String
    ): Result<BoundContract> = trustweaveCatching {
        val contract = contracts[contractId]
            ?: throw NotFoundException("Contract not found: $contractId")
        
        // Issue credential
        val credential = issueContractCredential(contract, issuerDid, issuerKeyId).getOrThrow()
        
        // Anchor to blockchain
        val anchorRef = anchorContract(contract, credential, chainId).getOrThrow()
        
        // Update contract with credential and anchor
        val credentialId = credential.id ?: throw IllegalStateException(
            "Credential must have an ID after issuance"
        )
        
        val updatedContract = updateContract(
            contract.copy(
                credentialId = credentialId,
                anchorRef = AnchorRefData.fromAnchorRef(anchorRef),
                status = ContractStatus.PENDING,
                updatedAt = Instant.now().toString()
            )
        ).getOrThrow()
        
        BoundContract(
            contract = updatedContract,
            credentialId = credentialId,
            anchorRef = AnchorRefData.fromAnchorRef(anchorRef)
        )
    }
    
    override suspend fun activateContract(
        contractId: String
    ): Result<SmartContract> = trustweaveCatching {
        val contract = contracts[contractId]
            ?: throw NotFoundException("Contract not found: $contractId")
        
        // Validate state transition
        val transitionValidation = ContractValidator.validateStateTransition(
            contract.status,
            ContractStatus.ACTIVE
        )
        if (!transitionValidation.isValid()) {
            throw InvalidOperationException(
                transitionValidation.errorMessage() ?: "Invalid state transition"
            )
        }
        
        // Check if contract is expired
        if (ContractValidator.isExpired(contract)) {
            throw InvalidOperationException(
                "Cannot activate expired contract"
            )
        }
        
        val updatedContract = updateContract(
            contract.copy(
                status = ContractStatus.ACTIVE,
                updatedAt = Instant.now().toString()
            )
        ).getOrThrow()
        
        updatedContract
    }
    
    override suspend fun executeContract(
        contract: SmartContract,
        executionContext: ExecutionContext
    ): Result<ExecutionResult> = trustweaveCatching {
        // Validate contract is active
        if (contract.status != ContractStatus.ACTIVE) {
            throw InvalidOperationException(
                "Contract must be in ACTIVE status to execute. Current status: ${contract.status}"
            )
        }
        
        // Check if contract is expired
        if (ContractValidator.isExpired(contract)) {
            // Auto-expire contract
            updateStatus(contract.id, ContractStatus.EXPIRED, "Contract expired").getOrThrow()
            throw InvalidOperationException(
                "Cannot execute expired contract"
            )
        }
        
        // Determine execution type based on execution model
        val executionType = when (contract.executionModel) {
            is ExecutionModel.Parametric -> ExecutionType.PARAMETRIC_TRIGGER
            is ExecutionModel.Conditional -> ExecutionType.CONDITIONAL_EVALUATION
            is ExecutionModel.Scheduled -> ExecutionType.SCHEDULED_ACTION
            is ExecutionModel.EventDriven -> ExecutionType.EVENT_RESPONSE
            is ExecutionModel.Manual -> ExecutionType.MANUAL_ACTION
        }
        
        // Evaluate conditions
        val conditionEvaluation = evaluateConditions(contract, executionContext.triggerData ?: buildJsonObject {}).getOrThrow()
        
        // Determine if contract should be executed
        val executed = conditionEvaluation.overallResult
        
        val outcomes = if (executed) {
            // Generate outcomes based on contract terms
            contract.terms.obligations.map { obligation ->
                ContractOutcome(
                    type = OutcomeType.STATUS_CHANGE,
                    description = "Obligation triggered: ${obligation.description}",
                    obligationTriggered = obligation.id,
                    metadata = obligation.metadata
                )
            }
        } else {
            emptyList()
        }
        
        // Update contract status if executed
        if (executed) {
            updateStatus(contract.id, ContractStatus.EXECUTED, "Contract conditions met").getOrThrow()
        }
        
        ExecutionResult(
            contractId = contract.id,
            executed = executed,
            executionType = executionType,
            outcomes = outcomes,
            evidence = executionContext.triggerData?.let { listOf() }, // Would contain VC IDs in real implementation
            timestamp = Instant.now().toString()
        )
    }
    
    override suspend fun evaluateConditions(
        contract: SmartContract,
        inputData: JsonElement
    ): Result<ConditionEvaluation> = trustweaveCatching {
        // 1. Extract engine reference from execution model
        val engineRef = contract.executionModel.toEngineReference()
        
        // 2. Handle manual execution
        if (engineRef is EngineReference.Manual) {
            throw IllegalStateException(
                "Manual execution does not require condition evaluation"
            )
        }
        
        // 3. Get engine (throws if not registered)
        val engineId = (engineRef as EngineReference.WithEngine).engineId
        val engine = engines.require(engineId)
        
        // 4. Verify engine integrity (tamper detection)
        engineRef.expectedHash?.let { expectedHash ->
            engines.verifyOrThrow(engineId, expectedHash)
        }
        
        // 5. Verify engine version compatibility (if specified)
        engineRef.expectedVersion?.let { expectedVersion ->
            require(expectedVersion.isNotBlank()) { "Expected version cannot be blank" }
            if (engine.version != expectedVersion) {
                throw IllegalStateException(
                    "Evaluation engine version mismatch. " +
                    "Expected: $expectedVersion, " +
                    "Actual: ${engine.version}"
                )
            }
        }
        
        // 6. Verify condition types are supported
        val unsupportedConditions = contract.terms.conditions.filter { condition ->
            condition.conditionType !in engine.supportedConditionTypes
        }
        if (unsupportedConditions.isNotEmpty()) {
            throw IllegalStateException(
                "Engine '$engineId' does not support condition types: " +
                unsupportedConditions.map { it.conditionType.name }.joinToString()
            )
        }
        
        // 7. Create evaluation context
        val context = EvaluationContext(
            contractId = contract.id,
            executionModel = contract.executionModel,
            contractData = contract.contractData
        )
        
        // 8. Evaluate conditions using the engine
        val conditionResults = contract.terms.conditions.map { condition ->
            condition.evaluateWith(engine, inputData, context)
        }
        
        val overallResult = conditionResults.all { it.satisfied && it.error == null }
        
        ConditionEvaluation(
            contractId = contract.id,
            conditions = conditionResults,
            overallResult = overallResult,
            timestamp = Instant.now().toString()
        )
    }
    
    
    override suspend fun updateStatus(
        contractId: String,
        newStatus: ContractStatus,
        reason: String?,
        metadata: JsonElement?
    ): Result<SmartContract> = trustweaveCatching {
        val contract = contracts[contractId]
            ?: throw NotFoundException("Contract not found: $contractId")
        
        // Validate state transition
        val transitionValidation = ContractValidator.validateStateTransition(
            contract.status,
            newStatus
        )
        if (!transitionValidation.isValid()) {
            throw InvalidOperationException(
                transitionValidation.errorMessage() ?: "Invalid state transition"
            )
        }
        
        val updatedContract = updateContract(
            contract.copy(
                status = newStatus,
                updatedAt = Instant.now().toString()
            )
        ).getOrThrow()
        
        updatedContract
    }
    
    override suspend fun getContract(contractId: String): Result<SmartContract> = trustweaveCatching {
        contracts[contractId]
            ?: throw NotFoundException("Contract not found: $contractId")
    }
    
    override suspend fun verifyContract(
        credentialId: String
    ): Result<Boolean> = trustweaveCatching {
        requireNotNull(credentialService) {
            "CredentialService is required for contract verification"
        }
        
        // Find contract by credential ID
        val contract = contracts.values.firstOrNull { it.credentialId == credentialId }
            ?: throw NotFoundException(
                "Contract not found for credential ID: $credentialId"
            )
        
        // Note: To fully verify, we would need to retrieve the credential
        // This requires credential storage which is not available in this service
        // For now, we verify the contract exists and has a credential ID
        // Full verification should be done via CredentialService.verify()
        contract.credentialId != null
    }
    
    private suspend fun updateContract(contract: SmartContract): Result<SmartContract> = trustweaveCatching {
        // Use compute for atomic update
        contracts.compute(contract.id) { _, _ -> contract }
        contract
    }
}

