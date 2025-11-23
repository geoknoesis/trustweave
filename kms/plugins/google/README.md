# TrustWeave Google Cloud KMS Plugin

Google Cloud Key Management Service (KMS) integration for trustweave.

## Overview

This module provides a complete implementation of TrustWeave's `KeyManagementService` interface using Google Cloud KMS. It enables secure key generation, signing, and management using Google Cloud's managed cryptographic services.

## Features

- ✅ Full `KeyManagementService` interface implementation
- ✅ Support for secp256k1, P-256, P-384, and RSA algorithms
- ✅ Multiple authentication methods (ADC, service account, environment variables)
- ✅ SPI provider for auto-discovery
- ✅ Comprehensive error handling
- ✅ Key versioning support
- ✅ Resource name resolution (full names and short names)

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.trustweave:TrustWeave-google-kms:1.0.0-SNAPSHOT")
}
```

## Quick Start

```kotlin
import com.trustweave.googlekms.*
import com.trustweave.kms.*

// Configure
val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .keyRing("my-key-ring")
    .build()

// Create KMS
val kms = GoogleCloudKeyManagementService(config)

// Generate key
val key = kms.generateKey(Algorithm.Secp256k1)

// Sign data
val signature = kms.sign(key.id, data.toByteArray())
```

## Configuration

### Using Application Default Credentials (Recommended)

```kotlin
val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .keyRing("my-key-ring")
    // No credentials needed - uses ADC
    .build()
```

### Using Service Account File

```kotlin
val config = GoogleKmsConfig.builder()
    .projectId("my-project")
    .location("us-east1")
    .keyRing("my-key-ring")
    .credentialsPath("/path/to/service-account.json")
    .build()
```

### Using Environment Variables

```bash
export GOOGLE_CLOUD_PROJECT=my-project
export GOOGLE_CLOUD_LOCATION=us-east1
export GOOGLE_CLOUD_KEY_RING=my-key-ring
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
```

```kotlin
val config = GoogleKmsConfig.fromEnvironment()
val kms = GoogleCloudKeyManagementService(config ?: throw IllegalStateException("Config not found"))
```

## SPI Auto-Discovery

The plugin registers itself via Java ServiceLoader:

```kotlin
import com.trustweave.kms.spi.*
import java.util.ServiceLoader

val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
val googleProvider = providers.find { it.name == "google-cloud-kms" }

val kms = googleProvider?.create(mapOf(
    "projectId" to "my-project",
    "location" to "us-east1",
    "keyRing" to "my-key-ring"
))
```

## Supported Algorithms

- **secp256k1** - Blockchain-compatible elliptic curve
- **P-256** - NIST P-256 (FIPS 140-2 compliant)
- **P-384** - NIST P-384 (FIPS 140-2 compliant)
- **RSA-2048** - RSA with 2048-bit keys
- **RSA-3072** - RSA with 3072-bit keys
- **RSA-4096** - RSA with 4096-bit keys

**Note:** Ed25519 and P-521 support may vary by Google Cloud KMS version.

## Documentation

For detailed documentation, see:
- [Google Cloud KMS Integration Guide](../../docs/integrations/google-kms.md)
- [Key Management Concepts](../../docs/core-concepts/key-management.md)

## Requirements

- Java 8 or higher
- Google Cloud KMS API enabled
- Appropriate IAM permissions (see documentation)

## License

See the main TrustWeave LICENSE file.

