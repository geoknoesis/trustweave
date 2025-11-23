# Testing Strategies

This guide explains testing strategies and best practices for TrustWeave applications and plugins.

## Overview

TrustWeave provides comprehensive testing utilities and strategies for:

- **Unit Testing** – testing individual components in isolation
- **Integration Testing** – testing component interactions
- **End-to-End Testing** – testing complete workflows
- **Mock Testing** – using in-memory implementations

## Testing Utilities

### TrustWeave-testkit

The `TrustWeave-testkit` module provides in-memory implementations:

- `InMemoryKeyManagementService` – in-memory KMS for testing
- `InMemoryBlockchainAnchorClient` – in-memory blockchain client for testing
- `DidKeyMockMethod` – mock DID method implementation
- `TrustWeaveTestFixture` – comprehensive test fixture builder

```kotlin
dependencies {
    testImplementation("com.trustweave:trustweave-testkit:1.0.0-SNAPSHOT")
}
```

## Unit Testing

### Testing DID Methods

```kotlin
import com.trustweave.testkit.*
import com.trustweave.did.*
import kotlin.test.Test
import kotlin.test.assertNotNull

class DidMethodTest {
    @Test
    fun testCreateDid() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = DidKeyMockMethod(kms)
        
        val options = didCreationOptions {
            algorithm = KeyAlgorithm.Ed25519
        }
        
        val did = method.createDid(options)
        assertNotNull(did)
        assert(did.id.startsWith("did:key:"))
    }
}
```

**What this does:** Tests DID creation in isolation using in-memory KMS.

**Outcome:** Fast, reliable unit tests without external dependencies.

### Testing Key Management

```kotlin
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.kms.*
import kotlin.test.Test
import kotlin.test.assertEquals

class KmsTest {
    @Test
    fun testGenerateAndSign() = runBlocking {
        val kms = InMemoryKeyManagementService()
        
        val key = kms.generateKey(Algorithm.Ed25519)
        assertNotNull(key)
        
        val data = "Hello, TrustWeave!".toByteArray()
        val signature = kms.sign(key.id, data)
        
        assertNotNull(signature)
        assertEquals(64, signature.size) // Ed25519 signature size
    }
}
```

**Outcome:** Tests key generation and signing without external KMS.

## Integration Testing

### Testing Credential Workflows

```kotlin
import com.trustweave.testkit.*
import com.trustweave.TrustWeave
import com.trustweave.did.*
import kotlin.test.Test

class CredentialWorkflowTest {
    @Test
    fun testCredentialIssuanceAndVerification() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet")
            .build()
            .use { fixture ->
                val TrustWeave = TrustWeave.create()
                
                // Create issuer DID
                val issuerDid = TrustWeave.dids.create()
                
                // Issue credential
                val credential = TrustWeave.issueCredential(
                    issuerDid = issuerDid.id,
                    issuerKeyId = issuerDid.document.verificationMethod.first().id,
                    credentialSubject = buildJsonObject {
                        put("id", "did:key:subject")
                        put("name", "Alice")
                    }
                ).getOrThrow()
                
                // Verify credential
                val verificationResult = TrustWeave.verifyCredential(credential).getOrThrow()
                assert(verificationResult.valid)
            }
    }
}
```

**Outcome:** Tests complete credential issuance and verification workflows.

### Testing Blockchain Anchoring

```kotlin
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.anchor.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AnchoringTest {
    @Test
    fun testAnchorAndRead() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet")
        
        val payload = "Hello, TrustWeave!".toByteArray()
        val result = client.writePayload(payload).getOrThrow()
        
        val readData = client.readPayload(result.anchorRef).getOrThrow()
        assertEquals(payload.toList(), readData.toList())
    }
}
```

**Outcome:** Tests blockchain anchoring without actual blockchain connections.

## End-to-End Testing

### EO Integration Tests

```kotlin
import com.trustweave.testkit.eo.BaseEoIntegrationTest
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.anchor.*
import kotlin.test.Test

class MyEoIntegrationTest : BaseEoIntegrationTest() {
    override fun createAnchorClient(
        chainId: String,
        options: Map<String, Any?>
    ): BlockchainAnchorClient {
        return InMemoryBlockchainAnchorClient(chainId)
    }
    
    @Test
    fun testEoScenario() = runBlocking {
        val result = runEoTestScenario()
        assert(result.verificationResult.valid)
    }
}
```

**Outcome:** Tests complete EO workflows with automatic setup and teardown.

## Test Fixtures

### Using TrustWeaveTestFixture

```kotlin
import com.trustweave.testkit.*
import kotlin.test.Test

class FixtureTest {
    @Test
    fun testWithFixture() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder()
            .withKms(InMemoryKeyManagementService())
            .withDidMethod("key") { DidKeyMockMethod(it) }
            .withBlockchainClient("algorand:testnet") {
                InMemoryBlockchainAnchorClient("algorand:testnet")
            }
            .build()
            .use { fixture ->
                val issuerDoc = fixture.createIssuerDid()
                assertNotNull(issuerDoc)
                
                val client = fixture.getBlockchainClient("algorand:testnet")
                assertNotNull(client)
            }
    }
}
```

**Outcome:** Provides comprehensive test environment setup with automatic cleanup.

## Best Practices

### Isolation

Keep tests isolated:

```kotlin
@Test
fun testIsolated() = runBlocking {
    // Each test gets its own fixture
    val fixture = TrustWeaveTestFixture.builder().build().use { fixture ->
        // Test code
    }
}
```

### Resource Cleanup

Use `use {}` for automatic cleanup:

```kotlin
fixture.use { fixture ->
    // Test code
    // Automatic cleanup on exit
}
```

### Error Testing

Test error cases:

```kotlin
@Test
fun testErrorHandling() = runBlocking {
    val kms = InMemoryKeyManagementService()
    
    val result = kms.sign("nonexistent-key", "data".toByteArray())
    result.fold(
        onSuccess = { fail("Expected error") },
        onFailure = { error ->
            assert(error is TrustWeaveError.KeyNotFound)
        }
    )
}
```

### Performance Testing

Test performance-critical paths:

```kotlin
@Test
fun testPerformance() = runBlocking {
    val kms = InMemoryKeyManagementService()
    
    val start = System.currentTimeMillis()
    
    repeat(1000) {
        kms.generateKey(Algorithm.Ed25519)
    }
    
    val duration = System.currentTimeMillis() - start
    assert(duration < 1000) // Should complete in under 1 second
}
```

## Testing Plugins

### Testing Custom DID Methods

```kotlin
import com.trustweave.testkit.*
import com.trustweave.did.*
import kotlin.test.Test

class MyCustomDidMethodTest {
    @Test
    fun testCreateAndResolve() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val config = MyDidConfig.default()
        val method = MyCustomDidMethod(kms, config)
        
        // Create DID
        val did = method.createDid(didCreationOptions {
            algorithm = KeyAlgorithm.Ed25519
        })
        
        // Resolve DID
        val resolutionResult = method.resolveDid(did.id)
        assertNotNull(resolutionResult.didDocument)
        assertEquals(did.id, resolutionResult.didDocument?.id)
    }
}
```

### Testing Custom Blockchain Adapters

```kotlin
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.anchor.*
import kotlin.test.Test

class MyBlockchainAdapterTest {
    @Test
    fun testAnchor() = runBlocking {
        val config = MyBlockchainConfig.testnet()
        val client = MyBlockchainAnchorClient("myblockchain:testnet", config)
        
        val payload = "test".toByteArray()
        val result = client.writePayload(payload).getOrThrow()
        
        assertNotNull(result.anchorRef.transactionHash)
    }
}
```

## Test Coverage

### Measuring Coverage

Use coverage tools to measure test coverage:

```bash
./gradlew test jacocoTestReport
```

### Target Coverage

Aim for:
- **Unit Tests** – 80%+ coverage
- **Integration Tests** – cover critical paths
- **End-to-End Tests** – cover main workflows

## Next Steps

- Review [TrustWeave-testkit Module](../modules/trustweave-testkit.md) for testing utilities
- See [Error Handling](error-handling.md) for error testing patterns
- Check [Creating Plugins](../contributing/creating-plugins.md) for plugin testing
- Explore existing tests in TrustWeave modules for examples

## References

- [TrustWeave-testkit Module](../modules/trustweave-testkit.md)
- [Error Handling](error-handling.md)
- [Creating Plugins](../contributing/creating-plugins.md)

