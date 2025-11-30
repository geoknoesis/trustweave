---
title: Smart Contract API Reference
nav_order: 7
parent: API Reference
---

# Smart Contract API Reference

> Complete API reference for TrustWeave Smart Contract operations

## Overview

The Smart Contract API provides methods for creating, binding, executing, and managing executable contracts with verifiable credentials and blockchain anchoring.

## Service Access

```kotlin
import com.trustweave.trust.TrustWeave

val trustWeave = TrustWeave.build { ... }
val contracts = trustWeave.contracts
```

## API Methods

### draft (or createDraft)

Creates a new contract draft.

**Signature:**
```kotlin
suspend fun draft(
    request: ContractDraftRequest
): Result<SmartContract>

// Also available as:
suspend fun createDraft(
    request: ContractDraftRequest
): Result<SmartContract>
```

**Parameters:**

- **`request`** (ContractDraftRequest, required): Contract draft request containing:
  - `contractType`: Type of contract (Insurance, Legal, Financial, etc.)
  - `executionModel`: How the contract executes (Parametric, Conditional, Scheduled, etc.)
  - `parties`: Contract parties identified by DIDs
  - `terms`: Contract terms (obligations, conditions, penalties, rewards)
  - `effectiveDate`: ISO 8601 timestamp when contract becomes effective
  - `expirationDate`: Optional ISO 8601 timestamp when contract expires
  - `contractData`: Domain-specific contract data as JSON

**Returns:** `Result<SmartContract>` with contract in `DRAFT` status

**Example:**
```kotlin
import com.trustweave.trust.TrustWeave

val trustWeave = TrustWeave.build { ... }

// Recommended: Use draft() for cleaner API
val contract = trustWeave.contracts.draft(
    request = ContractDraftRequest(
        contractType = ContractType.Insurance,
        executionModel = ExecutionModel.Parametric(...),
        parties = ContractParties(...),
        terms = ContractTerms(...),
        effectiveDate = Instant.now().toString(),
        contractData = buildJsonObject { ... }
    )
).getOrThrow()

// Alternative: createDraft() is also available
val contract2 = trustWeave.contracts.createDraft(request).getOrThrow()
```

**Validation:**
- Validates DID formats for all parties
- Ensures expiration date is after effective date
- Validates terms (unique IDs, valid party DIDs)
- Throws `InvalidOperationException` if validation fails

---

### bindContract

Binds a contract by issuing a verifiable credential and anchoring to blockchain.

**Signature:**
```kotlin
suspend fun bindContract(
    contractId: String,
    issuerDid: String,
    issuerKeyId: String,
    chainId: String = "algorand:mainnet"
): Result<BoundContract>
```

**Parameters:**

- **`contractId`** (String, required): ID of the contract to bind
- **`issuerDid`** (String, required): DID of the credential issuer
- **`issuerKeyId`** (String, required): Key ID from issuer's DID document
- **`chainId`** (String, optional): Blockchain chain ID (default: "algorand:mainnet")

**Returns:** `Result<BoundContract>` containing:
- `contract`: Updated contract with credential ID and anchor reference
- `credentialId`: ID of the issued verifiable credential
- `anchorRef`: Blockchain anchor reference

**Example:**
```kotlin
val bound = trustWeave.contracts.bindContract(
    contractId = contract.id,
    issuerDid = insurerDid,
    issuerKeyId = insurerKeyId,
    chainId = "algorand:mainnet"
).getOrThrow()
```

**Status Transition:** `DRAFT` → `PENDING`

---

### activateContract

Activates a contract (moves from PENDING to ACTIVE).

**Signature:**
```kotlin
suspend fun activateContract(
    contractId: String
): Result<SmartContract>
```

**Parameters:**

- **`contractId`** (String, required): ID of the contract to activate

**Returns:** `Result<SmartContract>` with contract in `ACTIVE` status

**Example:**
```kotlin
val active = trustWeave.contracts.activateContract(contractId).getOrThrow()
```

**Validation:**
- Contract must be in `PENDING` status
- Contract must not be expired
- Throws `InvalidOperationException` if validation fails

**Status Transition:** `PENDING` → `ACTIVE`

---

### executeContract

Executes a contract based on its execution model.

**Signature:**
```kotlin
suspend fun executeContract(
    contract: SmartContract,
    executionContext: ExecutionContext
): Result<ExecutionResult>
```

**Parameters:**

- **`contract`** (SmartContract, required): The contract to execute
- **`executionContext`** (ExecutionContext, required): Execution context containing:
  - `triggerData`: JSON data for parametric execution
  - `eventData`: JSON data for event-driven execution
  - `timeContext`: ISO 8601 timestamp for time-based execution
  - `additionalContext`: Additional context as JSON

**Returns:** `Result<ExecutionResult>` containing:
- `executed`: Whether contract was executed
- `executionType`: Type of execution (PARAMETRIC_TRIGGER, etc.)
- `outcomes`: List of contract outcomes
- `evidence`: List of verifiable credential IDs used as evidence
- `timestamp`: Execution timestamp

**Example:**
```kotlin
val result = trustWeave.contracts.executeContract(
    contract = activeContract,
    executionContext = ExecutionContext(
        triggerData = buildJsonObject {
            put("floodDepthCm", 75.0)
        }
    )
).getOrThrow()
```

**Validation:**
- Contract must be in `ACTIVE` status
- Contract must not be expired
- Automatically expires contract if expired

**Status Transition:** `ACTIVE` → `EXECUTED` (if conditions met)

---

### evaluateConditions

Evaluates contract conditions without executing.

**Signature:**
```kotlin
suspend fun evaluateConditions(
    contract: SmartContract,
    inputData: JsonElement
): Result<ConditionEvaluation>
```

**Parameters:**

- **`contract`** (SmartContract, required): The contract to evaluate
- **`inputData`** (JsonElement, required): Input data for condition evaluation

**Returns:** `Result<ConditionEvaluation>` containing:
- `conditions`: List of condition results
- `overallResult`: Whether all conditions are satisfied
- `timestamp`: Evaluation timestamp

**Example:**
```kotlin
val evaluation = trustWeave.contracts.evaluateConditions(
    contract = contract,
    inputData = buildJsonObject {
        put("floodDepthCm", 75.0)
    }
).getOrThrow()

evaluation.conditions.forEach { condition ->
    println("${condition.conditionId}: ${if (condition.satisfied) "✓" else "✗"}")
}
```

---

### updateStatus

Updates contract status with validation.

**Signature:**
```kotlin
suspend fun updateStatus(
    contractId: String,
    newStatus: ContractStatus,
    reason: String? = null,
    metadata: JsonElement? = null
): Result<SmartContract>
```

**Parameters:**

- **`contractId`** (String, required): ID of the contract
- **`newStatus`** (ContractStatus, required): New status
- **`reason`** (String, optional): Reason for status change
- **`metadata`** (JsonElement, optional): Additional metadata

**Returns:** `Result<SmartContract>` with updated status

**Example:**
```kotlin
val updated = trustWeave.contracts.updateStatus(
    contractId = contract.id,
    newStatus = ContractStatus.SUSPENDED,
    reason = "Under review"
).getOrThrow()
```

**Validation:**
- Validates state transition is allowed
- Throws `InvalidOperationException` for invalid transitions

---

### getContract

Retrieves a contract by ID.

**Signature:**
```kotlin
suspend fun getContract(contractId: String): Result<SmartContract>
```

**Parameters:**

- **`contractId`** (String, required): ID of the contract

**Returns:** `Result<SmartContract>`

**Example:**
```kotlin
val contract = trustWeave.contracts.getContract(contractId).getOrThrow()
```

**Errors:**
- `NotFoundException` if contract doesn't exist

---

### verifyContract

Verifies a contract's verifiable credential.

**Signature:**
```kotlin
suspend fun verifyContract(
    credentialId: String
): Result<Boolean>
```

**Parameters:**

- **`credentialId`** (String, required): ID of the contract credential

**Returns:** `Result<Boolean>` indicating verification result

**Example:**
```kotlin
val isValid = trustWeave.contracts.verifyContract(credentialId).getOrThrow()
```

---

## Data Models

### SmartContract

Main contract model:

```kotlin
data class SmartContract(
    val id: String,
    val contractNumber: String,
    val status: ContractStatus,
    val contractType: ContractType,
    val executionModel: ExecutionModel,
    val parties: ContractParties,
    val terms: ContractTerms,
    val effectiveDate: String,
    val expirationDate: String?,
    val createdAt: String,
    val updatedAt: String,
    val credentialId: String?,
    val anchorRef: AnchorRefData?,
    val contractData: JsonElement
)
```

### ContractStatus

Contract lifecycle status:

```kotlin
enum class ContractStatus {
    DRAFT, PENDING, ACTIVE, SUSPENDED,
    EXECUTED, EXPIRED, CANCELLED, TERMINATED
}
```

### ExecutionModel

Contract execution models:

```kotlin
sealed class ExecutionModel {
    data class Parametric(...) : ExecutionModel()
    data class Conditional(...) : ExecutionModel()
    data class Scheduled(...) : ExecutionModel()
    data class EventDriven(...) : ExecutionModel()
    object Manual : ExecutionModel()
}
```

### ContractParties

Contract parties:

```kotlin
data class ContractParties(
    val primaryPartyDid: String,
    val counterpartyDid: String,
    val additionalParties: Map<String, String> = emptyMap()
)
```

### ContractTerms

Contract terms:

```kotlin
data class ContractTerms(
    val obligations: List<Obligation>,
    val conditions: List<ContractCondition>,
    val penalties: List<Penalty>? = null,
    val rewards: List<Reward>? = null,
    val jurisdiction: String? = null,
    val governingLaw: String? = null,
    val disputeResolution: DisputeResolution? = null
)
```

## Error Handling

All methods return `Result<T>` which can be handled with:

```kotlin
result.fold(
    onSuccess = { contract -> /* handle success */ },
    onFailure = { error ->
        when (error) {
            is NotFoundException -> { /* contract not found */ }
            is InvalidOperationException -> { /* invalid operation */ }
            else -> { /* other error */ }
        }
    }
)
```

## See Also

- [Smart Contracts Core Concepts](../core-concepts/smart-contracts.md) for detailed concepts
- [Parametric Insurance Scenario](../scenarios/smart-contract-parametric-insurance-scenario.md) for complete example
- [Core API Reference](core-api.md) for TrustWeave facade API

