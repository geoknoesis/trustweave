---
title: KMS Quick Start Guide
nav_order: 11
parent: KMS Documentation
---

# KMS Quick Start Guide

**Version**: 1.0  
**Last Updated**: 2025-01-27

---

## Overview

This guide provides quick start examples for using TrustWeave KMS plugins. All examples use the latest Result-based API and type-safe constants.

---

## Table of Contents

1. [Using KeyManagementServices Factory](#using-keymanagementservices-factory)
2. [InMemory KMS (Development/Testing)](#inmemory-kms)
3. [AWS KMS](#aws-kms)
4. [Azure Key Vault](#azure-key-vault)
5. [Google Cloud KMS](#google-cloud-kms)
6. [HashiCorp Vault](#hashicorp-vault)
7. [IBM Key Protect](#ibm-key-protect)
8. [SPI Auto-Discovery](#spi-auto-discovery)

---

## Using KeyManagementServices

`KeyManagementServices` is a factory that simplifies creating KMS instances from any available plugin. It automatically discovers and manages all KMS plugins on your classpath, making it easy to switch between different key management providers.

### How It Works

When you add a KMS plugin to your project (like `trustweave-kms-aws` or `trustweave-kms-azure`), the plugin automatically registers itself with `KeyManagementServices`. You can then create KMS instances by simply providing the provider name and configuration:

```kotlin
import com.trustweave.kms.*

// Create an AWS KMS instance
val kms = KeyManagementServices.create("aws", mapOf(
    "region" to "us-east-1"
))
```

The factory handles all the complexity of finding and instantiating the correct plugin for you.

### Why It's Useful

- **Simple API**: Create KMS instances with a single line of code
- **Plugin Agnostic**: Switch between providers without changing your code structure
- **Type Safety**: Works with typed configuration builders for compile-time validation
- **Better Errors**: Clear error messages that list available providers when something goes wrong
- **Automatic Discovery**: All plugins are automatically available - just add the dependency
- **Instance Caching**: KMS instances are automatically cached, avoiding expensive client initialization overhead

### Basic Usage

Create a KMS instance by provider name:

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

// InMemory (for testing - no configuration needed)
val inMemoryKms = KeyManagementServices.create("inmemory")
```

### Typed Configuration

Many providers support typed configuration builders that provide compile-time safety and IDE autocomplete:

```kotlin
import com.trustweave.kms.*
import com.trustweave.awskms.awsKmsOptions

// Type-safe configuration with IDE autocomplete
val kms = KeyManagementServices.create("aws", awsKmsOptions {
    region = "us-east-1"
    accessKeyId = "AKIA..."
    secretAccessKey = "..."
    endpointOverride = "http://localhost:4566"  // For LocalStack
})
```

### Discovering Available Providers

Check which KMS providers are available in your project:

```kotlin
import com.trustweave.kms.*

val providers = KeyManagementServices.availableProviders()
println("Available providers: $providers")
// Output: [aws, azure, google-cloud-kms, vault, ibm, inmemory, waltid]
```

### Instance Caching

`KeyManagementServices` uses caching to improve performance and avoid expensive setup costs. Calling `create()` multiple times with the same provider and configuration returns the same cached instance.

### Error Handling

The factory provides helpful error messages when a provider isn't found:

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

### Available Providers

| Provider | Name | Configuration Required |
|----------|------|------------------------|
| AWS KMS | `aws` | Yes (region) |
| Azure Key Vault | `azure` | Yes (vaultUrl) |
| Google Cloud KMS | `google-cloud-kms` | Yes (projectId, location) |
| HashiCorp Vault | `vault` | Yes (address, token) |
| IBM Key Protect | `ibm` | Yes (apiKey, instanceId) |
| InMemory | `inmemory` | No |
| WaltID | `waltid` | Depends on configuration |

---

## InMemory KMS

Perfect for development and testing. No configuration required.

```kotlin
import com.trustweave.kms.inmemory.*
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys

// Create service (no configuration needed)
val kms = InMemoryKeyManagementService()

// Generate a key
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        println("Key created: ${keyHandle.id}")
        
        // Sign data
        val sign = kms.sign(keyHandle.id, "Hello, World!".toByteArray())
        when (sign) {
            is SignResult.Success -> println("Signature created")
            is SignResult.Failure -> println("Error: ${sign.reason}")
        }
        
        // Get public key
        val publicKeyResult = kms.getPublicKey(keyHandle.id)
        when (publicKeyResult) {
            is GetPublicKeyResult.Success -> println("Public key: ${publicKeyResult.keyHandle.publicKeyJwk}")
            is GetPublicKeyResult.Failure -> println("Error: ${publicKeyResult.reason}")
        }
        
        // Delete key
        val deleteResult = kms.deleteKey(keyHandle.id)
        when (deleteResult) {
            is DeleteKeyResult.Deleted -> println("Key deleted")
            is DeleteKeyResult.NotFound -> println("Key not found")
        }
    }
    is GenerateKeyResult.Failure -> {
        println("Error: ${result.reason}")
    }
}
```

---

## AWS KMS

### Configuration

```kotlin
import com.trustweave.awskms.*

// Using IAM Role (Recommended for EC2/ECS/Lambda)
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .build()

// Or using Access Keys
val config = AwsKmsConfig.builder()
    .region("us-east-1")
    .accessKeyId("AKIA...")
    .secretAccessKey("...")
    .cacheTtlSeconds(300)  // Optional: 5 minutes cache
    .build()

val kms = AwsKeyManagementService(config)
```

### Usage

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys

// Generate key with options
val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-key-id",
        KmsOptionKeys.DESCRIPTION to "My key",
        KmsOptionKeys.TAGS to mapOf("Environment" to "Production"),
        KmsOptionKeys.ALIAS to "alias/my-key",
        KmsOptionKeys.ENABLE_AUTOMATIC_ROTATION to true
    )
)

when (result) {
    is GenerateKeyResult.Success -> {
        val keyId = result.keyHandle.id
        
        // Sign
        val sign = kms.sign(keyId, "data".toByteArray())
        when (sign) {
            is SignResult.Success -> println("Signed: ${sign.signature.size} bytes")
            is SignResult.Failure -> println("Error: ${sign.reason}")
        }
    }
    is GenerateKeyResult.Failure -> println("Error: ${result.reason}")
}
```

---

## Azure Key Vault

### Configuration

```kotlin
import com.trustweave.azurekms.*

// Using Managed Identity (Recommended for Azure)
val config = AzureKmsConfig.builder()
    .vaultUrl("https://myvault.vault.azure.net")
    .build()

// Or using Service Principal
val config = AzureKmsConfig.builder()
    .vaultUrl("https://myvault.vault.azure.net")
    .clientId("your-client-id")
    .clientSecret("your-client-secret")
    .tenantId("your-tenant-id")
    .build()

val kms = AzureKeyManagementService(config)
```

### Usage

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-key-id",
        KmsOptionKeys.DESCRIPTION to "My key"
    )
)

when (result) {
    is GenerateKeyResult.Success -> {
        val sign = kms.sign(result.keyHandle.id, "data".toByteArray())
        // Handle result...
    }
    is GenerateKeyResult.Failure -> {
        // Handle error...
    }
}
```

---

## Google Cloud KMS

### Configuration

```kotlin
import com.trustweave.googlekms.*

// Using Application Default Credentials (Recommended)
val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .keyRing("my-key-ring")  // Optional
    .cacheTtlSeconds(300)  // Optional: 5 minutes cache
    .build()

val kms = GoogleCloudKeyManagementService(config)
```

### Usage

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-key-id",
        KmsOptionKeys.LABELS to mapOf(
            "environment" to "production",
            "owner" to "security-team"
        )
    )
)

when (result) {
    is GenerateKeyResult.Success -> {
        val sign = kms.sign(result.keyHandle.id, "data".toByteArray())
        // Handle result...
    }
    is GenerateKeyResult.Failure -> {
        // Handle error...
    }
}
```

---

## HashiCorp Vault

### Configuration

```kotlin
import com.trustweave.hashicorpkms.*

// Using Token Authentication
val config = VaultKmsConfig.builder()
    .address("http://localhost:8200")
    .token("hvs.xxx")
    .transitPath("transit")  // Optional, default is "transit"
    .build()

// Or using AppRole
val config = VaultKmsConfig.builder()
    .address("http://localhost:8200")
    .appRolePath("approle")
    .roleId("role-id")
    .secretId("secret-id")
    .transitPath("transit")
    .build()

val kms = VaultKeyManagementService(config)
```

### Usage

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-key-name",
        KmsOptionKeys.EXPORTABLE to false,
        KmsOptionKeys.ALLOW_PLAINTEXT_BACKUP to false
    )
)

when (result) {
    is GenerateKeyResult.Success -> {
        val sign = kms.sign(result.keyHandle.id, "data".toByteArray())
        // Handle result...
    }
    is GenerateKeyResult.Failure -> {
        // Handle error...
    }
}
```

---

## IBM Key Protect

### Configuration

```kotlin
import com.trustweave.kms.ibm.*

val config = IbmKmsConfig.builder()
    .apiKey("your-api-key")
    .instanceId("your-instance-id")
    .region("us-south")  // Optional, default is "us-south"
    .build()

val kms = IbmKeyManagementService(config)
```

### Usage

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.P256,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-key-id",
        KmsOptionKeys.DESCRIPTION to "My key",
        KmsOptionKeys.EXTRACTABLE to false
    )
)

when (result) {
    is GenerateKeyResult.Success -> {
        val sign = kms.sign(result.keyHandle.id, "data".toByteArray())
        // Handle result...
    }
    is GenerateKeyResult.Failure -> {
        // Handle error...
    }
}
```

---

## SPI Auto-Discovery

All KMS plugins are automatically discovered via Java ServiceLoader. The recommended way to use them is through the `KeyManagementServices` factory:

### Using KeyManagementServices Factory (Recommended)

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys

// Simple factory API - no ServiceLoader needed!
// Create KMS instances directly by provider name
val awsKms = KeyManagementServices.create("aws", mapOf(
    KmsOptionKeys.REGION to "us-east-1"
))
val azureKms = KeyManagementServices.create("azure", mapOf(
    "vaultUrl" to "https://myvault.vault.azure.net"
))
val googleKms = KeyManagementServices.create("google-cloud-kms", mapOf(
    "projectId" to "my-project"
))
val vaultKms = KeyManagementServices.create("vault", mapOf(
    "address" to "http://localhost:8200"
))
val ibmKms = KeyManagementServices.create("ibm", mapOf(
    "apiKey" to "your-api-key"
))
val inMemoryKms = KeyManagementServices.create("inmemory")

// Use KMS
val kms = awsKms
```

### Discovering Available Providers

```kotlin
import com.trustweave.kms.*

// Get list of all available providers
val providers = KeyManagementServices.availableProviders()
println("Available providers: $providers")
// Output: [aws, azure, google-cloud-kms, vault, ibm, inmemory, waltid]
```

### Using the KMS Instance

Once created, use the KMS instance to generate keys, sign data, and manage cryptographic operations:

```kotlin
import com.trustweave.kms.*

val kms = KeyManagementServices.create("aws", mapOf("region" to "us-east-1"))

// Generate a key
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
    is GenerateKeyResult.Success -> {
        println("Key created: ${result.keyHandle.id}")
        
        // Sign data with the key
        val signResult = kms.sign(result.keyHandle.id, "Hello, World!".toByteArray())
        when (signResult) {
            is SignResult.Success -> println("Signature created")
            is SignResult.Failure -> println("Error: ${signResult.reason}")
        }
    }
    is GenerateKeyResult.Failure -> {
        println("Error: ${result.reason}")
    }
}
```

---

## Common Patterns

### Error Handling

Always use exhaustive `when` expressions:

```kotlin
when (val result = kms.generateKey(algorithm)) {
    is GenerateKeyResult.Success -> {
        // Handle success
    }
    is GenerateKeyResult.Failure.UnsupportedAlgorithm -> {
        // Algorithm not supported
    }
    is GenerateKeyResult.Failure.InvalidOptions -> {
        // Invalid options (e.g., duplicate key ID)
    }
    is GenerateKeyResult.Failure.Error -> {
        // Generic error
    }
}
```

### Key Lifecycle

```kotlin
// 1. Generate
val generateResult = kms.generateKey(Algorithm.Ed25519)
if (generateResult !is GenerateKeyResult.Success) {
    return // Handle error
}
val keyId = generateResult.keyHandle.id

// 2. Get public key
val publicKeyResult = kms.getPublicKey(keyId)
if (publicKeyResult !is GetPublicKeyResult.Success) {
    return // Handle error
}
val publicKey = publicKeyResult.keyHandle.publicKeyJwk

// 3. Sign
val sign = kms.sign(keyId, data)
if (sign !is SignResult.Success) {
    return // Handle error
}
val signature = sign.signature

// 4. Delete (idempotent)
val deleteResult = kms.deleteKey(keyId)
when (deleteResult) {
    is DeleteKeyResult.Deleted -> println("Key deleted")
    is DeleteKeyResult.NotFound -> println("Key already deleted")
}
```

### Using Type-Safe Constants

Always use `KmsOptionKeys` constants:

```kotlin
import com.trustweave.kms.KmsOptionKeys

val options = mapOf(
    KmsOptionKeys.KEY_ID to "my-key-id",  // ✅ Good
    KmsOptionKeys.DESCRIPTION to "My key",
    KmsOptionKeys.TAGS to mapOf("env" to "prod")
)

// ❌ Bad: Don't use magic strings
val badOptions = mapOf(
    "keyId" to "my-key-id",  // ❌ Don't do this
    "description" to "My key"
)
```

---

## Next Steps

- [Complete Configuration Guide](KMS_PLUGINS_CONFIGURATION.md)
- [Plugin Testing Guide](../../docs/internal/development/KMS_PLUGIN_TESTING_GUIDE.md)
- [Result-Based API Documentation](../../docs/core-concepts/result-pattern.md)

---

**Last Updated**: 2025-01-27

