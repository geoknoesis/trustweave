# Credential Exchange Protocols - Best Practices

Guidelines and best practices for using credential exchange protocols effectively and securely.

## Table of Contents

1. [Security Best Practices](#security-best-practices)
2. [Performance Optimization](#performance-optimization)
3. [Error Handling](#error-handling)
4. [Protocol Selection](#protocol-selection)
5. [Design Patterns](#design-patterns)
6. [Testing](#testing)

---

## Security Best Practices

### 1. Always Validate Inputs

**❌ Bad:**
```kotlin
val offer = registry.offerCredential("didcomm", request)
// No validation - may fail with cryptic error
```

**✅ Good:**
```kotlin
// Validate before operation
if (!isValidDid(request.issuerDid)) {
    throw IllegalArgumentException("Invalid issuer DID")
}
if (request.credentialPreview.attributes.isEmpty()) {
    throw IllegalArgumentException("Preview must have attributes")
}

val offer = registry.offerCredential("didcomm", request)
```

**Why:** Early validation provides better error messages and prevents security issues.

---

### 2. Use Secure Key Management

**❌ Bad:**
```kotlin
// Storing keys in plain text
val keyStore = InMemoryKeyStore()  // Keys in memory only
```

**✅ Good:**
```kotlin
// Use encrypted key storage
val keyStore = EncryptedFileLocalKeyStore(
    filePath = "/secure/keys",
    masterKey = secureMasterKey
)

// Or use cloud KMS
val kms = AwsKmsService(region = "us-east-1")
```

**Why:** Secure key management prevents key theft and unauthorized access.

---

### 3. Always Encrypt Messages

**❌ Bad:**
```kotlin
// Disabling encryption
options = mapOf(
    "fromKeyId" to "did:key:issuer#key-1",
    "toKeyId" to "did:key:holder#key-1",
    "encrypt" to false  // ⚠️ Security risk
)
```

**✅ Good:**
```kotlin
// Always encrypt (default)
options = mapOf(
    "fromKeyId" to "did:key:issuer#key-1",
    "toKeyId" to "did:key:holder#key-1"
    // encrypt defaults to true
)
```

**Why:** Encryption protects message confidentiality and integrity.

---

### 4. Verify Credentials Before Use

**❌ Bad:**
```kotlin
val issue = registry.issueCredential("didcomm", request)
// Use credential without verification
processCredential(issue.credential)
```

**✅ Good:**
```kotlin
val issue = registry.issueCredential("didcomm", request)

// Verify before use
val verification = trustLayer.verify {
    credential(issue.credential)
}

if (verification.valid) {
    processCredential(issue.credential)
} else {
    throw IllegalStateException("Credential invalid: ${verification.errors}")
}
```

**Why:** Verification ensures credential authenticity and validity.

---

### 5. Use Secure DID Resolution

**❌ Bad:**
```kotlin
// Mock resolver in production
val resolveDid: suspend (String) -> DidDocument? = { did ->
    DidDocument(id = did, verificationMethod = emptyList())
}
```

**✅ Good:**
```kotlin
// Use real DID resolver
val resolveDid: suspend (String) -> DidDocument? = { did ->
    yourDidResolver.resolve(did)
}
```

**Why:** Secure DID resolution ensures you're using correct keys and identities.

---

### 6. Implement Proper Error Handling

**❌ Bad:**
```kotlin
val offer = registry.offerCredential("didcomm", request)
// No error handling - may expose sensitive information
```

**✅ Good:**
```kotlin
try {
    val offer = registry.offerCredential("didcomm", request)
} catch (e: IllegalArgumentException) {
    logger.error("Invalid argument", e)
    // Don't expose sensitive information
    throw UserFriendlyException("Failed to create offer")
} catch (e: Exception) {
    logger.error("Unexpected error", e)
    throw UserFriendlyException("An error occurred")
}
```

**Why:** Proper error handling prevents information leakage and improves security.

---

## Performance Optimization

### 1. Reuse Registry Instances

**❌ Bad:**
```kotlin
// Creating new registry for each operation
fun createOffer(request: CredentialOfferRequest) {
    val registry = CredentialExchangeProtocolRegistry()
    registry.register(DidCommExchangeProtocol(didCommService))
    return registry.offerCredential("didcomm", request)
}
```

**✅ Good:**
```kotlin
// Reuse registry instance
class CredentialService {
    private val registry = CredentialExchangeProtocolRegistry()
    
    init {
        registry.register(DidCommExchangeProtocol(didCommService))
    }
    
    suspend fun createOffer(request: CredentialOfferRequest) {
        return registry.offerCredential("didcomm", request)
    }
}
```

**Why:** Reusing instances reduces overhead and improves performance.

---

### 2. Use Connection Pooling for Database Storage

**❌ Bad:**
```kotlin
// Creating new connection for each operation
val dataSource = DriverManagerDataSource(url, user, password)
```

**✅ Good:**
```kotlin
// Use connection pooling
val dataSource = HikariDataSource().apply {
    jdbcUrl = url
    username = user
    password = password
    maximumPoolSize = 10
    minimumIdle = 5
}
```

**Why:** Connection pooling improves database performance and resource usage.

---

### 3. Cache DID Documents

**❌ Bad:**
```kotlin
// Resolving DID every time
val resolveDid: suspend (String) -> DidDocument? = { did ->
    yourDidResolver.resolve(did)  // Network call every time
}
```

**✅ Good:**
```kotlin
// Cache DID documents
val didCache = mutableMapOf<String, DidDocument?>()

val resolveDid: suspend (String) -> DidDocument? = { did ->
    didCache.getOrPut(did) {
        yourDidResolver.resolve(did)
    }
}
```

**Why:** Caching reduces network calls and improves performance.

---

### 4. Use Async Operations

**❌ Bad:**
```kotlin
// Blocking operations
val offer = runBlocking {
    registry.offerCredential("didcomm", request)
}
```

**✅ Good:**
```kotlin
// Async operations
suspend fun createOffer(request: CredentialOfferRequest) {
    val offer = registry.offerCredential("didcomm", request)
    // Process asynchronously
}
```

**Why:** Async operations improve concurrency and responsiveness.

---

### 5. Batch Operations When Possible

**❌ Bad:**
```kotlin
// Processing one at a time
for (request in requests) {
    val offer = registry.offerCredential("didcomm", request)
    processOffer(offer)
}
```

**✅ Good:**
```kotlin
// Batch processing
val offers = requests.map { request ->
    async {
        registry.offerCredential("didcomm", request)
    }
}.awaitAll()

offers.forEach { processOffer(it) }
```

**Why:** Batch processing improves throughput and efficiency.

---

## Error Handling

### 1. Always Handle Errors

**❌ Bad:**
```kotlin
val offer = registry.offerCredential("didcomm", request)
// No error handling
```

**✅ Good:**
```kotlin
try {
    val offer = registry.offerCredential("didcomm", request)
} catch (e: IllegalArgumentException) {
    // Handle invalid argument
} catch (e: UnsupportedOperationException) {
    // Handle unsupported operation
} catch (e: Exception) {
    // Handle other errors
}
```

**Why:** Error handling prevents crashes and improves reliability.

---

### 2. Provide User-Friendly Error Messages

**❌ Bad:**
```kotlin
catch (e: Exception) {
    throw e  // Technical error message
}
```

**✅ Good:**
```kotlin
catch (e: IllegalArgumentException) {
    when {
        e.message?.contains("not registered") == true -> {
            throw UserFriendlyException("Protocol not available. Please contact support.")
        }
        e.message?.contains("Missing required option") == true -> {
            throw UserFriendlyException("Missing required configuration. Please check settings.")
        }
        else -> {
            throw UserFriendlyException("Invalid request. Please check your input.")
        }
    }
}
```

**Why:** User-friendly messages improve user experience.

---

### 3. Log Errors for Debugging

**❌ Bad:**
```kotlin
catch (e: Exception) {
    // No logging
    throw UserFriendlyException("An error occurred")
}
```

**✅ Good:**
```kotlin
catch (e: Exception) {
    logger.error("Failed to create offer", e)
    logger.debug("Request: $request")
    logger.debug("Protocol: didcomm")
    throw UserFriendlyException("An error occurred")
}
```

**Why:** Logging helps with debugging and troubleshooting.

---

## Protocol Selection

### 1. Choose Protocol Based on Requirements

**Decision Tree:**
```
Need peer-to-peer encryption?
├─ Yes → Use DIDComm
└─ No
    ├─ Web-based OAuth integration?
    │  ├─ Yes → Use OIDC4VCI
    │  └─ No
    │     └─ Browser-based wallet?
    │        ├─ Yes → Use CHAPI
    │        └─ No → Use DIDComm (default)
```

**Why:** Choosing the right protocol improves security, performance, and compatibility.

---

### 2. Support Multiple Protocols

**❌ Bad:**
```kotlin
// Only supporting one protocol
val offer = registry.offerCredential("didcomm", request)
```

**✅ Good:**
```kotlin
// Support multiple protocols with fallback
suspend fun offerWithFallback(
    request: CredentialOfferRequest
): CredentialOfferResponse {
    val protocols = listOf("didcomm", "oidc4vci", "chapi")
    
    for (protocol in protocols) {
        if (registry.isRegistered(protocol)) {
            try {
                return registry.offerCredential(protocol, request)
            } catch (e: Exception) {
                logger.warn("Protocol $protocol failed: ${e.message}")
                continue
            }
        }
    }
    
    throw IllegalStateException("All protocols failed")
}
```

**Why:** Multiple protocols provide redundancy and flexibility.

---

## Design Patterns

### 1. Use Factory Pattern for Service Creation

**❌ Bad:**
```kotlin
// Creating services directly
val didCommService = InMemoryDidCommService(packer, resolveDid)
```

**✅ Good:**
```kotlin
// Using factory
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
```

**Why:** Factory pattern provides consistent creation and configuration.

---

### 2. Use Registry Pattern for Protocol Management

**❌ Bad:**
```kotlin
// Managing protocols manually
val protocols = mutableMapOf<String, CredentialExchangeProtocol>()
protocols["didcomm"] = DidCommExchangeProtocol(didCommService)
```

**✅ Good:**
```kotlin
// Using registry
val registry = CredentialExchangeProtocolRegistry()
registry.register(DidCommExchangeProtocol(didCommService))
```

**Why:** Registry pattern provides centralized management and discovery.

---

### 3. Use Strategy Pattern for Protocol Selection

**❌ Bad:**
```kotlin
// Hard-coded protocol selection
val offer = registry.offerCredential("didcomm", request)
```

**✅ Good:**
```kotlin
// Strategy-based selection
class ProtocolSelector {
    fun selectProtocol(context: ExchangeContext): String {
        return when {
            context.needsEncryption -> "didcomm"
            context.isWebBased -> "oidc4vci"
            context.isBrowser -> "chapi"
            else -> "didcomm"
        }
    }
}

val protocol = protocolSelector.selectProtocol(context)
val offer = registry.offerCredential(protocol, request)
```

**Why:** Strategy pattern provides flexible protocol selection.

---

## Testing

### 1. Use In-Memory Implementations for Testing

**❌ Bad:**
```kotlin
// Using production services in tests
val kms = AwsKmsService(region = "us-east-1")
val didCommService = DidCommFactory.createDatabaseService(...)
```

**✅ Good:**
```kotlin
// Using test implementations
val kms = InMemoryKeyManagementService()
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
```

**Why:** In-memory implementations are faster and don't require external services.

---

### 2. Test Error Scenarios

**❌ Bad:**
```kotlin
// Only testing happy path
@Test
fun testOfferCredential() {
    val offer = registry.offerCredential("didcomm", request)
    assertNotNull(offer)
}
```

**✅ Good:**
```kotlin
// Testing error scenarios
@Test
fun testOfferCredential_ProtocolNotRegistered() {
    val registry = CredentialExchangeProtocolRegistry()
    assertThrows<IllegalArgumentException> {
        runBlocking {
            registry.offerCredential("didcomm", request)
        }
    }
}

@Test
fun testOfferCredential_InvalidDID() {
    val request = CredentialOfferRequest(
        issuerDid = "invalid-did",  // Invalid format
        holderDid = "did:key:holder",
        credentialPreview = preview
    )
    assertThrows<IllegalArgumentException> {
        runBlocking {
            registry.offerCredential("didcomm", request)
        }
    }
}
```

**Why:** Testing error scenarios improves reliability and robustness.

---

### 3. Test Protocol Switching

**❌ Bad:**
```kotlin
// Only testing one protocol
@Test
fun testOfferCredential() {
    val offer = registry.offerCredential("didcomm", request)
    assertNotNull(offer)
}
```

**✅ Good:**
```kotlin
// Testing multiple protocols
@Test
fun testOfferCredential_MultipleProtocols() {
    registry.register(DidCommExchangeProtocol(didCommService))
    registry.register(Oidc4VciExchangeProtocol(oidc4vciService))
    
    val didCommOffer = runBlocking {
        registry.offerCredential("didcomm", request)
    }
    assertNotNull(didCommOffer)
    
    val oidcOffer = runBlocking {
        registry.offerCredential("oidc4vci", request)
    }
    assertNotNull(oidcOffer)
}
```

**Why:** Testing multiple protocols ensures compatibility and flexibility.

---

## Related Documentation

- **[Quick Start](./QUICK_START.md)** - Get started quickly
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Error Handling](./ERROR_HANDLING.md)** - Error handling guide
- **[Workflows](./WORKFLOWS.md)** - Step-by-step workflows
- **[Security](../../security/README.md)** - Security guidelines

