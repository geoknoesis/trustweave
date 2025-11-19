# Testing Guidelines

This guide outlines testing guidelines and best practices for VeriCore.

## Overview

VeriCore testing strategy includes:

- **Unit Tests** – test individual components in isolation
- **Integration Tests** – test component interactions
- **End-to-End Tests** – test complete workflows
- **Test Utilities** – `vericore-testkit` for in-memory implementations

## Testing Principles

### Test Isolation

Each test should be independent:

```kotlin
@Test
fun testIsolated() = runBlocking {
    // Each test gets its own fixture
    val fixture = VeriCoreTestFixture.builder().build().use { fixture ->
        // Test code
    }
}
```

### Test Naming

Use descriptive test names:

```kotlin
@Test
fun testCreateDidWithEd25519Algorithm() = runBlocking {
    // ...
}
```

### Test Organization

Organize tests by functionality:

```kotlin
class DidMethodTest {
    @Test
    fun testCreateDid() = runBlocking {
        // ...
    }
    
    @Test
    fun testResolveDid() = runBlocking {
        // ...
    }
}
```

## Unit Testing

### Testing DID Methods

```kotlin
import com.geoknoesis.vericore.testkit.*
import com.geoknoesis.vericore.did.*
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

### Testing Key Management

```kotlin
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import com.geoknoesis.vericore.kms.*
import kotlin.test.Test

class KmsTest {
    @Test
    fun testGenerateAndSign() = runBlocking {
        val kms = InMemoryKeyManagementService()
        
        val key = kms.generateKey(Algorithm.Ed25519)
        assertNotNull(key)
        
        val data = "Hello, VeriCore!".toByteArray()
        val signature = kms.sign(key.id, data)
        
        assertNotNull(signature)
        assertEquals(64, signature.size) // Ed25519 signature size
    }
}
```

## Integration Testing

### Testing Credential Workflows

```kotlin
import com.geoknoesis.vericore.testkit.*
import com.geoknoesis.vericore.VeriCore
import kotlin.test.Test

class CredentialWorkflowTest {
    @Test
    fun testCredentialIssuanceAndVerification() = runBlocking {
        val fixture = VeriCoreTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet")
            .build()
            .use { fixture ->
                val vericore = VeriCore.create()
                
                // Create issuer DID
                val issuerDid = vericore.createDid().getOrThrow()
                
                // Issue credential
                val credential = vericore.issueCredential(
                    issuerDid = issuerDid.id,
                    issuerKeyId = issuerDid.document.verificationMethod.first().id,
                    credentialSubject = buildJsonObject {
                        put("id", "did:key:subject")
                        put("name", "Alice")
                    }
                ).getOrThrow()
                
                // Verify credential
                val verificationResult = vericore.verifyCredential(credential).getOrThrow()
                assert(verificationResult.valid)
            }
    }
}
```

## End-to-End Testing

### EO Integration Tests

```kotlin
import com.geoknoesis.vericore.testkit.eo.BaseEoIntegrationTest
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import com.geoknoesis.vericore.anchor.*
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

## Test Utilities

### VeriCoreTestFixture

Use test fixtures for setup:

```kotlin
import com.geoknoesis.vericore.testkit.*
import kotlin.test.Test

class FixtureTest {
    @Test
    fun testWithFixture() = runBlocking {
        val fixture = VeriCoreTestFixture.builder()
            .withKms(InMemoryKeyManagementService())
            .withDidMethod("key") { DidKeyMockMethod(it) }
            .withBlockchainClient("algorand:testnet") {
                InMemoryBlockchainAnchorClient("algorand:testnet")
            }
            .build()
            .use { fixture ->
                val issuerDoc = fixture.createIssuerDid()
                assertNotNull(issuerDoc)
            }
    }
}
```

## Error Testing

### Testing Error Cases

```kotlin
@Test
fun testErrorHandling() = runBlocking {
    val kms = InMemoryKeyManagementService()
    
    val result = kms.sign("nonexistent-key", "data".toByteArray())
    result.fold(
        onSuccess = { fail("Expected error") },
        onFailure = { error ->
            assert(error is VeriCoreError.KeyNotFound)
        }
    )
}
```

### Testing Validation

```kotlin
@Test
fun testInvalidInput() = runBlocking {
    val method = DidKeyMockMethod(InMemoryKeyManagementService())
    
    val result = method.resolveDid("invalid-did")
    result.fold(
        onSuccess = { fail("Expected error") },
        onFailure = { error ->
            assert(error is VeriCoreError.DidResolutionFailed)
        }
    )
}
```

## Best Practices

### Resource Cleanup

Always use `use {}` for cleanup:

```kotlin
fixture.use { fixture ->
    // Test code
    // Automatic cleanup on exit
}
```

### Test Data

Use meaningful test data:

```kotlin
val issuerDid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
val subjectDid = "did:key:z6MkhbXBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
```

### Assertions

Use descriptive assertions:

```kotlin
// Good
assertNotNull(did, "DID should not be null")
assertEquals("did:key:", did.id.substring(0, 8), "DID should start with did:key:")

// Less clear
assert(did != null)
assert(did.id.startsWith("did:key:"))
```

## Test Coverage

### Coverage Goals

Aim for:

- **Unit Tests** – 80%+ coverage
- **Integration Tests** – cover critical paths
- **End-to-End Tests** – cover main workflows

### Measuring Coverage

```bash
./gradlew test jacocoTestReport
```

## Running Tests

### All Tests

```bash
./gradlew test
```

### Specific Module

```bash
./gradlew :vericore-core:test
```

### Specific Test Class

```bash
./gradlew :vericore-core:test --tests "DidMethodTest"
```

### With Verbose Output

```bash
./gradlew test --info
```

## Next Steps

- Review [vericore-testkit Module](../modules/vericore-testkit.md) for testing utilities
- See [Testing Strategies](../advanced/testing-strategies.md) for advanced patterns
- Check [Development Setup](development-setup.md) for environment setup
- Explore existing tests in VeriCore modules for examples

## References

- [vericore-testkit Module](../modules/vericore-testkit.md)
- [Testing Strategies](../advanced/testing-strategies.md)
- [Kotlin Test Documentation](https://kotlinlang.org/api/latest/kotlin.test/)

