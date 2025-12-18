# Kotlin Result Pattern Language Feature Proposal

## Overview

This proposal introduces native language support for Result patterns in Kotlin, making error handling more concise, type-safe, and ergonomic. The feature addresses the common pattern of using sealed class hierarchies for Result types (as seen extensively in the TrustWeave codebase) by providing first-class language support.

## Current Pain Points

Based on real-world usage patterns, developers currently face:

1. **Boilerplate**: Each Result type requires:
   - Sealed class definition
   - Nested Failure sealed class
   - Multiple error case data classes
   - Repetitive extension functions (onSuccess, onFailure, getOrThrow, fold)
   - Manual isSuccess/isFailure properties

2. **Verbose Pattern Matching**: `when` expressions are verbose and repetitive:
   ```kotlin
   when (val result = kms.getPublicKeyResult(keyId)) {
       is GetPublicKeyResult.Success -> {
           val handle = result.keyHandle
           // Use key handle
       }
       is GetPublicKeyResult.Failure.KeyNotFound -> {
           println("Key not found: ${result.keyId}")
       }
       is GetPublicKeyResult.Failure.Error -> {
           // Handle error
       }
   }
   ```

3. **No Error Propagation**: Unlike Rust's `?` operator, Kotlin requires manual error handling at each step.

4. **Type Erasure**: Standard `Result<T>` loses specific error type information.

## Proposed Feature: Native Result Types

### 1. Result Type Declaration Syntax

Introduce a new `result` keyword for declaring Result types with built-in success/error variants:

```kotlin
// Current approach (verbose):
sealed class GetPublicKeyResult {
    data class Success(val keyHandle: KeyHandle) : GetPublicKeyResult()
    sealed class Failure : GetPublicKeyResult() {
        data class KeyNotFound(val keyId: KeyId) : Failure()
        data class Error(val keyId: KeyId, val reason: String, val cause: Throwable?) : Failure()
    }
}

// Proposed approach (concise):
result GetPublicKeyResult {
    success KeyHandle
    failure {
        KeyNotFound(keyId: KeyId)
        Error(keyId: KeyId, reason: String, cause: Throwable? = null)
    }
}
```

**Compilation**: This generates the same sealed class hierarchy as the current approach, but with:
- Automatic `isSuccess`/`isFailure` properties
- Built-in `onSuccess`, `onFailure`, `getOrThrow`, `fold` methods
- Smart casting for success values
- Exhaustive checking in `when` expressions

### 2. Result Expression Syntax

Allow direct construction of Result values:

```kotlin
// Success case
fun getKey(): GetPublicKeyResult = success(keyHandle)

// Failure cases
fun getKey(): GetPublicKeyResult = failure.KeyNotFound(keyId)
fun getKey(): GetPublicKeyResult = failure.Error(keyId, "Failed", cause)
```

### 3. Pattern Matching Improvements

Enhanced `when` expressions with destructuring and automatic smart casting:

```kotlin
// Current:
when (val result = kms.getPublicKeyResult(keyId)) {
    is GetPublicKeyResult.Success -> {
        val handle = result.keyHandle  // Manual property access
        // Use handle
    }
    is GetPublicKeyResult.Failure.KeyNotFound -> {
        println("Key not found: ${result.keyId}")
    }
    is GetPublicKeyResult.Failure.Error -> {
        // Handle error
    }
}

// Proposed:
when (val result = kms.getPublicKeyResult(keyId)) {
    success(handle) -> {
        // handle is automatically available, smart-cast to KeyHandle
        // Use handle directly
    }
    failure.KeyNotFound(keyId) -> {
        // keyId is automatically destructured
        println("Key not found: $keyId")
    }
    failure.Error(keyId, reason, cause) -> {
        // All properties automatically destructured
        // Handle error
    }
}
```

### 4. Error Propagation Operator (`?`)

Introduce Rust-style error propagation:

```kotlin
// Current (verbose):
suspend fun processKey(keyId: KeyId): ProcessResult {
    val keyResult = kms.getPublicKeyResult(keyId)
    if (keyResult is GetPublicKeyResult.Failure) {
        return ProcessResult.Failure.KeyNotFound(keyResult.keyId)
    }
    val keyHandle = keyResult.keyHandle
    
    val signResult = kms.signResult(keyHandle.id, data)
    if (signResult is SignResult.Failure) {
        return ProcessResult.Failure.SignError(signResult.reason)
    }
    val signature = signResult.signature
    
    return ProcessResult.Success(signature)
}

// Proposed (concise):
suspend fun processKey(keyId: KeyId): ProcessResult {
    val keyHandle = kms.getPublicKeyResult(keyId)?  // Auto-propagate on failure
    val signature = kms.signResult(keyHandle.id, data)?  // Auto-propagate on failure
    return success(signature)
}
```

**Behavior**: The `?` operator:
- Extracts success value if result is success
- Returns early with appropriate failure if result is failure
- Automatically maps failure types when propagating between different Result types

### 5. Result Builder DSL

For complex error handling chains:

```kotlin
// Current:
val result = kms.getPublicKeyResult(keyId)
    .fold(
        onFailure = { failure -> /* handle */ },
        onSuccess = { handle -> 
            kms.signResult(handle.id, data)
                .fold(
                    onFailure = { /* handle */ },
                    onSuccess = { signature -> /* use */ }
                )
        }
    )

// Proposed:
result {
    val keyHandle = kms.getPublicKeyResult(keyId)?
    val signature = kms.signResult(keyHandle.id, data)?
    success(signature)  // Final success value
} catch { failure ->
    // Handle any propagated failures
    ProcessResult.Failure.from(failure)
}
```

### 6. Automatic Result Type Inference

When using `?` operator, the compiler infers the appropriate Result type:

```kotlin
fun process(): ProcessResult {
    val keyHandle = kms.getPublicKeyResult(keyId)?  // Inferred: GetPublicKeyResult -> KeyHandle
    val signature = kms.signResult(keyHandle.id, data)?  // Inferred: SignResult -> ByteArray
    return success(signature)  // Inferred: ProcessResult.Success(ByteArray)
}
```

### 7. Result Type Aliases and Composition

Allow composing Result types:

```kotlin
// Define base error types
result KmsError {
    failure {
        KeyNotFound(keyId: KeyId)
        UnsupportedAlgorithm(algorithm: Algorithm)
        Error(reason: String, cause: Throwable?)
    }
}

// Compose with success types
result GetPublicKeyResult = Result<KeyHandle, KmsError>
result SignResult = Result<ByteArray, KmsError>
result GenerateKeyResult = Result<KeyHandle, KmsError>
```

## Migration Path

The feature is designed to be:
- **Backward compatible**: Existing sealed class Result types continue to work
- **Gradual adoption**: Can be adopted incrementally
- **Interoperable**: New Result types work with existing extension functions

## Example: Real-World Transformation

### Before (Current Code):

```kotlin
override suspend fun getPublicKeyResult(keyId: KeyId): GetPublicKeyResult = withContext(Dispatchers.IO) {
    try {
        val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId.value)
        val describeResponse = kmsClient.describeKey(
            DescribeKeyRequest.builder()
                .keyId(resolvedKeyId)
                .build()
        )
        val keyMetadata = describeResponse.keyMetadata()
        val keySpec = keyMetadata.keySpec()
        val algorithmName = keySpec.toString()
        val algorithm = parseAlgorithmFromKeySpec(algorithmName)
            ?: return@withContext GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Unknown key spec: $algorithmName"
            )

        val publicKeyResponse = kmsClient.getPublicKey(
            GetPublicKeyRequest.builder()
                .keyId(resolvedKeyId)
                .build()
        )
        val publicKeyBytes = publicKeyResponse.publicKey().asByteArray()
        val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

        GetPublicKeyResult.Success(
            KeyHandle(
                id = KeyId(keyMetadata.arn() ?: keyMetadata.keyId()),
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        )
    } catch (e: AwsServiceException) {
        if (e.statusCode() == 404 || e.awsErrorDetails()?.errorCode() == "NotFoundException") {
            GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
        } else {
            GetPublicKeyResult.Failure.Error(
                keyId = keyId,
                reason = "Failed to get public key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    } catch (e: Exception) {
        GetPublicKeyResult.Failure.Error(
            keyId = keyId,
            reason = "Failed to get public key: ${e.message ?: "Unknown error"}",
            cause = e
        )
    }
}
```

### After (With Proposed Feature):

```kotlin
override suspend fun getPublicKeyResult(keyId: KeyId): GetPublicKeyResult = withContext(Dispatchers.IO) {
    result {
        val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId.value)
        
        val describeResponse = kmsClient.describeKey(
            DescribeKeyRequest.builder()
                .keyId(resolvedKeyId)
                .build()
        )
        val keyMetadata = describeResponse.keyMetadata()
        val keySpec = keyMetadata.keySpec()
        val algorithmName = keySpec.toString()
        val algorithm = parseAlgorithmFromKeySpec(algorithmName)
            ?: return failure.Error(
                keyId = keyId,
                reason = "Unknown key spec: $algorithmName"
            )

        val publicKeyResponse = kmsClient.getPublicKey(
            GetPublicKeyRequest.builder()
                .keyId(resolvedKeyId)
                .build()
        )
        val publicKeyBytes = publicKeyResponse.publicKey().asByteArray()
        val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

        success(
            KeyHandle(
                id = KeyId(keyMetadata.arn() ?: keyMetadata.keyId()),
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        )
    } catch (e: AwsServiceException) {
        if (e.statusCode() == 404 || e.awsErrorDetails()?.errorCode() == "NotFoundException") {
            failure.KeyNotFound(keyId = keyId)
        } else {
            failure.Error(
                keyId = keyId,
                reason = "Failed to get public key: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    } catch (e: Exception) {
        failure.Error(
            keyId = keyId,
            reason = "Failed to get public key: ${e.message ?: "Unknown error"}",
            cause = e
        )
    }
}
```

## Benefits

1. **Reduced Boilerplate**: ~70% reduction in Result type definitions
2. **Improved Readability**: More concise and expressive code
3. **Better Type Safety**: Compiler-enforced exhaustive checking
4. **Error Propagation**: Rust-style `?` operator for cleaner error handling
5. **Smart Casting**: Automatic type narrowing in pattern matching
6. **Composability**: Easy to compose and transform Result types

## Implementation Considerations

1. **Compiler Plugin**: Initial implementation could be a compiler plugin
2. **IR Transformation**: Transform Result syntax to sealed classes at IR level
3. **IDE Support**: Enhanced autocomplete and refactoring for Result types
4. **Interoperability**: Seamless integration with existing Result extension functions

## Future Enhancements

1. **Async Result Propagation**: `await?` for coroutines
2. **Result Comprehensions**: List comprehension-style syntax for Result chains
3. **Automatic Error Mapping**: Type-safe error conversion between Result types
4. **Result Debugging**: Enhanced debugging experience with Result types

