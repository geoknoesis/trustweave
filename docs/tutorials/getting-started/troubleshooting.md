---
title: Troubleshooting Guide
nav_order: 100
parent: Getting Started
redirect_from:
  - /getting-started/troubleshooting/

---

# Troubleshooting Guide

Common issues and solutions when working with TrustWeave.

> **Version:** 0.6.0
> If you encounter issues not covered here, please [file an issue](https://github.com/your-org/TrustWeave/issues) or check the [FAQ](../../faq.md).

## Common Issues

### DID Method Not Registered

**Error:**
```
IllegalStateException: DID method 'web' is not registered
Available methods: [key]
```

**Solution:**
Register the DID method before using it:

```kotlin
val trustWeave = TrustWeave.build {
    did {
        method("web") { domain("yourdomain.com") }
    }
}
```

**Prevention:**
- Check available methods via configuration: `trustWeave.configuration.didRegistry.getAllMethodNames()`
- Use `did:key` for testing (included by default)
- Register methods during TrustWeave initialization

### Chain Not Registered

**Error:**
```
IllegalStateException: Blockchain chain 'algorand:testnet' is not registered
Available chains: []
```

**Solution:**
Register the blockchain client before anchoring:

```kotlin
val trustWeave = TrustWeave.build {
    anchor {
        chain("algorand:testnet") {
            provider("algorand")
            options { /* map from your Algorand client / env */ }
        }
        chain("polygon:mainnet") {
            provider("polygon")
            options { /* RPC URL, credentials, etc. */ }
        }
    }
}
```

**Prevention:**
- Check available chains via configuration: `trustWeave.configuration.blockchainRegistry.getAllChainIds()`
- Use `InMemoryBlockchainAnchorClient` for testing
- Register clients during TrustWeave initialization

### Credential Verification Fails

**Error:**
```
VerificationResult.Invalid.InvalidProof(..., errors=[Proof verification failed])
```

**Common Causes:**

1. **Issuer DID not resolvable**
   ```kotlin
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519

   // Ensure issuer DID is created and resolvable
   val issuerDid = trustWeave.createDid {
       method(KEY)
       algorithm(ED25519)
   }.getOrThrowDid()
   // Use this DID for issuance
   ```

2. **Key ID mismatch**
   ```kotlin
   import org.trustweave.trust.types.getOrThrowDid
   import org.trustweave.did.resolver.DidResolutionResult
   import org.trustweave.did.resolver.errorMessage
   import org.trustweave.did.identifiers.extractKeyId
   import org.trustweave.trust.dsl.credential.DidMethods.KEY

   // Get the correct key ID from the DID document
   val issuerDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
   val issuerDoc = when (val res = trustWeave.resolveDid(issuerDid)) {
       is DidResolutionResult.Success -> res.document
       else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
   }
   val issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.extractKeyId()
       ?: throw IllegalStateException("No verification method found")
   ```

3. **Credential expired**
   ```kotlin
   import kotlinx.datetime.Clock

   // credential.expirationDate is already a kotlinx.datetime.Instant?
   val expiration = credential.expirationDate
   if (expiration != null && expiration < Clock.System.now()) {
       println("Credential expired")
   }
   ```

**Solution:**
- Verify issuer DID is resolvable
- Ensure key ID matches the DID document
- Check credential expiration dates
- Verify proof type is supported

### Wallet Creation Fails

**Error:**
```
WalletException.WalletCreationFailed: Provider 'database' not found
```

**Solution:**
- Use `WalletProvider.InMemory` for testing
- Register custom wallet factories if needed
- Check wallet provider availability

```kotlin
import org.trustweave.trust.types.getOrThrow

val wallet = trustWeave.wallet {
    id("holder-wallet")
    holder("did:key:holder")
    enableOrganization()
    enablePresentation()
}.getOrThrow()
```

### Plugin Initialization Fails

**Error:**
```
PluginException.InitializationFailed: Configuration missing
```

**Solution:**
- Ensure all required configuration is provided
- Check plugin-specific requirements
- Verify environment variables are set

There is no `trustWeave.initialize(config)` API. All plugin configuration happens
inside `TrustWeave.build { ... }`. Re-read the failing plugin's required-options
contract and pass values via the matching DSL block:

```kotlin
import org.trustweave.trust.TrustWeave

val trustWeave = TrustWeave.build {
    kms {
        // KMS plugins typically need a credential or endpoint here.
    }
    did {
        method("web") {
            // did:web plugin options
        }
    }
    blockchains {
        chain("algorand:testnet") {
            provider("algorand")
            options {
                put("algodUrl",   System.getenv("ALGOD_URL"))
                put("algodToken", System.getenv("ALGOD_TOKEN"))
            }
        }
    }
}
```

If a plugin still fails: confirm it appears on the classpath (`./gradlew dependencies`),
that its `META-INF/services` provider file is shaded into the jar, and that the
DSL block name matches the plugin id reported by the failing `PluginException`.

## Debugging Workflows

### Step 1: Enable Comprehensive Logging

Enable detailed logging to see what's happening:

```kotlin
// Add logging dependency
dependencies {
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

// Configure logback.xml for detailed debugging
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- TrustWeave specific loggers -->
    <logger name="org.trustweave" level="DEBUG"/>
    <logger name="org.trustweave.core" level="TRACE"/>
    <logger name="org.trustweave.plugins" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

### Step 2: Verify System State

Check all registries and available services:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.types.getOrThrowDid

suspend fun debugSystemState(trustWeave: TrustWeave) {
    println("=== TrustWeave System State ===")

    // Registered DID methods (from the facade configuration)
    val methods = trustWeave.configuration.didMethods.keys.sorted()
    println("Available DID methods: $methods")
    if (methods.isEmpty()) {
        println("⚠️  WARNING: No DID methods registered!")
    }

    // Registered anchor chain IDs
    val chains = trustWeave.configuration.blockchainRegistry.getAllChainIds().sorted()
    println("Available blockchain chain IDs: $chains")

    println("\n=== Plugin Status ===")
    // Add plugin status checks if available

    println("\n=== Basic Operation Tests ===")
    try {
        val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()
        println("✅ DID creation works: ${did.value}")
    } catch (e: IllegalStateException) {
        println("❌ DID creation failed: ${e.message}")
    }
}
```

### Step 3: Validate Inputs Before Operations

Always validate inputs to catch errors early:

```kotlin
import org.trustweave.core.util.DidValidator
import org.trustweave.credential.validation.CredentialValidator

fun validateBeforeOperation(did: String, credential: VerifiableCredential? = null) {
    // Validate DID format
    val didValidation = DidValidator.validateFormat(did)
    if (!didValidation.isValid()) {
        val error = didValidation as ValidationResult.Invalid
        println("❌ Invalid DID format: ${error.message}")
        println("   Field: ${error.field}")
        println("   Value: ${error.value}")
        return
    }

    // Validate DID method is available
    val method = did.substringAfter("did:").substringBefore(":")
    // Note: This requires a TrustWeave instance - pass it as parameter
    // val availableMethods = trustWeave.configuration.didMethods.keys
    // if (method !in availableMethods) {
    //     println("❌ DID method '$method' not available")
    //     println("   Available methods: $availableMethods")
    //     return
    // }

    // Validate credential if provided
    credential?.let {
        val credValidation = CredentialValidator.validateStructure(it)
        if (!credValidation.isValid()) {
            val error = credValidation as ValidationResult.Invalid
            println("❌ Invalid credential structure: ${error.message}")
            println("   Field: ${error.field}")
            return
        }
    }

    println("✅ All validations passed")
}
```

### Step 4: Trace Operation Flow

Add detailed tracing to understand operation flow:

```kotlin
suspend fun traceDidResolution(did: String) {
    println("=== Tracing DID Resolution ===")
    println("Input DID: $did")

    // Step 1: Format validation
    println("\n[Step 1] Validating DID format...")
    val formatValidation = DidValidator.validateFormat(did)
    if (!formatValidation.isValid()) {
        println("❌ Format validation failed")
        return
    }
    println("✅ Format valid")

    // Step 2: Method extraction
    println("\n[Step 2] Extracting method...")
    val method = did.substringAfter("did:").substringBefore(":")
    println("Method: $method")

    // Step 3: Method availability
    println("\n[Step 3] Checking method availability...")
    // Note: This requires a TrustWeave instance - pass it as parameter
    // val availableMethods = trustWeave.configuration.didMethods.keys
    // println("Available methods: $availableMethods")
    // if (method !in availableMethods) {
    //     println("❌ Method not available")
    //     return
    // }
    // println("✅ Method available")

    // Step 4: Resolution attempt
    println("\n[Step 4] Attempting resolution...")
    val startTime = System.currentTimeMillis()
    val resolution = trustWeave.resolveDid(did)
    val duration = System.currentTimeMillis() - startTime
    when (resolution) {
        is DidResolutionResult.Success ->
            println("✅ Resolved (${duration}ms): ${resolution.document.id}")
        is DidResolutionResult.Failure -> {
            println("❌ Resolution failed (${duration}ms)")
            println("   Error: ${resolution.errorMessage}")
        }
    }
}
```

### Step 5: Isolate the Problem

Create a minimal reproducible example:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage

suspend fun minimalReproducibleExample() {
    println("=== Minimal Reproducible Example ===")

    // Step 1: Create TrustWeave instance
    println("\n[1] Creating TrustWeave instance...")
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
    println("✅ TrustWeave created")

    // Step 2: Create a DID
    println("\n[2] Creating DID...")
    val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()
    println("✅ DID created: ${did.value}")

    // Step 3: Resolve the DID
    println("\n[3] Resolving DID...")
    when (val resolution = trustWeave.resolveDid(did)) {
        is DidResolutionResult.Success -> {
            println("✅ DID resolved")
            println("   Document: ${resolution.document.id}")
        }
        is DidResolutionResult.Failure -> {
            println("❌ Resolution failed: ${resolution.errorMessage}")
        }
    }
}
```

### Step 6: Check Error Context

Always examine error context for debugging clues:

```kotlin
fun analyzeError(error: TrustWeaveException) {
    println("=== Error Analysis ===")
    println("Code: ${error.code}")
    println("Message: ${error.message}")
    println("\nContext:")
    error.context.forEach { (key, value) ->
        println("  $key: $value")
    }

    // Check for specific error types
    when (error) {
        is DidException.DidMethodNotRegistered -> {
            println("\n💡 Suggestions:")
            println("  - Register the method in TrustWeave.build { did { method(\"web\") { domain(...) } } } (or via SPI)")
            println("  - Use an available method: ${error.availableMethods}")
        }
        is BlockchainException.ChainNotRegistered -> {
            println("\n💡 Suggestions:")
            println("  - Register the chain in TrustWeave.build { anchor { chain(\"chainId\") { provider(...) } } }")
            println("  - Use an available chain: ${error.availableChains}")
        }
        is DidException.InvalidDidFormat -> {
            println("\n💡 Suggestions:")
            println("  - Check DID format: did:<method>:<identifier>")
            println("  - Validate before use: DidValidator.validateFormat(...)")
        }
        else -> {
            println("\n💡 General suggestions:")
            println("  - Check error context above")
            println("  - Verify inputs are valid")
            println("  - Check system state: debugSystemState(TrustWeave)")
        }
    }

    error.cause?.let { cause ->
        println("\nUnderlying exception:")
        println("  ${cause::class.simpleName}: ${cause.message}")
        cause.printStackTrace()
    }
}
```

### Step 7: Network and Connectivity Checks

For operations requiring network access:

```kotlin
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage

suspend fun checkNetworkConnectivity(trustWeave: TrustWeave) {
    println("=== Network Connectivity Check ===")

    // Test DID resolution (requires network for did:web)
    val testDid = "did:web:example.com"
    println("Testing resolution of: $testDid")

    val startTime = System.currentTimeMillis()
    val resolution = trustWeave.resolveDid(testDid)
    val duration = System.currentTimeMillis() - startTime

    when (resolution) {
        is DidResolutionResult.Success ->
            println("✅ Network connectivity OK (${duration}ms)")
        is DidResolutionResult.Failure.NotFound ->
            println("⚠️  Network accessible but DID not found (${duration}ms)")
        is DidResolutionResult.Failure -> {
            println("❌ Network issue or resolver error (${duration}ms)")
            println("   Error: ${resolution.errorMessage}")
        }
    }
}
```

## Debugging Tips

### Enable Logging

See [Step 1: Enable Comprehensive Logging](#step-1-enable-comprehensive-logging) above.

### Check Registry State

See [Step 2: Verify System State](#step-2-verify-system-state) above.

### Validate Inputs

See [Step 3: Validate Inputs Before Operations](#step-3-validate-inputs-before-operations) above.

## Performance Issues

### Slow DID Resolution

**Issue:** DID resolution takes too long

**Solutions:**
- Use in-memory DID methods for testing
- Cache resolved DID documents
- Use local DID resolvers when possible

```kotlin
import org.trustweave.trust.TrustWeave
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

// Cache resolved DIDs
val didCache: Cache<String, DidDocument> = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build()

suspend fun resolveDidCached(
    trustWeave: TrustWeave,
    did: String
): DidDocument? {
    return didCache.get(did) {
        val resolution = trustWeave.resolveDid(did)
        when (resolution) {
            is DidResolutionResult.Success -> resolution.document
            else -> null
        }
    }
}
```

### Memory Usage

**Issue:** High memory usage with many credentials

**Solutions:**
- Use persistent wallet providers instead of in-memory
- Archive old credentials
- Implement pagination for credential queries

Persistent wallet storage is selected with `provider("database")` (or another
registered wallet-storage factory) inside `trustWeave.wallet { ... }`. There is **no**
`storageProvider` / `storagePath` DSL on `WalletBuilder`, and `Wallet.list(...)`
takes a `CredentialFilter?` — not an `(offset, limit)` pair. Page through results
by attaching predicates to `CredentialFilter`.

```kotlin
import org.trustweave.wallet.CredentialFilter

// Use a registered persistent wallet provider.
val wallet = trustWeave.wallet {
    holder(holderDid)
    provider("database")
}.getOrThrow()

// All credentials (uses default empty filter).
val all = wallet.list()

// Filter to narrow the working set instead of paginating in memory.
val degrees = wallet.list(
    CredentialFilter(type = listOf("UniversityDegreeCredential"))
)
```

### Slow Credential Issuance

**Issue:** Credential issuance is slow

**Solutions:**
- Use faster cryptographic algorithms (Ed25519 vs RSA)
- Cache issuer DID documents
- Use connection pooling for KMS operations
- Batch operations when possible

```kotlin
// Batch credential issuance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.trustweave.did.identifiers.Did

suspend fun issueMultipleCredentials(
    trustWeave: TrustWeave,
    requests: List<CredentialRequest>
): List<VerifiableCredential> {
    return requests.map { request ->
        async {
            trustWeave.issue {
                credential {
                    type(CredentialType.VerifiableCredential, CredentialType.fromString(request.type))
                    issuer(request.issuerDid)
                    subject {
                        id(request.holderDid)
                        request.claims.forEach { (key, value) ->
                            key to value
                        }
                    }
                }
                signedBy(issuerDid = Did(request.issuerDid), keyId = request.keyId)
            }
        }
    }.awaitAll()
}
```

### High CPU Usage

**Issue:** High CPU usage during operations

**Solutions:**
- Use hardware acceleration for cryptographic operations
- Implement request throttling
- Use async operations to avoid blocking
- Profile and optimize hot paths

## Concurrency Issues

### Race Conditions

**Issue:** Concurrent operations causing inconsistent state

**Solutions:**
- Use thread-safe data structures
- Implement proper locking for shared state
- Use coroutine-safe operations

```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThreadSafeCredentialStore {
    private val mutex = Mutex()
    private val credentials = mutableMapOf<String, VerifiableCredential>()

    suspend fun store(id: String, credential: VerifiableCredential) {
        mutex.withLock {
            credentials[id] = credential
        }
    }

    suspend fun get(id: String): VerifiableCredential? {
        return mutex.withLock {
            credentials[id]
        }
    }
}
```

### Deadlocks

**Issue:** Operations hanging due to deadlocks

**Solutions:**
- Avoid nested locks
- Use timeout for operations
- Use non-blocking operations

```kotlin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.dsl.credential.DidMethods.KEY

suspend fun operationWithTimeout(
    trustWeave: TrustWeave,
    timeoutMillis: Long = 5000
) {
    try {
        withTimeout(timeoutMillis) {
            
            val did = try {
                trustWeave.createDid { method(KEY) }.getOrThrowDid()
            } catch (e: IllegalStateException) {
                logger.error("DID creation failed: ${e.message}")
                return@withTimeout
            }
            // ... operation
        }
    } catch (e: TimeoutCancellationException) {
        logger.error("Operation timed out after ${timeoutMillis}ms")
        throw TrustWeaveException.Unknown(
            code = "OPERATION_TIMEOUT",
            message = "Operation timed out",
            context = emptyMap(),
            cause = e
        )
    }
}
```

### Resource Exhaustion

**Issue:** Running out of connections, memory, or threads

**Solutions:**
- Implement connection pooling
- Set resource limits
- Use backpressure mechanisms
- Monitor resource usage

```kotlin
// Configure connection pool
val dataSource = HikariDataSource().apply {
    jdbcUrl = "jdbc:postgresql://db.example.org.trustweave"
    maximumPoolSize = 20  // Limit connections
    minimumIdle = 5
    connectionTimeout = 30000
    idleTimeout = 600000
    maxLifetime = 1800000
}
```

## Environment-Specific Issues

### Java Version Mismatch

**Error:**
```
Unsupported class file major version 65
```

**Solution:**
- Ensure Java 21+ is installed
- Set `JAVA_HOME` environment variable
- Update Gradle Java toolchain

```kotlin
// build.gradle.kts
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

### Kotlin Version Conflicts

**Error:**
```
Kotlin version mismatch
```

**Solution:**
- Use Kotlin 2.2.0+ as required
- Update Kotlin version in `buildSrc/src/main/kotlin/Versions.kt`
- Sync Gradle dependencies

### Coroutine Context Issues

**Error:**
```
kotlinx.coroutines.CancellationException
```

**Solution:**
- Ensure proper coroutine scope
- Use `runBlocking` for main functions
- Handle cancellation properly

```kotlin
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Your TrustWeave code here
}
```

## Getting Help

If you're still experiencing issues:

1. **Check the FAQ**: [FAQ](../../faq.md)
2. **Review Examples**: See the [Quick Start Guide](quick-start.md) for runnable examples
3. **Check Error Handling**: [Error Handling](../../api-reference/advanced/error-handling.md)
4. **File an Issue**: Include:
   - TrustWeave version
   - Kotlin/Java versions
   - Error message and stack trace
   - Minimal reproducible example

## Related Documentation

- Error Handling](../advanced/error-handling.md) - Detailed error handling patterns
- Installation](installation.md) - Setup and configuration
- Quick Start](quick-start.md) - Getting started guide
- API Reference](../api-reference/core-api.md) - Complete API documentation

