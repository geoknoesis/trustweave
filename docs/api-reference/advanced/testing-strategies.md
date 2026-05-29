---
title: Testing Strategies
nav_exclude: true
redirect_from:
  - /advanced/testing-strategies/

---

# Testing Strategies

This guide explains testing strategies and best practices for TrustWeave applications and plugins.

## Overview

TrustWeave provides comprehensive testing utilities and strategies for:

- **Unit Testing** – testing individual components in isolation
- **Integration Testing** – testing component interactions
- **End-to-End Testing** – testing complete workflows
- **Mock Testing** – using in-memory implementations

## Testing Utilities

### trustweave-testkit

The `trustweave-testkit` module provides in-memory implementations:

- `InMemoryKeyManagementService` – in-memory KMS for testing
- `InMemoryBlockchainAnchorClient` – in-memory blockchain client for testing
- `DidKeyMockMethod` – mock DID method implementation
- `TrustWeaveTestFixture` – comprehensive test fixture builder

```kotlin
dependencies {
    testImplementation("org.trustweave:testkit:0.6.0")
}
```

## Unit Testing

### Testing DID Methods

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertNotNull

class DidMethodTest {
    @Test
    fun testCreateDid() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = DidKeyMockMethod(kms)

        val options = didCreationOptions {
            algorithm = KeyAlgorithm.ED25519
        }

        val document = method.createDid(options)
        assertNotNull(document)
        assert(document.id.value.startsWith("did:key:"))
    }
}
```

**What this does:** Tests DID creation in isolation using in-memory KMS.

**Outcome:** Fast, reliable unit tests without external dependencies.

### Testing Key Management

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KmsTest {
    @Test
    fun testGenerateAndSign() = runBlocking {
        val kms = InMemoryKeyManagementService()

        val generated = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generated is GenerateKeyResult.Success, generated.toString())
        val handle = (generated as GenerateKeyResult.Success).keyHandle

        val data = "Hello, TrustWeave!".toByteArray()
        val signed = kms.sign(handle.id, data)
        assertTrue(signed is SignResult.Success, signed.toString())
        val signature = (signed as SignResult.Success).signature

        assertNotNull(signature)
        assertEquals(64, signature.size) // Ed25519 signature size
    }
}
```

**Outcome:** Tests key generation and signing without external KMS.

## Integration Testing

### Testing Credential Workflows

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.types.getOrThrowDid
import kotlin.test.Test
import kotlin.test.assertTrue

class CredentialWorkflowTest {
    @Test
    fun testCredentialIssuanceAndVerification() = runBlocking {
        val trustWeave = TrustWeave.build {
            keys { provider(IN_MEMORY); algorithm(ED25519) }
            did { method(KEY) { algorithm(ED25519) } }
        }
        val issuerDid = trustWeave.createDid { }.getOrThrowDid()
        val credential = trustWeave.issue {
            credential {
                type("VerifiableCredential", "PersonCredential")
                issuer(issuerDid)
                subject {
                    id("did:key:subject")
                    "name" to "Alice"
                }
            }
            signedBy(issuerDid) // key id auto-extracted from the DID document
        }.getOrThrow()
        val verification = trustWeave.verify(credential)
        assertTrue(verification is VerificationResult.Valid, verification.toString())
    }
}
```

**Outcome:** Tests complete credential issuance and verification workflows.

### Testing Blockchain Anchoring

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlin.test.Test
import kotlin.test.assertEquals

class AnchoringTest {
    @Test
    fun testAnchorAndRead() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet")
        val payload = JsonPrimitive("Hello, TrustWeave!")

        val anchored = client.writePayload(payload, "application/json")
        val readBack = client.readPayload(anchored.ref)

        assertEquals(payload, readBack.payload)
    }
}
```

**Outcome:** Tests blockchain anchoring without actual blockchain connections.

## End-to-End Testing

### EO Integration Tests

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.eo.BaseEoIntegrationTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MyEoIntegrationTest : BaseEoIntegrationTest() {
    override fun createAnchorClient(
        chainId: String,
        options: Map<String, Any?>
    ): BlockchainAnchorClient {
        return InMemoryBlockchainAnchorClient(chainId)
    }

    @Test
    fun testEoScenario() {
        val result = runEoTestScenario()
        assertTrue(result.verificationResult.valid)
    }
}
```

**Outcome:** Tests complete EO workflows with automatic setup and teardown.

## Test Fixtures

### Using TrustWeaveTestFixture

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.testkit.TrustWeaveTestFixture
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertNotNull

class FixtureTest {
    @Test
    fun testWithFixture() = runBlocking {
        TrustWeaveTestFixture.builder()
            .withKms(InMemoryKeyManagementService())
            .withDidMethod("key")
            .withBlockchainClient(
                "algorand:testnet",
                InMemoryBlockchainAnchorClient("algorand:testnet")
            )
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
    TrustWeaveTestFixture.builder().withDidMethod("key").build().use { fixture ->
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
import kotlinx.coroutines.runBlocking
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

@Test
fun testErrorHandling() = runBlocking {
    val kms = InMemoryKeyManagementService()

    val result = kms.sign(KeyId("nonexistent-key"), "data".toByteArray())
    when (result) {
        is SignResult.Success -> fail("Expected error")
        is SignResult.Failure.KeyNotFound -> assertTrue(true)
        is SignResult.Failure -> fail("Expected KeyNotFound, got $result")
    }
}
```

### Performance Testing

Test performance-critical paths:

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.kms.Algorithm
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test

@Test
fun testPerformance() = runBlocking {
    val kms = InMemoryKeyManagementService()

    val start = System.currentTimeMillis()

    repeat(1000) {
        kms.generateKey(Algorithm.Ed25519)
    }

    val duration = System.currentTimeMillis() - start
    assert(duration < 5_000) // Illustrative threshold; real perf varies
}
```

## Testing Plugins

### Testing Custom DID Methods

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MyCustomDidMethodTest {
    @Test
    fun testCreateAndResolve() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val config = MyDidConfig.default()
        val method = MyCustomDidMethod(kms, config)

        // Create DID
        val document = method.createDid(didCreationOptions {
            algorithm = KeyAlgorithm.ED25519
        })

        // Resolve DID
        val resolutionResult = method.resolveDid(document.id)
        val resolved = when (resolutionResult) {
            is DidResolutionResult.Success -> resolutionResult.document
            else -> null
        }
        assertNotNull(resolved)
        assertEquals(document.id, resolved?.id)
    }
}
```

### Testing custom blockchain adapters

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlin.test.Test
import kotlin.test.assertTrue

class MyBlockchainAdapterTest {
    @Test
    fun testAnchor() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("myblockchain:testnet")
        val anchored = client.writePayload(JsonPrimitive("test"), "application/json")
        assertTrue(anchored.ref.txHash.isNotEmpty())
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

- Review [trustweave-testkit Module](../modules/trustweave-testkit.md) for testing utilities
- See [Error Handling](error-handling.md) for error testing patterns
- Check [Creating Plugins](../../contributing/creating-plugins.md) for plugin testing
- Explore existing tests in TrustWeave modules for examples

## References

- TrustWeave-testkit Module](../modules/trustweave-testkit.md)
- Error Handling](error-handling.md)
- Creating Plugins](../contributing/creating-plugins.md)

