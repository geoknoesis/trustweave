# KMS Plugin Testing Guide

**Version**: 1.0  
**Last Updated**: 2025-01-27  
**Audience**: Plugin Developers

---

## Overview

This guide provides best practices for testing KMS plugin implementations in TrustWeave. It covers test structure, edge cases, performance testing, and integration testing strategies.

---

## Table of Contents

1. [Test Structure](#test-structure)
2. [Contract Tests](#contract-tests)
3. [Edge Case Tests](#edge-case-tests)
4. [Performance Tests](#performance-tests)
5. [Integration Tests](#integration-tests)
6. [Best Practices](#best-practices)
7. [Common Pitfalls](#common-pitfalls)

---

## Test Structure

### Recommended Test Classes

Each plugin should implement the following test classes:

1. **Contract Tests** - Extend `KeyManagementServiceContractTest`
2. **Edge Case Tests** - Extend `PluginEdgeCaseTestTemplate`
3. **Performance Tests** - Extend `KeyManagementServicePerformanceTest`
4. **Integration Tests** - Extend `KmsIntegrationTest` (if using testkit)
5. **Provider-Specific Tests** - Custom tests for provider-specific features

### Example Structure

```
kms/plugins/myplugin/src/test/kotlin/org.trustweave/myplugin/
├── MyPluginContractTest.kt          # Contract compliance
├── MyPluginEdgeCaseTest.kt           # Edge cases
├── MyPluginPerformanceTest.kt       # Performance benchmarks
├── MyPluginIntegrationTest.kt       # Integration tests
└── MyPluginSpecificTest.kt          # Provider-specific tests
```

---

## Contract Tests

### Purpose

Contract tests ensure your plugin correctly implements the `KeyManagementService` interface contract.

### Implementation

```kotlin
class MyPluginContractTest : KeyManagementServiceContractTest() {
    override fun createKms(): KeyManagementService {
        return MyKeyManagementService(createTestConfig())
    }

    override fun getSupportedAlgorithms(): List<Algorithm> {
        return MyKeyManagementService.SUPPORTED_ALGORITHMS.toList()
    }
}
```

### What Contract Tests Cover

- ✅ All supported algorithms generate successfully
- ✅ Public key retrieval works correctly
- ✅ Signing operations succeed
- ✅ Key deletion is idempotent
- ✅ Error handling for unsupported algorithms
- ✅ Error handling for missing keys
- ✅ Full key lifecycle (generate → get → sign → delete)

---

## Edge Case Tests

### Purpose

Edge case tests validate your plugin handles unusual inputs and error scenarios gracefully.

### Implementation

```kotlin
class MyPluginEdgeCaseTest : PluginEdgeCaseTestTemplate() {
    override fun createKms(): KeyManagementService {
        return MyKeyManagementService(createTestConfig())
    }

    override fun getSupportedAlgorithms(): List<Algorithm> {
        return MyKeyManagementService.SUPPORTED_ALGORITHMS.toList()
    }

    @Test
    fun `test provider-specific rate limiting`() = runBlocking {
        val kms = createKms()
        // Test rate limiting behavior
    }
}
```

### Required Edge Cases

- ✅ Empty/null inputs
- ✅ Very large inputs (10MB+)
- ✅ Invalid key IDs
- ✅ Concurrent operations
- ✅ Provider-specific error scenarios
- ✅ Rate limiting scenarios
- ✅ Network timeout scenarios
- ✅ Invalid credentials scenarios

### Provider-Specific Edge Cases

Add tests for provider-specific scenarios:

- **AWS**: Throttling, service unavailability, region-specific errors
- **Azure**: Rate limiting, subscription issues, key vault access policies
- **Google**: Quota exceeded, project access issues
- **HashiCorp**: Token expiration, vault unavailability
- **IBM**: Instance access issues, key protection policies

---

## Performance Tests

### Purpose

Performance tests measure operation latency and throughput to identify regressions.

### Implementation

```kotlin
class MyPluginPerformanceTest : KeyManagementServicePerformanceTest() {
    override fun createKms(): KeyManagementService {
        return MyKeyManagementService(createTestConfig())
    }

    override fun getSupportedAlgorithms(): List<Algorithm> {
        return MyKeyManagementService.SUPPORTED_ALGORITHMS.toList()
    }
}
```

### Performance Metrics

- **Key Generation**: Should complete in < 1 second (for in-memory)
- **Signing**: Should complete in < 500ms (for in-memory)
- **Public Key Retrieval**: Should complete in < 50ms (with caching)
- **Concurrent Operations**: Should scale linearly

### Performance Thresholds

Adjust thresholds based on your provider:

- **In-Memory**: Very fast (< 100ms for most operations)
- **Cloud KMS**: Network latency + provider latency (typically 100-500ms)
- **HSM**: Hardware-dependent (typically 50-200ms)

---

## Integration Tests

### Purpose

Integration tests validate your plugin works with actual provider services.

### Implementation

```kotlin
@RequiresPlugin("myplugin")
class MyPluginIntegrationTest : KmsIntegrationTest() {
    override fun createKms(): KeyManagementService {
        return MyKeyManagementService(loadCredentials())
    }

    @Test
    fun `test real key generation`() = runBlocking {
        val kms = createKms()
        val result = kms.generateKeyResult(Algorithm.Ed25519)
        assertTrue(result is GenerateKeyResult.Success)
    }
}
```

### Integration Test Requirements

- Use `@RequiresPlugin` annotation to skip if credentials unavailable
- Test with real provider services
- Clean up resources after tests
- Handle provider-specific rate limits

---

## Best Practices

### 1. Test Isolation

- Each test should be independent
- Clean up resources (keys) after each test
- Use unique key IDs to avoid conflicts

### 2. Error Handling

- Test all failure scenarios
- Verify error messages are actionable
- Ensure errors are logged appropriately

### 3. Thread Safety

- Test concurrent operations
- Verify thread-safe behavior
- Use `ConcurrentHashMap` for shared state

### 4. Input Validation

- Test invalid inputs
- Test boundary conditions
- Test edge cases (empty, null, very large)

### 5. Caching

- Test cache hits and misses
- Test cache invalidation
- Verify TTL behavior

### 6. Idempotency

- Test delete operations are idempotent
- Verify multiple calls produce same result

---

## Common Pitfalls

### ❌ Don't: Hard-code Test Data

```kotlin
// ❌ Bad
val keyId = KeyId("test-key")

// ✅ Good
val keyId = KeyId("test-key-${System.currentTimeMillis()}")
```

### ❌ Don't: Ignore Error Cases

```kotlin
// ❌ Bad
val result = kms.generateKeyResult(algorithm)
// Assume success

// ✅ Good
val result = kms.generateKeyResult(algorithm)
assertTrue(result is GenerateKeyResult.Success, "Key generation failed: $result")
```

### ❌ Don't: Skip Edge Cases

```kotlin
// ❌ Bad
@Test
fun `test signing`() = runBlocking {
    val result = kms.signResult(keyId, "normal data".toByteArray())
    assertTrue(result is SignResult.Success)
}

// ✅ Good
@Test
fun `test signing with empty data`() = runBlocking {
    val result = kms.signResult(keyId, ByteArray(0))
    // Test behavior
}

@Test
fun `test signing with large data`() = runBlocking {
    val largeData = ByteArray(10 * 1024 * 1024)
    val result = kms.signResult(keyId, largeData)
    // Test behavior
}
```

### ❌ Don't: Forget Cleanup

```kotlin
// ❌ Bad
@Test
fun `test key generation`() = runBlocking {
    val result = kms.generateKeyResult(algorithm)
    // Key remains in provider
}

// ✅ Good
@Test
fun `test key generation`() = runBlocking {
    val result = kms.generateKeyResult(algorithm)
    assertTrue(result is GenerateKeyResult.Success)
    
    // Cleanup
    val keyId = result.keyHandle.id
    kms.deleteKeyResult(keyId)
}
```

---

## Test Coverage Checklist

- [ ] Contract tests for all supported algorithms
- [ ] Edge case tests for invalid inputs
- [ ] Edge case tests for boundary conditions
- [ ] Performance tests for all operations
- [ ] Integration tests with real provider
- [ ] Provider-specific error scenarios
- [ ] Concurrent operation tests
- [ ] Cache behavior tests
- [ ] Idempotency tests
- [ ] Input validation tests
- [ ] Error message validation
- [ ] Resource cleanup tests

---

## Resources

- [KeyManagementServiceContractTest](../kms/kms-core/src/test/kotlin/org.trustweave/kms/KeyManagementServiceContractTest.kt)
- [PluginEdgeCaseTestTemplate](../testkit/src/main/kotlin/org.trustweave/testkit/kms/PluginEdgeCaseTestTemplate.kt)
- [KeyManagementServicePerformanceTest](../kms/kms-core/src/test/kotlin/org.trustweave/kms/KeyManagementServicePerformanceTest.kt)
- [KmsIntegrationTest](../testkit/src/main/kotlin/org.trustweave/testkit/integration/KmsIntegrationTest.kt)

---

## Questions?

If you have questions about testing your plugin, please:
1. Review existing plugin tests (AWS, Azure, Google, InMemory)
2. Check the testkit module for utilities
3. Review the code review documents for examples

