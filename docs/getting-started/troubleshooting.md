# Troubleshooting Guide

Common issues and solutions when working with VeriCore.

> **Version:** 1.0.0-SNAPSHOT  
> If you encounter issues not covered here, please [file an issue](https://github.com/your-org/vericore/issues) or check the [FAQ](../faq.md).

## Common Issues

### DID Method Not Registered

**Error:**
```
VeriCoreError.DidMethodNotRegistered: Method 'web' is not registered
Available methods: [key]
```

**Solution:**
Register the DID method before using it:

```kotlin
val vericore = VeriCore.create {
    didMethods {
        + DidKeyMethod()  // Already included by default
        + DidWebMethod()  // Add this for did:web support
    }
}
```

**Prevention:**
- Always check available methods: `vericore.getAvailableDidMethods()`
- Use `did:key` for testing (included by default)
- Register methods during VeriCore initialization

### Chain Not Registered

**Error:**
```
VeriCoreError.ChainNotRegistered: Chain 'algorand:testnet' is not registered
Available chains: []
```

**Solution:**
Register the blockchain client before anchoring:

```kotlin
val vericore = VeriCore.create {
    blockchain {
        "algorand:testnet" to algorandClient
        "polygon:mainnet" to polygonClient
    }
}
```

**Prevention:**
- Check available chains: `vericore.getAvailableChains()`
- Use `InMemoryBlockchainAnchorClient` for testing
- Register clients during VeriCore initialization

### Credential Verification Fails

**Error:**
```
CredentialVerificationResult(valid=false, errors=[Proof verification failed])
```

**Common Causes:**

1. **Issuer DID not resolvable**
   ```kotlin
   // Ensure issuer DID is registered and resolvable
   val issuerDid = vericore.createDid().getOrThrow()
   // Use this DID for issuance
   ```

2. **Key ID mismatch**
   ```kotlin
   // Get the correct key ID from the DID document
   val issuerDocument = vericore.createDid().getOrThrow()
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
VeriCoreError.WalletCreationFailed: Provider 'database' not found
```

**Solution:**
- Use `WalletProvider.InMemory` for testing
- Register custom wallet factories if needed
- Check wallet provider availability

```kotlin
val wallet = vericore.createWallet(
    holderDid = "did:key:holder",
    provider = WalletProvider.InMemory  // Use in-memory for testing
).getOrThrow()
```

### Plugin Initialization Fails

**Error:**
```
VeriCoreError.PluginInitializationFailed: Configuration missing
```

**Solution:**
- Ensure all required configuration is provided
- Check plugin-specific requirements
- Verify environment variables are set

```kotlin
val config = mapOf(
    "database" to mapOf("url" to "jdbc:postgresql://localhost/vericore"),
    "apiKey" to System.getenv("PLUGIN_API_KEY")
)

vericore.initialize(config).fold(
    onSuccess = { println("Plugins initialized") },
    onFailure = { error -> println("Initialization failed: ${error.message}") }
)
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
    
    <!-- VeriCore specific loggers -->
    <logger name="com.geoknoesis.vericore" level="DEBUG"/>
    <logger name="com.geoknoesis.vericore.core" level="TRACE"/>
    <logger name="com.geoknoesis.vericore.plugins" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

### Step 2: Verify System State

Check all registries and available services:

```kotlin
fun debugSystemState(vericore: VeriCore) {
    println("=== VeriCore System State ===")
    
    // Check registered DID methods
    val methods = vericore.getAvailableDidMethods()
    println("Available DID methods: $methods")
    if (methods.isEmpty()) {
        println("⚠️  WARNING: No DID methods registered!")
    }
    
    // Check registered blockchain chains
    val chains = vericore.getAvailableChains()
    println("Available chains: $chains")
    
    // Check plugin status
    println("\n=== Plugin Status ===")
    // Add plugin status checks if available
    
    // Test basic operations
    println("\n=== Basic Operation Tests ===")
    val testDid = vericore.createDid().getOrNull()
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
import com.geoknoesis.vericore.core.DidValidator
import com.geoknoesis.vericore.core.CredentialValidator

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
    val availableMethods = vericore.getAvailableDidMethods()
    if (method !in availableMethods) {
        println("❌ DID method '$method' not available")
        println("   Available methods: $availableMethods")
        return
    }
    
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
    val availableMethods = vericore.getAvailableDidMethods()
    println("Available methods: $availableMethods")
    if (method !in availableMethods) {
        println("❌ Method not available")
        return
    }
    println("✅ Method available")
    
    // Step 4: Resolution attempt
    println("\n[Step 4] Attempting resolution...")
    val startTime = System.currentTimeMillis()
    val result = vericore.resolveDid(did)
    val duration = System.currentTimeMillis() - startTime
    
    result.fold(
        onSuccess = { resolution ->
            println("✅ Resolution successful (${duration}ms)")
            println("   Document ID: ${resolution.document?.id}")
            println("   Methods: ${resolution.document?.verificationMethod?.size ?: 0}")
        },
        onFailure = { error ->
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
    
    // Step 1: Create VeriCore instance
    println("\n[1] Creating VeriCore instance...")
    val vericore = VeriCore.create()
    println("✅ VeriCore created")
    
    // Step 2: Create a DID
    println("\n[2] Creating DID...")
    val didResult = vericore.createDid()
    didResult.fold(
        onSuccess = { did ->
            println("✅ DID created: ${did.id}")
            
            // Step 3: Resolve the DID
            println("\n[3] Resolving DID...")
            val resolveResult = vericore.resolveDid(did.id)
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
fun analyzeError(error: VeriCoreError) {
    println("=== Error Analysis ===")
    println("Code: ${error.code}")
    println("Message: ${error.message}")
    println("\nContext:")
    error.context.forEach { (key, value) ->
        println("  $key: $value")
    }
    
    // Check for specific error types
    when (error) {
        is VeriCoreError.DidMethodNotRegistered -> {
            println("\n💡 Suggestions:")
            println("  - Register the method: vericore.registerDidMethod(...)")
            println("  - Use an available method: ${error.availableMethods}")
        }
        is VeriCoreError.ChainNotRegistered -> {
            println("\n💡 Suggestions:")
            println("  - Register the chain: vericore.registerBlockchainClient(...)")
            println("  - Use an available chain: ${error.availableChains}")
        }
        is VeriCoreError.InvalidDidFormat -> {
            println("\n💡 Suggestions:")
            println("  - Check DID format: did:<method>:<identifier>")
            println("  - Validate before use: DidValidator.validateFormat(...)")
        }
        else -> {
            println("\n💡 General suggestions:")
            println("  - Check error context above")
            println("  - Verify inputs are valid")
            println("  - Check system state: debugSystemState(vericore)")
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
    val result = vericore.resolveDid(testDid)
    val duration = System.currentTimeMillis() - startTime
    
    result.fold(
        onSuccess = {
            println("✅ Network connectivity OK (${duration}ms)")
        },
        onFailure = { error ->
            when (error) {
                is VeriCoreError.DidNotFound -> {
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
// Cache resolved DIDs
val didCache = mutableMapOf<String, DidDocument>()

suspend fun resolveDidCached(did: String): DidDocument? {
    return didCache.getOrPut(did) {
        vericore.resolveDid(did).getOrThrow().document
    }
}
```

### Memory Usage

**Issue:** High memory usage with many credentials

**Solutions:**
- Use persistent wallet providers instead of in-memory
- Archive old credentials
- Implement pagination for credential queries

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
    // Your VeriCore code here
}
```

## Getting Help

If you're still experiencing issues:

1. **Check the FAQ**: [FAQ](../faq.md)
2. **Review Examples**: See the [Quick Start Guide](quick-start.md) for runnable examples
3. **Check Error Handling**: [Error Handling](../advanced/error-handling.md)
4. **File an Issue**: Include:
   - VeriCore version
   - Kotlin/Java versions
   - Error message and stack trace
   - Minimal reproducible example

## Related Documentation

- [Error Handling](../advanced/error-handling.md) - Detailed error handling patterns
- [Installation](installation.md) - Setup and configuration
- [Quick Start](quick-start.md) - Getting started guide
- [API Reference](../api-reference/core-api.md) - Complete API documentation

