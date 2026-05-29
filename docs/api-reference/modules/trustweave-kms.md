---
title: kms:kms-core
---

# kms:kms-core

The `kms:kms-core` module provides the key management service abstraction used throughout TrustWeave. This module defines the interface for key generation, signing, and management operations.

```kotlin
dependencies {
    implementation("org.trustweave:kms-kms-core:0.6.0")
    implementation("org.trustweave:common:0.6.0")
}
```

**Result:** Gradle exposes the `KeyManagementService` interface and related types so you can integrate with any KMS backend.

## Overview

The `kms:kms-core` module provides:

- **KeyManagementService Interface** – algorithm-agnostic interface for key operations
- **Algorithm Support** – `sealed class Algorithm` with subtypes for supported algorithms (Ed25519, secp256k1, P-256/P-384/P-521, RSA, BLS12-381, Custom)
- **Key Models** – key handle and key metadata structures
- **Signing Operations** – signing and verification methods
- **Result Types** – sealed result types (`GenerateKeyResult`, `SignResult`, `GetPublicKeyResult`, `DeleteKeyResult`) for type-safe error handling
- **SPI Support** – service provider interface for auto-discovery of KMS implementations

## Key Components

### KeyManagementService

The core interface for key management operations:

```kotlin
import org.trustweave.kms.*
import org.trustweave.kms.results.*
import org.trustweave.core.identifiers.KeyId

interface KeyManagementService {
    suspend fun getSupportedAlgorithms(): Set<Algorithm>
    suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?> = emptyMap()
    ): GenerateKeyResult
    suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm? = null
    ): SignResult
    suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult
    suspend fun deleteKey(keyId: KeyId): DeleteKeyResult
}
```

**What this does:** Defines the contract for key generation, signing, and key retrieval operations that all KMS implementations must fulfill.

**Outcome:** Allows TrustWeave to work with any key management backend (HSM, cloud KMS, in-memory, etc.) through a unified interface.

### Algorithm Support

```kotlin
sealed class Algorithm(val name: String) {
    object Ed25519     : Algorithm("Ed25519")
    object Secp256k1   : Algorithm("secp256k1")
    object P256        : Algorithm("P-256")
    object P384        : Algorithm("P-384")
    object P521        : Algorithm("P-521")
    object BLS12_381   : Algorithm("BLS12-381")

    data class RSA(val rsaKeySize: Int) : Algorithm("RSA-$rsaKeySize")  // 2048, 3072, 4096
    data class Custom(override val name: String) : Algorithm(name)
}
```

**What this does:** Provides type-safe algorithm identifiers for key generation and signing operations.

**Outcome:** Enables compile-time validation of algorithm choices and clear error messages for unsupported algorithms. RSA key sizes are typed as `data class` parameters; `Custom` lets plugins advertise non-standard algorithms.

## Usage Example

```kotlin
import org.trustweave.kms.*
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.spi.KeyManagementServiceProvider
import java.util.ServiceLoader

// Discover KMS provider via SPI
val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
val kmsProvider = providers.first { it.name == "aws" }

// Create KMS instance
val kms = kmsProvider.create(mapOf("region" to "us-east-1"))

// Generate key — result is a sealed type
val handle = when (val result = kms.generateKey(Algorithm.Secp256k1)) {
    is GenerateKeyResult.Success -> result.keyHandle
    is GenerateKeyResult.Failure -> error("Key generation failed: $result")
}

// Sign data
val signature = when (val result = kms.sign(handle.id, data)) {
    is SignResult.Success -> result.signature
    is SignResult.Failure  -> error("Signing failed: $result")
}
```

**What this does:** Uses SPI to discover and instantiate a KMS provider, then generates a key and signs data.

**Outcome:** Enables pluggable key management backends without hard dependencies.

## KMS Implementations

TrustWeave provides several KMS implementations:

- **In-Memory** (`org.trustweave:kms-plugins-inmemory` or `org.trustweave:testkit`) – For testing and development. See [TestKit Documentation](trustweave-testkit.md).
- **AWS KMS** (`org.trustweave:kms-plugins-aws`) – AWS Key Management Service integration. See [AWS KMS Integration Guide](../../how-to/integrations/aws-kms.md).
- **Azure Key Vault** (`org.trustweave:kms-plugins-azure`) – Azure Key Vault integration. See [Azure KMS Integration Guide](../../how-to/integrations/azure-kms.md).
- **Google Cloud KMS** (`org.trustweave:kms-plugins-google`) – Google Cloud KMS integration. See [Google KMS Integration Guide](../../how-to/integrations/google-kms.md).
- **HashiCorp Vault** (`org.trustweave:kms-plugins-hashicorp`) – HashiCorp Vault Transit engine integration. See [HashiCorp Vault KMS Integration Guide](../../how-to/integrations/hashicorp-vault-kms.md).
- **walt.id** (`org.trustweave:kms-plugins-waltid`) – walt.id KMS integration. See [walt.id Integration Guide](../../how-to/integrations/waltid.md).

See the [Key Management](../../core-concepts/key-management.md) guide for detailed information about each implementation.

## Dependencies

- Depends on [`common`](trustweave-common.md) for core types and exceptions
- Upstream modules (DID methods, credential services) depend on `kms:kms-core` for key operations

## Next Steps

- Review [Key Management Concepts](../../core-concepts/key-management.md) for usage patterns
- Explore [KMS Integration Guides](../../how-to/integrations/README.md) for specific provider setups
- See [`testkit`](trustweave-testkit.md) for in-memory implementations for testing
- Check [Creating Plugins](../../contributing/creating-plugins.md) to implement custom KMS backends

