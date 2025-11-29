---
title: Writing Tests Guide
---

# Writing Tests Guide

This guide explains how to write tests for TrustWeave plugins and components.

## Test Structure

### Unit Tests

Use `BasePluginTest` for unit tests:

```kotlin
import com.trustweave.testkit.BasePluginTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class MyPluginTest : BasePluginTest() {

    @Test
    fun testSomething() = runBlocking {
        val fixture = createFixture()
        // Test code
    }
}
```

### Integration Tests

Use `BaseIntegrationTest` for integration tests:

```kotlin
import com.trustweave.testkit.BaseIntegrationTest
import org.junit.jupiter.api.Tag
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@Tag("integration")
class MyIntegrationTest : BaseIntegrationTest() {

    @Test
    fun testWithRealService() = runBlocking {
        val fixture = createFixture()
        // Integration test code
    }
}
```

## Test Templates

Use the provided templates as starting points:

### Plugin Test Templates

- **DID Methods**: `docs/contributing/test-templates/DidMethodTestTemplate.kt`
- **KMS Plugins**: `docs/contributing/test-templates/KmsPluginTestTemplate.kt`
- **Chain Plugins**: `docs/contributing/test-templates/ChainPluginTestTemplate.kt`

### Trust Layer Integration Test Templates

Comprehensive in-memory workflow templates for trust layer integration tests:

- **Location**: `core/TrustWeave-trust/src/test/kotlin/com/geoknoesis/TrustWeave/integration/InMemoryTrustLayerIntegrationTest.kt`
- **Templates Available**:
  - Complete in-memory workflow
  - Credential revocation workflow
  - Wallet storage workflow
  - Verifiable presentation workflow
  - DID update workflow
  - Blockchain anchoring workflow
  - Smart contract workflow
  - External services template

See [Trust Layer Test Templates](trust-layer-test-templates.md) for detailed documentation and examples.

## Test Checklist

For each plugin, ensure tests cover:

- ✅ **Happy Path**: Successful operations
- ✅ **Error Handling**: Invalid inputs, network errors
- ✅ **Edge Cases**: Null values, empty collections, boundary values
- ✅ **Algorithm Variations**: Different algorithms/configurations
- ✅ **SPI Discovery**: Automatic plugin discovery (if applicable)
- ✅ **Interface Compliance**: All interface methods tested

## Test Naming

Use descriptive test names:

```kotlin
@Test
fun `test create DID with Ed25519 algorithm`() = runBlocking {
    // Test code
}

@Test
fun `test resolve non-existent DID returns error`() = runBlocking {
    // Test code
}
```

## Test Organization

Organize tests by functionality:

```kotlin
class MyPluginTest : BasePluginTest() {

    @Test
    fun testCreate() = runBlocking { }

    @Test
    fun testRead() = runBlocking { }

    @Test
    fun testUpdate() = runBlocking { }

    @Test
    fun testDelete() = runBlocking { }
}
```

## Using Test Fixtures

```kotlin
class MyTest : BasePluginTest() {

    @Test
    fun testWithCustomFixture() = runBlocking {
        withFixture({
            withDidMethodPlugin("key")
            withKmsPlugin("aws", mapOf("region" to "us-east-1"))
        }) { fixture ->
            // Test code
        }
    }
}
```

## Best Practices

1. **Test Isolation**: Each test should be independent
2. **Cleanup**: Use `use {}` pattern for automatic cleanup
3. **Assertions**: Use descriptive assertion messages
4. **Error Testing**: Test both success and failure cases
5. **Coverage**: Aim for 80%+ line coverage

## Next Steps

- [Trust Layer Test Templates](trust-layer-test-templates.md) - Comprehensive workflow templates
- [Test Patterns](test-patterns.md) - Common test patterns and examples
- [Integration Testing](integration-testing.md) - Integration test best practices
- [Plugin Credential Handling](plugin-credential-handling.md) - Handling external service credentials

