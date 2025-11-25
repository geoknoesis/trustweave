# Troubleshooting Guide

Common issues and solutions when working with TrustWeave.

> **Version:** 1.0.0-SNAPSHOT  
> If you encounter issues not covered here, please [file an issue](https://github.com/your-org/TrustWeave/issues) or check the [FAQ](../faq.md).

## Common Issues

### DID Method Not Registered

**Error:**
```
TrustWeaveError.DidMethodNotRegistered: Method 'web' is not registered
Available methods: [key]
```

**Solution:**
Register the DID method before using it:

```kotlin
val TrustWeave = TrustWeave.create {
    didMethods {
        + DidKeyMethod()  // Already included by default
        + DidWebMethod()  // Add this for did:web support
    }
}
```

**Prevention:**
- Always check available methods: `trustweave.dids.availableMethods()`
- Use `did:key` for testing (included by default)
- Register methods during TrustWeave initialization

### Chain Not Registered

**Error:**
```
TrustWeaveError.ChainNotRegistered: Chain 'algorand:testnet' is not registered
Available chains: []
```

**Solution:**
Register the blockchain client before anchoring:

```kotlin
val TrustWeave = TrustWeave.create {
    blockchains {
        "algorand:testnet" to algorandClient
        "polygon:mainnet" to polygonClient
    }
}
```

**Prevention:**
- Check available chains: `trustweave.blockchains.availableChains()`
- Use `InMemoryBlockchainAnchorClient` for testing
- Register clients during TrustWeave initialization

### Credential Verification Fails

**Error:**
```
CredentialVerificationResult(valid=false, errors=[Proof verification failed])
```

**Common Causes:**

1. **Issuer DID not resolvable**
   ```kotlin
   // Ensure issuer DID is registered and resolvable
   val issuerDid = TrustWeave.dids.create()
   // Use this DID for issuance
   ```

2. **Key ID mismatch**
   ```kotlin
   // Get the correct key ID from the DID document
   val issuerDocument = TrustWeave.dids.create()
   val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
       ?: error("No verification method found")
   ```

3. **Credential expired**
   ```kotlin
   // Check expiration date
   if (credential.expirationDate != null) {
       val expiration = Instant.parse(credential.expirationDate)
       if (expiration.isBefore(Instant.now())) {
           println("Credential expired")
       }
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
TrustWeaveError.WalletCreationFailed: Provider 'database' not found
```

**Solution:**
- Use `WalletProvider.InMemory` for testing
- Register custom wallet factories if needed
- Check wallet provider availability

```kotlin
val wallet = trustweave.wallets.create(
    holderDid = "did:key:holder",
    type = WalletType.InMemory  // Use in-memory for testing
)
```

### Plugin Initialization Fails

**Error:**
```
TrustWeaveError.PluginInitializationFailed: Configuration missing
```

**Solution:**
- Ensure all required configuration is provided
- Check plugin-specific requirements
- Verify environment variables are set

```kotlin
val config = mapOf(
    "database" to mapOf("url" to "jdbc:postgresql://localhost/TrustWeave"),
    "apiKey" to System.getenv("PLUGIN_API_KEY")
)

try {
    trustweave.initialize(config)
    println("Plugins initialized")
} catch (error: TrustWeaveError) {
    println("Initialization failed: ${error.message}")
}
```

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
    <logger name="com.trustweave" level="DEBUG"/>
    <logger name="com.trustweave.core" level="TRACE"/>
    <logger name="com.trustweave.plugins" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

### Step 2: Verify System State

Check all registries and available services:

```kotlin
fun debugSystemState(trustweave: TrustWeave) {
    println("=== TrustWeave System State ===")
    
    // Check registered DID methods
    val methods = trustweave.dids.availableMethods()
    println("Available DID methods: $methods")
    if (methods.isEmpty()) {
        println("⚠️  WARNING: No DID methods registered!")
    }
    
    // Check registered blockchain chains
    val chains = trustweave.blockchains.availableChains()
    println("Available chains: $chains")
    
    // Check plugin status
    println("\n=== Plugin Status ===")
    // Add plugin status checks if available
    
    // Test basic operations
    println("\n=== Basic Operation Tests ===")
    val testDid = try { trustweave.dids.create() } catch (e: Exception) { null }
    if (testDid != null) {
        println("✅ DID creation works: ${testDid.id}")
    } else {
        println("❌ DID creation failed")
    }
}
```

### Step 3: Validate Inputs Before Operations

Always validate inputs to catch errors early:

```kotlin
import com.trustweave.core.util.DidValidator
import com.trustweave.credential.validation.CredentialValidator

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
    // val availableMethods = trustweave.dids.availableMethods()
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
    // val availableMethods = trustweave.dids.availableMethods()
    // println("Available methods: $availableMethods")
    // if (method !in availableMethods) {
    //     println("❌ Method not available")
    //     return
    // }
    // println("✅ Method available")
    
    // Step 4: Resolution attempt
    println("\n[Step 4] Attempting resolution...")
    val startTime = System.currentTimeMillis()
    val resolution = try {
        trustweave.dids.resolve(did)
    } catch (error: TrustWeaveError) {
        val duration = System.currentTimeMillis() - startTime
        println("❌ Resolution failed (${duration}ms)")
            println("   Error: ${error.message}")
            println("   Code: ${error.code}")
            error.context.forEach { (key, value) ->
                println("   $key: $value")
            }
        }
    )
}
```

### Step 5: Isolate the Problem

Create a minimal reproducible example:

```kotlin
suspend fun minimalReproducibleExample() {
    println("=== Minimal Reproducible Example ===")
    
    // Step 1: Create TrustWeave instance
    println("\n[1] Creating TrustWeave instance...")
    val TrustWeave = TrustWeave.create()
    println("✅ TrustWeave created")
    
    // Step 2: Create a DID
    println("\n[2] Creating DID...")
    val did = TrustWeave.dids.create()
    val didResult = Result.success(did)
    didResult.fold(
        onSuccess = { did ->
            println("✅ DID created: ${did.id}")
            
            // Step 3: Resolve the DID
            println("\n[3] Resolving DID...")
            val resolution = TrustWeave.dids.resolve(did.id)
            val resolveResult = Result.success(resolution)
            resolveResult.fold(
                onSuccess = { resolution ->
                    println("✅ DID resolved")
                    println("   Document: ${resolution.document?.id}")
                },
                onFailure = { error ->
                    println("❌ Resolution failed")
                    println("   Error: ${error.message}")
                }
            )
        },
        onFailure = { error ->
            println("❌ DID creation failed")
            println("   Error: ${error.message}")
            println("   Code: ${error.code}")
        }
    )
}
```

### Step 6: Check Error Context

Always examine error context for debugging clues:

```kotlin
fun analyzeError(error: TrustWeaveError) {
    println("=== Error Analysis ===")
    println("Code: ${error.code}")
    println("Message: ${error.message}")
    println("\nContext:")
    error.context.forEach { (key, value) ->
        println("  $key: $value")
    }
    
    // Check for specific error types
    when (error) {
        is TrustWeaveError.DidMethodNotRegistered -> {
            println("\n💡 Suggestions:")
            println("  - Register the method during TrustWeave.create { didMethods { + DidMethod() } }")
            println("  - Use an available method: ${error.availableMethods}")
        }
        is TrustWeaveError.ChainNotRegistered -> {
            println("\n💡 Suggestions:")
            println("  - Register the chain during TrustWeave.create { blockchains { \"chainId\" to client } }")
            println("  - Use an available chain: ${error.availableChains}")
        }
        is TrustWeaveError.InvalidDidFormat -> {
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
suspend fun checkNetworkConnectivity() {
    println("=== Network Connectivity Check ===")
    
    // Test DID resolution (requires network for did:web)
    val testDid = "did:web:example.com"
    println("Testing resolution of: $testDid")
    
    val startTime = System.currentTimeMillis()
    val resolution = TrustWeave.dids.resolve(testDid)
    val result = Result.success(resolution)
    val duration = System.currentTimeMillis() - startTime
    
    result.fold(
        onSuccess = {
            println("✅ Network connectivity OK (${duration}ms)")
        },
        onFailure = { error ->
            when (error) {
                is TrustWeaveError.DidNotFound -> {
                    println("⚠️  Network accessible but DID not found")
                }
                else -> {
                    println("❌ Network issue or timeout")
                    println("   Error: ${error.message}")
                    println("   Duration: ${duration}ms")
                }
            }
        }
    )
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
import com.trustweave.trust.TrustLayer
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

// Cache resolved DIDs
val didCache: Cache<String, DidDocument> = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build()

suspend fun resolveDidCached(
    trustLayer: TrustLayer,
    did: String
): DidDocument? {
    return didCache.get(did) {
        val context = trustLayer.getDslContext()
        val resolver = context.getDidResolver()
        resolver?.resolve(did)?.document
    }
}
```

### Memory Usage

**Issue:** High memory usage with many credentials

**Solutions:**
- Use persistent wallet providers instead of in-memory
- Archive old credentials
- Implement pagination for credential queries

```kotlin
// Use persistent storage instead of in-memory
val wallet = trustLayer.wallet {
    holder(holderDid)
    // Use database storage
    storageProvider("database")
    storagePath("wallets/${holderDid}")
}

// Implement pagination
val credentials = wallet.list(offset = 0, limit = 100)
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

suspend fun issueMultipleCredentials(
    trustLayer: TrustLayer,
    requests: List<CredentialRequest>
): List<VerifiableCredential> {
    return requests.map { request ->
        async {
            trustLayer.issue {
                credential {
                    type("VerifiableCredential", request.type)
                    issuer(request.issuerDid)
                    subject {
                        id(request.holderDid)
                        request.claims.forEach { (key, value) ->
                            claim(key, value)
                        }
                    }
                }
                by(issuerDid = request.issuerDid, keyId = request.keyId)
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

suspend fun operationWithTimeout(
    trustLayer: TrustLayer,
    timeoutMillis: Long = 5000
) {
    try {
        withTimeout(timeoutMillis) {
            val did = trustLayer.createDid { method("key") }
            // ... operation
        }
    } catch (e: TimeoutCancellationException) {
        logger.error("Operation timed out after ${timeoutMillis}ms")
        throw TrustWeaveError.Unknown(
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
    jdbcUrl = "jdbc:postgresql://db.example.com/trustweave"
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

1. **Check the FAQ**: [FAQ](../faq.md)
2. **Review Examples**: See the [Quick Start Guide](quick-start.md) for runnable examples
3. **Check Error Handling**: [Error Handling](../advanced/error-handling.md)
4. **File an Issue**: Include:
   - TrustWeave version
   - Kotlin/Java versions
   - Error message and stack trace
   - Minimal reproducible example

## Related Documentation

- [Error Handling](../advanced/error-handling.md) - Detailed error handling patterns
- [Installation](installation.md) - Setup and configuration
- [Quick Start](quick-start.md) - Getting started guide
- [API Reference](../api-reference/core-api.md) - Complete API documentation

