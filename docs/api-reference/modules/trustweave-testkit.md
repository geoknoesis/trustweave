---
title: testkit
redirect_from:
  - /modules/trustweave-testkit/
parent: Module Reference
grand_parent: API Reference
---

# testkit

The `testkit` module provides in-memory test implementations and utilities for all TrustWeave service interfaces. This module is essential for testing and prototyping without external dependencies.

```kotlin
dependencies {
    testImplementation("org.trustweave:testkit:0.6.0")
}
```

**Result:** Gradle exposes in-memory implementations of KMS, DID methods, and blockchain clients so you can write tests without external services.

## Overview

The `testkit` module provides:

- **In-Memory Implementations** – mock implementations of all SPI interfaces
- **Test Fixtures** – comprehensive test fixture builder for complete test environments
- **EO Test Integration** – reusable EO test scenarios with TestContainers support
- **Integrity Verification** – utilities for verifying integrity chains
- **Test Data Builders** – builders for creating VC, Linkset, and artifact structures

## Key Components

### In-Memory Implementations

#### InMemoryKeyManagementService

```kotlin
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult

val kms = InMemoryKeyManagementService()
val handle = (kms.generateKey(Algorithm.Secp256k1) as GenerateKeyResult.Success).keyHandle
val signature = (kms.sign(handle.id, data.toByteArray()) as SignResult.Success).signature
```

**What this does:** Provides an in-memory key management service that stores keys in memory.

**Outcome:** Enables testing of key operations without external KMS providers.

#### InMemoryBlockchainAnchorClient

```kotlin
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.core.json.jsonData

val client = InMemoryBlockchainAnchorClient("algorand:testnet")
val result = client.writePayload(jsonData { "note" to "hi" })
val readData = client.readPayload(result.ref)
```

**What this does:** Provides an in-memory blockchain client that stores anchored data in memory.

**Outcome:** Enables testing of anchoring operations without actual blockchain connections.

#### DidKeyMockMethod

```kotlin
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm

val kms = InMemoryKeyManagementService()
val method = DidKeyMockMethod(kms)
val didDoc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
```

**What this does:** Provides a mock DID method implementation for testing DID operations.

**Outcome:** Enables testing of DID creation, resolution, and updates without external DID registries.

### TrustWeaveTestFixture

A comprehensive test fixture builder for setting up complete test environments:

```kotlin
import org.trustweave.testkit.TrustWeaveTestFixture
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService

val kms = InMemoryKeyManagementService()
val fixture = TrustWeaveTestFixture.builder()
    .withKms(kms)
    .withDidMethod(DidKeyMockMethod(kms))
    .withBlockchainClient("algorand:testnet", InMemoryBlockchainAnchorClient("algorand:testnet"))
    .build()

// Use fixture
val issuerDoc = fixture.createIssuerDid()
```

**What this does:** Provides a fluent API for setting up complete test environments with KMS, DID methods, and blockchain clients.

**Outcome:** Reduces boilerplate in test setup and ensures consistent test configurations.

### EO Test Integration

Reusable EO test scenarios with TestContainers support:

```kotlin
import org.trustweave.credential.results.VerificationResult
import org.trustweave.testkit.eo.BaseEoIntegrationTest

class MyEoTest : BaseEoIntegrationTest() {
    override fun createAnchorClient(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient {
        return InMemoryBlockchainAnchorClient(chainId)
    }

    @Test
    fun testEoScenario() = runBlocking {
        val result = runEoTestScenario()
        assertTrue(result.verificationResult is VerificationResult.Valid)
    }
}
```

**What this does:** Provides a base class for EO integration tests with common setup/teardown.

**Outcome:** Enables easy testing of complete EO workflows with any blockchain adapter.

### Integrity Verification

Utilities for verifying integrity chains:

```kotlin
import org.trustweave.testkit.integrity.IntegrityVerifier

val verifier = IntegrityVerifier()
val result = verifier.verifyIntegrityChain(artifacts, linkset, credential)
assertTrue(result.valid)
```

**What this does:** Provides utilities for verifying the integrity of data chains.

**Outcome:** Enables testing of integrity verification logic.

## Usage Examples

### Basic Test Setup

```kotlin
import org.trustweave.testkit.*

val fixture = TrustWeaveTestFixture.builder()
    .withInMemoryBlockchainClient("algorand:testnet")
    .build()
    .use { fixture ->
        val issuerDoc = fixture.createIssuerDid()
        // Test code here
    }
```

**What this does:** Creates a minimal test fixture with in-memory implementations.

**Outcome:** Provides a quick setup for basic tests.

### Complete Test Environment

```kotlin
import org.trustweave.testkit.TrustWeaveTestFixture
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService

val kms = InMemoryKeyManagementService()
TrustWeaveTestFixture.builder()
    .withKms(kms)
    .withDidMethod(DidKeyMockMethod(kms))
    .withBlockchainClient("algorand:testnet", InMemoryBlockchainAnchorClient("algorand:testnet"))
    .withBlockchainClient("polygon:testnet", InMemoryBlockchainAnchorClient("polygon:testnet"))
    .build()
    .use { fixture ->
        // Test with multiple DID methods and blockchains
    }
```

> **TODO:** The fixture builder no longer exposes a Pascal-case `withDidmethod(name) { factory }` overload. Today you pass a constructed `DidMethod` to `withDidMethod(method)`, or use `withDidMethodPlugin(name, config)` for SPI-driven plugins.

**What this does:** Creates a complete test environment with multiple DID methods and blockchain clients.

**Outcome:** Enables testing of complex scenarios with multiple providers.

## Dependencies

- Depends on the core TrustWeave modules (`common` (includes SPI), `trust`, `did:did-core`, `kms:kms-core`, `anchors:anchor-core`)
- No external runtime dependencies (all implementations are in-memory)

## Best Practices

1. **Use `use {}` for Automatic Cleanup** – The test fixture implements `Closeable` for automatic resource cleanup.

2. **Isolate Test Fixtures** – Create separate fixtures for each test to avoid shared state.

3. **Use EO Test Integration for Workflows** – Use `BaseEoIntegrationTest` for testing complete workflows.

4. **Verify Integrity** – Use `IntegrityVerifier` to verify integrity chains in tests.

## TrustWeave integration test templates

Comprehensive in-memory test templates for TrustWeave integration tests are available in:
```
trust/src/test/kotlin/org/trustweave/integration/InMemoryTrustWeaveIntegrationTest.kt
```

These templates provide complete, working examples of:
- Basic credential issuance and verification
- Credential revocation workflows
- Wallet storage and retrieval
- Verifiable presentation creation
- DID update operations
- Blockchain anchoring
- Smart contract workflows
- External services integration

**Key Pattern:** All templates follow the essential pattern of extracting key IDs from DID documents (not generating new keys) to ensure proof verification succeeds.

See [TrustWeave test templates](../../contributing/testing/trustweave-test-templates.md) for detailed documentation.

## Plugin Credential Handling

The testkit includes a system for handling tests that require external service credentials:

- **`@RequiresPlugin` annotation** - Marks tests requiring specific plugins
- **`PluginCredentialExtension`** - JUnit 5 extension that automatically skips tests when required environment variables are not available
- **Provider-based configuration** - Each plugin advertises its required environment variables

See [Plugin Credential Handling](../../contributing/testing/plugin-credential-handling.md) for details.

## Next Steps

- Review [Testing Strategies](../advanced/testing-strategies.md) for advanced testing patterns
- See [TrustWeave test templates](../../contributing/testing/trustweave-test-templates.md) for comprehensive workflow templates
- Explore [Plugin Credential Handling](../../contributing/testing/plugin-credential-handling.md) for external service testing
- Explore [EO Test Integration README](https://github.com/geoknoesis/trustweave/blob/main/testkit/src/main/kotlin/org/trustweave/testkit/eo) for detailed EO test utilities
- See [Test Fixtures](../../testkit/src/main/kotlin/org/trustweave/testkit/TrustWeaveTestFixture.kt) for fixture builder documentation
- Check [Creating Plugins](../../contributing/creating-plugins.md) to understand SPI interfaces being mocked

