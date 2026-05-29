---
title: Testing Guidelines
nav_exclude: true
---

# Testing Guidelines

This guide outlines testing guidelines and best practices for TrustWeave.

## Overview

TrustWeave testing strategy includes:

- **Unit Tests** – test individual components in isolation
- **Integration Tests** – test component interactions
- **End-to-End Tests** – test complete workflows
- **Test Utilities** – `trustweave-testkit` for in-memory implementations

## Testing Principles

### Test Isolation

Each test should be independent:

```kotlin
@Test
fun testIsolated() = runBlocking {
    // Each test gets its own fixture
    val fixture = TrustWeaveTestFixture.builder().build().use { fixture ->
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
import org.trustweave.testkit.*
import org.trustweave.did.*
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

        val didDoc = method.createDid(options)
        assertNotNull(didDoc)
        // DidDocument.id is a Did value class; use `.value` for the raw string.
        assert(didDoc.id.value.startsWith("did:key:"))
    }
}
```

### Testing Key Management

```kotlin
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.kms.*
import kotlin.test.Test

class KmsTest {
    @Test
    fun testGenerateAndSign() = runBlocking {
        val kms = InMemoryKeyManagementService()

        // generateKey/sign return sealed Result types — destructure or pattern-match.
        val key = (kms.generateKey(Algorithm.Ed25519) as GenerateKeyResult.Success).keyHandle
        assertNotNull(key)

        val data = "Hello, TrustWeave!".toByteArray()
        val signature = (kms.sign(key.id, data) as SignResult.Success).signature

        assertNotNull(signature)
        assertEquals(64, signature.size) // Ed25519 signature size
    }
}
```

## Integration Testing

### Testing Credential Workflows

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.types.getOrThrowDid
import kotlin.test.Test
import kotlin.test.assertTrue

class CredentialWorkflowTest {
    @Test
    fun testCredentialIssuanceAndVerification() = runBlocking {
        val trustWeave = TrustWeave.build {
            keys { provider(KmsProviders.IN_MEMORY); algorithm(KeyAlgorithms.ED25519) }
            did { method(DidMethods.KEY) { algorithm(KeyAlgorithms.ED25519) } }
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
            signedBy(issuerDid, "key-1")
        }.getOrThrow()
        val verification = trustWeave.verify(credential)
        assertTrue(verification is VerificationResult.Valid, verification.toString())
    }
}
```

## End-to-End Testing

### EO Integration Tests

```kotlin
import org.trustweave.testkit.eo.BaseEoIntegrationTest
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.anchor.*
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
        // runEoTestScenario returns EoTestResult whose verificationResult is an
        // IntegrityVerificationResult (not VerificationResult). Check `.valid`.
        assert(result.verificationResult.valid)
    }
}
```

## Test Utilities

### TrustWeaveTestFixture

Use test fixtures for setup:

```kotlin
import org.trustweave.testkit.*
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlin.test.Test

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

    // KMS operations return sealed Result types — pattern-match, do not .fold().
    val result = kms.sign(KeyId("nonexistent-key"), "data".toByteArray())
    assertTrue(result is SignResult.Failure.KeyNotFound)
}
```

### Testing Validation

```kotlin
@Test
fun testInvalidInput() = runBlocking {
    val method = DidKeyMockMethod(InMemoryKeyManagementService())

    // resolveDid takes a Did value class and returns DidResolutionResult.
    val result = method.resolveDid(Did("did:key:unknown"))
    assertTrue(result is DidResolutionResult.Failure.NotFound)
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
assertNotNull(didDoc, "DID document should not be null")
// DidDocument.id is a Did value class — use `.value` for the raw string.
assertEquals("did:key:", didDoc.id.value.substring(0, 8), "DID should start with did:key:")

// Less clear
assert(didDoc != null)
assert(didDoc.id.value.startsWith("did:key:"))
```

## Test Coverage

### Coverage Goals

Aim for:

- **Unit Tests** – 80%+ coverage
- **Integration Tests** – cover critical paths
- **End-to-End Tests** – cover main workflows

### Measuring Coverage

Coverage tooling is **not** wired into the build today. The root `build.gradle.kts`
applies neither JaCoCo nor Kover, so `./gradlew tasks` will not list a
`jacocoTestReport` (or `koverReport`) target — running it fails with
`Task 'jacocoTestReport' not found`.

To collect coverage locally, apply a plugin in your fork and re-run tests, for
example:

```kotlin
// settings.gradle.kts or root build.gradle.kts
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.6"
}
```
```bash
./gradlew test koverHtmlReport
```

Wiring coverage into the upstream build is tracked as a separate workstream — open
an issue if you want it adopted project-wide.

## Running Tests

### All Tests

```bash
./gradlew test
```

### Specific Module

```bash
./gradlew :common:test
```

### Specific Test Class

```bash
./gradlew :common:test --tests "DidMethodTest"
```

### With Verbose Output

```bash
./gradlew test --info
```

## Next Steps

- Review [trustweave-testkit Module](../api-reference/modules/trustweave-testkit.md) for testing utilities
- See [Testing Strategies](../api-reference/advanced/testing-strategies.md) for advanced patterns
- Check [Development Setup](development-setup.md) for environment setup
- Explore existing tests in TrustWeave modules for examples

## References

- trustweave-testkit Module](../modules/trustweave-testkit.md)
- Testing Strategies](../advanced/testing-strategies.md)
- Kotlin Test Documentation](https://kotlinlang.org/api/latest/kotlin.test/)


