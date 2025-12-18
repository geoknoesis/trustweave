---
title: InMemory KMS Integration
parent: Integration Modules
---

# InMemory KMS Integration

> This guide covers the native TrustWeave in-memory Key Management Service. The InMemory KMS plugin provides a built-in implementation for development, testing, and scenarios where a full HSM or cloud KMS is not required.

## Overview

The `kms/plugins/inmemory` module provides a native TrustWeave implementation that uses Java's built-in cryptographic providers for key generation, storage, and signing. It's designed for:

- Development and testing
- Single-process applications
- Ephemeral key management scenarios
- Prototyping and demos

**⚠️ Important**: Keys are stored in memory only and will be lost when the service instance is destroyed. For production use, consider using a persistent KMS provider (AWS, Azure, Google, HashiCorp, or HSMs).

## Installation

Add the InMemory KMS module to your dependencies:

```kotlin
dependencies {
    // Only need to add the InMemory KMS plugin - core dependencies are included transitively
    implementation("com.trustweave.kms:inmemory:1.0.0-SNAPSHOT")
}
```

**Note:** The InMemory KMS plugin automatically includes `trustweave-kms` and `trustweave-common` as transitive dependencies, so you don't need to declare them explicitly.

## Quick Start

```kotlin
import com.trustweave.kms.inmemory.*
import com.trustweave.kms.*

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

## Configuration

No configuration is required. The InMemory KMS works out of the box:

```kotlin
val kms = InMemoryKeyManagementService()
```

## SPI Auto-Discovery

The plugin registers itself via Java ServiceLoader:

```kotlin
import com.trustweave.kms.*

// Simple factory API - no ServiceLoader needed!
val kms = KeyManagementServices.create("inmemory")  // No options needed
```

## Supported Algorithms

| Algorithm | Status | Notes |
|-----------|--------|-------|
| Ed25519 | ✅ Supported | Requires Java 15+ or BouncyCastle provider |
| secp256k1 | ✅ Supported | Via Java's EC provider |
| P-256 | ✅ Supported | Via Java's EC provider |
| P-384 | ✅ Supported | Via Java's EC provider |
| P-521 | ✅ Supported | Via Java's EC provider |
| RSA-2048 | ✅ Supported | Via Java's RSA provider |
| RSA-3072 | ✅ Supported | Via Java's RSA provider |
| RSA-4096 | ✅ Supported | Via Java's RSA provider |

## Key Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-id"  // Optional
    )
)
```

## Features

### Thread Safety

✅ This implementation is fully thread-safe. All operations use:
- `ConcurrentHashMap` for key storage
- `Dispatchers.IO` for I/O-bound cryptographic operations

### Input Validation

The plugin validates:
- **Key IDs**: Max 256 characters, non-blank, alphanumeric with dashes/underscores/slashes/colons
- **Signing data**: Non-empty, max 10 MB
- **Duplicate keys**: Prevents creating keys with duplicate IDs

### Algorithm Requirements

- **Ed25519**: Requires Java 15+ or BouncyCastle provider
- **EC algorithms**: Supported via Java's standard EC provider
- **RSA algorithms**: Supported via Java's standard RSA provider

## Error Handling

All operations use the Result-based API for type-safe error handling:

```kotlin
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
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
        // Other error
    }
}
```

## Important Notes

### Key Storage

⚠️ **Keys are stored in memory only** and will be lost when:
- The service instance is destroyed
- The JVM process terminates
- The application restarts

**For production use**, consider using a persistent KMS provider:
- AWS KMS (FIPS 140-3 Level 3 validated)
- Azure Key Vault
- Google Cloud KMS
- HashiCorp Vault
- Hardware Security Modules (HSMs)

### Security Considerations

- Private keys are stored in memory and are accessible to the JVM process
- Keys are not encrypted at rest (they're in memory)
- Suitable for development/testing, not for production secrets
- Consider using a hardware-backed KMS for production

## Use Cases

### Development and Testing

Perfect for local development and unit testing:

```kotlin
class MyServiceTest {
    private val kms = InMemoryKeyManagementService()
    
    @Test
    fun testSigning() = runBlocking {
        val result = kms.generateKey(Algorithm.Ed25519)
        // ... test signing operations
    }
}
```

### Prototyping

Quick prototyping without cloud dependencies:

```kotlin
val kms = InMemoryKeyManagementService()
// Prototype your application logic
```

### Single-Process Applications

For applications that don't require key persistence:

```kotlin
// Application starts
val kms = InMemoryKeyManagementService()

// Generate keys as needed
// Keys exist for the lifetime of the application
```

## Related Documentation

- [Key Management Concepts](../core-concepts/key-management.md)
- [KMS Quick Start Guide](../kms/KMS_QUICK_START.md)
- [KMS Plugins Configuration](../kms/KMS_PLUGINS_CONFIGURATION.md)

## See Also

- [AWS KMS Integration](./aws-kms.md) - Production-ready cloud KMS
- [Azure Key Vault Integration](./azure-kms.md) - Production-ready cloud KMS
- [Google Cloud KMS Integration](./google-kms.md) - Production-ready cloud KMS
- [HashiCorp Vault Integration](./hashicorp-vault-kms.md) - On-premises or cloud KMS

