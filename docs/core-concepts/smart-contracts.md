---
title: Smart Contracts
---

# Smart Contracts

> TrustWeave Smart Contracts provide a domain-agnostic abstraction for executable agreements with verifiable credentials and blockchain anchoring support.

## What is a Smart Contract?

A **Smart Contract** in TrustWeave is an executable agreement between parties that combines:

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-contract:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-all:1.0.0-SNAPSHOT")
}
```

**Result:** Grants access to the SmartContract models and service APIs referenced throughout this guide.

1. **Verifiable Identity** – parties identified by DIDs
2. **Cryptographic Proof** – contract terms wrapped in Verifiable Credentials
3. **Immutable Audit Trail** – blockchain anchoring for tamper-proof records
4. **Pluggable Execution** – parametric, conditional, scheduled, event-driven, or manual execution models

## Why Smart Contracts Matter in TrustWeave

- **Domain-Agnostic**: Works for insurance, legal, financial, SLA, and supply chain contracts
- **Trust**: Cryptographic proof of contract terms prevents disputes
- **Automation**: Parametric execution enables automatic payouts based on external data
- **Auditability**: Blockchain anchoring provides immutable audit trails
- **Standards-Based**: Integrates with W3C Verifiable Credentials and DIDs

## Contract Lifecycle

Smart Contracts follow a clear lifecycle:

```
DRAFT → PENDING → ACTIVE → EXECUTED/EXPIRED/CANCELLED/TERMINATED
```

| Status | Description |
|--------|-------------|
| `DRAFT` | Being created/negotiated |
| `PENDING` | Awaiting signatures/approval |
| `ACTIVE` | In effect and executable |
| `SUSPENDED` | Temporarily paused |
| `EXECUTED` | Conditions met, executed |
| `EXPIRED` | Past expiration date |
| `CANCELLED` | Cancelled by parties |
| `TERMINATED` | Terminated by breach or agreement |

## Execution Models

TrustWeave supports multiple execution models:

### 1. Parametric Execution

Triggers based on external data (e.g., EO data, weather, market data):

```kotlin
ExecutionModel.Parametric(
    triggerType = TriggerType.EarthObservation,
    evaluationEngine = "parametric-insurance"
)
```

**Use Cases:**
- Parametric insurance (flood depth, temperature thresholds)
- Weather derivatives
- Market-based triggers

### 2. Conditional Execution

If/then logic with rule evaluation:

```kotlin
ExecutionModel.Conditional(
    conditions = listOf(
        ContractCondition(
            id = "condition-1",
            description = "Value exceeds threshold",
            conditionType = ConditionType.THRESHOLD,
            expression = "$.floodDepth >= 50"
        )
    ),
    evaluationEngine = "rule-engine"
)
```

**Use Cases:**
- Service level agreements
- Performance-based contracts
- Compliance monitoring

### 3. Scheduled Execution

Time-based actions:

```kotlin
ExecutionModel.Scheduled(
    schedule = ScheduleDefinition(
        cronExpression = "0 0 * * *", // Daily at midnight
        timezone = "UTC"
    ),
    evaluationEngine = "scheduler"
)
```

**Use Cases:**
- Recurring payments
- Periodic reviews
- Automated renewals

### 4. Event-Driven Execution

Responds to external events:

```kotlin
ExecutionModel.EventDriven(
    eventTypes = listOf("payment.received", "delivery.completed"),
    evaluationEngine = "event-processor"
)
```

**Use Cases:**
- Supply chain contracts
- Payment processing
- Workflow automation

### 5. Manual Execution

Requires human intervention:

```kotlin
ExecutionModel.Manual
```

**Use Cases:**
- Legal contracts requiring approval
- Complex decisions
- Dispute resolution

## How TrustWeave Manages Smart Contracts

| Component | Purpose |
|-----------|---------|
| `SmartContractService` | Interface for contract operations |
| `DefaultSmartContractService` | In-memory implementation (for testing/development) |
| `ContractValidator` | Validates parties, dates, terms, and state transitions |
| `TrustWeave.contracts` | High-level facade for contract operations |

## Example: Creating a Contract

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.contract.models.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

suspend fun createFloodInsuranceContract(
    trustweave: TrustWeave,
    insurerDid: String,
    insuredDid: String
) {
    val contract = trustweave.contracts.draft(
        request = ContractDraftRequest(
            contractType = ContractType.Insurance,
            executionModel = ExecutionModel.Parametric(
                triggerType = TriggerType.EarthObservation,
                evaluationEngine = "parametric-insurance"
            ),
            parties = ContractParties(
                primaryPartyDid = insurerDid,
                counterpartyDid = insuredDid
            ),
            terms = ContractTerms(
                obligations = listOf(
                    Obligation(
                        id = "obligation-1",
                        partyDid = insurerDid,
                        description = "Pay out if flood depth exceeds 50cm",
                        obligationType = ObligationType.PAYMENT,
                        deadline = null
                    )
                ),
                conditions = listOf(
                    ContractCondition(
                        id = "condition-1",
                        description = "Flood depth >= 50cm",
                        conditionType = ConditionType.THRESHOLD,
                        expression = "$.floodDepth >= 50"
                    )
                )
            ),
            effectiveDate = Instant.now().toString(),
            expirationDate = Instant.now().plusSeconds(365 * 24 * 60 * 60).toString(),
            contractData = buildJsonObject {
                put("productType", "SarFlood")
                put("coverageAmount", 1_000_000.0)
                put("location", buildJsonObject {
                    put("latitude", 35.2271)
                    put("longitude", -80.8431)
                    put("address", "Charlotte, NC")
                })
            }
        )
    ).getOrThrow()
    
    println("Created contract: ${contract.id}")
    return contract
}
```

**Outcome:** Creates a draft contract with parametric execution model, ready for binding and activation.

## Example: Binding a Contract

Binding issues a verifiable credential and anchors the contract to blockchain:

```kotlin
suspend fun bindContract(
    trustweave: TrustWeave,
    contractId: String,
    issuerDid: String,
    issuerKeyId: String
) {
    val bound = trustweave.contracts.bindContract(
        contractId = contractId,
        issuerDid = issuerDid,
        issuerKeyId = issuerKeyId,
        chainId = "algorand:mainnet"
    ).getOrThrow()
    
    println("Contract bound:")
    println("  Credential ID: ${bound.credentialId}")
    println("  Anchor: ${bound.anchorRef.txHash}")
    println("  Status: ${bound.contract.status}")
}
```

**Outcome:** Contract is now verifiable via credential and anchored to blockchain for audit trail.

## Example: Executing a Contract

For parametric contracts, execution happens when trigger data arrives:

```kotlin
suspend fun executeFloodContract(
    trustweave: TrustWeave,
    contract: SmartContract,
    floodDataCredential: VerifiableCredential
) {
    val result = trustweave.contracts.executeContract(
        contract = contract,
        executionContext = ExecutionContext(
            triggerData = buildJsonObject {
                put("floodDepthCm", 75.0)
                put("credentialId", floodDataCredential.id)
            }
        )
    ).getOrThrow()
    
    if (result.executed) {
        println("Contract executed!")
        result.outcomes.forEach { outcome ->
            println("  Outcome: ${outcome.description}")
            outcome.monetaryImpact?.let {
                println("  Amount: ${it.amount} ${it.currency}")
            }
        }
    } else {
        println("Contract conditions not met")
    }
}
```

**Outcome:** Evaluates conditions and executes contract if thresholds are met, generating outcomes and updating contract status.

## Validation

TrustWeave automatically validates:

- **DID Format**: All party DIDs must be valid
- **Date Ranges**: Expiration must be after effective date
- **State Transitions**: Only valid transitions are allowed
- **Terms**: Obligations and conditions must have unique IDs
- **Expiration**: Contracts are automatically checked for expiration

```kotlin
// Validation happens automatically in draft()
val trustweave = TrustWeave.create()
try {
    val contract = trustweave.contracts.draft(request).getOrThrow()
    println("Contract created: ${contract.id}")
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.ValidationFailed -> {
            println("Validation failed: ${error.reason}")
        }
        else -> println("Error: ${error.message}")
    }
}
```

## State Transition Rules

Valid state transitions are enforced:

- `DRAFT` → `PENDING`, `CANCELLED`
- `PENDING` → `ACTIVE`, `CANCELLED`
- `ACTIVE` → `EXECUTED`, `SUSPENDED`, `EXPIRED`, `TERMINATED`, `CANCELLED`
- `SUSPENDED` → `ACTIVE`, `TERMINATED`, `CANCELLED`
- Terminal states (`EXECUTED`, `EXPIRED`, `CANCELLED`, `TERMINATED`) cannot transition

```kotlin
// Invalid transition will throw InvalidOperationException
val trustweave = TrustWeave.create()
try {
    trustweave.contracts.updateStatus(
        contractId = contract.id,
        newStatus = ContractStatus.EXECUTED // Must be ACTIVE first
    ).getOrThrow()
} catch (error: TrustWeaveError) {
    println("State transition failed: ${error.message}")
}
```

## Integration with TrustWeave

Smart Contracts integrate seamlessly with TrustWeave's trust infrastructure:

### Verifiable Credentials

Contracts are issued as Verifiable Credentials:

```kotlin
// Contract credential is automatically issued during bindContract()
val trustweave = TrustWeave.create()
val bound = trustweave.contracts.bindContract(...).getOrThrow()
// bound.credentialId contains the VC ID
```

### Blockchain Anchoring

Contracts are anchored to blockchain for audit trails:

```kotlin
// Anchor reference is stored in contract.anchorRef
val trustweave = TrustWeave.create()
val contract = trustweave.contracts.getContract(contractId).getOrThrow()
contract.anchorRef?.let { anchor ->
    println("Anchored on: ${anchor.chainId}")
    println("Transaction: ${anchor.txHash}")
}
```

### DID-Based Parties

All parties are identified by DIDs:

```kotlin
ContractParties(
    primaryPartyDid = "did:key:insurer-123",
    counterpartyDid = "did:key:insured-456",
    additionalParties = mapOf(
        "broker" to "did:key:broker-789",
        "reinsurer" to "did:key:reinsurer-101"
    )
)
```

## Practical Usage Tips

- **Validation**: Contract operations return `Result<T>` - use `fold()` or `getOrThrow()` for error handling
- **State Management**: Use `updateStatus()` for explicit state transitions
- **Expiration**: Check expiration before executing contracts
- **Error Handling**: All operations return `Result<T>` with structured `TrustWeaveError` types
- **Storage**: `DefaultSmartContractService` is in-memory; use persistent storage for production
- **Condition Evaluation**: Condition evaluation is extensible; implement custom evaluators for production

## See Also

- [Evaluation Engines](evaluation-engines.md) for pluggable condition evaluation with tamper protection
- [Parametric Insurance Scenario](../scenarios/parametric-insurance-mga-implementation-guide.md) for complete parametric insurance example
- [Verifiable Credentials](verifiable-credentials.md) for credential issuance and verification
- [Blockchain Anchoring](blockchain-anchoring.md) for anchoring concepts
- [DIDs](dids.md) for DID management
- [API Reference](../api-reference/core-api.md) for complete API documentation

