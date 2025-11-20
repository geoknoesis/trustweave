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

## Debugging Tips

### Enable Logging

```kotlin
// Add logging dependency
dependencies {
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

// Configure logback.xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="DEBUG">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

### Check Registry State

```kotlin
// Check registered DID methods
val methods = vericore.getAvailableDidMethods()
println("Available DID methods: $methods")

// Check registered blockchain chains
val chains = vericore.getAvailableChains()
println("Available chains: $chains")

// Check wallet capabilities
val wallet = vericore.createWallet("did:key:holder").getOrThrow()
println("Wallet capabilities: ${wallet.capabilities}")
```

### Validate Inputs

```kotlin
import com.geoknoesis.vericore.core.DidValidator

// Validate DID format before use
val validation = DidValidator.validateFormat("did:key:z6Mk...")
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Invalid DID: ${error.message}")
}
```

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

