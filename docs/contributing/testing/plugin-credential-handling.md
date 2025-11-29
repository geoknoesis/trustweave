---
title: Plugin Credential Handling for Tests
---

# Plugin Credential Handling for Tests

This guide explains how TrustWeave handles tests that require external credentials, tokens, or API keys for plugins.

## Overview

TrustWeave uses a **self-describing plugin architecture** where each plugin advertises what environment variables it needs. Tests can be annotated to require specific plugins, and the test framework automatically skips tests when required credentials are not available.

## How It Works

1. **Each plugin provider declares required environment variables** via the `requiredEnvironmentVariables` property
2. **Tests are annotated** with `@RequiresPlugin("plugin-name")` to indicate they need a plugin
3. **The test framework automatically discovers plugins** via ServiceLoader
4. **Tests are skipped** if the plugin's required environment variables are not set

## Plugin Provider Interface

All plugin providers (KMS, DID methods, blockchain chains) implement methods to advertise their requirements:

```kotlin
interface KeyManagementServiceProvider {
    val requiredEnvironmentVariables: List<String>
    fun hasRequiredEnvironmentVariables(): Boolean
    // ...
}
```

### Required vs Optional Environment Variables

- **Required**: Listed without prefix (e.g., `"AWS_REGION"`)
- **Optional**: Prefixed with `"?"` (e.g., `"?AWS_SESSION_TOKEN"`)

## Using @RequiresPlugin Annotation

### Basic Usage

```kotlin
import com.trustweave.testkit.annotations.RequiresPlugin

@RequiresPlugin("aws")
@Test
fun `test AWS KMS`() = runBlocking {
    // Test will be skipped if AWS_REGION is not set
    val kms = // ... create AWS KMS
}
```

### Multiple Plugins

```kotlin
@RequiresPlugin("google-cloud-kms", "ethereum")
@Test
fun `test with multiple plugins`() = runBlocking {
    // Test will be skipped if either plugin's env vars are missing
}
```

### Plugin Names

Plugin names match the provider name:
- **KMS providers**: `"aws"`, `"azure"`, `"google-cloud-kms"`, `"hashicorp"`, etc.
- **DID method providers**: `"ethr"`, `"ion"`, `"polygon"`, `"key"`, `"web"`, etc.
- **Blockchain anchor providers**: `"ethereum"`, `"algorand"`, `"polygon"`, etc.

## Implementing Plugin Requirements

### Example: AWS KMS Provider

```kotlin
class AwsKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "aws"

    override val requiredEnvironmentVariables: List<String> = listOf(
        "AWS_REGION",  // Required
        "?AWS_ACCESS_KEY_ID",  // Optional (can use IAM roles)
        "?AWS_SECRET_ACCESS_KEY"  // Optional (can use IAM roles)
    )

    override fun hasRequiredEnvironmentVariables(): Boolean {
        // Custom logic: Check for region OR IAM role availability
        return (System.getenv("AWS_REGION") != null ||
                System.getenv("AWS_DEFAULT_REGION") != null) ||
               // Check if running on AWS (IAM role available)
               try {
                   Class.forName("software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider")
                   true
               } catch (e: ClassNotFoundException) {
                   false
               }
    }
}
```

### Example: DID Method Provider

```kotlin
class EthrDidMethodProvider : DidMethodProvider {
    override val name: String = "ethr"

    override val requiredEnvironmentVariables: List<String> = listOf(
        "ETHEREUM_RPC_URL",
        "?ETHEREUM_PRIVATE_KEY"  // Optional if only resolving
    )
}
```

### Example: Blockchain Anchor Provider

```kotlin
class AlgorandBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {
    override val name: String = "algorand"

    override val requiredEnvironmentVariables: List<String> = listOf(
        "ALGORAND_ALGOD_URL",
        "?ALGORAND_ALGOD_TOKEN",
        "?ALGORAND_INDEXER_URL"
    )
}
```

## Configuration

### Environment Variables

Control test behavior with environment variables:

```bash
# Skip tests when credentials are missing (default: true)
export VERICORE_TEST_SKIP_IF_NO_CREDENTIALS=true

# Fail tests when credentials are missing
export VERICORE_TEST_SKIP_IF_NO_CREDENTIALS=false
```

### Programmatic Check

You can also check credentials programmatically:

```kotlin
import com.trustweave.testkit.config.TestConfig

@Test
fun `test with manual check`() = runBlocking {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        TestConfig.skipIfNoCredentials(),
        "Credentials not available"
    )
    // Test code
}
```

## Benefits

1. **Scalable**: Each plugin declares its own requirements - no centralized list
2. **Self-documenting**: Plugin requirements are visible in the code
3. **Automatic**: Tests are skipped automatically when credentials are missing
4. **Flexible**: Supports optional environment variables
5. **Discoverable**: Uses ServiceLoader to find plugins automatically

## Best Practices

1. **Always declare required env vars** in your plugin provider
2. **Use optional prefix `"?"`** for env vars that have fallbacks (IAM roles, default credentials, etc.)
3. **Override `hasRequiredEnvironmentVariables()`** for custom logic (e.g., checking for IAM roles)
4. **Document env vars** in your plugin's README or documentation
5. **Use `@RequiresPlugin`** on tests that actually need the plugin to function

## Example: Complete Plugin Implementation

```kotlin
// kms/plugins/myplugin/src/main/kotlin/MyKmsProvider.kt
class MyKmsProvider : KeyManagementServiceProvider {
    override val name: String = "my-kms"

    override val supportedAlgorithms: Set<Algorithm> = setOf(
        Algorithm.Ed25519,
        Algorithm.Secp256k1
    )

    // Declare required environment variables
    override val requiredEnvironmentVariables: List<String> = listOf(
        "MY_KMS_API_KEY",
        "MY_KMS_ENDPOINT",
        "?MY_KMS_REGION"  // Optional
    )

    // Optional: Override for custom credential checking logic
    override fun hasRequiredEnvironmentVariables(): Boolean {
        return System.getenv("MY_KMS_API_KEY") != null &&
               System.getenv("MY_KMS_ENDPOINT") != null
    }

    override fun create(options: Map<String, Any?>): KeyManagementService {
        // Implementation
    }
}
```

## Testing Your Plugin

```kotlin
// kms/plugins/myplugin/src/test/kotlin/MyKmsProviderTest.kt
class MyKmsProviderTest {

    @Test
    @RequiresPlugin("my-kms")
    fun `test with credentials`() = runBlocking {
        // This test will be skipped if MY_KMS_API_KEY is not set
        val provider = MyKmsProvider()
        val kms = provider.create(mapOf(
            "apiKey" to System.getenv("MY_KMS_API_KEY")!!,
            "endpoint" to System.getenv("MY_KMS_ENDPOINT")!!
        ))
        // Test code
    }
}
```

## See Also

- [Testing Guidelines](testing-guidelines.md) - General testing best practices
- [Integration Testing](integration-testing.md) - Integration test patterns
- [Test Setup Guide](test-setup-guide.md) - Environment setup

