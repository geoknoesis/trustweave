---
title: IBM Key Protect KMS Integration
parent: Integration Modules
---

# IBM Key Protect KMS Integration

> This guide covers the IBM Key Protect / Hyper Protect Crypto Services integration for TrustWeave. The IBM KMS plugin provides production-ready key management with FIPS 140-3 Level 4 compliance and support for all IBM Key Protect-compatible algorithms.

## Overview

The `kms/plugins/ibm` module provides a complete implementation of TrustWeave's `KeyManagementService` interface using IBM Key Protect or Hyper Protect Crypto Services. This integration enables you to:

- Use IBM Key Protect for secure key generation and storage with **FIPS 140-3 Level 4 validated HSMs**
- Leverage IBM's enterprise-grade key management capabilities
- Support all IBM Key Protect-compatible algorithms (Ed25519, secp256k1, P-256/P-384/P-521, RSA)
- Integrate with existing IBM Cloud infrastructure and IAM policies
- Meet regulatory compliance requirements with FIPS-validated cryptographic operations

## Installation

Add the IBM Key Protect module to your dependencies:

```kotlin
dependencies {
    // Only need to add the IBM Key Protect KMS plugin - core dependencies are included transitively
    implementation("com.trustweave.kms:ibm:1.0.0-SNAPSHOT")
}
```

**Note:** The IBM Key Protect KMS plugin automatically includes `trustweave-kms` and `trustweave-common` as transitive dependencies, so you don't need to declare them explicitly.

## Quick Start

```kotlin
import com.trustweave.kms.ibm.*
import com.trustweave.kms.*

// Configure
val config = IbmKmsConfig.builder()
    .apiKey("your-api-key")
    .instanceId("your-instance-id")
    .region("us-south")
    .build()

// Create KMS
val kms = IbmKeyManagementService(config)

// Generate key
val result = kms.generateKey(Algorithm.Ed25519)
when (result) {
    is GenerateKeyResult.Success -> {
        println("Key created: ${result.keyHandle.id}")
        println("Public key JWK: ${result.keyHandle.publicKeyJwk}")
    }
    is GenerateKeyResult.Failure -> {
        println("Error: ${result.reason}")
    }
}

// Sign data
val sign = kms.sign(result.keyHandle.id, "Hello, TrustWeave!".toByteArray())
when (sign) {
    is SignResult.Success -> println("Signature: ${sign.signature.toHexString()}")
    is SignResult.Failure -> println("Error: ${sign.reason}")
}
```

## Configuration

### Basic Configuration

```kotlin
val config = IbmKmsConfig.builder()
    .apiKey("your-api-key")
    .instanceId("your-instance-id")
    .region("us-south")  // us-south, us-east, eu-gb, etc.
    .build()
```

### Custom Service URL

```kotlin
val config = IbmKmsConfig.builder()
    .apiKey("your-api-key")
    .instanceId("your-instance-id")
    .region("us-south")
    .serviceUrl("https://custom-kms-endpoint.cloud.ibm.com")
    .build()
```

### Using Environment Variables

```bash
export IBM_API_KEY=your-api-key
export IBM_INSTANCE_ID=your-instance-id
export IBM_REGION=us-south  # Optional
export IBM_SERVICE_URL=https://us-south.kms.cloud.ibm.com  # Optional
```

```kotlin
val config = IbmKmsConfig.fromEnvironment()
val kms = IbmKeyManagementService(config ?: throw IllegalStateException("Config not found"))
```

## SPI Auto-Discovery

The plugin registers itself via Java ServiceLoader:

```kotlin
import com.trustweave.kms.*

// Simple factory API - no ServiceLoader needed!
val kms = KeyManagementServices.create("ibm", mapOf(
    "apiKey" to "your-api-key",
    "instanceId" to "your-instance-id",
    "region" to "us-south",  // Optional, default is "us-south"
    "serviceUrl" to "https://us-south.kms.cloud.ibm.com"  // Optional
))
```

## Supported Algorithms

| Algorithm | IBM Key Type | Notes |
|-----------|--------------|-------|
| Ed25519 | Ed25519 | Standard |
| secp256k1 | secp256k1 | Blockchain-compatible |
| P-256 | EC:secp256r1 | Standard |
| P-384 | EC:secp384r1 | Standard |
| P-521 | EC:secp521r1 | Standard |
| RSA-2048 | RSA-2048 | ⚠️ Legacy (deprecated) |
| RSA-3072 | RSA-3072 | Recommended |
| RSA-4096 | RSA-4096 | High security |

**Note**: RSA-2048 is deprecated. Use RSA-3072 or RSA-4096 for new deployments.

## Key Options

```kotlin
import com.trustweave.kms.KmsOptionKeys

val result = kms.generateKey(
    Algorithm.Ed25519,
    options = mapOf(
        KmsOptionKeys.KEY_ID to "my-custom-key-id",  // Optional
        KmsOptionKeys.DESCRIPTION to "My key description",  // Optional
        KmsOptionKeys.EXTRACTABLE to false  // Optional, prevent key extraction
    )
)
```

## Key Deletion

```kotlin
val deleteResult = kms.deleteKey(keyId)
when (deleteResult) {
    is DeleteKeyResult.Deleted -> println("Key deleted")
    is DeleteKeyResult.NotFound -> println("Key not found (already deleted)")
    is DeleteKeyResult.Failure.Error -> println("Error: ${deleteResult.reason}")
}
```

## Key ID Format

IBM Key Protect uses CRN (Cloud Resource Name) format for key IDs:

```
crn:v1:bluemix:public:kms:us-south:a/xxx:key:xxx
```

The plugin handles both full CRN and short key ID formats.

## Access Control

Required IAM permissions:

- **Key Management**: `kms.secrets.manage`, `kms.secrets.read`
- **Cryptographic Operations**: `kms.secrets.rotate`, `kms.secrets.wrap`, `kms.secrets.unwrap`

Configure via IBM Cloud IAM.

## Requirements

- Java 8 or higher
- IBM Key Protect or Hyper Protect Crypto Services instance
- API key with appropriate permissions
- Network access to IBM Cloud KMS endpoints

## Related Documentation

- [Key Management Concepts](../core-concepts/key-management.md)
- [IBM Key Protect Documentation](https://cloud.ibm.com/docs/key-protect)

## See Also

- [IBM Key Protect Documentation](https://cloud.ibm.com/docs/key-protect)
- [IBM Cloud IAM Documentation](https://cloud.ibm.com/docs/iam)

