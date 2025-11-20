# vericore-testkit

The `vericore-testkit` module provides in-memory test implementations and utilities for all VeriCore service interfaces. This module is essential for testing and prototyping without external dependencies.

```kotlin
dependencies {
    testImplementation("com.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes in-memory implementations of KMS, DID methods, and blockchain clients so you can write tests without external services.

## Overview

The `vericore-testkit` module provides:

- **In-Memory Implementations** – mock implementations of all SPI interfaces
- **Test Fixtures** – comprehensive test fixture builder for complete test environments
- **EO Test Integration** – reusable EO test scenarios with TestContainers support
- **Integrity Verification** – utilities for verifying integrity chains
- **Test Data Builders** – builders for creating VC, Linkset, and artifact structures

## Key Components

### In-Memory Implementations

#### InMemoryKeyManagementService

```kotlin
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService

val kms = InMemoryKeyManagementService()
val key = kms.generateKey(Algorithm.Secp256k1)
val signature = kms.sign(key.id, data.toByteArray())
```

**What this does:** Provides an in-memory key management service that stores keys in memory.

**Outcome:** Enables testing of key operations without external KMS providers.

#### InMemoryBlockchainAnchorClient

```kotlin
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient

val client = InMemoryBlockchainAnchorClient("algorand:testnet")
val result = client.writePayload(payload.toByteArray())
val readData = client.readPayload(result.anchorRef)
```

**What this does:** Provides an in-memory blockchain client that stores anchored data in memory.

**Outcome:** Enables testing of anchoring operations without actual blockchain connections.

#### DidKeyMockMethod

```kotlin
import com.geoknoesis.vericore.testkit.did.DidKeyMockMethod

val kms = InMemoryKeyManagementService()
val method = DidKeyMockMethod(kms)
val didDoc = method.createDid(didCreationOptions {
    algorithm = KeyAlgorithm.Ed25519
})
```

**What this does:** Provides a mock DID method implementation for testing DID operations.

**Outcome:** Enables testing of DID creation, resolution, and updates without external DID registries.

### VeriCoreTestFixture

A comprehensive test fixture builder for setting up complete test environments:

```kotlin
import com.geoknoesis.vericore.testkit.VeriCoreTestFixture

val fixture = VeriCoreTestFixture.builder()
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
import com.geoknoesis.vericore.testkit.eo.BaseEoIntegrationTest

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
import com.geoknoesis.vericore.testkit.integrity.IntegrityVerifier

val verifier = IntegrityVerifier()
val result = verifier.verifyIntegrityChain(artifacts, linkset, credential)
assertTrue(result.valid)
```

**What this does:** Provides utilities for verifying the integrity of data chains.

**Outcome:** Enables testing of integrity verification logic.

## Usage Examples

### Basic Test Setup

```kotlin
import com.geoknoesis.vericore.testkit.*

val fixture = VeriCoreTestFixture.builder()
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
val fixture = VeriCoreTestFixture.builder()
    .withKms(InMemoryKeyManagementService())
    .withDidMethod("key") { DidKeyMockMethod(it) }
    .withDidMethod("web") { DidWebMockMethod(it) }
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

- Depends on all VeriCore modules (`vericore-core`, `vericore-spi`, `vericore-trust`, `vericore-did`, `vericore-kms`, `vericore-anchor`)
- No external runtime dependencies (all implementations are in-memory)

## Best Practices

1. **Use `use {}` for Automatic Cleanup** – The test fixture implements `Closeable` for automatic resource cleanup.

2. **Isolate Test Fixtures** – Create separate fixtures for each test to avoid shared state.

3. **Use EO Test Integration for Workflows** – Use `BaseEoIntegrationTest` for testing complete workflows.

4. **Verify Integrity** – Use `IntegrityVerifier` to verify integrity chains in tests.

## Next Steps

- Review [Testing Strategies](../advanced/testing-strategies.md) for advanced testing patterns
- Explore [EO Test Integration README](../../core/vericore-testkit/src/main/kotlin/com/geoknoesis/vericore/testkit/eo/README.md) for detailed EO test utilities
- See [Test Fixtures](../../core/vericore-testkit/src/main/kotlin/com/geoknoesis/vericore/testkit/VeriCoreTestFixture.kt) for fixture builder documentation
- Check [Creating Plugins](../contributing/creating-plugins.md) to understand SPI interfaces being mocked

