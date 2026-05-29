---
title: Performance Considerations
nav_exclude: true
redirect_from:
  - /advanced/performance/

---

# Performance Considerations

This guide covers performance considerations and optimization strategies for TrustWeave applications.

## Overview

TrustWeave is designed for high performance with:

- **Async Operations** – all I/O uses Kotlin coroutines
- **Efficient Serialization** – Kotlinx Serialization for JSON handling
- **Minimal Dependencies** – only essential dependencies
- **Optimized Algorithms** – efficient cryptographic operations

## Performance Best Practices

### Coroutine Usage

TrustWeave operations are async by default:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.trustweave.trust.TrustWeave

// Use coroutines for concurrent operations
suspend fun processMultiple(trustWeave: TrustWeave) = coroutineScope {
    val results = listOf(
        async { trustWeave.createDid { } },
        async { trustWeave.createDid { } },
        async { trustWeave.createDid { } }
    )
    results.awaitAll()
}
```

**What this does:** Executes multiple operations concurrently.

**Outcome:** Better performance for I/O-bound operations.

### Batch Operations

Batch operations when possible:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

// Batch credential issuance (concurrent) — each entry is IssuanceResult
val issuanceResults = subjects.map { subject ->
    async {
        trustWeave.issue {
            credential {
                issuer(issuerDid)
                subject {
                    id("did:key:holder")
                    // map fields from `subject` (your domain model) into claims here
                }
            }
            signedBy(issuerDid, issuerKeyId)
        }
    }
}.awaitAll()

// Or collapse to credentials where failures should abort:
// val credentials = issuanceResults.map { it.getOrThrow() }
```

**Outcome:** Reduces overhead from repeated setup.

### Connection Pooling

For database-backed services, use connection pooling:

```kotlin
// Use HikariCP or similar for connection pooling
val dataSource = HikariDataSource().apply {
    jdbcUrl = "jdbc:postgresql://localhost/TrustWeave"
    maximumPoolSize = 10
}
```

**Outcome:** Reuses database connections for better performance.

### Caching

Cache frequently accessed data:

```kotlin
// Cache DID documents
val didCache = ConcurrentHashMap<String, DidDocument>()

suspend fun resolveWithCache(trustWeave: TrustWeave, did: String): DidDocument {
    return didCache.getOrPut(did) {
        when (val res = trustWeave.resolveDid(did)) {
            is DidResolutionResult.Success -> res.document
            else -> error("DID not resolved: $did ($res)")
        }
    }
}
```

**Outcome:** Reduces repeated DID resolution calls.

## Memory Management

### Resource Cleanup

Use `use {}` for automatic cleanup:

```kotlin
val fixture = TrustWeaveTestFixture.builder().build().use { fixture ->
    // Use fixture
    // Automatic cleanup on exit
}
```

**Outcome:** Prevents resource leaks.

### Large Payloads

For large payloads, consider streaming:

```kotlin
// Stream large credential batches
fun processLargeBatch(subjects: Sequence<JsonObject>) {
    subjects.chunked(100).forEach { chunk ->
        // Process chunk
        processChunk(chunk)
    }
}
```

**Outcome:** Reduces memory usage for large datasets.

## Network Optimization

### HTTP Client Configuration

Configure HTTP clients for optimal performance:

```kotlin
// Use OkHttp with connection pooling
val client = OkHttpClient.Builder()
    .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

**Outcome:** Optimizes network connection reuse.

### Retry Logic

Implement retry logic for network operations:

```kotlin
suspend fun <T> retry(
    times: Int = 3,
    delay: Long = 1000,
    block: suspend () -> T
): T {
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(delay)
        }
    }
    return block()
}
```

**Outcome:** Handles transient network failures.

## Blockchain Optimization

### Transaction Batching

Batch blockchain transactions when possible:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

// Batch multiple anchors using the BlockchainService facade
val results = credentials.map { credential ->
    async {
        trustWeave.blockchains.anchor(
            data = credential,
            serializer = MyCredentialDto.serializer(),
            chainId = chainId
        )
    }
}.awaitAll()
```

**Outcome:** Reduces blockchain transaction fees.

### Confirmation Strategy

`BlockchainAnchorClient` (and the higher-level `trustWeave.blockchains` facade) do
not expose a generic `confirmationStrategy` option — confirmation policy is owned
by each chain plugin and configured at plugin-construction time. Anchoring goes
through `BlockchainService.anchor(data, serializer, chainId)` and reads through
`BlockchainService.read(ref, serializer)`; both are one-shot calls that return when
the underlying client considers the write/read settled.

```kotlin
import org.trustweave.anchor.AnchorRef

// Write — settlement semantics depend on the chain plugin registered under chainId.
val anchored = trustWeave.blockchains.anchor(
    data       = myDto,
    serializer = MyDto.serializer(),
    chainId    = "algorand:testnet"
)

// Read back later.
val roundTrip: MyDto = trustWeave.blockchains.read(anchored.ref, MyDto.serializer())
```

To tune confirmation depth, retries, polling cadence, or finality, configure the
chain plugin you registered in `TrustWeave.build { blockchains { chain(...) { ... } } }`
(see `anchors/plugins/algorand`, `anchors/plugins/ethereum`, etc. — each plugin
defines its own options keys in its `BlockchainAnchorClientOptions` companion).

## Cryptographic Performance

### Algorithm Selection

Choose algorithms based on performance needs:

```kotlin
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult

// Ed25519 is faster than RSA
val fast = kms.generateKey(Algorithm.Ed25519)
val fastHandle = (fast as? GenerateKeyResult.Success)?.keyHandle

// RSA-2048 is more compatible but slower
val compatible = kms.generateKey(Algorithm.RSA.RSA_2048)
val compatibleHandle = (compatible as? GenerateKeyResult.Success)?.keyHandle
```

**Outcome:** Optimizes signing and verification speed.

### Key Caching

Cache key handles for repeated operations:

```kotlin
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.results.GenerateKeyResult
import java.util.concurrent.ConcurrentHashMap

val issuerKeys = ConcurrentHashMap<String, KeyHandle>()

suspend fun getOrCreateKey(issuerDid: String): KeyHandle {
    return issuerKeys.getOrPut(issuerDid) {
        when (val r = kms.generateKey(Algorithm.Ed25519)) {
            is GenerateKeyResult.Success -> r.keyHandle
            is GenerateKeyResult.Failure -> error("Key generation failed: $r")
        }
    }
}
```

**Outcome:** Reduces key generation overhead.

## Monitoring and Profiling

### Performance Metrics

Collect performance metrics:

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.types.getOrThrowDid

// Measure operation time
runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
    val start = System.currentTimeMillis()
    trustWeave.createDid { }.getOrThrowDid()
    val duration = System.currentTimeMillis() - start
    println("DID creation took ${duration}ms")
}
```

### Profiling

Use profiling tools to identify bottlenecks:

```bash
# Use JProfiler or similar
./gradlew test --profile
```

## Scalability Considerations

### Horizontal Scaling

Design for horizontal scaling:

```kotlin
// Stateless operations scale horizontally — each instance holds only configuration + clients
val trustWeave = TrustWeave.quickStart()
```

**Outcome:** Enables horizontal scaling.

### Load Balancing

Use load balancing for services:

```kotlin
// Multiple instances can share load (each with its own config / connection pools)
val instances = listOf(
    TrustWeave.quickStart(),
    TrustWeave.quickStart(),
    TrustWeave.quickStart()
)
```

**Outcome:** Distributes load across instances.

## Benchmarking

### Performance Benchmarks

Create benchmarks for critical paths:

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.trust.types.getOrThrowDid
import kotlin.test.Test
import kotlin.test.assertTrue

@Test
fun benchmarkDidCreation() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
    val iterations = 1000
    val start = System.currentTimeMillis()
    repeat(iterations) {
        trustWeave.createDid { }.getOrThrowDid()
    }
    val duration = System.currentTimeMillis() - start
    val avgTime = duration.toDouble() / iterations
    println("Average DID creation: ${avgTime}ms")
    assertTrue(avgTime < 100) // illustrative threshold
}
```

## Next Steps

- Review [Error Handling](error-handling.md) for performance-aware error handling
- See [Testing Strategies](testing-strategies.md) for performance testing
- Check module-specific documentation for performance tips
- Explore [Architecture Overview](../../core-concepts/introduction/architecture-overview.md) for design considerations

## References

- Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- Error Handling](error-handling.md)
- Testing Strategies](testing-strategies.md)

