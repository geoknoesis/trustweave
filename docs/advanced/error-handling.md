# Error Handling

TrustWeave provides structured error handling with rich context for better debugging and error recovery.

## Overview

All TrustWeave API operations return `Result<T>`, which provides a consistent way to handle both success and failure cases. Errors are automatically converted to `TrustWeaveError` types with structured context.

## Error Types

TrustWeave uses a sealed hierarchy of error types that extend `TrustWeaveException`. All errors include:
- **code**: String error code for programmatic handling
- **message**: Human-readable error message
- **context**: Map of additional context information
- **cause**: Optional underlying exception

### Complete Error Type Reference

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `DidNotFound` | `DID_NOT_FOUND` | `did`, `availableMethods` | DID resolution fails |
| `DidMethodNotRegistered` | `DID_METHOD_NOT_REGISTERED` | `method`, `availableMethods` | Using unregistered DID method |
| `InvalidDidFormat` | `INVALID_DID_FORMAT` | `did`, `reason` | DID format validation fails |
| `CredentialInvalid` | `CREDENTIAL_INVALID` | `reason`, `credentialId`, `field` | Credential validation fails |
| `CredentialIssuanceFailed` | `CREDENTIAL_ISSUANCE_FAILED` | `reason`, `issuerDid` | Credential issuance fails |
| `ChainNotRegistered` | `CHAIN_NOT_REGISTERED` | `chainId`, `availableChains` | Using unregistered blockchain |
| `WalletCreationFailed` | `WALLET_CREATION_FAILED` | `reason`, `provider`, `walletId` | Wallet creation fails |
| `PluginNotFound` | `PLUGIN_NOT_FOUND` | `pluginId`, `pluginType` | Plugin lookup fails |
| `PluginInitializationFailed` | `PLUGIN_INITIALIZATION_FAILED` | `pluginId`, `reason` | Plugin initialization fails |
| `ValidationFailed` | `VALIDATION_FAILED` | `field`, `reason`, `value` | Input validation fails |
| `InvalidOperation` | `INVALID_OPERATION` | `message`, `context`, `cause` | Invalid operation attempted |
| `InvalidState` | `INVALID_STATE` | `message`, `context`, `cause` | Invalid state detected |
| `Unknown` | `UNKNOWN_ERROR` | `message`, `context`, `cause` | Unhandled exception |

### DID-Related Errors

```kotlin
// DID not found
TrustWeaveError.DidNotFound(
    did = "did:key:z6Mk...",
    availableMethods = listOf("key", "web")
)

// DID method not registered
TrustWeaveError.DidMethodNotRegistered(
    method = "web",
    availableMethods = listOf("key")
)

// Invalid DID format
TrustWeaveError.InvalidDidFormat(
    did = "invalid-did",
    reason = "DID must match format: did:<method>:<identifier>"
)
```

### Credential-Related Errors

```kotlin
// Credential validation failed
TrustWeaveError.CredentialInvalid(
    reason = "Credential issuer is required",
    credentialId = "urn:uuid:123",
    field = "issuer"
)

// Credential issuance failed
TrustWeaveError.CredentialIssuanceFailed(
    reason = "Failed to sign credential",
    issuerDid = "did:key:issuer"
)
```

### Blockchain-Related Errors

```kotlin
// Chain not registered
TrustWeaveError.ChainNotRegistered(
    chainId = "ethereum:mainnet",
    availableChains = listOf("algorand:testnet", "polygon:testnet")
)
```

### Wallet-Related Errors

```kotlin
// Wallet creation failed
TrustWeaveError.WalletCreationFailed(
    reason = "Provider not found",
    provider = "database",
    walletId = "wallet-123"
)
```

### Plugin-Related Errors

```kotlin
// Plugin not found
TrustWeaveError.PluginNotFound(
    pluginId = "waltid-credential",
    pluginType = "credential-service"
)

// Plugin initialization failed
TrustWeaveError.PluginInitializationFailed(
    pluginId = "waltid-credential",
    reason = "Configuration missing"
)
```

### Validation Errors

```kotlin
// Validation failed
TrustWeaveError.ValidationFailed(
    field = "issuer",
    reason = "Invalid DID format",
    value = "invalid-did"
)
```

### Generic Errors

```kotlin
// Invalid operation
TrustWeaveError.InvalidOperation(
    code = "INVALID_OPERATION",
    message = "Operation not allowed in current state",
    context = mapOf("operation" to "createDid", "state" to "stopped"),
    cause = null
)

// Invalid state
TrustWeaveError.InvalidState(
    code = "INVALID_STATE",
    message = "TrustWeave not initialized",
    context = emptyMap(),
    cause = null
)

// Unknown error (catch-all)
TrustWeaveError.Unknown(
    code = "UNKNOWN_ERROR",
    message = "Unexpected error occurred",
    context = emptyMap(),
    cause = originalException
)
```

## Error Code Quick Reference

Quick lookup table for common error codes and their solutions:

| Code | Error Type | Common Causes | Solutions |
|------|------------|---------------|-----------|
| `DID_NOT_FOUND` | `DidNotFound` | DID not resolvable, method not registered, network issue | Check DID format, ensure method registered, verify network connectivity |
| `DID_METHOD_NOT_REGISTERED` | `DidMethodNotRegistered` | Method not in registry | Register method via `registerDidMethod()` or use available method from `getAvailableDidMethods()` |
| `INVALID_DID_FORMAT` | `InvalidDidFormat` | DID doesn't match `did:<method>:<identifier>` format | Validate DID format before use, check for typos |
| `CREDENTIAL_INVALID` | `CredentialInvalid` | Missing required fields, invalid structure, missing proof | Check credential structure, ensure all required fields present |
| `CREDENTIAL_ISSUANCE_FAILED` | `CredentialIssuanceFailed` | Signing failed, key not found, DID resolution failed | Verify issuer DID is resolvable, check key exists in DID document |
| `CHAIN_NOT_REGISTERED` | `ChainNotRegistered` | Chain not registered in registry | Register blockchain client via `registerBlockchainClient()` or use available chain from `getAvailableChains()` |
| `WALLET_CREATION_FAILED` | `WalletCreationFailed` | Provider not found, configuration invalid, storage unavailable | Check provider name, verify configuration, ensure storage accessible |
| `PLUGIN_NOT_FOUND` | `PluginNotFound` | Plugin not on classpath, not registered | Add plugin dependency, register plugin manually |
| `PLUGIN_INITIALIZATION_FAILED` | `PluginInitializationFailed` | Configuration missing, connection failed, dependency issue | Check plugin configuration, verify dependencies, test connectivity |
| `VALIDATION_FAILED` | `ValidationFailed` | Input doesn't meet requirements | Validate inputs before operations, check format requirements |

## Common Pitfalls

### Pitfall 1: Using getOrThrow() in Production

❌ **Bad:**
```kotlin
// Throws exception, crashes application
val did = TrustWeave.dids.create()
```

✅ **Good:**
```kotlin
// Handle errors gracefully
val did = TrustWeave.dids.create()
// Note: dids.create() returns DidDocument directly, not Result
// For error handling, wrap in try-catch
result.fold(
    onSuccess = { did -> 
        // Process DID
        processDid(did)
    },
    onFailure = { error -> 
        // Handle error appropriately
        logger.error("Failed to create DID", error)
        // Show user-friendly message or retry
    }
)
```

**Why:** `getOrThrow()` throws exceptions that can crash your application. Use `fold()` for production code.

### Pitfall 2: Not Checking Error Context

❌ **Bad:**
```kotlin
result.fold(
    onFailure = { error -> 
        println("Error: ${error.message}")  // Loses valuable context
    }
)
```

✅ **Good:**
```kotlin
result.fold(
    onFailure = { error ->
        logger.error("Error: ${error.message}")
        logger.debug("Error code: ${error.code}")
        logger.debug("Context: ${error.context}")
        
        // Use context for better error handling
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                logger.info("Available methods: ${error.availableMethods}")
                // Suggest alternatives to user
            }
            is TrustWeaveError.ChainNotRegistered -> {
                logger.info("Available chains: ${error.availableChains}")
                // Suggest fallback chains
            }
            // ... handle other specific errors
        }
    }
)
```

**Why:** Error context contains valuable debugging information and alternative options.

### Pitfall 3: Ignoring Warnings in Verification Results

❌ **Bad:**
```kotlin
val verification = TrustWeave.verifyCredential(credential).getOrThrow()
if (verification.valid) {
    // Use credential without checking warnings
    processCredential(credential)
}
```

✅ **Good:**
```kotlin
val verification = TrustWeave.verifyCredential(credential).getOrThrow()
if (verification.valid) {
    // Check warnings before using
    if (verification.warnings.isNotEmpty()) {
        logger.warn("Credential has warnings: ${verification.warnings}")
        // Decide if warnings are acceptable for your use case
    }
    
    // Check specific validation flags
    if (!verification.proofValid) {
        logger.error("Proof validation failed")
        return
    }
    
    if (!verification.notRevoked) {
        logger.warn("Credential may be revoked")
        // Handle revocation appropriately
    }
    
    processCredential(credential)
}
```

**Why:** Warnings indicate potential issues that may affect credential validity in the future.

### Pitfall 4: Not Validating Inputs Before Operations

❌ **Bad:**
```kotlin
// No validation, may fail with cryptic error
val resolution = TrustWeave.dids.resolve(userInputDid)
```

✅ **Good:**
```kotlin
// Validate before operation
val validation = DidValidator.validateFormat(userInputDid)
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    return Result.failure(
        TrustWeaveError.InvalidDidFormat(
            did = userInputDid,
            reason = error.message
        )
    )
}

// Now safe to proceed
val resolution = TrustWeave.dids.resolve(userInputDid)
```

**Why:** Early validation provides better error messages and prevents unnecessary operations.

### Pitfall 5: Not Handling Specific Error Types

❌ **Bad:**
```kotlin
result.fold(
    onFailure = { error -> 
        // Generic handling loses specific error information
        println("Something went wrong: ${error.message}")
    }
)
```

✅ **Good:**
```kotlin
result.fold(
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                // Specific handling for method not registered
                logger.warn("Method not registered: ${error.method}")
                logger.info("Available methods: ${error.availableMethods}")
                // Register method or suggest alternatives
            }
            is TrustWeaveError.InvalidDidFormat -> {
                // Specific handling for invalid format
                logger.error("Invalid DID format: ${error.reason}")
                // Show format requirements to user
            }
            is TrustWeaveError.CredentialInvalid -> {
                // Specific handling for invalid credential
                logger.error("Credential invalid: ${error.reason}")
                logger.debug("Field: ${error.field}")
                // Fix credential or reject
            }
            else -> {
                // Generic handling for unknown errors
                logger.error("Unexpected error: ${error.message}", error)
            }
        }
    }
)
```

**Why:** Specific error types provide actionable information for recovery.

## Error Handling Patterns

### Basic Error Handling

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.core.*

val TrustWeave = TrustWeave.create()

// Handle errors with fold
val did = TrustWeave.dids.create()
// Note: dids.create() returns DidDocument directly, not Result
// For error handling, wrap in try-catch
result.fold(
    onSuccess = { did -> 
        println("Created DID: ${did.id}")
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            else -> {
                println("Error: ${error.message}")
                error.context.forEach { (key, value) ->
                    println("  $key: $value")
                }
            }
        }
    }
)
```

### Using getOrThrow for Simple Cases

```kotlin
// For simple cases where you want to throw on error
val did = TrustWeave.dids.create()

// For better error messages, use getOrThrowError
val did = TrustWeave.dids.create() // Throws TrustWeaveError on failure
```

### Error Context

All errors include context information that can help with debugging:

```kotlin
val result = TrustWeave.anchor(data, serializer, "ethereum:mainnet")
result.fold(
    onSuccess = { anchor -> println("Anchored: ${anchor.ref.txHash}") },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.ChainNotRegistered -> {
                println("Chain ID: ${error.chainId}")
                println("Available chains: ${error.availableChains}")
                println("Context: ${error.context}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

### Converting Exceptions to Errors

TrustWeave automatically converts exceptions to `TrustWeaveError`:

```kotlin
import com.trustweave.core.toTrustWeaveError

try {
    // Some operation that might throw
    val result = someOperation()
} catch (e: Exception) {
    val error = e.toTrustWeaveError()
    println("Error code: ${error.code}")
    println("Context: ${error.context}")
}
```

## Result Utilities

TrustWeave provides extension functions for working with `Result<T>`:

### mapError

Transform errors in a Result:

```kotlin
val did = TrustWeave.dids.create()
// Note: dids.create() returns DidDocument directly, not Result
// For error handling, wrap in try-catch
    .mapError { it.toTrustWeaveError() }
```

### combine

Combine multiple Results:

```kotlin
val results = listOf(
    async { TrustWeave.dids.create() },
    async { TrustWeave.dids.create() },
    async { TrustWeave.dids.create() }
)

val combined = results.combine { dids ->
    dids.map { it.id }
}

combined.fold(
    onSuccess = { ids -> println("Created DIDs: $ids") },
    onFailure = { error -> println("Error: ${error.message}") }
)
```

### mapAsync

Batch operations with async mapping:

```kotlin
val dids = listOf("did:key:1", "did:key:2", "did:key:3")
val results = dids.mapAsync { did ->
    TrustWeave.dids.resolve(did)
}

results.fold(
    onSuccess = { resolutions -> 
        resolutions.forEach { println("Resolved: ${it.document?.id}") }
    },
    onFailure = { error -> println("Error: ${error.message}") }
)
```

## Input Validation

TrustWeave validates inputs before operations to catch errors early:

### DID Validation

```kotlin
import com.trustweave.core.DidValidator

// Validate DID format
val validation = DidValidator.validateFormat("did:key:z6Mk...")
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Validation failed: ${error.message}")
    println("Field: ${error.field}")
    println("Value: ${error.value}")
}

// Validate DID method
val availableMethods = listOf("key", "web")
val methodValidation = DidValidator.validateMethod("did:key:z6Mk...", availableMethods)
if (!methodValidation.isValid()) {
    println("Method not supported")
}
```

### Credential Validation

```kotlin
import com.trustweave.core.CredentialValidator

// Validate credential structure
val validation = CredentialValidator.validateStructure(credential)
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Credential validation failed: ${error.message}")
    println("Field: ${error.field}")
}

// Validate proof
val proofValidation = CredentialValidator.validateProof(credential)
if (!proofValidation.isValid()) {
    println("Credential missing proof")
}
```

### Chain ID Validation

```kotlin
import com.trustweave.core.ChainIdValidator

// Validate chain ID format
val validation = ChainIdValidator.validateFormat("algorand:testnet")
if (!validation.isValid()) {
    println("Invalid chain ID format")
}

// Validate chain is registered
val availableChains = listOf("algorand:testnet", "polygon:testnet")
val registeredValidation = ChainIdValidator.validateRegistered("ethereum:mainnet", availableChains)
if (!registeredValidation.isValid()) {
    println("Chain not registered")
}
```

## Best Practices

### 1. Always Handle Errors

```kotlin
// ❌ Bad: Ignoring errors
val did = TrustWeave.dids.create()

// ✅ Good: Handling errors explicitly
val did = TrustWeave.dids.create()
// Note: dids.create() returns DidDocument directly, not Result
// For error handling, wrap in try-catch
result.fold(
    onSuccess = { did -> /* handle success */ },
    onFailure = { error -> /* handle error */ }
)
```

### 2. Use Error Context

```kotlin
// ✅ Good: Use error context for debugging
val result = TrustWeave.anchor(data, serializer, chainId)
result.fold(
    onSuccess = { /* success */ },
    onFailure = { error ->
        logger.error("Anchoring failed", error)
        logger.debug("Error context: ${error.context}")
        logger.debug("Error code: ${error.code}")
    }
)
```

### 3. Check Error Types

```kotlin
// ✅ Good: Handle specific error types
val did = TrustWeave.dids.create(method = "web")
result.fold(
    onSuccess = { /* success */ },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                // Suggest available methods
                println("Method 'web' not available. Try: ${error.availableMethods}")
            }
            is TrustWeaveError.InvalidDidFormat -> {
                // Show format requirements
                println("Invalid format: ${error.reason}")
            }
            else -> {
                // Generic error handling
                println("Error: ${error.message}")
            }
        }
    }
)
```

### 4. Validate Inputs Early

```kotlin
// ✅ Good: Validate before operation
val did = "did:key:z6Mk..."
val validation = DidValidator.validateFormat(did)
if (!validation.isValid()) {
    return Result.failure(TrustWeaveError.InvalidDidFormat(did, validation.errorMessage() ?: ""))
}

val resolution = TrustWeave.dids.resolve(did)
```

### 5. Use Result Utilities

```kotlin
// ✅ Good: Use combine for batch operations
val dids = listOf("did:key:1", "did:key:2", "did:key:3")
    val results = dids.map { TrustWeave.dids.resolve(it) }

val combined = results.combine { resolutions ->
    resolutions.mapNotNull { it.document?.id }
}

combined.fold(
    onSuccess = { ids -> println("Resolved: $ids") },
    onFailure = { error -> println("Error: ${error.message}") }
)
```

## Plugin Lifecycle Errors

When managing plugin lifecycles, errors are handled automatically:

```kotlin
val TrustWeave = TrustWeave.create()

// Initialize plugins
TrustWeave.initialize().fold(
    onSuccess = { println("Plugins initialized") },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.PluginInitializationFailed -> {
                println("Plugin ${error.pluginId} failed to initialize: ${error.reason}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)

// Start plugins
TrustWeave.start().fold(
    onSuccess = { println("Plugins started") },
    onFailure = { error -> println("Error starting plugins: ${error.message}") }
)

// Stop plugins
TrustWeave.stop().fold(
    onSuccess = { println("Plugins stopped") },
    onFailure = { error -> println("Error stopping plugins: ${error.message}") }
)

// Cleanup plugins
TrustWeave.cleanup().fold(
    onSuccess = { println("Plugins cleaned up") },
    onFailure = { error -> println("Error cleaning up: ${error.message}") }
)
```

## Migration Guide

If you're migrating from exception-based error handling to Result-based:

### Before (Exception-based)

```kotlin
try {
    val did = TrustWeave.dids.create()
    val credential = TrustWeave.issueCredential(...)
} catch (e: IllegalArgumentException) {
    println("Invalid argument: ${e.message}")
} catch (e: Exception) {
    println("Error: ${e.message}")
}
```

### After (Result-based)

```kotlin
val did = TrustWeave.dids.create()
didResult.fold(
    onSuccess = { did ->
        val credentialResult = TrustWeave.issueCredential(...)
        credentialResult.fold(
            onSuccess = { credential -> /* success */ },
            onFailure = { error -> /* handle error */ }
        )
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

## Error Recovery Patterns

### Retry with Exponential Backoff

For transient errors (network issues, temporary unavailability), implement retry logic:

```kotlin
import kotlinx.coroutines.delay
import kotlin.random.Random

suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    multiplier: Double = 2.0,
    operation: suspend () -> Result<T>
): Result<T> {
    var delay = initialDelay.toDouble()
    var lastError: TrustWeaveError? = null
    
    repeat(maxRetries) { attempt ->
        val result = operation()
        
        result.fold(
            onSuccess = { return result },
            onFailure = { error ->
                lastError = error
                
                // Don't retry on certain errors
                when (error) {
                    is TrustWeaveError.InvalidDidFormat,
                    is TrustWeaveError.CredentialInvalid,
                    is TrustWeaveError.ValidationFailed -> {
                        return result // Don't retry validation errors
                    }
                    else -> {
                        if (attempt < maxRetries - 1) {
                            val jitter = Random.nextLong(0, (delay * 0.1).toLong())
                            val actualDelay = minOf((delay + jitter).toLong(), maxDelay)
                            delay(actualDelay)
                            delay *= multiplier
                        }
                    }
                }
            }
        )
    }
    
    return Result.failure(lastError ?: TrustWeaveError.Unknown(
        code = "RETRY_EXHAUSTED",
        message = "Operation failed after $maxRetries retries",
        context = emptyMap(),
        cause = null
    ))
}

// Usage
val result = retryWithBackoff {
    TrustWeave.dids.resolve("did:web:example.com")
}
```

### Fallback to Alternative Methods

When a DID method fails, try alternative methods:

```kotlin
suspend fun resolveDidWithFallback(
    did: String,
    preferredMethods: List<String> = listOf("web", "key", "peer")
): Result<DidResolutionResult> {
    val method = did.substringAfter("did:").substringBefore(":")
    
    // Try preferred method first
    if (method in preferredMethods) {
        val resolution = TrustWeave.dids.resolve(did)
        if (result.isSuccess) return result
    }
    
    // Try fallback methods
    for (fallbackMethod in preferredMethods) {
        if (fallbackMethod == method) continue
        
        val fallbackDid = did.replace("did:$method:", "did:$fallbackMethod:")
        val resolution = TrustWeave.dids.resolve(fallbackDid)
        if (result.isSuccess) {
            return result
        }
    }
    
    return Result.failure(TrustWeaveError.DidNotFound(
        did = did,
        availableMethods = TrustWeave.getAvailableDidMethods()
    ))
}
```

### Automatic Method Registration

Automatically register missing DID methods when available:

```kotlin
suspend fun createDidWithAutoRegistration(
    method: String,
    options: DidCreationOptions? = null
): Result<DidDocument> {
    val did = TrustWeave.dids.create(method, options)
    
    return result.fold(
        onSuccess = { did -> Result.success(did) },
        onFailure = { error ->
            when (error) {
                is TrustWeaveError.DidMethodNotRegistered -> {
                    // Try to find and register the method
                    val methodClass = findDidMethodClass(method)
                    if (methodClass != null) {
                        TrustWeave.registerDidMethod(methodClass)
                        // Retry after registration
                        TrustWeave.dids.create(method, options)
                    } else {
                        Result.failure(error)
                    }
                }
                else -> Result.failure(error)
            }
        }
    )
}

// Helper to find method class (implementation depends on your SPI setup)
fun findDidMethodClass(method: String): DidMethod? {
    // Use ServiceLoader or reflection to find available methods
    return ServiceLoader.load(DidMethod::class.java)
        .firstOrNull { it.methodName() == method }
}
```

### Circuit Breaker Pattern

Prevent cascading failures with a circuit breaker:

```kotlin
enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val timeout: Long = 60000, // 1 minute
    private val halfOpenMaxCalls: Int = 3
) {
    private var state = CircuitState.CLOSED
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var halfOpenSuccessCount = 0
    
    suspend fun <T> execute(operation: suspend () -> Result<T>): Result<T> {
        when (state) {
            CircuitState.CLOSED -> {
                val result = operation()
                result.fold(
                    onSuccess = { 
                        failureCount = 0
                        return result
                    },
                    onFailure = {
                        failureCount++
                        if (failureCount >= failureThreshold) {
                            state = CircuitState.OPEN
                            lastFailureTime = System.currentTimeMillis()
                        }
                        return result
                    }
                )
            }
            CircuitState.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > timeout) {
                    state = CircuitState.HALF_OPEN
                    halfOpenSuccessCount = 0
                } else {
                    return Result.failure(TrustWeaveError.InvalidState(
                        code = "CIRCUIT_OPEN",
                        message = "Circuit breaker is open",
                        context = mapOf("timeout" to timeout.toString()),
                        cause = null
                    ))
                }
            }
            CircuitState.HALF_OPEN -> {
                val result = operation()
                result.fold(
                    onSuccess = {
                        halfOpenSuccessCount++
                        if (halfOpenSuccessCount >= halfOpenMaxCalls) {
                            state = CircuitState.CLOSED
                            failureCount = 0
                        }
                        return result
                    },
                    onFailure = {
                        state = CircuitState.OPEN
                        lastFailureTime = System.currentTimeMillis()
                        return result
                    }
                )
            }
        }
    }
}

// Usage
val circuitBreaker = CircuitBreaker()

val result = circuitBreaker.execute {
    TrustWeave.dids.resolve("did:web:example.com")
}
```

### Graceful Degradation

Provide fallback behavior when operations fail:

```kotlin
suspend fun verifyCredentialWithFallback(
    credential: VerifiableCredential,
    strictMode: Boolean = false
): CredentialVerificationResult {
    val result = TrustWeave.verifyCredential(credential).getOrNull()
    
    if (result != null && result.valid) {
        return result
    }
    
    // Fallback: Basic validation without network calls
    if (!strictMode) {
        val basicValidation = CredentialValidator.validateStructure(credential)
        if (basicValidation.isValid()) {
            return CredentialVerificationResult(
                valid = true,
                proofValid = false, // Unknown without network
                notExpired = credential.expirationDate?.let { 
                    Instant.parse(it).isAfter(Instant.now()) 
                } ?: true,
                notRevoked = null, // Unknown without network
                warnings = listOf("Full verification unavailable, using basic validation"),
                errors = emptyList()
            )
        }
    }
    
    return result ?: CredentialVerificationResult(
        valid = false,
        errors = listOf("Verification failed and no fallback available")
    )
}
```

### Error Aggregation

Collect multiple errors before failing:

```kotlin
suspend fun batchResolveDids(
    dids: List<String>
): Result<Map<String, DidResolutionResult>> {
    val results = mutableMapOf<String, DidResolutionResult>()
    val errors = mutableListOf<TrustWeaveError>()
    
    dids.forEach { did ->
        val resolution = TrustWeave.dids.resolve(did)
        result.fold(
            onSuccess = { resolution -> results[did] = resolution },
            onFailure = { error -> errors.add(error) }
        )
    }
    
    return if (errors.isEmpty() || results.isNotEmpty()) {
        Result.success(results)
    } else {
        Result.failure(TrustWeaveError.Unknown(
            code = "BATCH_FAILED",
            message = "All operations failed: ${errors.size} errors",
            context = mapOf("errors" to errors.map { it.code }.joinToString()),
            cause = null
        ))
    }
}
```

### Timeout Handling

Add timeouts to prevent hanging operations:

```kotlin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

suspend fun <T> withTimeoutOrError(
    timeoutMillis: Long,
    operation: suspend () -> Result<T>
): Result<T> {
    return try {
        withTimeout(timeoutMillis) {
            operation()
        }
    } catch (e: TimeoutCancellationException) {
        Result.failure(TrustWeaveError.Unknown(
            code = "OPERATION_TIMEOUT",
            message = "Operation timed out after ${timeoutMillis}ms",
            context = mapOf("timeout" to timeoutMillis.toString()),
            cause = e
        ))
    }
}

// Usage
val result = withTimeoutOrError(5000) {
    TrustWeave.dids.resolve("did:web:example.com")
}
```

## Related Documentation

- [API Reference](../api-reference/)
- [Verification Policies](verification-policies.md)
- [Plugin Lifecycle](../advanced/plugin-lifecycle.md)
- [Troubleshooting](../getting-started/troubleshooting.md)

