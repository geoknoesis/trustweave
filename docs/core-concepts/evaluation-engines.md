# Evaluation Engines

> VeriCore Evaluation Engines provide a pluggable, tamper-proof system for evaluating contract conditions with cryptographic integrity verification.

## Overview

Evaluation Engines are pluggable components that implement domain-specific logic for evaluating contract conditions. They provide:

- **Pluggable Architecture**: Register custom engines for different domains (insurance, finance, supply chain, etc.)
- **Tamper Detection**: Cryptographic hashing ensures engines haven't been modified
- **Version Control**: Track engine versions for compatibility
- **Type Safety**: Strongly-typed interfaces for condition evaluation

## Why Evaluation Engines?

Traditional contract execution systems have hardcoded evaluation logic, making them:
- Inflexible for different domains
- Vulnerable to tampering
- Difficult to update without breaking existing contracts

VeriCore's Evaluation Engine framework solves these problems by:
- Allowing domain-specific engines (parametric insurance, rule engines, etc.)
- Cryptographically protecting engine implementations
- Enabling engine updates while maintaining contract integrity

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Contract Execution                          │
│  ┌───────────────────────────────────────────────────┐  │
│  │  DefaultSmartContractService                      │  │
│  │  - Extracts engine ID from ExecutionModel        │  │
│  │  - Verifies engine integrity                     │  │
│  │  - Delegates to registered engine                │  │
│  └───────────────────┬───────────────────────────────┘  │
└──────────────────────┼──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              EvaluationEngines                          │
│  ┌───────────────────────────────────────────────────┐  │
│  │  - Thread-safe engine storage                    │  │
│  │  - Hash verification                             │  │
│  │  - Engine lookup (operator overloads)           │  │
│  └───────────────────┬───────────────────────────────┘  │
└──────────────────────┼──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│         ContractEvaluationEngine                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │  - engineId: String                               │  │
│  │  - version: String                                │  │
│  │  - implementationHash: String                     │  │
│  │  - evaluateCondition()                            │  │
│  │  - evaluateConditions()                           │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Core Components

### ContractEvaluationEngine Interface

The main interface that all evaluation engines must implement:

```kotlin
interface ContractEvaluationEngine {
    val engineId: String
    val version: String
    val implementationHash: String
    val supportedConditionTypes: Set<ConditionType>
    
    suspend fun evaluateCondition(
        condition: ContractCondition,
        inputData: JsonElement,
        context: EvaluationContext
    ): Boolean
    
    suspend fun evaluateConditions(
        conditions: List<ContractCondition>,
        inputData: JsonElement,
        context: EvaluationContext
    ): Map<String, Boolean>
}
```

#### Why JsonElement for Input Data?

The evaluation engine API uses `JsonElement` for `inputData` rather than `Any` or generic types. This design decision is based on several key considerations:

**1. Architectural Consistency**

VeriCore is built on a JSON-first architecture:
- Verifiable Credentials use JSON-LD format
- DID Documents are JSON
- Blockchain anchors store JSON payloads
- Contract data (`contractData`) is `JsonElement`
- Execution context (`ExecutionContext.triggerData`) is `JsonElement`

Using `JsonElement` maintains consistency across the entire framework, making the API predictable and easy to understand.

**2. Real-World Data Sources**

All external data sources in VeriCore are JSON-native:
- **Earth Observation APIs** (ESA, NASA, Planet) → JSON responses
- **Weather APIs** (NOAA, Weather.com) → JSON responses  
- **IoT Sensors** → JSON payloads (MQTT, HTTP)
- **Financial APIs** → JSON market data
- **Verifiable Credentials** → JSON-LD format

Since all data arrives as JSON, `JsonElement` eliminates unnecessary conversion overhead.

**3. Type Safety with Flexibility**

`JsonElement` provides a balance between type safety and flexibility:

```kotlin
// Compile-time: Ensures it's structured JSON
val inputData: JsonElement = buildJsonObject {
    put("floodDepthCm", 75.0)
    put("timestamp", Instant.now().toString())
}

// Runtime: Safe, explicit access with null handling
val inputObj = inputData as? JsonObject
    ?: throw IllegalArgumentException("Expected JSON object")

val value = inputObj["floodDepthCm"]?.jsonPrimitive?.content?.toDoubleOrNull()
```

This is safer than `Any`, which provides no compile-time guarantees and requires extensive runtime type checking.

**4. Serialization & Integrity**

`JsonElement` integrates seamlessly with kotlinx.serialization and enables integrity verification:

```kotlin
// Direct serialization support
val json = Json.encodeToJsonElement(executionModel)

// Can hash for tamper detection
val inputHash = DigestUtils.sha256DigestMultibase(inputData)
```

Using `Any` would require conversion to JSON before hashing, adding complexity and potential for errors.

**5. Performance**

`JsonElement` offers optimal performance:
- **Zero-copy parsing**: kotlinx.serialization parses JSON efficiently
- **Lazy evaluation**: Only accessed fields are parsed
- **Efficient memory**: Tree structure is memory-efficient
- **Native serialization**: No conversion overhead

**6. Developer Experience**

`JsonElement` provides a clear, explicit API:

```kotlin
// Clear and explicit
val data = buildJsonObject {
    put("temperature", 35.5)
    put("humidity", 0.65)
}
engine.evaluateCondition(condition, data, context)
```

Compared to `Any`, which is unclear about accepted types and requires documentation to understand what's supported.

**7. Extensibility**

`JsonElement` supports any JSON structure without breaking changes:

```kotlin
// Engine can handle any JSON schema
class CustomEngine : BaseEvaluationEngine() {
    override suspend fun evaluateCondition(
        condition: ContractCondition,
        inputData: JsonElement,  // Flexible for any JSON structure
        context: EvaluationContext
    ): Boolean {
        // Can parse and validate any JSON schema
        val obj = inputData as? JsonObject ?: return false
        // Handle different data structures
    }
}
```

**8. Framework Integration**

`JsonElement` works seamlessly with VeriCore's execution flow:

```kotlin
// Data flows naturally through the system
val executionContext = ExecutionContext(
    triggerData = buildJsonObject { ... }  // JsonElement
)

// Passed directly to evaluation
evaluateConditions(contract, executionContext.triggerData)  // JsonElement

// Engine receives it directly
engine.evaluateCondition(condition, inputData, context)  // JsonElement
```

No conversion layers needed - data flows naturally from external sources through the framework to engines.

**Conclusion**

`JsonElement` is the optimal choice because it:
- ✅ Maintains architectural consistency
- ✅ Matches real-world data formats
- ✅ Provides type safety with flexibility
- ✅ Enables integrity verification
- ✅ Offers optimal performance
- ✅ Improves developer experience
- ✅ Supports extensibility
- ✅ Integrates seamlessly with the framework

Using `Any` would reduce type safety, add conversion complexity, and break consistency with VeriCore's JSON-first architecture.

### BaseEvaluationEngine

Base class that provides automatic hash calculation:

```kotlin
abstract class BaseEvaluationEngine : ContractEvaluationEngine {
    // implementationHash is computed automatically from class file bytecode
    final override val implementationHash: String by lazy {
        computeImplementationHash()
    }
}
```

### EvaluationEngines

Thread-safe collection for managing evaluation engines with operator overloads:

```kotlin
interface EvaluationEngines {
    operator fun plusAssign(engine: ContractEvaluationEngine)  // engines += engine
    operator fun minusAssign(engineId: String)                 // engines -= "engine-id"
    operator fun get(engineId: String): ContractEvaluationEngine?  // engines["engine-id"]
    operator fun contains(engineId: String): Boolean           // "engine-id" in engines
    fun verify(engineId: String, expectedHash: String): Boolean
    val keys: Set<String>
    val size: Int
    fun clear()
}

// Factory function
fun EvaluationEngines(): EvaluationEngines
```

**Example Usage:**

```kotlin
val engines = EvaluationEngines()

// Register using += operator
engines += ParametricInsuranceEngine()

// Get using [] operator
val engine = engines["parametric-insurance"]

// Check if registered using 'in' operator
if ("parametric-insurance" in engines) {
    // Engine is registered
}

// Verify integrity
engines.verify("parametric-insurance", expectedHash)
```

## Creating a Custom Engine

### Step 1: Extend BaseEvaluationEngine

```kotlin
package com.example.contracts.engines

import com.geoknoesis.vericore.contract.evaluation.*
import com.geoknoesis.vericore.contract.models.*

class MyCustomEngine : BaseEvaluationEngine() {
    override val engineId: String = "my-custom-engine"
    override val version: String = "1.0.0"
    override val supportedConditionTypes: Set<ConditionType> = setOf(
        ConditionType.THRESHOLD,
        ConditionType.RANGE
    )
    
    override suspend fun evaluateCondition(
        condition: ContractCondition,
        inputData: JsonElement,
        context: EvaluationContext
    ): Boolean {
        return when (condition.conditionType) {
            ConditionType.THRESHOLD -> evaluateThreshold(condition, inputData)
            ConditionType.RANGE -> evaluateRange(condition, inputData)
            else -> throw UnsupportedOperationException(
                "Condition type ${condition.conditionType} not supported"
            )
        }
    }
    
    private fun evaluateThreshold(
        condition: ContractCondition,
        inputData: JsonElement
    ): Boolean {
        // Your implementation
        TODO()
    }
    
    private fun evaluateRange(
        condition: ContractCondition,
        inputData: JsonElement
    ): Boolean {
        // Your implementation
        TODO()
    }
}
```

### Step 2: Register the Engine

```kotlin
val engines = EvaluationEngines()
val engine = MyCustomEngine()

// Register using += operator
engines += engine

// Engine hash is computed automatically
println("Engine hash: ${engine.implementationHash}")
```

### Step 3: Use in Contracts

```kotlin
val contract = vericore.contracts.draft(
    request = ContractDraftRequest(
        executionModel = ExecutionModel.Parametric(
            triggerType = TriggerType.EarthObservation,
            evaluationEngine = "my-custom-engine"
            // engineHash will be added during bindContract()
        ),
        terms = ContractTerms(
            conditions = listOf(
                ContractCondition(
                    id = "condition-1",
                    description = "Value exceeds threshold",
                    conditionType = ConditionType.THRESHOLD,
                    expression = "$.value >= 50"
                )
            ),
            obligations = listOf(...)
        ),
        // ... other fields
    )
).getOrThrow()
```

## Engine Hash Calculation

The engine hash is computed from the compiled class file bytecode using SHA-256:

```kotlin
// Hash is computed automatically by BaseEvaluationEngine
// The hash is computed from class file bytecode using SHA-256
// Returns: "uABC123..." (multibase-encoded)
val hash = engine.implementationHash
```

### Hash Verification Flow

1. **During Binding**: Engine hash is computed and stored in the contract credential
2. **During Execution**: Current engine hash is compared with stored hash
3. **If Mismatch**: `SecurityException` is thrown - engine may have been tampered with

```kotlin
// During contract binding (automatic via extension function)
val executionModelWithHash = contract.executionModel.withEngineHash(engines)

// During contract execution (automatic in DefaultSmartContractService)
val engineRef = contract.executionModel.toEngineReference()
val engine = engines.require(engineRef.engineId!!)
engineRef.expectedHash?.let { expectedHash ->
    engines.verifyOrThrow(engineRef.engineId, expectedHash)
}
```

## Tamper Protection

The evaluation engine framework provides multiple layers of tamper protection:

### 1. Cryptographic Hashing

Engine implementations are hashed using SHA-256, and the hash is:
- Stored in the contract's `ExecutionModel`
- Included in the signed credential
- Verified before each evaluation

### 2. Credential Signing

The `executionModel` (including `engineHash`) is part of the credential subject, which is:
- Cryptographically signed by the issuer
- Anchored to blockchain for immutability
- Verified during credential verification

### 3. Runtime Verification

Before evaluating conditions, the system:
- Retrieves the engine from the registry
- Computes the current engine hash
- Compares with the hash stored in the contract
- Throws `SecurityException` if mismatch detected

## Example: Parametric Insurance Engine

The `ParametricInsuranceEngine` is included as an example implementation:

```kotlin
val engines = EvaluationEngines()
val engine = ParametricInsuranceEngine()
engines += engine

// Create parametric insurance contract
val contract = vericore.contracts.draft(
    request = ContractDraftRequest(
        executionModel = ExecutionModel.Parametric(
            triggerType = TriggerType.EarthObservation,
            evaluationEngine = "parametric-insurance"
        ),
        terms = ContractTerms(
            conditions = listOf(
                ContractCondition(
                    id = "flood-threshold",
                    description = "Flood depth >= 50cm",
                    conditionType = ConditionType.THRESHOLD,
                    expression = "$.floodDepthCm >= 50"
                )
            ),
            obligations = listOf(...)
        )
    )
)

// Bind contract (engine hash is captured)
val bound = vericore.contracts.bindContract(
    contractId = contract.id,
    issuerDid = issuerDid,
    issuerKeyId = issuerKeyId
)

// Execute contract (engine integrity is verified)
val result = vericore.contracts.executeContract(
    contract = bound.contract,
    executionContext = ExecutionContext(
        triggerData = buildJsonObject {
            put("floodDepthCm", 75.0)
        }
    )
)
```

## Expression Formats

Different engines may support different expression formats. The `ParametricInsuranceEngine` supports:

### Threshold Expressions

```
$.path >= value
$.path <= value
$.path == value
$.path > value
$.path < value
$.path != value
```

Examples:
- `$.floodDepthCm >= 50`
- `$.temperature <= 30`
- `$.rainfallMm == 100`

### Range Expressions

```
$.path >= min && $.path <= max
```

Example:
- `$.temperature >= 20 && $.temperature <= 30`

### Comparison Expressions

```
$.path1 >= $.path2
$.path1 <= $.path2
$.path1 == $.path2
```

Example:
- `$.currentValue > $.previousValue`

## Best Practices

### 1. Engine Versioning

Always increment version when making breaking changes:

```kotlin
override val version: String = "1.0.0"  // Initial version
override val version: String = "1.1.0"  // Minor update
override val version: String = "2.0.0"  // Breaking change
```

### 2. Condition Type Support

Only declare support for condition types you actually implement:

```kotlin
override val supportedConditionTypes: Set<ConditionType> = setOf(
    ConditionType.THRESHOLD  // Only declare what you support
)
```

### 3. Error Handling

Provide clear error messages for invalid expressions:

```kotlin
if (expression.contains(">=")) {
    // Parse threshold
} else {
    throw IllegalArgumentException(
        "Invalid expression: $expression. " +
        "Expected format: \$.path >= value"
    )
}
```

### 4. Testing

Test engines thoroughly before registration:

```kotlin
@Test
fun `test threshold evaluation`() {
    val engine = MyEngine()
    val condition = ContractCondition(
        id = "test",
        description = "Test",
        conditionType = ConditionType.THRESHOLD,
        expression = "$.value >= 50"
    )
    val inputData = buildJsonObject { put("value", 75.0) }
    
    val result = runBlocking {
        engine.evaluateCondition(condition, inputData, context)
    }
    
    assertTrue(result)
}
```

## Integration with VeriCore

Evaluation engines integrate seamlessly with VeriCore's contract system:

### Contract Creation

```kotlin
val contract = vericore.contracts.draft(
    request = ContractDraftRequest(
        executionModel = ExecutionModel.Parametric(
            triggerType = TriggerType.EarthObservation,
            evaluationEngine = "parametric-insurance"
        ),
        // ... other fields
    )
)
```

### Contract Binding

During binding, the engine hash is automatically captured:

```kotlin
val bound = vericore.contracts.bindContract(
    contractId = contract.id,
    issuerDid = issuerDid,
    issuerKeyId = issuerKeyId
)
// bound.contract.executionModel.engineHash contains the hash
```

### Contract Execution

During execution, engine integrity is automatically verified:

```kotlin
val result = vericore.contracts.executeContract(
    contract = contract,
    executionContext = ExecutionContext(
        triggerData = inputData
    )
)
// Engine integrity is verified automatically
```

## Security Considerations

1. **Engine Registration**: Only register engines from trusted sources
2. **Hash Verification**: Always verify engine hashes before evaluation
3. **Version Compatibility**: Check engine versions for compatibility
4. **Expression Validation**: Validate expressions before evaluation
5. **Input Sanitization**: Sanitize input data to prevent injection attacks

## Troubleshooting

### Engine Not Found

```
Evaluation engine 'my-engine' is not registered
```

**Solution**: Register the engine before creating contracts:

```kotlin
val engines = EvaluationEngines()
engines += MyEngine()
```

### Integrity Check Failed

```
Evaluation engine 'my-engine' integrity check failed
```

**Solution**: The engine has been modified. Re-register the original engine or update the contract.

### Version Mismatch

```
Evaluation engine version mismatch. Expected: 1.0.0, Actual: 2.0.0
```

**Solution**: Use the correct engine version or update the contract to use the new version.

## See Also

- [Smart Contracts](smart-contracts.md) for contract lifecycle and execution models
- [Verifiable Credentials](verifiable-credentials.md) for credential issuance and verification
- [Blockchain Anchoring](blockchain-anchoring.md) for anchoring concepts
- [API Reference](../api-reference/contract-api.md) for complete API documentation

