# Test Patterns

Common test patterns and examples for VeriCore.

## Pattern 1: Basic Plugin Test

```kotlin
class MyPluginTest : BasePluginTest() {
    
    @Test
    fun testBasicOperation() = runBlocking {
        val plugin = createPlugin()
        val result = plugin.doSomething()
        assertNotNull(result)
    }
}
```

## Pattern 2: Error Handling Test

```kotlin
@Test
fun testInvalidInputThrowsException() = runBlocking {
    val plugin = createPlugin()
    
    assertThrows<IllegalArgumentException> {
        runBlocking {
            plugin.doSomething("invalid-input")
        }
    }
}
```

## Pattern 3: Integration Test with TestContainers

```kotlin
@Testcontainers
@Tag("integration")
class MyIntegrationTest : BaseIntegrationTest() {
    
    companion object {
        @JvmStatic
        val localStack = LocalStackContainer.create()
    }
    
    @Test
    fun testWithRealService() = runBlocking {
        val config = createConfig(localStack.getEndpoint())
        val service = createService(config)
        
        val result = service.operation()
        assertNotNull(result)
    }
}
```

## Pattern 4: Retry Logic for Flaky Tests

```kotlin
@Test
fun testWithRetry() = runBlocking {
    retry(maxAttempts = 3) {
        val result = flakyOperation()
        assertNotNull(result)
    }
}
```

## Pattern 5: Test with Multiple Plugins

```kotlin
@Test
fun testMultiplePlugins() = runBlocking {
    val scenario = MultiPluginScenario(fixture)
    scenario.testMultipleDidMethods(listOf(method1, method2))
}
```

## Pattern 6: Credential Lifecycle Test

```kotlin
@Test
fun testCredentialLifecycle() = runBlocking {
    val scenario = CredentialLifecycleScenario(fixture)
    scenario.execute()
}
```

## Pattern 7: Test with Custom Fixture

```kotlin
@Test
fun testWithCustomSetup() = runBlocking {
    withFixture({
        withDidMethodPlugin("key")
        withKmsPlugin("aws", mapOf("region" to "us-east-1"))
        withChainPlugin("eip155:1", mapOf("rpcUrl" to "http://localhost:8545"))
    }) { fixture ->
        // Test code
    }
}
```

## Pattern 8: Parameterized Tests

```kotlin
@ParameterizedTest
@ValueSource(strings = ["Ed25519", "Secp256k1", "P256"])
fun testWithDifferentAlgorithms(algorithm: String) = runBlocking {
    val keyHandle = kms.generateKey(Algorithm.fromName(algorithm)!!)
    assertNotNull(keyHandle)
}
```

## Pattern 9: Test Data Builders

```kotlin
@Test
fun testWithTestData() = runBlocking {
    val credentialSubject = fixture.createTestCredentialSubject(
        id = "did:key:test",
        additionalClaims = mapOf(
            "name" to "Test User",
            "email" to "test@example.com"
        )
    )
    
    assertNotNull(credentialSubject)
}
```

## Pattern 10: Async Assertions

```kotlin
@Test
fun testAsyncOperation() = runBlocking {
    val operation = startAsyncOperation()
    
    assertEventually(timeoutSeconds = 10) {
        operation.isComplete()
    }
}
```

## Common Assertions

```kotlin
// Basic assertions
assertNotNull(value)
assertTrue(condition)
assertEquals(expected, actual)

// Collection assertions
assertTrue(collection.isNotEmpty())
assertTrue(collection.contains(item))

// String assertions
assertTrue(string.startsWith("prefix"))
assertTrue(string.matches(Regex("pattern")))
```

## Next Steps

- [Integration Testing](integration-testing.md) - Integration test best practices
- [Test Setup Guide](test-setup-guide.md) - Environment setup

