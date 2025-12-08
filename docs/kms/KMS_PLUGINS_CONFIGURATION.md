---
title: KMS Plugins Configuration Guide
nav_order: 12
parent: KMS Documentation
---

# KMS Plugins Configuration Guide

**Version**: 1.0  
**Last Updated**: 2025-01-27  
**Status**: Current

---

## Overview

This guide provides comprehensive configuration documentation for all TrustWeave KMS plugins. All plugins implement the `KeyManagementService` interface and use the Result-based API for type-safe error handling.

---

## Table of Contents

1. [Using KeyManagementServices Factory](#using-keymanagementservices-factory)
2. [Common Configuration Patterns](#common-configuration-patterns)
3. [AWS KMS](#aws-kms)
4. [Azure Key Vault](#azure-key-vault)
5. [Google Cloud KMS](#google-cloud-kms)
6. [HashiCorp Vault](#hashicorp-vault)
7. [IBM Key Protect](#ibm-key-protect)
8. [InMemory KMS](#inmemory-kms)
9. [WaltID KMS](#waltid-kms)
10. [SPI Auto-Discovery](#spi-auto-discovery)
11. [Common Options](#common-options)

---

## Using KeyManagementServices

`KeyManagementServices` is a factory that simplifies creating KMS instances from any available plugin. It automatically discovers and manages all KMS plugins on your classpath, making it easy to switch between different key management providers.

### How It Works with Plugins

When you add a KMS plugin dependency to your project (e.g., `trustweave-kms-aws`), the plugin automatically registers itself with `KeyManagementServices`. The factory maintains a registry of all available plugins and can instantiate them on demand.

**Example:** Adding AWS KMS plugin to your `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.trustweave.kms:aws:1.0.0-SNAPSHOT")
}
```

Once the dependency is added, the AWS provider becomes immediately available through the factory:

```kotlin
import com.trustweave.kms.*

val kms = KeyManagementServices.create("aws", mapOf("region" to "us-east-1"))
```

### Why It's Useful

- **Simplified API**: Create KMS instances with a single method call
- **Plugin Discovery**: Automatically finds all available plugins on your classpath
- **Consistent Interface**: Same API works for all providers
- **Type Safety**: Supports typed configuration builders for compile-time validation
- **Better Developer Experience**: Clear error messages and IDE autocomplete support
- **Easy Provider Switching**: Change providers by just changing the provider name

### Creating KMS Instances

#### Map-Based Configuration

```kotlin
import com.trustweave.kms.*

// AWS KMS
val awsKms = KeyManagementServices.create("aws", mapOf(
    "region" to "us-east-1"
))

// Azure Key Vault
val azureKms = KeyManagementServices.create("azure", mapOf(
    "vaultUrl" to "https://myvault.vault.azure.net"
))

// Google Cloud KMS
val googleKms = KeyManagementServices.create("google-cloud-kms", mapOf(
    "projectId" to "my-project",
    "location" to "us-east1"
))

// HashiCorp Vault
val vaultKms = KeyManagementServices.create("vault", mapOf(
    "address" to "http://localhost:8200",
    "token" to "hvs.xxx"
))

// IBM Key Protect
val ibmKms = KeyManagementServices.create("ibm", mapOf(
    "apiKey" to "your-api-key",
    "instanceId" to "your-instance-id"
))

// InMemory (no configuration needed)
val inMemoryKms = KeyManagementServices.create("inmemory")
```

#### Typed Configuration (Recommended)

For providers that support typed configuration builders, you get compile-time safety and IDE autocomplete:

```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

// Type-safe AWS configuration
val awsKms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
    accessKeyId = "AKIA..."
    secretAccessKey = "..."
    endpointOverride = "http://localhost:4566"  // For LocalStack
    cacheTtlSeconds = 300
})
```

### Discovering Providers

#### List Available Providers

```kotlin
import com.trustweave.kms.*

val providers = KeyManagementServices.availableProviders()
println("Available providers: $providers")
// Output: [aws, azure, google-cloud-kms, vault, ibm, inmemory, waltid]
```

#### Get Provider Instance (Advanced)

For advanced use cases where you need direct access to the provider:

```kotlin
import com.trustweave.kms.*

val provider = KeyManagementServices.getProvider("aws")
if (provider != null) {
    // Check algorithm support
    val supportsEd25519 = provider.supportsAlgorithm(Algorithm.Ed25519)
    
    // Check environment variables
    val hasEnvVars = provider.hasRequiredEnvironmentVariables()
}
```

### Error Handling

The factory provides helpful error messages:

```kotlin
import com.trustweave.kms.*

try {
    val kms = KeyManagementServices.create("unknown-provider")
} catch (e: IllegalArgumentException) {
    println(e.message)
    // Output: KMS provider 'unknown-provider' not found. 
    //         Available providers: [aws, azure, google-cloud-kms, vault, ibm, inmemory, waltid]
}
```

### Instance Caching

`KeyManagementServices` uses caching to improve performance and avoid expensive setup costs. Calling `create()` multiple times with the same provider and configuration returns the same cached instance.

### Provider Names Reference

| Provider | Name | Configuration Required |
|----------|------|------------------------|
| AWS KMS | `aws` | Yes (region) |
| Azure Key Vault | `azure` | Yes (vaultUrl) |
| Google Cloud KMS | `google-cloud-kms` | Yes (projectId, location) |
| HashiCorp Vault | `vault` | Yes (address, token) |
| IBM Key Protect | `ibm` | Yes (apiKey, instanceId) |
| InMemory | `inmemory` | No |
| WaltID | `waltid` | Depends on configuration |

### Best Practices

1. **Use Typed Configuration When Available**: Provides compile-time safety and better IDE support
2. **Handle Errors Gracefully**: Always catch `IllegalArgumentException` when provider name might be dynamic
3. **Check Provider Availability**: Use `availableProviders()` to verify a provider is available before attempting to create it
4. **Use Factory for All Code**: `KeyManagementServices.create()` is the recommended way to create KMS instances

---

## Common Configuration Patterns

All KMS plugins follow consistent patterns:

### 1. Builder Pattern

```kotlin
val config = PluginKmsConfig.builder()
    .requiredParam("value")
    .optionalParam("value")
    .build()

val kms = PluginKeyManagementService(config)
```

### 2. Environment Variables

```kotlin
val config = PluginKmsConfig.fromEnvironment()
val kms = PluginKeyManagementService(config ?: throw IllegalStateException("Config not found"))
```

### 3. Factory API (Recommended)

```kotlin
import com.trustweave.kms.*

val kms = KeyManagementServices.create("provider-name", mapOf(
    "param1" to "value1",
    "param2" to "value2"
))
```

### 4. Map-Based Configuration (SPI - Legacy)

```kotlin
// Direct provider usage (not recommended - use factory instead)
val kms = provider.create(mapOf(
    "param1" to "value1",
    "param2" to "value2"
))
```

### 5. Result-Based API

All operations return sealed Result types:

```kotlin
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        // Use keyHandle
    }
    is GenerateKeyResult.Failure -> {
        // Handle error
    }
}
```

---

## AWS KMS

### Configuration Class

```kotlin
data class AwsKmsConfig(
    val region: String,                          // Required
    val accessKeyId: String? = null,             // Optional (uses IAM role if not provided)
    val secretAccessKey: String? = null,         // Optional
    val sessionToken: String? = null,            // Optional (for temporary credentials)
    val endpointOverride: String? = null,        // Optional (for local testing)
    val pendingWindowInDays: Int? = null,        // Optional (7-30 days, default 30)
    val cacheTtlSeconds: Long? = 300             // Optional (default 5 minutes)
)
```

### Builder Pattern

```kotlin
import com.trustweave.awskms.*

// Using IAM Role (Recommended for EC2/ECS/Lambda)
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .build()

// Using Access Keys
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .accessKeyId("AKIAIOSFODNN7EXAMPLE")
    .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
    .build()

// With Temporary Credentials
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .accessKeyId("AKIAIOSFODNN7EXAMPLE")
    .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
    .sessionToken("temporary-session-token")
    .build()

// With Custom Cache TTL
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .cacheTtlSeconds(600)  // 10 minutes
    .build()

// With Custom Pending Window
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .pendingWindowInDays(7)  // Minimum 7 days
    .build()
```

### Environment Variables

```bash
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
export AWS_SESSION_TOKEN=temporary-session-token  # Optional
export AWS_KMS_PENDING_WINDOW_DAYS=30  # Optional
export AWS_KMS_CACHE_TTL_SECONDS=300  # Optional
```

```kotlin
val config = AwsKmsConfig.fromEnvironment()
val kms = AwsKeyManagementService(config ?: throw IllegalStateException("AWS config not found"))
```

### SPI Configuration

```kotlin
import com.trustweave.kms.KmsOptionKeys

val kms = awsProvider.create(mapOf(
    KmsOptionKeys.REGION to "us-east-1",
    KmsOptionKeys.ACCESS_KEY_ID to "AKIAIOSFODNN7EXAMPLE",
    KmsOptionKeys.SECRET_ACCESS_KEY to "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
    KmsOptionKeys.SESSION_TOKEN to "temporary-session-token",  // Optional
    KmsOptionKeys.PENDING_WINDOW_IN_DAYS to 30,  // Optional
    "cacheTtlSeconds" to 300L  // Optional
))
```

### Key Generation Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-id",  // Optional
        KmsOptionKeys.DESCRIPTION to "My key description",  // Optional
        KmsOptionKeys.TAGS to mapOf(  // Optional
            "Environment" to "Production",
            "Owner" to "Security Team"
        ),
        KmsOptionKeys.ALIAS to "alias/my-key",  // Optional
        KmsOptionKeys.ENABLE_AUTOMATIC_ROTATION to true  // Optional
    )
)
```

### Supported Algorithms

- Ed25519 (⚠️ Not in FIPS cert)
- secp256k1 (✅ FIPS 140-3 Level 3)
- P-256, P-384, P-521 (✅ FIPS 140-3 Level 3)
- RSA-2048 (⚠️ Legacy, deprecated)
- RSA-3072, RSA-4096 (✅ FIPS 140-3 Level 3)

---

## Azure Key Vault

### Configuration Class

```kotlin
data class AzureKmsConfig(
    val vaultUrl: String,                        // Required (must be HTTPS)
    val clientId: String? = null,                 // Optional (uses Managed Identity if not provided)
    val clientSecret: String? = null,             // Optional
    val tenantId: String? = null,                // Optional
    val endpointOverride: String? = null         // Optional (for local testing)
)
```

### Builder Pattern

```kotlin
import com.trustweave.azurekms.*

// Using Managed Identity (Recommended for Azure VMs/App Service/Functions)
val config = AzureKmsConfig.builder()
    .vaultUrl("https://myvault.vault.azure.net")
    .build()

// Using Service Principal
val config = AzureKmsConfig.builder()
    .vaultUrl("https://myvault.vault.azure.net")
    .clientId("your-client-id")
    .clientSecret("your-client-secret")
    .tenantId("your-tenant-id")
    .build()
```

### Environment Variables

```bash
export AZURE_VAULT_URL=https://myvault.vault.azure.net
export AZURE_CLIENT_ID=your-client-id
export AZURE_CLIENT_SECRET=your-client-secret
export AZURE_TENANT_ID=your-tenant-id
```

```kotlin
val config = AzureKmsConfig.fromEnvironment()
val kms = AzureKeyManagementService(config ?: throw IllegalStateException("Azure config not found"))
```

### SPI Configuration

```kotlin
val kms = azureProvider.create(mapOf(
    "vaultUrl" to "https://myvault.vault.azure.net",
    "clientId" to "your-client-id",
    "clientSecret" to "your-client-secret",
    "tenantId" to "your-tenant-id"
))
```

### Key Generation Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-id",  // Optional
        KmsOptionKeys.DESCRIPTION to "My key description",  // Optional
        KmsOptionKeys.TAGS to mapOf(  // Optional
            "Environment" to "Production",
            "Owner" to "Security Team"
        )
    )
)
```

### Supported Algorithms

- secp256k1
- P-256, P-384, P-521
- RSA-2048 (⚠️ Legacy, deprecated)
- RSA-3072, RSA-4096

**Note**: Ed25519 is not directly supported by Azure Key Vault.

---

## Google Cloud KMS

### Configuration Class

```kotlin
data class GoogleKmsConfig(
    val projectId: String,                       // Required
    val location: String,                         // Required
    val keyRing: String? = null,                  // Optional
    val credentialsPath: String? = null,         // Optional (uses ADC if not provided)
    val credentialsJson: String? = null,         // Optional (JSON string)
    val endpoint: String? = null,                // Optional (for local testing)
    val cacheTtlSeconds: Long? = 300             // Optional (default 5 minutes)
)
```

### Builder Pattern

```kotlin
import com.trustweave.googlekms.*

// Using Application Default Credentials (Recommended)
val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .keyRing("my-key-ring")  // Optional
    .build()

// Using Service Account JSON File
val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .keyRing("my-key-ring")
    .credentialsPath("/path/to/service-account.json")
    .build()

// Using Service Account JSON String
val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .credentialsJson("""{"type":"service_account",...}""")
    .build()

// With Custom Cache TTL
val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .cacheTtlSeconds(600)  // 10 minutes
    .build()
```

### Environment Variables

```bash
export GOOGLE_CLOUD_PROJECT=my-project
export GOOGLE_CLOUD_LOCATION=us-east1
export GOOGLE_CLOUD_KEY_RING=my-key-ring  # Optional
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json  # Optional
export GOOGLE_CLOUD_KMS_CACHE_TTL_SECONDS=300  # Optional
```

```kotlin
val config = GoogleKmsConfig.fromEnvironment()
val kms = GoogleCloudKeyManagementService(config ?: throw IllegalStateException("Google config not found"))
```

### SPI Configuration

```kotlin
val kms = googleProvider.create(mapOf(
    "projectId" to "my-project",
    "location" to "us-east1",
    "keyRing" to "my-key-ring",  // Optional
    "credentialsPath" to "/path/to/service-account.json",  // Optional
    "cacheTtlSeconds" to 300L  // Optional
))
```

### Key Generation Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-id",  // Optional
        KmsOptionKeys.LABELS to mapOf(  // Optional
            "environment" to "production",
            "owner" to "security-team"
        )
    )
)
```

### Supported Algorithms

- Ed25519
- secp256k1
- P-256, P-384, P-521
- RSA-2048 (⚠️ Legacy, deprecated)
- RSA-3072, RSA-4096

---

## HashiCorp Vault

### Configuration Class

```kotlin
data class VaultKmsConfig(
    val address: String,                         // Required
    val token: String? = null,                   // Optional (for token auth)
    val transitPath: String = "transit",         // Optional (default: "transit")
    val namespace: String? = null,               // Optional (for Vault Enterprise)
    val appRolePath: String? = null,             // Optional (for AppRole auth)
    val roleId: String? = null,                  // Optional (for AppRole auth)
    val secretId: String? = null,                // Optional (for AppRole auth)
    val engineVersion: Int = 2                   // Optional (default: 2)
)
```

### Builder Pattern

```kotlin
import com.trustweave.hashicorpkms.*

// Using Token Authentication (Default)
val config = VaultKmsConfig.builder()
    .address("http://localhost:8200")
    .token("hvs.xxx")
    .transitPath("transit")  // Optional, default is "transit"
    .build()

// Using AppRole Authentication
val config = VaultKmsConfig.builder()
    .address("http://localhost:8200")
    .appRolePath("approle")
    .roleId("role-id")
    .secretId("secret-id")
    .transitPath("transit")
    .build()

// With Namespace (Vault Enterprise)
val config = VaultKmsConfig.builder()
    .address("http://localhost:8200")
    .token("hvs.xxx")
    .namespace("admin")
    .transitPath("transit")
    .build()
```

### Environment Variables

```bash
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=hvs.xxx
export VAULT_NAMESPACE=admin  # Optional (Vault Enterprise)
export VAULT_TRANSIT_PATH=transit  # Optional
```

```kotlin
val config = VaultKmsConfig.fromEnvironment()
val kms = VaultKeyManagementService(config ?: throw IllegalStateException("Vault config not found"))
```

### SPI Configuration

```kotlin
val kms = vaultProvider.create(mapOf(
    "address" to "http://localhost:8200",
    "token" to "hvs.xxx",
    "transitPath" to "transit",  // Optional
    "namespace" to "admin"  // Optional
))
```

### Key Generation Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-name"  // Optional
    )
)
```

### Supported Algorithms

- Ed25519
- secp256k1
- P-256, P-384, P-521
- RSA-2048 (⚠️ Legacy, deprecated)
- RSA-3072, RSA-4096

---

## IBM Key Protect

### Configuration Class

```kotlin
data class IbmKmsConfig(
    val apiKey: String,                          // Required
    val instanceId: String,                       // Required
    val region: String = "us-south",             // Optional (default: "us-south")
    val serviceUrl: String? = null,              // Optional
    val endpointOverride: String? = null         // Optional (for local testing)
)
```

### Builder Pattern

```kotlin
import com.trustweave.kms.ibm.*

val config = IbmKmsConfig.builder()
    .apiKey("your-api-key")
    .instanceId("your-instance-id")
    .region("us-south")  // Optional, default is "us-south"
    .build()
```

### Environment Variables

```bash
export IBM_API_KEY=your-api-key
export IBM_INSTANCE_ID=your-instance-id
export IBM_REGION=us-south  # Optional
export IBM_SERVICE_URL=https://us-south.kms.cloud.ibm.com  # Optional
```

```kotlin
val config = IbmKmsConfig.fromEnvironment()
val kms = IbmKeyManagementService(config ?: throw IllegalStateException("IBM config not found"))
```

### SPI Configuration

```kotlin
val kms = ibmProvider.create(mapOf(
    "apiKey" to "your-api-key",
    "instanceId" to "your-instance-id",
    "region" to "us-south",  // Optional
    "serviceUrl" to "https://us-south.kms.cloud.ibm.com"  // Optional
))
```

### Key Generation Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-id",  // Optional
        KmsOptionKeys.DESCRIPTION to "My key description"  // Optional
    )
)
```

### Supported Algorithms

- secp256k1
- P-256, P-384, P-521
- RSA-2048 (⚠️ Legacy, deprecated)
- RSA-3072, RSA-4096

**Note**: Ed25519 is not directly supported by IBM Key Protect.

---

## InMemory KMS

### Configuration

The InMemory KMS requires no configuration - it's a simple in-memory implementation for development and testing.

```kotlin
import com.trustweave.kms.inmemory.*

// No configuration needed
val kms = InMemoryKeyManagementService()
```

### SPI Configuration

```kotlin
val kms = inMemoryProvider.create()  // No options needed
```

### Key Generation Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-id"  // Optional
    )
)
```

### Supported Algorithms

- Ed25519 (requires Java 15+ or BouncyCastle)
- secp256k1
- P-256, P-384, P-521
- RSA-2048, RSA-3072, RSA-4096

**Note**: Keys are stored in memory only and will be lost when the service instance is destroyed. Suitable for development and testing only.

---

## WaltID KMS

### Configuration

The WaltID KMS is similar to InMemory - it's an in-memory implementation that uses Java's built-in cryptographic providers.

```kotlin
import com.trustweave.waltid.*

// No configuration needed
val kms = WaltIdKeyManagementService()
```

### SPI Configuration

```kotlin
val kms = waltidProvider.create()  // No options needed
```

### Key Generation Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-id"  // Optional
    )
)
```

### Supported Algorithms

- Ed25519 (requires Java 15+ or BouncyCastle)
- secp256k1
- P-256, P-384, P-521

**Note**: Keys are stored in memory only. Suitable for development and testing only.

---

## SPI Auto-Discovery

All plugins register themselves via Java ServiceLoader. You can discover and use them programmatically:

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys

// Simple factory API - no ServiceLoader needed!
// Create KMS instances directly by provider name
val kms = KeyManagementServices.create("aws", mapOf(
    KmsOptionKeys.REGION to "us-east-1"
))

// Or get list of available providers
val availableProviders = KeyManagementServices.availableProviders()
println("Available providers: $availableProviders")
```

### Provider Names

- `aws` - AWS KMS
- `azure` - Azure Key Vault
- `google-cloud-kms` - Google Cloud KMS
- `vault` - HashiCorp Vault
- `ibm` - IBM Key Protect
- `inmemory` - InMemory KMS
- `waltid` - WaltID KMS

---

## Common Options

All plugins support common options via `KmsOptionKeys` constants:

### Key Generation Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val options = mapOf(
    // Key Identification
    KmsOptionKeys.KEY_ID to "my-custom-key-id",  // Optional: Custom key identifier
    
    // Metadata
    KmsOptionKeys.DESCRIPTION to "My key description",  // Optional: Key description
    KmsOptionKeys.TAGS to mapOf(  // Optional: Key tags (provider-specific)
        "Environment" to "Production",
        "Owner" to "Security Team"
    ),
    
    // Provider-Specific Options
    KmsOptionKeys.ALIAS to "alias/my-key",  // AWS: Key alias
    KmsOptionKeys.LABELS to mapOf(...),  // Google: Key labels
    KmsOptionKeys.ENABLE_AUTOMATIC_ROTATION to true,  // AWS: Enable key rotation
    KmsOptionKeys.EXPORTABLE to false,  // AWS: Prevent key export
    KmsOptionKeys.ALLOW_PLAINTEXT_BACKUP to false  // AWS: Prevent plaintext backup
)
```

### Available Constants

See `KmsOptionKeys` for the complete list of supported option keys:

- `KEY_ID` - Custom key identifier
- `KEY_NAME` - Key name (provider-specific)
- `DESCRIPTION` - Key description
- `TAGS` - Key tags (Map<String, String>)
- `ALIAS` - Key alias (AWS)
- `LABELS` - Key labels (Google)
- `ENABLE_AUTOMATIC_ROTATION` - Enable automatic rotation (AWS)
- `EXPORTABLE` - Allow key export (AWS)
- `ALLOW_PLAINTEXT_BACKUP` - Allow plaintext backup (AWS)
- And more...

---

## Best Practices

### 1. Use Type-Safe Constants

Always use `KmsOptionKeys` constants instead of magic strings:

```kotlin
// ✅ Good
options[KmsOptionKeys.KEY_ID] = "my-key"

// ❌ Bad
options["keyId"] = "my-key"
```

### 2. Handle Results Properly

Always use exhaustive `when` expressions for Result types:

```kotlin
when (val result = kms.generateKey(algorithm)) {
    is GenerateKeyResult.Success -> {
        // Handle success
    }
    is GenerateKeyResult.Failure.UnsupportedAlgorithm -> {
        // Handle unsupported algorithm
    }
    is GenerateKeyResult.Failure.InvalidOptions -> {
        // Handle invalid options
    }
    is GenerateKeyResult.Failure.Error -> {
        // Handle generic error
    }
}
```

### 3. Use Environment Variables for Secrets

Never hard-code credentials:

```kotlin
// ✅ Good
val config = AwsKmsConfig.fromEnvironment()

// ❌ Bad
val config = AwsKmsConfig.builder()
    .accessKeyId("AKIA...")  // Don't hard-code!
    .build()
```

### 4. Configure Caching Appropriately

For cloud KMS plugins, configure cache TTL based on your needs:

```kotlin
// High-traffic: Longer cache
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .cacheTtlSeconds(600)  // 10 minutes
    .build()

// Low-traffic: Shorter cache
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .cacheTtlSeconds(60)  // 1 minute
    .build()
```

---

## Troubleshooting

### Common Issues

1. **Authentication Failures**
   - Check credentials are correct
   - Verify IAM/RBAC permissions
   - Check environment variables are set

2. **Key Not Found Errors**
   - Verify key ID format matches provider expectations
   - Check key exists in provider
   - Verify permissions to access key

3. **Unsupported Algorithm Errors**
   - Check provider's supported algorithms
   - Use `getSupportedAlgorithms()` to query
   - Consider algorithm compatibility

4. **Cache Issues**
   - Reduce cache TTL if keys are updated externally
   - Disable cache for development: `cacheTtlSeconds = null`
   - Check cache invalidation on key deletion

---

## Additional Resources

- [KMS Core Concepts](../../docs/core-concepts/key-management.md)
- [Plugin Testing Guide](../../docs/internal/development/KMS_PLUGIN_TESTING_GUIDE.md)
- [Result-Based API Guide](../../docs/core-concepts/result-pattern.md)

---

**Last Updated**: 2025-01-27

