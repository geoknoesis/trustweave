---
title: Integration Testing Best Practices
---

# Integration Testing Best Practices

This guide covers best practices for writing integration tests in TrustWeave.

## Overview

Integration tests verify that multiple components work together correctly. They may use real services (via TestContainers) or test networks.

## TestContainers Usage

### LocalStack (AWS Services)

```kotlin
@Testcontainers
class AwsKmsIntegrationTest : KmsIntegrationTest() {

    companion object {
        @JvmStatic
        val localStack = LocalStackContainer.create()
    }

    override fun getKms(): KeyManagementService {
        val config = AwsKmsConfig.builder()
            .region("us-east-1")
            .endpointOverride(localStack.getKmsEndpoint())
            .build()
        return AwsKeyManagementService(config)
    }
}
```

### HashiCorp Vault

```kotlin
@Testcontainers
class VaultKmsIntegrationTest : KmsIntegrationTest() {

    companion object {
        @JvmStatic
        val vault = VaultContainer.create()
    }

    override fun getKms(): KeyManagementService {
        val config = VaultKmsConfig.builder()
            .address(vault.getVaultUrl())
            .token(vault.getRootToken())
            .build()
        return VaultKeyManagementService(config)
    }
}
```

### Ganache (Ethereum)

`GanacheContainer` lives in `anchors/plugins/ganache` (test sources). Instantiate it directly — there is no `create()` factory.

```kotlin
@Testcontainers
class EthereumIntegrationTest : ChainIntegrationTest() {

    companion object {
        @JvmStatic
        val ganache = GanacheContainer().apply { start() }
    }

    override fun getChainId(): String = "eip155:1337"

    override fun getChainClient(): BlockchainAnchorClient {
        // EthereumBlockchainAnchorClient takes (chainId, options: Map<String, Any?>).
        return EthereumBlockchainAnchorClient(
            chainId = getChainId(),
            options = mapOf(
                "rpcUrl" to ganache.getRpcUrl(),
                "privateKey" to ganache.getFirstAccountPrivateKey()
            )
        )
    }
}
```

## Test Isolation

Each integration test should be isolated:

```kotlin
@BeforeEach
override fun setUp() {
    super.setUp()
    // Additional setup
}

@AfterEach
override fun tearDown() {
    super.tearDown()
    // Additional cleanup
}
```

## Retry Logic

Use retry for flaky operations:

```kotlin
@Test
fun testFlakyOperation() = runBlocking {
    retry(maxAttempts = 3, delayMs = 1000) {
        val result = operation()
        assertNotNull(result)
    }
}
```

## Timeout Handling

Set appropriate timeouts:

```kotlin
@Test
fun testWithTimeout() = runBlocking {
    assertEventually(timeoutSeconds = 30) {
        condition.isMet()
    }
}
```

## Test Scenarios

Use reusable scenarios:

```kotlin
@Test
fun testCredentialLifecycle() = runBlocking {
    val scenario = CredentialLifecycleScenario(fixture)
    scenario.execute()
}

@Test
fun testMultiplePlugins() = runBlocking {
    val scenario = MultiPluginScenario(fixture)
    scenario.testMultipleDidMethods(listOf(method1, method2))
}
```

## Best Practices

1. **Use TestContainers**: Prefer TestContainers over real services
2. **Tag Tests**: Tag integration tests with `@Tag("integration")`
3. **Isolate Tests**: Each test should be independent
4. **Cleanup**: Always clean up resources
5. **Retry Flaky Tests**: Use retry logic for network operations
6. **Set Timeouts**: Configure appropriate timeouts
7. **Skip When Needed**: Skip tests if services unavailable

## Skipping Integration Tests

```bash
# Skip integration tests
./gradlew test -PskipIntegrationTests=true

# Or set environment variable
export VERICORE_SKIP_INTEGRATION_TESTS=true
./gradlew test
```

## Next Steps

- Test Setup Guide](test-setup-guide.md) - Environment setup
- Writing Tests](writing-tests.md) - Writing new tests

