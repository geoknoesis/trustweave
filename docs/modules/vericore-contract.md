# vericore-contract Module

> **Version:** 1.0.0-SNAPSHOT  
> Domain-agnostic Smart Contract abstraction with verifiable credentials and blockchain anchoring

## Overview

The `vericore-contract` module provides a generic Smart Contract abstraction for executable agreements between parties. It integrates with VeriCore's verifiable credentials and blockchain anchoring to provide trust, auditability, and automation.

## Purpose

Smart Contracts in VeriCore enable:

- **Executable Agreements**: Contracts that can automatically execute based on conditions
- **Verifiable Terms**: Contract terms wrapped in Verifiable Credentials
- **Immutable Audit Trails**: Blockchain anchoring for tamper-proof records
- **Multi-Party Support**: Parties identified by DIDs
- **Pluggable Execution**: Parametric, conditional, scheduled, event-driven, or manual execution

## When to Use

Add this module when you need:

- Parametric insurance contracts
- Service level agreements (SLAs)
- Financial derivatives
- Legal contracts with automated execution
- Supply chain agreements
- Any executable agreement requiring trust and auditability

## Installation

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-contract:1.0.0-SNAPSHOT")
}
```

**Note:** This module is included in `vericore-all`, so you may already have access if you're using the all-in-one distribution.

## Module Dependencies

```
vericore-contract
    → vericore-core
    → vericore-spi
    → vericore-json
    → vericore-anchor
    → vericore-did
```

## Key Components

### Models

- **`SmartContract`**: Main contract data model
- **`ContractStatus`**: Contract lifecycle status enum
- **`ContractType`**: Contract type classification (Insurance, Legal, Financial, etc.)
- **`ExecutionModel`**: Execution model (Parametric, Conditional, Scheduled, etc.)
- **`ContractParties`**: Parties identified by DIDs
- **`ContractTerms`**: Obligations, conditions, penalties, rewards
- **`AnchorRefData`**: Serializable wrapper for blockchain anchor references

### Services

- **`SmartContractService`**: Interface for contract operations
- **`DefaultSmartContractService`**: In-memory implementation
- **`ContractValidator`**: Validation utilities

### Integration

- **`ContractService`**: Service wrapper exposed via `VeriCore.contracts`

## Usage Example

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.contract.models.*

val vericore = VeriCore.create()

// Create contract draft
val contract = vericore.contracts.draft(
    request = ContractDraftRequest(
        contractType = ContractType.Insurance,
        executionModel = ExecutionModel.Parametric(...),
        parties = ContractParties(...),
        terms = ContractTerms(...),
        effectiveDate = Instant.now().toString(),
        contractData = buildJsonObject { ... }
    )
).getOrThrow()

// Bind contract (issue VC and anchor)
val bound = vericore.contracts.bindContract(
    contractId = contract.id,
    issuerDid = insurerDid,
    issuerKeyId = insurerKeyId
).getOrThrow()

// Activate contract
val active = vericore.contracts.activateContract(bound.contract.id).getOrThrow()

// Execute contract
val result = vericore.contracts.executeContract(
    contract = active,
    executionContext = ExecutionContext(
        triggerData = buildJsonObject { ... }
    )
).getOrThrow()
```

## Features

### Contract Lifecycle

Manages complete contract lifecycle:
- `DRAFT` → `PENDING` → `ACTIVE` → `EXECUTED`/`EXPIRED`/`CANCELLED`/`TERMINATED`

### Execution Models

Supports multiple execution models:
- **Parametric**: Triggers based on external data (EO, weather, market data)
- **Conditional**: If/then logic with rule evaluation
- **Scheduled**: Time-based actions
- **Event-Driven**: Responds to external events
- **Manual**: Requires human intervention

### Validation

Automatic validation of:
- DID formats for all parties
- Date ranges (expiration after effective date)
- State transitions
- Terms (unique IDs, valid party DIDs)
- Expiration checking

### VeriCore Integration

- **Verifiable Credentials**: Contracts issued as VCs
- **Blockchain Anchoring**: Contracts anchored for audit trails
- **DID-Based Parties**: All parties identified by DIDs

## Thread Safety

`DefaultSmartContractService` uses `ConcurrentHashMap` for thread-safe contract storage. All operations use Kotlin coroutines for non-blocking I/O.

## Storage

The default implementation (`DefaultSmartContractService`) uses in-memory storage suitable for:
- Testing and development
- Prototyping
- Short-lived contracts

For production use, implement `SmartContractService` with persistent storage (database, etc.).

## See Also

- [Smart Contracts Core Concepts](../core-concepts/smart-contracts.md) for detailed concepts
- [Smart Contract API Reference](../api-reference/smart-contract-api.md) for complete API documentation
- [Parametric Insurance Scenario](../scenarios/smart-contract-parametric-insurance-scenario.md) for complete example
- [Verifiable Credentials](../core-concepts/verifiable-credentials.md) for credential concepts
- [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) for anchoring concepts

