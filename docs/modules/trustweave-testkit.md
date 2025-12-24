---
title: trustweave-testkit
---

# trustweave-testkit

The `trustweave-testkit` module provides in-memory test implementations and utilities for all TrustWeave service interfaces. This module is essential for testing and prototyping without external dependencies.

```kotlin
dependencies {
    testImplementation("org.trustweave:testkit:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes in-memory implementations of KMS, DID methods, and blockchain clients so you can write tests without external services.

## Overview

The `trustweave-testkit` module provides:

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

val kms = InMemoryKeyManagementService()
val key = kms.generateKey(Algorithm.Secp256k1)
val signature = kms.sign(key.id, data.toByteArray())
```

**What this does:** Provides an in-memory key management service that stores keys in memory.

**Outcome:** Enables testing of key operations without external KMS providers.

#### InMemoryBlockchainAnchorClient

```kotlin
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

val client = InMemoryBlockchainAnchorClient("algorand:testnet")
val result = client.writePayload(payload.toByteArray())
val readData = client.readPayload(result.anchorRef)
```

**What this does:** Provides an in-memory blockchain client that stores anchored data in memory.

**Outcome:** Enables testing of anchoring operations without actual blockchain connections.

#### DidKeyMockMethod

```kotlin
import org.trustweave.testkit.did.DidKeyMockMethod

val kms = InMemoryKeyManagementService()
val method = DidKeyMockMethod(kms)
val didDoc = method.createDid(didCreationOptions {
    algorithm = KeyAlgorithm.Ed25519
})
```

**What this does:** Provides a mock DID method implementation for testing DID operations.

**Outcome:** Enables testing of DID creation, resolution, and updates without external DID registries.

### TrustWeaveTestFixture

A comprehensive test fixture builder for setting up complete test environments:

```kotlin
import org.trustweave.testkit.TrustWeaveTestFixture

val fixture = TrustWeaveTestFixture.builder()
    .withKms(InMemoryKeyManagementService())
    .withDidMethod("key", DidKeyMockMethod(kms))
    .withBlockchainClient("algorand:testnet", InMemoryBlockchainAnchorClient("algorand:testnet"))
    .build()

// Use fixture
val issuerDoc = fixture.createIssuerDid()
val anchorClient = fixture.getBlockchainClient("algorand:testnet")
```

**What this does:** Provides a fluent API for setting up complete test environments with KMS, DID methods, and blockchain clients.

**Outcome:** Reduces boilerplate in test setup and ensures consistent test configurations.

### EO Test Integration

Reusable EO test scenarios with TestContainers support:

```kotlin
import org.trustweave.testkit.eo.BaseEoIntegrationTest

class MyEoTest : BaseEoIntegrationTest() {
    override fun createAnchorClient(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient {
        return InMemoryBlockchainAnchorClient(chainId)
    }

    @Test
    fun testEoScenario() = runBlocking {
        val result = runEoTestScenario()
        assertTrue(result.verificationResult.valid)
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
val fixture = TrustWeaveTestFixture.builder()
    .withKms(InMemoryKeyManagementService())
    .withDidmethod(KEY) { DidKeyMockMethod(it) }
    .withDidmethod(WEB) { DidWebMockMethod(it) }
    .withBlockchainClient("algorand:testnet") { InMemoryBlockchainAnchorClient("algorand:testnet") }
    .withBlockchainClient("polygon:testnet") { InMemoryBlockchainAnchorClient("polygon:testnet") }
    .build()
    .use { fixture ->
        // Test with multiple DID methods and blockchains
    }
```

**What this does:** Creates a complete test environment with multiple DID methods and blockchain clients.

**Outcome:** Enables testing of complex scenarios with multiple providers.

## Dependencies

- Depends on all TrustWeave modules (`trustweave-common` (includes SPI), `trustweave-trust`, `trustweave-did`, `trustweave-kms`, `trustweave-anchor`)
- No external runtime dependencies (all implementations are in-memory)

## Best Practices

1. **Use `use {}` for Automatic Cleanup** – The test fixture implements `Closeable` for automatic resource cleanup.

2. **Isolate Test Fixtures** – Create separate fixtures for each test to avoid shared state.

3. **Use EO Test Integration for Workflows** – Use `BaseEoIntegrationTest` for testing complete workflows.

4. **Verify Integrity** – Use `IntegrityVerifier` to verify integrity chains in tests.

## Trust Layer Test Templates

Comprehensive in-memory test templates for trust layer integration tests are available in:
```
core/TrustWeave-trust/src/test/kotlin/com/geoknoesis/TrustWeave/integration/InMemoryTrustLayerIntegrationTest.kt
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

See [Trust Layer Test Templates](../contributing/testing/trust-layer-test-templates.md) for detailed documentation.

## Plugin Credential Handling

The testkit includes a system for handling tests that require external service credentials:

- **`@RequiresPlugin` annotation** - Marks tests requiring specific plugins
- **`PluginCredentialExtension`** - JUnit 5 extension that automatically skips tests when required environment variables are not available
- **Provider-based configuration** - Each plugin advertises its required environment variables

See [Plugin Credential Handling](../contributing/testing/plugin-credential-handling.md) for details.

## Next Steps

- Review [Testing Strategies](../advanced/testing-strategies.md) for advanced testing patterns
- See [Trust Layer Test Templates](../contributing/testing/trust-layer-test-templates.md) for comprehensive workflow templates
- Explore [Plugin Credential Handling](../contributing/testing/plugin-credential-handling.md) for external service testing
- Explore [EO Test Integration README](../../core/TrustWeave-testkit/src/main/kotlin/com/geoknoesis/TrustWeave/testkit/eo/README.md) for detailed EO test utilities
- See [Test Fixtures](../../core/TrustWeave-testkit/src/main/kotlin/com/geoknoesis/TrustWeave/testkit/TrustWeaveTestFixture.kt) for fixture builder documentation
- Check [Creating Plugins](../contributing/creating-plugins.md) to understand SPI interfaces being mocked

