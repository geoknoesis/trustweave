---
title: trustweave-kms
---

# trustweave-kms

The `trustweave-kms` module provides the key management service abstraction used throughout TrustWeave. This module defines the interface for key generation, signing, and management operations.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes the `KeyManagementService` interface and related types so you can integrate with any KMS backend.

## Overview

The `trustweave-kms` module provides:

- **KeyManagementService Interface** – algorithm-agnostic interface for key operations
- **Algorithm Support** – types for supported algorithms (Ed25519, secp256k1, P-256/P-384/P-521, RSA)
- **Key Models** – key ID and key metadata structures
- **Signing Operations** – signing and verification methods
- **SPI Support** – service provider interface for auto-discovery of KMS implementations

## Key Components

### KeyManagementService

The core interface for key management operations:

```kotlin
import com.trustweave.kms.*

interface KeyManagementService {
    suspend fun generateKey(algorithm: Algorithm): KeyHandle
    suspend fun sign(keyId: KeyId, data: ByteArray): ByteArray
    suspend fun getPublicKey(keyId: KeyId): KeyHandle
    suspend fun deleteKey(keyId: KeyId): Boolean
}
```

**What this does:** Defines the contract for key generation, signing, and key retrieval operations that all KMS implementations must fulfill.

**Outcome:** Allows TrustWeave to work with any key management backend (HSM, cloud KMS, in-memory, etc.) through a unified interface.

### Algorithm Support

```kotlin
enum class Algorithm {
    Ed25519,
    Secp256k1,
    P256,
    P384,
    P521,
    Rsa2048,
    Rsa3072,
    Rsa4096
}
```

**What this does:** Provides type-safe algorithm identifiers for key generation and signing operations.

**Outcome:** Enables compile-time validation of algorithm choices and clear error messages for unsupported algorithms.

## Usage Example

```kotlin
import com.trustweave.kms.*
import java.util.ServiceLoader

// Discover KMS provider via SPI
val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
val kmsProvider = providers.find { it.name == "aws" }

// Create KMS instance
val kms = kmsProvider?.create(mapOf(
    "region" to "us-east-1"
))

// Generate key
val key = kms?.generateKey(Algorithm.Secp256k1)

// Sign data
val signature = key?.let { kms.sign(it.id, data.toByteArray()) }
// Note: key.id is a KeyId value class, which is automatically used by sign()
```

**What this does:** Uses SPI to discover and instantiate a KMS provider, then generates a key and signs data.

**Outcome:** Enables pluggable key management backends without hard dependencies.

## KMS Implementations

TrustWeave provides several KMS implementations:

- **In-Memory** (`com.trustweave:testkit`) – For testing and development. See [TestKit Documentation](trustweave-testkit.md).
- **AWS KMS** (`com.trustweave.kms:aws`) – AWS Key Management Service integration. See [AWS KMS Integration Guide](../integrations/aws-kms.md).
- **Azure Key Vault** (`com.trustweave.kms:azure`) – Azure Key Vault integration. See [Azure KMS Integration Guide](../integrations/azure-kms.md).
- **Google Cloud KMS** (`com.trustweave.kms:google`) – Google Cloud KMS integration. See [Google KMS Integration Guide](../integrations/google-kms.md).
- **HashiCorp Vault** (`com.trustweave.kms:hashicorp`) – HashiCorp Vault Transit engine integration. See [HashiCorp Vault KMS Integration Guide](../integrations/hashicorp-vault-kms.md).
- **walt.id** (`com.trustweave.kms:waltid`) – walt.id KMS integration. See [walt.id Integration Guide](../integrations/waltid.md).

See the [Key Management](../core-concepts/key-management.md) guide for detailed information about each implementation.

## Dependencies

- Depends on [`trustweave-common`](trustweave-common.md) for core types and exceptions
- Upstream modules (DID methods, credential services) depend on `trustweave-kms` for key operations

## Next Steps

- Review [Key Management Concepts](../core-concepts/key-management.md) for usage patterns
- Explore [KMS Integration Guides](../integrations/README.md) for specific provider setups
- See [`trustweave-testkit`](trustweave-testkit.md) for in-memory implementations for testing
- Check [Creating Plugins](../contributing/creating-plugins.md) to implement custom KMS backends

