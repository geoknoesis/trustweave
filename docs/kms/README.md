---
title: KMS Documentation
nav_order: 10
parent: Documentation
---

# TrustWeave KMS Documentation

**Version**: 1.0  
**Last Updated**: 2025-01-27

---

## Overview

TrustWeave Key Management Service (KMS) provides a unified, type-safe interface for cryptographic key operations across multiple cloud providers and hardware security modules (HSMs).

---

## Quick Links

- **[Quick Start Guide](KMS_QUICK_START.md)** - Get started in 5 minutes
- **[Plugin Configuration Guide](KMS_PLUGINS_CONFIGURATION.md)** - Complete configuration reference
- **[Testing Guide](../../docs/internal/development/KMS_PLUGIN_TESTING_GUIDE.md)** - Plugin testing best practices

---

## Key Features

- ✅ **Type-Safe API**: Result-based error handling with sealed classes
- ✅ **Simple Factory API**: `KeyManagementServices` factory hides ServiceLoader complexity
- ✅ **Instance Caching**: Automatic caching of KMS instances for optimal performance
- ✅ **Multiple Providers**: AWS, Azure, Google, HashiCorp, IBM, InMemory, and more
- ✅ **SPI Auto-Discovery**: Automatic plugin discovery via Java ServiceLoader
- ✅ **Thread-Safe**: All implementations are thread-safe
- ✅ **Comprehensive Testing**: Contract tests, edge case tests, performance tests
- ✅ **Production-Ready**: FIPS 140-3 Level 3 validated options available

---

## Supported Plugins

| Plugin | Provider Name | Status | FIPS 140-3 |
|--------|---------------|--------|------------|
| **AWS KMS** | `aws` | ✅ Production | ✅ Level 3 |
| **Azure Key Vault** | `azure` | ✅ Production | ✅ Level 2 |
| **Google Cloud KMS** | `google-cloud-kms` | ✅ Production | ✅ Level 3 |
| **HashiCorp Vault** | `vault` | ✅ Production | ⚠️ Depends on backend |
| **IBM Key Protect** | `ibm` | ✅ Production | ✅ Level 4 |
| **InMemory** | `inmemory` | ✅ Development/Testing | ❌ No |
| **WaltID** | `waltid` | ✅ Development/Testing | ❌ No |

---

## Quick Example

### Using KeyManagementServices Factory (Recommended)

```kotlin
import com.trustweave.kms.*

// Simple factory API - no ServiceLoader needed!
val kms = KeyManagementServices.create("inmemory")

// Generate a key
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyId = result.keyHandle.id
        
        // Sign data
        val sign = kms.sign(keyId, "Hello, World!".toByteArray())
        when (sign) {
            is SignResult.Success -> println("Signed: ${sign.signature.size} bytes")
            is SignResult.Failure -> println("Error: ${sign.reason}")
        }
    }
    is GenerateKeyResult.Failure -> println("Error: ${result.reason}")
}
```

### Direct Instantiation (Alternative)

```kotlin
import com.trustweave.kms.inmemory.*
import com.trustweave.kms.*

// Direct instantiation (for InMemory only)
val kms = InMemoryKeyManagementService()

// Generate a key
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
    is GenerateKeyResult.Success -> {
        val keyId = result.keyHandle.id
        
        // Sign data
        val sign = kms.sign(keyId, "Hello, World!".toByteArray())
        when (sign) {
            is SignResult.Success -> println("Signed: ${sign.signature.size} bytes")
            is SignResult.Failure -> println("Error: ${sign.reason}")
        }
    }
    is GenerateKeyResult.Failure -> println("Error: ${result.reason}")
}
```

---

## Documentation Structure

### Getting Started
- **[Quick Start Guide](KMS_QUICK_START.md)** - Examples for all plugins
- **[Plugin Configuration Guide](KMS_PLUGINS_CONFIGURATION.md)** - Complete configuration reference

### Plugin-Specific Documentation
- **[AWS KMS](../integrations/aws-kms.md)** - AWS KMS plugin
- **[Azure Key Vault](../integrations/azure-kms.md)** - Azure Key Vault plugin
- **[Google Cloud KMS](../integrations/google-kms.md)** - Google Cloud KMS plugin
- **[HashiCorp Vault](../integrations/hashicorp-vault-kms.md)** - HashiCorp Vault plugin
- **[IBM Key Protect](../integrations/ibm-key-protect-kms.md)** - IBM Key Protect plugin
- **[InMemory KMS](../integrations/inmemory-kms.md)** - InMemory KMS plugin

### Developer Resources
- **[Testing Guide](../../docs/internal/development/KMS_PLUGIN_TESTING_GUIDE.md)** - Plugin testing best practices
- **[Code Review](../../docs/internal/reviews/KMS_FINAL_REVIEW_AND_SCORE_2025.md)** - Latest code review and score

---

## Core Concepts

### Result-Based API

All operations return sealed Result types for type-safe error handling:

```kotlin
sealed class GenerateKeyResult {
    data class Success(val keyHandle: KeyHandle) : GenerateKeyResult()
    sealed class Failure : GenerateKeyResult() {
        data class UnsupportedAlgorithm(val algorithm: Algorithm) : Failure()
        data class InvalidOptions(val reason: String) : Failure()
        data class Error(val algorithm: Algorithm, val reason: String, val cause: Throwable?) : Failure()
    }
}
```

### Type-Safe Constants

Use `KmsOptionKeys` constants instead of magic strings:

```kotlin
import com.trustweave.kms.KmsOptionKeys

val options = mapOf(
    KmsOptionKeys.KEY_ID to "my-key-id",
    KmsOptionKeys.DESCRIPTION to "My key",
    KmsOptionKeys.TAGS to mapOf("env" to "prod")
)
```

### Algorithm Support

All plugins support a common set of algorithms:

- **Ed25519** - Modern EdDSA signature scheme
- **secp256k1** - Blockchain-compatible curve
- **P-256, P-384, P-521** - NIST standard curves
- **RSA-2048** (⚠️ Legacy, deprecated)
- **RSA-3072, RSA-4096** - Recommended RSA sizes

---

## Architecture

### Core Components

- **`KeyManagementService`** - Main interface for all KMS operations
- **`Algorithm`** - Sealed class hierarchy for type-safe algorithms
- **`KeyHandle`** - Represents a key with metadata
- **Result Types** - Sealed classes for operation results
- **SPI** - Service Provider Interface for plugin discovery

### Plugin Structure

Each plugin implements:
- `KeyManagementService` interface
- `KeyManagementServiceProvider` for SPI discovery
- Configuration class with builder pattern
- Algorithm mapping utilities

---

## Best Practices

### 1. Use Type-Safe Constants

```kotlin
// ✅ Good
options[KmsOptionKeys.KEY_ID] = "my-key"

// ❌ Bad
options["keyId"] = "my-key"
```

### 2. Handle All Result Cases

```kotlin
when (val result = kms.generateKey(algorithm)) {
    is GenerateKeyResult.Success -> { /* ... */ }
    is GenerateKeyResult.Failure.UnsupportedAlgorithm -> { /* ... */ }
    is GenerateKeyResult.Failure.InvalidOptions -> { /* ... */ }
    is GenerateKeyResult.Failure.Error -> { /* ... */ }
}
```

### 3. Use Environment Variables for Secrets

```kotlin
// ✅ Good
val config = AwsKmsConfig.fromEnvironment()

// ❌ Bad
val config = AwsKmsConfig.builder()
    .accessKeyId("AKIA...")  // Don't hard-code!
    .build()
```

### 4. Leverage Instance Caching

`KeyManagementServices` automatically caches KMS instances. Calling `create()` with the same provider and configuration returns the cached instance, avoiding expensive client initialization:

```kotlin
import com.trustweave.kms.*

// First call creates and caches instance
val kms1 = KeyManagementServices.create("aws", mapOf("region" to "us-east-1"))

// Same configuration returns cached instance
val kms2 = KeyManagementServices.create("aws", mapOf("region" to "us-east-1"))
// kms1 === kms2 (same instance)
```

**Note**: This is different from provider-level caching (like AWS KMS `cacheTtlSeconds`). Instance caching is at the factory level and caches the entire KMS client instance.

---

## Requirements

- **Java**: 8 or higher
- **Kotlin**: 1.9+ (for coroutines support)
- **Dependencies**: Varies by plugin (see [Plugin Configuration Guide](KMS_PLUGINS_CONFIGURATION.md))

---

## License

See the main TrustWeave LICENSE file.

---

**Last Updated**: 2025-01-27

