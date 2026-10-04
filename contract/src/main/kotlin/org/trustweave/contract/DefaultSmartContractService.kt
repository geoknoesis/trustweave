package org.trustweave.contract

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.contract.evaluation.EngineReference
import org.trustweave.contract.evaluation.EvaluationContext
import org.trustweave.contract.evaluation.EvaluationEngines
import org.trustweave.contract.evaluation.evaluateWith
import org.trustweave.contract.evaluation.require
import org.trustweave.contract.evaluation.toEngineReference
import org.trustweave.contract.evaluation.verifyOrThrow
import org.trustweave.contract.evaluation.withEngineHash
import org.trustweave.contract.models.AnchorRefData
import org.trustweave.contract.models.BoundContract
import org.trustweave.contract.models.ConditionEvaluation
import org.trustweave.contract.models.ContractOutcome
import org.trustweave.contract.models.ContractStatus
import org.trustweave.contract.models.ExecutionContext
import org.trustweave.contract.models.ExecutionModel
import org.trustweave.contract.models.ExecutionResult
import org.trustweave.contract.models.ExecutionType
import org.trustweave.contract.models.OutcomeType
import org.trustweave.contract.models.SmartContract
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.util.trustweaveCatching
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.results.VerificationResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.Result
import org.trustweave.credential.CredentialService as CredentialServiceInterface

/**
 * Looks up the verifiable credential that was issued for a contract.
 *
 * [DefaultSmartContractService] remembers the credentials it issued itself through
 * [SmartContractService.bindContract]; a resolver is only needed to verify contracts whose
 * credential was issued elsewhere (or by a previous process). Return `null` when the credential
 * is unknown; [DefaultSmartContractService.verifyContract] then fails rather than reporting
 * the contract as verified.
 */
fun interface ContractCredentialResolver {
    suspend fun resolve(credentialId: String): VerifiableCredential?
}

/**
 * Decides whether [issuerDid] may vouch for [contract] in [DefaultSmartContractService.verifyContract].
 *
 * Consulted in addition to the built-in rules (the issuer is a party to the contract, or it is the
 * issuer DID this service itself used when it issued the credential).
 */
fun interface TrustedIssuerPolicy {
    suspend fun isTrusted(
        issuerDid: String,
        contract: SmartContract,
    ): Boolean
}

/** One recorded status change of a contract, including the optional reason and metadata. */
data class ContractStatusChange(
    val from: ContractStatus,
    val to: ContractStatus,
    val reason: String?,
    val metadata: JsonElement?,
    val at: String,
)

/**
 * Capacity limits of [DefaultSmartContractService]'s in-memory stores. Reaching [maxContracts] makes
 * [DefaultSmartContractService.createDraft] fail loudly (nothing is evicted); the status history of
 * one contract keeps only its most recent [maxStatusHistoryPerContract] changes.
 */
data class ContractStoreLimits(
    val maxContracts: Int = 100_000,
    val maxStatusHistoryPerContract: Int = 1_000,
) {
    init {
        require(maxContracts > 0) { "maxContracts must be positive" }
        require(maxStatusHistoryPerContract > 0) { "maxStatusHistoryPerContract must be positive" }
    }
}

/**
 * Default in-memory implementation of SmartContractService.
 *
 * This is a simple implementation suitable for testing and development.
 * Production implementations should use persistent storage.
 *
 * All state changes of one contract are serialised by a per-contract [Mutex] and written with a
 * compare-and-set against the record that was read, so two concurrent executions of the same
 * ACTIVE contract cannot both execute it. A slow credential issuance, anchor write or engine
 * evaluation therefore only delays the one contract it belongs to, never an unrelated one; the
 * mutex of a contract exists only while an operation on it is running or waiting.
 *
 * **Lifecycle rules.**
 * - A contract can only become ACTIVE (through [activateContract] or [updateStatus]) once
 *   [bindContract] bound a credential to it, and, when a blockchain registry is configured, an anchor.
 * - [updateStatus] refuses [ContractStatus.EXECUTED]: a contract is executed only by
 *   [executeContract], which evaluates its conditions first. A [ExecutionModel.Manual] contract has
 *   no engine; it executes when its terms contain no conditions (conditions cannot be evaluated
 *   automatically, so such a contract is refused rather than executed unchecked).
 * - Re-binding replaces the previous credential and drops everything this service kept for it.
 *
 * @param credentialResolver optional lookup for contract credentials that this instance did not
 *   issue itself; used by [verifyContract].
 * @param trustedIssuerPolicy optional extra policy deciding which issuers may vouch for a contract
 *   in [verifyContract]. Without it, the credential issuer must be a party to the contract or
 *   the issuer DID this service itself used when issuing the credential.
 * @param verifyAnchorOnVerify when true, [verifyContract] also checks that the anchored payload
 *   attests to this contract and credential (default false). Fails closed: a contract with no anchor,
 *   or whose anchor's chain has no client in the registry, does not verify.
 * @param limits capacity limits of the in-memory stores, see [ContractStoreLimits].
 */
class DefaultSmartContractService
    @JvmOverloads
    constructor(
        private val credentialService: CredentialServiceInterface? = null,
        private val blockchainRegistry: BlockchainAnchorRegistry? = null,
        private val engines: EvaluationEngines = EvaluationEngines(),
        private val credentialResolver: ContractCredentialResolver? = null,
        private val trustedIssuerPolicy: TrustedIssuerPolicy? = null,
        private val verifyAnchorOnVerify: Boolean = false,
        private val limits: ContractStoreLimits = ContractStoreLimits(),
    ) : SmartContractService {
        private val contracts = ConcurrentHashMap<String, SmartContract>()

        /** Credentials issued by [bindContract], keyed by credential ID, for [verifyContract]. */
        private val issuedCredentials = ConcurrentHashMap<String, VerifiableCredential>()

        /** Secondary index: credential ID to contract ID, kept in step with [ContractStatus]-binding writes. */
        private val contractIdByCredentialId = ConcurrentHashMap<String, String>()

        /** DID the service used as issuer when it issued the credential, by credential ID. */
        private val issuerDidByCredentialId = ConcurrentHashMap<String, String>()

        /** Payload anchored for a credential, for optional anchor re-verification. */
        private val anchoredPayloads = ConcurrentHashMap<String, JsonObject>()

        private val statusHistories = ConcurrentHashMap<String, CopyOnWriteArrayList<ContractStatusChange>>()

        private class LockEntry {
            val mutex = Mutex()
            var holders = 0 // only touched inside ConcurrentHashMap.compute, i.e. atomically
        }

        /** One mutex per contract with an operation in flight; entries are removed when the last one finishes. */
        private val contractLocks = ConcurrentHashMap<String, LockEntry>()

        private val lastContractNumber = AtomicLong(0)

        private suspend fun <T> withContractLock(
            contractId: String,
            block: suspend () -> T,
        ): T {
            val entry = contractLocks.compute(contractId) { _, existing -> (existing ?: LockEntry()).also { it.holders++ } }!!
            try {
                return entry.mutex.withLock { block() }
            } finally {
                contractLocks.compute(contractId) { _, existing ->
                    existing?.let { it.holders-- }
                    existing?.takeIf { it.holders > 0 }
                }
            }
        }

        /** Number of contracts with a lock entry, for tests: must return to 0 when the service is idle. */
        internal fun activeLockCount(): Int = contractLocks.size

        /** Sizes of the per-credential caches, for tests. */
        internal fun cacheSizes(): Map<String, Int> =
            mapOf(
                "issuedCredentials" to issuedCredentials.size,
                "issuerDidByCredentialId" to issuerDidByCredentialId.size,
                "anchoredPayloads" to anchoredPayloads.size,
                "contractIdByCredentialId" to contractIdByCredentialId.size,
            )

        /** Collision-free within this instance: strictly increasing millisecond counter plus a random suffix. */
        private fun nextContractNumber(nowMillis: Long): String {
            val n = lastContractNumber.updateAndGet { prev -> maxOf(prev + 1, nowMillis) }
            return "CONTRACT-$n-${UUID.randomUUID().toString().take(8)}"
        }

        /** Recorded status changes (including reason and metadata) for [contractId], oldest first. */
        fun statusHistory(contractId: String): List<ContractStatusChange> = statusHistories[contractId]?.toList() ?: emptyList()

        private fun storedContract(contractId: String): SmartContract =
            contracts[contractId] ?: throw TrustWeaveException.NotFound(resource = "Contract: $contractId")

        // JSON serializer for encoding execution model and terms
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        override suspend fun createDraft(request: ContractDraftRequest): Result<SmartContract> =
            trustweaveCatching {
                // Validate request
                val validation = ContractValidator.validateDraftRequest(request)
                if (!validation.isValid()) {
                    throw TrustWeaveException.InvalidOperation(
                        message = validation.errorMessage() ?: "Invalid contract draft request",
                    )
                }

                if (contracts.size >= limits.maxContracts) {
                    throw TrustWeaveException.InvalidOperation(
                        message = "Contract store is full (${limits.maxContracts} contracts); nothing was created",
                    )
                }

                val contractId = UUID.randomUUID().toString()
                val now = Clock.System.now()
                val contractNumber = nextContractNumber(now.toEpochMilliseconds())
                val nowStr = now.toString()

                val contract =
                    SmartContract(
                        id = contractId,
                        contractNumber = contractNumber,
                        status = ContractStatus.DRAFT,
                        contractType = request.contractType,
                        executionModel = request.executionModel,
                        parties = request.parties,
                        terms = request.terms,
                        effectiveDate = request.effectiveDate,
                        expirationDate = request.expirationDate,
                        createdAt = nowStr,
                        updatedAt = nowStr,
                        credentialId = null,
                        anchorRef = null,
                        contractData = request.contractData,
                    )

                contracts[contractId] = contract
                contract
            }

        override suspend fun issueContractCredential(
            contract: SmartContract,
            issuerDid: String,
            issuerKeyId: String,
        ): Result<VerifiableCredential> =
            trustweaveCatching {
                requireNotNull(credentialService) {
                    "CredentialService is required for issuing contract credentials"
                }

                // Extract and capture engine hash if engine is registered
                val executionModelWithHash = contract.executionModel.withEngineHash(engines)

                val claims =
                    buildJsonObject {
                        put("contractNumber", contract.contractNumber)
                        put("contractType", contract.contractType.toString())
                        put("status", contract.status.name)
                        put(
                            "parties",
                            buildJsonObject {
                                put("primaryPartyDid", contract.parties.primaryPartyDid)
                                put("counterpartyDid", contract.parties.counterpartyDid)
                                contract.parties.additionalParties.forEach { (role, did) ->
                                    put(role, did)
                                }
                            },
                        )
                        put("effectiveDate", contract.effectiveDate)
                        put("expirationDate", contract.expirationDate ?: "")
                        put("contractData", contract.contractData)
                        // Include execution model and terms in credential for tamper protection
                        put("executionModel", json.encodeToJsonElement(executionModelWithHash))
                        put("terms", json.encodeToJsonElement(contract.terms))
                    }

                val credentialSubject =
                    CredentialSubject.fromIri(
                        iri = Iri(contract.id),
                        claims = claims,
                    )

                val issuanceRequest =
                    IssuanceRequest(
                        format = ProofSuiteId.VC_LD,
                        issuer = Issuer.from(Iri(issuerDid)),
                        issuerKeyId =
                            issuerKeyId?.let {
                                org.trustweave.did.identifiers.VerificationMethodId
                                    .parse(
                                        it,
                                        org.trustweave.did.identifiers
                                            .Did(issuerDid),
                                    )
                            },
                        credentialSubject = credentialSubject,
                        type =
                            listOf(
                                CredentialType.fromString("VerifiableCredential"),
                                CredentialType.fromString("SmartContractCredential"),
                            ),
                        id = CredentialId("urn:uuid:${UUID.randomUUID()}"),
                        issuedAt = Clock.System.now(),
                    )

                val issuanceResult = credentialService.issue(issuanceRequest)
                when (issuanceResult) {
                    is org.trustweave.credential.results.IssuanceResult.Success -> issuanceResult.credential
                    is org.trustweave.credential.results.IssuanceResult.Failure -> throw TrustWeaveException(
                        code = "CREDENTIAL_ISSUANCE_FAILED",
                        message = "Failed to issue credential: $issuanceResult",
                    )
                }
            }

        override suspend fun anchorContract(
            contract: SmartContract,
            credential: VerifiableCredential,
            chainId: String,
        ): Result<AnchorRef> =
            trustweaveCatching {
                val blockchainClient =
                    blockchainRegistry?.get(chainId)
                        ?: throw IllegalStateException("No blockchain client available for chain: $chainId")

                val payload =
                    buildJsonObject {
                        put("contractId", contract.id)
                        put(
                            "credentialId",
                            credential.id?.value ?: throw IllegalStateException(
                                "Credential must have an ID after issuance",
                            ),
                        )
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
            chainId: String,
        ): Result<BoundContract> =
            trustweaveCatching {
                withContractLock(contractId) { bindLocked(contractId, issuerDid, issuerKeyId, chainId) }
            }

        private suspend fun bindLocked(
            contractId: String,
            issuerDid: String,
            issuerKeyId: String,
            chainId: String,
        ): BoundContract {
            val contract = storedContract(contractId)

            // Binding is only legal while the contract is still being negotiated: DRAFT, or PENDING
            // (re-binding before activation). Never from ACTIVE/EXECUTED/TERMINATED/... , which would
            // otherwise reset the lifecycle and allow re-execution.
            if (contract.status != ContractStatus.PENDING) {
                val transition = ContractValidator.validateStateTransition(contract.status, ContractStatus.PENDING)
                if (!transition.isValid()) {
                    throw TrustWeaveException.InvalidOperation(
                        message =
                            "Cannot bind contract in status ${contract.status}: " +
                                (transition.errorMessage() ?: "invalid state transition"),
                    )
                }
            }

            // Issue credential
            val credential = issueContractCredential(contract, issuerDid, issuerKeyId).getOrThrow()

            // Anchor to blockchain
            val anchorRef = anchorContract(contract, credential, chainId).getOrThrow()

            // Update contract with credential and anchor
            val credentialId =
                credential.id?.value ?: throw IllegalStateException(
                    "Credential must have an ID after issuance",
                )

            val previousCredentialId = contract.credentialId
            // Register the new credential first and roll back if the compare-and-set loses, so a failed
            // bind leaves nothing behind. verifyContract only trusts an index entry whose contract record
            // names the same credential, so the window before the CAS is invisible to it.
            issuedCredentials[credentialId] = credential
            issuerDidByCredentialId[credentialId] = issuerDid
            contractIdByCredentialId[credentialId] = contractId
            anchoredPayloads[credentialId] = anchoredPayloadFor(contract, credentialId)

            val updatedContract =
                try {
                    replaceContract(
                        contract,
                        contract.copy(
                            credentialId = credentialId,
                            anchorRef = AnchorRefData.fromAnchorRef(anchorRef),
                            status = ContractStatus.PENDING,
                            updatedAt = Clock.System.now().toString(),
                        ),
                    )
                } catch (e: Throwable) {
                    forgetCredential(credentialId, contractId)
                    throw e
                }
            // The previous binding is superseded: drop everything kept for it.
            if (previousCredentialId != null && previousCredentialId != credentialId) {
                forgetCredential(previousCredentialId, contractId)
            }
            recordStatus(contractId, contract.status, ContractStatus.PENDING, "bound", null)

            return BoundContract(
                contract = updatedContract,
                credentialId = credentialId,
                anchorRef = AnchorRefData.fromAnchorRef(anchorRef),
            )
        }

        private fun forgetCredential(
            credentialId: String,
            contractId: String,
        ) {
            issuedCredentials.remove(credentialId)
            issuerDidByCredentialId.remove(credentialId)
            anchoredPayloads.remove(credentialId)
            contractIdByCredentialId.remove(credentialId, contractId)
        }

        private fun anchoredPayloadFor(
            contract: SmartContract,
            credentialId: String,
        ): JsonObject =
            buildJsonObject {
                put("contractId", contract.id)
                put("credentialId", credentialId)
                put("contractNumber", contract.contractNumber)
                put("status", contract.status.name)
            }

        private fun recordStatus(
            contractId: String,
            from: ContractStatus,
            to: ContractStatus,
            reason: String?,
            metadata: JsonElement?,
        ) {
            val history = statusHistories.computeIfAbsent(contractId) { CopyOnWriteArrayList() }
            history.add(ContractStatusChange(from, to, reason, metadata, Clock.System.now().toString()))
            // Keep only the most recent changes (callers hold the contract's lock, so this cannot interleave).
            while (history.size > limits.maxStatusHistoryPerContract) history.removeAt(0)
        }

        override suspend fun activateContract(contractId: String): Result<SmartContract> =
            trustweaveCatching {
                withContractLock(contractId) { activateLocked(contractId) }
            }

        private fun activateLocked(contractId: String): SmartContract {
            val contract = storedContract(contractId)

            // Validate state transition
            val transitionValidation =
                ContractValidator.validateStateTransition(
                    contract.status,
                    ContractStatus.ACTIVE,
                )
            if (!transitionValidation.isValid()) {
                throw TrustWeaveException.InvalidOperation(
                    message = transitionValidation.errorMessage() ?: "Invalid state transition",
                )
            }

            // Check if contract is expired
            if (ContractValidator.isExpired(contract)) {
                throw TrustWeaveException.InvalidOperation(
                    message = "Cannot activate expired contract",
                )
            }

            requireBoundForActivation(contract)

            return replaceContract(
                contract,
                contract.copy(
                    status = ContractStatus.ACTIVE,
                    updatedAt = Clock.System.now().toString(),
                ),
            ).also { recordStatus(contractId, contract.status, ContractStatus.ACTIVE, null, null) }
        }

        /** A contract may only be ACTIVE once a credential (and, with an anchor layer configured, an anchor) is bound. */
        private fun requireBoundForActivation(contract: SmartContract) {
            if (contract.credentialId == null) {
                throw TrustWeaveException.InvalidOperation(
                    message = "Cannot activate contract ${contract.id}: no credential is bound to it. Call bindContract first.",
                )
            }
            if (blockchainRegistry != null && contract.anchorRef == null) {
                throw TrustWeaveException.InvalidOperation(
                    message = "Cannot activate contract ${contract.id}: it is not anchored. Call bindContract first.",
                )
            }
        }

        override suspend fun executeContract(
            contract: SmartContract,
            executionContext: ExecutionContext,
        ): Result<ExecutionResult> =
            trustweaveCatching {
                // The caller's object is only used to identify the contract: status, terms and execution
                // model all come from the stored record, under the contract's lock, so a stale or edited
                // snapshot cannot be executed and two concurrent calls cannot both execute it.
                withContractLock(contract.id) { executeLocked(storedContract(contract.id), executionContext) }
            }

        private suspend fun executeLocked(
            contract: SmartContract,
            executionContext: ExecutionContext,
        ): ExecutionResult {
            // Validate contract is active
            if (contract.status != ContractStatus.ACTIVE) {
                throw TrustWeaveException.InvalidOperation(
                    message = "Contract must be in ACTIVE status to execute. Current status: ${contract.status}",
                )
            }

            // Check if contract is expired
            if (ContractValidator.isExpired(contract)) {
                // Auto-expire contract
                transitionLocked(contract.id, ContractStatus.EXPIRED, "expired at execution time", null)
                throw TrustWeaveException.InvalidOperation(
                    message = "Cannot execute expired contract",
                )
            }

            // Determine execution type based on execution model
            val executionType =
                when (contract.executionModel) {
                    is ExecutionModel.Parametric -> ExecutionType.PARAMETRIC_TRIGGER
                    is ExecutionModel.Conditional -> ExecutionType.CONDITIONAL_EVALUATION
                    is ExecutionModel.Scheduled -> ExecutionType.SCHEDULED_ACTION
                    is ExecutionModel.EventDriven -> ExecutionType.EVENT_RESPONSE
                    is ExecutionModel.Manual -> ExecutionType.MANUAL_ACTION
                }

            // Evaluate conditions
            val conditionEvaluation =
                if (contract.executionModel is ExecutionModel.Manual) {
                    manualEvaluation(contract)
                } else {
                    evaluateConditions(contract, executionContext.triggerData ?: buildJsonObject {}).getOrThrow()
                }

            // Determine if contract should be executed
            val executed = conditionEvaluation.overallResult

            val outcomes =
                if (executed) {
                    // Generate outcomes based on contract terms
                    contract.terms.obligations.map { obligation ->
                        ContractOutcome(
                            type = OutcomeType.STATUS_CHANGE,
                            description = "Obligation triggered: ${obligation.description}",
                            obligationTriggered = obligation.id,
                            metadata = obligation.metadata,
                        )
                    }
                } else {
                    emptyList()
                }

            // Update contract status if executed (the only path to EXECUTED: updateStatus refuses it)
            if (executed) {
                transitionLocked(contract.id, ContractStatus.EXECUTED, viaExecution = true)
            }

            // Evidence is the credential that binds the executed terms; null when the contract was
            // never bound to one.
            val evidence = listOfNotNull(contract.credentialId).takeIf { it.isNotEmpty() }

            return ExecutionResult(
                contractId = contract.id,
                executed = executed,
                executionType = executionType,
                outcomes = outcomes,
                evidence = evidence,
                timestamp = Clock.System.now().toString(),
            )
        }

        /** A manual contract has no engine: it can only be executed when there is nothing to evaluate. */
        private fun manualEvaluation(contract: SmartContract): ConditionEvaluation {
            if (contract.terms.conditions.isNotEmpty()) {
                throw TrustWeaveException.InvalidOperation(
                    message =
                        "Manual contract ${contract.id} has ${contract.terms.conditions.size} condition(s) that cannot be " +
                            "evaluated automatically; use an execution model with an evaluation engine",
                )
            }
            return ConditionEvaluation(
                contractId = contract.id,
                conditions = emptyList(),
                overallResult = true,
                timestamp = Clock.System.now().toString(),
            )
        }

        override suspend fun evaluateConditions(
            contract: SmartContract,
            inputData: JsonElement,
        ): Result<ConditionEvaluation> =
            trustweaveCatching {
                // 1. Extract engine reference from execution model
                val engineRef = contract.executionModel.toEngineReference()

                // 2. Handle manual execution
                if (engineRef is EngineReference.Manual) {
                    throw IllegalStateException(
                        "Manual execution does not require condition evaluation",
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
                                "Actual: ${engine.version}",
                        )
                    }
                }

                // 6. Verify condition types are supported
                val unsupportedConditions =
                    contract.terms.conditions.filter { condition ->
                        condition.conditionType !in engine.supportedConditionTypes
                    }
                if (unsupportedConditions.isNotEmpty()) {
                    throw IllegalStateException(
                        "Engine '$engineId' does not support condition types: " +
                            unsupportedConditions.map { it.conditionType.name }.joinToString(),
                    )
                }

                // 7. Create evaluation context
                val context =
                    EvaluationContext(
                        contractId = contract.id,
                        executionModel = contract.executionModel,
                        contractData = contract.contractData,
                    )

                // 8. Evaluate conditions using the engine
                val conditionResults =
                    contract.terms.conditions.map { condition ->
                        condition.evaluateWith(engine, inputData, context)
                    }

                val overallResult = conditionResults.all { it.satisfied && it.error == null }

                ConditionEvaluation(
                    contractId = contract.id,
                    conditions = conditionResults,
                    overallResult = overallResult,
                    timestamp = Clock.System.now().toString(),
                )
            }

        override suspend fun updateStatus(
            contractId: String,
            newStatus: ContractStatus,
            reason: String?,
            metadata: JsonElement?,
        ): Result<SmartContract> =
            trustweaveCatching {
                if (newStatus == ContractStatus.EXECUTED) {
                    throw TrustWeaveException.InvalidOperation(
                        message =
                            "A contract cannot be set to EXECUTED through updateStatus: use executeContract, " +
                                "which evaluates the contract's conditions first",
                    )
                }
                withContractLock(contractId) { transitionLocked(contractId, newStatus, reason, metadata) }
            }

        /** Validates and applies a status transition. Caller must hold the contract's lock. */
        private fun transitionLocked(
            contractId: String,
            newStatus: ContractStatus,
            reason: String? = null,
            metadata: JsonElement? = null,
            viaExecution: Boolean = false,
        ): SmartContract {
            val contract = storedContract(contractId)
            check(newStatus != ContractStatus.EXECUTED || viaExecution) { "EXECUTED is only reachable through executeContract" }

            // Validate state transition
            val transitionValidation =
                ContractValidator.validateStateTransition(
                    contract.status,
                    newStatus,
                )
            if (!transitionValidation.isValid()) {
                throw TrustWeaveException.InvalidOperation(
                    message = transitionValidation.errorMessage() ?: "Invalid state transition",
                )
            }
            if (newStatus == ContractStatus.ACTIVE) requireBoundForActivation(contract)

            return replaceContract(
                contract,
                contract.copy(
                    status = newStatus,
                    updatedAt = Clock.System.now().toString(),
                ),
            ).also { recordStatus(contractId, contract.status, newStatus, reason, metadata) }
        }

        override suspend fun getContract(contractId: String): Result<SmartContract> =
            trustweaveCatching {
                contracts[contractId]
                    ?: throw org.trustweave.core.exception.TrustWeaveException
                        .NotFound("Contract not found: $contractId")
            }

        /**
         * Verifies the contract bound to [credentialId].
         *
         * The credential is taken from the credentials this instance issued in [bindContract] or,
         * failing that, from the configured [ContractCredentialResolver]. It is then verified with
         * the credential service (proof, validity, status) and checked to actually bind the stored
         * contract: subject ID, contract number, dates, parties, contract data, terms and execution
         * model must match.
         *
         * @return success(true) when all checks pass, success(false) when the credential is invalid or
         *   does not match the stored contract, and a failure when verification cannot be performed
         *   (no credential service, unknown contract, or credential not obtainable).
         */
        override suspend fun verifyContract(credentialId: String): Result<Boolean> =
            trustweaveCatching {
                requireNotNull(credentialService) {
                    "CredentialService is required for contract verification"
                }

                // Find contract by credential ID
                val contract =
                    contractIdByCredentialId[credentialId]
                        ?.let { contracts[it] }
                        ?.takeIf { it.credentialId == credentialId }
                        ?: throw TrustWeaveException.NotFound(
                            resource = "Contract for credential ID: $credentialId",
                        )

                val credential =
                    issuedCredentials[credentialId]
                        ?: credentialResolver?.resolve(credentialId)
                        ?: throw TrustWeaveException.NotFound(
                            resource =
                                "Credential $credentialId for contract ${contract.id} " +
                                    "(not issued by this service and no ContractCredentialResolver could provide it)",
                        )

                if (credential.id?.value != credentialId) {
                    false
                } else {
                    when (credentialService.verify(credential)) {
                        is VerificationResult.Valid ->
                            credentialBindsContract(credential, contract) &&
                                issuerIsTrusted(credential, contract) &&
                                anchorIsValid(credentialId, contract)
                        is VerificationResult.Invalid -> false
                    }
                }
            }

        private suspend fun issuerIsTrusted(
            credential: VerifiableCredential,
            contract: SmartContract,
        ): Boolean {
            val issuer = credential.issuer.id.value
            val parties = contract.parties
            if (issuer == parties.primaryPartyDid || issuer == parties.counterpartyDid) return true
            if (issuer in parties.additionalParties.values) return true
            val ownIssuer = credential.id?.value?.let { issuerDidByCredentialId[it] }
            if (ownIssuer != null && ownIssuer == issuer) return true
            return trustedIssuerPolicy?.isTrusted(issuer, contract) == true
        }

        private suspend fun anchorIsValid(
            credentialId: String,
            contract: SmartContract,
        ): Boolean {
            if (!verifyAnchorOnVerify) return true
            val anchor = contract.anchorRef ?: return false
            // Fail closed: verification was asked for, so a chain nobody can query must not pass.
            val client = blockchainRegistry?.get(anchor.chainId) ?: return false
            val payload = anchoredPayloads[credentialId] ?: return false
            return client.verifyAnchor(payload, anchor.toAnchorRef())
        }

        /**
         * Checks that the credential's subject carries exactly the stored contract's binding claims,
         * so a valid credential for one contract cannot vouch for different (or edited) terms.
         */
        private fun credentialBindsContract(
            credential: VerifiableCredential,
            contract: SmartContract,
        ): Boolean {
            val subject = credential.credentialSubject
            if (subject.id?.value != contract.id) return false
            val claims = subject.claims

            fun claimString(name: String): String? = (claims[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

            if (claimString("contractNumber") != contract.contractNumber) return false
            if (claimString("effectiveDate") != contract.effectiveDate) return false
            if (claimString("expirationDate") != (contract.expirationDate ?: "")) return false
            if (claims["contractData"] != contract.contractData) return false
            if (claims["terms"] != json.encodeToJsonElement(contract.terms)) return false

            val parties = claims["parties"] as? JsonObject ?: return false
            val expectedParties =
                buildMap {
                    put("primaryPartyDid", contract.parties.primaryPartyDid)
                    put("counterpartyDid", contract.parties.counterpartyDid)
                    putAll(contract.parties.additionalParties)
                }
            if (parties.keys != expectedParties.keys) return false
            if (expectedParties.any { (k, v) -> (parties[k] as? JsonPrimitive)?.content != v }) return false

            val boundModel =
                claims["executionModel"]?.let {
                    runCatching { json.decodeFromJsonElement(ExecutionModel.serializer(), it) }.getOrNull()
                } ?: return false
            // The credential records the engine version/hash captured at issuance; the stored draft
            // usually does not. Compare the model without them, and exactly when the draft pins a hash.
            return withoutEngineHash(boundModel) == withoutEngineHash(contract.executionModel) &&
                (engineHashOf(contract.executionModel) == null || boundModel == contract.executionModel)
        }

        private fun engineHashOf(model: ExecutionModel): String? =
            when (model) {
                is ExecutionModel.Parametric -> model.engineHash
                is ExecutionModel.Conditional -> model.engineHash
                is ExecutionModel.Scheduled -> model.engineHash
                is ExecutionModel.EventDriven -> model.engineHash
                ExecutionModel.Manual -> null
            }

        private fun withoutEngineHash(model: ExecutionModel): ExecutionModel =
            when (model) {
                is ExecutionModel.Parametric -> model.copy(engineVersion = null, engineHash = null)
                is ExecutionModel.Conditional -> model.copy(engineVersion = null, engineHash = null)
                is ExecutionModel.Scheduled -> model.copy(engineVersion = null, engineHash = null)
                is ExecutionModel.EventDriven -> model.copy(engineVersion = null, engineHash = null)
                ExecutionModel.Manual -> model
            }

        /**
         * Compare-and-set write: replaces [expected] with [updated] only if the stored record is
         * still [expected]. Fails loudly instead of silently overwriting a concurrent change.
         */
        private fun replaceContract(
            expected: SmartContract,
            updated: SmartContract,
        ): SmartContract {
            require(expected.id == updated.id) { "Contract ID cannot change" }
            if (!contracts.replace(expected.id, expected, updated)) {
                throw TrustWeaveException.InvalidOperation(
                    message = "Contract ${expected.id} was modified concurrently; reload it and retry",
                )
            }
            return updated
        }
    }
