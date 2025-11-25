# Credential Exchange Protocols - Error Handling

Complete guide to error handling for credential exchange protocols.

## Overview

All credential exchange operations can throw exceptions. This guide documents all possible errors and how to handle them.

## Error Types

### Registry Errors

#### IllegalArgumentException: Protocol Not Registered

**When it occurs:**
- Calling any registry method with an unregistered protocol name

**Error message:**
```
Protocol 'didcomm' not registered. Available: []
```

**Code example:**
```kotlin
try {
    val offer = registry.offerCredential("didcomm", request)
} catch (e: IllegalArgumentException) {
    println("Error: ${e.message}")
    // Output: Protocol 'didcomm' not registered. Available: []
}
```

**Solutions:**

1. **Register the protocol before use:**
   ```kotlin
   val registry = CredentialExchangeProtocolRegistry()
   val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
   registry.register(DidCommExchangeProtocol(didCommService))
   
   // Now safe to use
   val offer = registry.offerCredential("didcomm", request)
   ```

2. **Check available protocols:**
   ```kotlin
   val available = registry.getAllProtocolNames()
   if (!available.contains("didcomm")) {
       println("Protocol not available. Available: $available")
       // Register protocol or use different protocol
   }
   ```

3. **Use isRegistered() to check:**
   ```kotlin
   if (!registry.isRegistered("didcomm")) {
       // Register protocol
       registry.register(DidCommExchangeProtocol(didCommService))
   }
   ```

**Prevention:**
- Always register protocols before use
- Check `registry.isRegistered()` before calling methods
- Use `registry.getAllProtocolNames()` to see available protocols

---

#### UnsupportedOperationException: Operation Not Supported

**When it occurs:**
- Protocol doesn't support the requested operation

**Error message:**
```
Protocol 'oidc4vci' does not support REQUEST_PROOF operation
```

**Code example:**
```kotlin
try {
    val proofRequest = registry.requestProof("oidc4vci", request)
} catch (e: UnsupportedOperationException) {
    println("Error: ${e.message}")
    // Output: Protocol 'oidc4vci' does not support REQUEST_PROOF operation
}
```

**Solutions:**

1. **Check supported operations:**
   ```kotlin
   val protocol = registry.get("oidc4vci")
   if (protocol != null) {
       println("Supported operations: ${protocol.supportedOperations}")
       // Output: Supported operations: [OFFER_CREDENTIAL, REQUEST_CREDENTIAL, ISSUE_CREDENTIAL]
   }
   ```

2. **Use a different protocol:**
   ```kotlin
   // OIDC4VCI doesn't support proof requests, use DIDComm instead
   val proofRequest = registry.requestProof("didcomm", request)
   ```

3. **Use a different operation:**
   ```kotlin
   // If you need proof functionality, use DIDComm or CHAPI
   if (registry.isRegistered("didcomm")) {
       val proofRequest = registry.requestProof("didcomm", request)
   } else {
       println("No protocol available for proof requests")
   }
   ```

**Prevention:**
- Check `protocol.supportedOperations` before calling methods
- Use protocol comparison table to choose the right protocol
- Handle `UnsupportedOperationException` gracefully

---

### Protocol-Specific Errors

#### DIDComm Errors

##### Missing Required Options

**When it occurs:**
- Missing `fromKeyId` or `toKeyId` in options

**Error message:**
```
Missing required option: fromKeyId
```

**Code example:**
```kotlin
try {
    val offer = registry.offerCredential(
        protocolName = "didcomm",
        request = CredentialOfferRequest(
            issuerDid = "did:key:issuer",
            holderDid = "did:key:holder",
            credentialPreview = preview,
            options = mapOf(
                // Missing fromKeyId and toKeyId
            )
        )
    )
} catch (e: IllegalArgumentException) {
    println("Error: ${e.message}")
}
```

**Solution:**
```kotlin
val offer = registry.offerCredential(
    protocolName = "didcomm",
    request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = preview,
        options = mapOf(
            "fromKeyId" to "did:key:issuer#key-1",  // Required
            "toKeyId" to "did:key:holder#key-1"     // Required
        )
    )
)
```

---

##### Invalid Key ID Format

**When it occurs:**
- Key ID doesn't match expected format

**Error message:**
```
Invalid key ID format: <key-id>
```

**Solution:**
```kotlin
// Key ID must be in format: did:key:...#key-1
val keyId = "$issuerDid#key-1"  // Correct format
```

---

##### DID Resolution Failure

**When it occurs:**
- Cannot resolve DID to DID document
- DID document doesn't contain required keys

**Error message:**
```
Failed to resolve DID: did:key:issuer
```

**Solution:**
```kotlin
// Ensure DID resolver is properly configured
val resolveDid: suspend (String) -> DidDocument? = { did ->
    // Implement real DID resolution
    yourDidResolver.resolve(did)
}

// Ensure DID document contains required keys
val document = resolveDid("did:key:issuer")
if (document?.verificationMethod?.isEmpty() == true) {
    println("DID document has no verification methods")
}
```

---

##### Key Not Found in KMS

**When it occurs:**
- Key ID references a key that doesn't exist in KMS

**Error message:**
```
Key not found: did:key:issuer#key-1
```

**Solution:**
```kotlin
// Ensure key exists in KMS before use
val keyId = "did:key:issuer#key-1"
if (!kms.keyExists(keyId)) {
    // Create key or use existing key
    kms.createKey(keyId, algorithm = "Ed25519")
}
```

---

#### OIDC4VCI Errors

##### Missing Credential Issuer URL

**When it occurs:**
- Missing `credentialIssuer` in options

**Error message:**
```
Missing required option: credentialIssuer
```

**Solution:**
```kotlin
val offer = registry.offerCredential(
    protocolName = "oidc4vci",
    request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = preview,
        options = mapOf(
            "credentialIssuer" to "https://issuer.example.com"  // Required
        )
    )
)
```

---

##### HTTP Request Failure

**When it occurs:**
- Network error connecting to credential issuer
- Credential issuer returns error response

**Error message:**
```
HTTP request failed: 404 Not Found
```

**Solution:**
```kotlin
try {
    val offer = registry.offerCredential("oidc4vci", request)
} catch (e: Exception) {
    when {
        e.message?.contains("404") == true -> {
            println("Credential issuer not found. Check URL.")
        }
        e.message?.contains("401") == true -> {
            println("Authentication failed. Check credentials.")
        }
        e.message?.contains("timeout") == true -> {
            println("Request timed out. Retry later.")
        }
        else -> {
            println("HTTP error: ${e.message}")
        }
    }
}
```

---

##### Token Exchange Failure

**When it occurs:**
- Authorization code is invalid
- Token exchange endpoint returns error

**Error message:**
```
Token exchange failed: invalid_grant
```

**Solution:**
```kotlin
// Ensure authorization code is valid and not expired
val options = mapOf(
    "credentialIssuer" to "https://issuer.example.com",
    "authorizationCode" to validAuthorizationCode  // Must be valid and not expired
)
```

---

##### Invalid Credential Issuer Metadata

**When it occurs:**
- Credential issuer metadata is invalid
- Required endpoints are missing

**Error message:**
```
Invalid credential issuer metadata: missing token_endpoint
```

**Solution:**
```kotlin
// Verify credential issuer metadata is valid
// Check that all required endpoints are present
```

---

#### CHAPI Errors

##### Browser Not Available

**When it occurs:**
- CHAPI requires browser environment
- Running in non-browser context

**Error message:**
```
CHAPI requires browser environment
```

**Solution:**
```kotlin
// CHAPI only works in browser
// Use different protocol for server-side operations
if (isBrowserEnvironment()) {
    val offer = registry.offerCredential("chapi", request)
} else {
    // Use DIDComm or OIDC4VCI for server-side
    val offer = registry.offerCredential("didcomm", request)
}
```

---

### Validation Errors

#### Invalid DID Format

**When it occurs:**
- DID doesn't match format: `did:<method>:<identifier>`

**Error message:**
```
Invalid DID format: invalid-did
```

**Solution:**
```kotlin
// Validate DID format before use
fun isValidDid(did: String): Boolean {
    return did.matches(Regex("^did:[a-z0-9]+:.+$"))
}

val issuerDid = "did:key:issuer"
if (!isValidDid(issuerDid)) {
    println("Invalid DID format")
    return
}
```

---

#### Empty Credential Preview

**When it occurs:**
- `credentialPreview.attributes` is empty

**Error message:**
```
Credential preview attributes must not be empty
```

**Solution:**
```kotlin
val preview = CredentialPreview(
    attributes = listOf(
        CredentialAttribute("name", "Alice")  // Must have at least one attribute
    )
)
```

---

#### Invalid Credential

**When it occurs:**
- Credential is missing required fields
- Credential structure is invalid

**Error message:**
```
Invalid credential: missing issuer
```

**Solution:**
```kotlin
// Ensure credential has all required fields
val credential = VerifiableCredential(
    type = listOf("VerifiableCredential"),  // Required
    issuer = "did:key:issuer",              // Required
    credentialSubject = buildJsonObject { }, // Required
    issuanceDate = Instant.now().toString() // Required
)
```

---

## Error Handling Patterns

### Pattern 1: Check Before Use

```kotlin
// Check protocol is registered
if (!registry.isRegistered("didcomm")) {
    println("Protocol not registered")
    return
}

// Check operation is supported
val protocol = registry.get("didcomm")
if (protocol?.supportedOperations?.contains(ExchangeOperation.OFFER_CREDENTIAL) != true) {
    println("Operation not supported")
    return
}

// Now safe to use
val offer = registry.offerCredential("didcomm", request)
```

---

### Pattern 2: Try-Catch with Specific Handling

```kotlin
try {
    val offer = registry.offerCredential("didcomm", request)
    println("Offer created: ${offer.offerId}")
} catch (e: IllegalArgumentException) {
    when {
        e.message?.contains("not registered") == true -> {
            println("Protocol not registered. Register it first.")
            registry.register(DidCommExchangeProtocol(didCommService))
            // Retry
            val offer = registry.offerCredential("didcomm", request)
        }
        e.message?.contains("Missing required option") == true -> {
            println("Missing required option. Check options map.")
        }
        else -> {
            println("Invalid argument: ${e.message}")
        }
    }
} catch (e: UnsupportedOperationException) {
    println("Operation not supported. Use different protocol or operation.")
} catch (e: Exception) {
    println("Unexpected error: ${e.message}")
    e.printStackTrace()
}
```

---

### Pattern 3: Fallback to Alternative Protocol

```kotlin
suspend fun offerCredentialWithFallback(
    preferredProtocol: String,
    fallbackProtocols: List<String>,
    request: CredentialOfferRequest
): CredentialOfferResponse? {
    // Try preferred protocol first
    if (registry.isRegistered(preferredProtocol)) {
        try {
            return registry.offerCredential(preferredProtocol, request)
        } catch (e: Exception) {
            println("Preferred protocol failed: ${e.message}")
        }
    }
    
    // Try fallback protocols
    for (protocol in fallbackProtocols) {
        if (registry.isRegistered(protocol)) {
            try {
                return registry.offerCredential(protocol, request)
            } catch (e: Exception) {
                println("Fallback protocol $protocol failed: ${e.message}")
            }
        }
    }
    
    return null
}

// Usage
val offer = offerCredentialWithFallback(
    preferredProtocol = "didcomm",
    fallbackProtocols = listOf("oidc4vci", "chapi"),
    request = request
) ?: throw IllegalStateException("All protocols failed")
```

---

### Pattern 4: Validate Before Operation

```kotlin
fun validateOfferRequest(request: CredentialOfferRequest): ValidationResult {
    val errors = mutableListOf<String>()
    
    // Validate DIDs
    if (!isValidDid(request.issuerDid)) {
        errors.add("Invalid issuer DID format")
    }
    if (!isValidDid(request.holderDid)) {
        errors.add("Invalid holder DID format")
    }
    
    // Validate preview
    if (request.credentialPreview.attributes.isEmpty()) {
        errors.add("Credential preview attributes must not be empty")
    }
    
    // Validate protocol-specific options
    // (Implementation depends on protocol)
    
    return if (errors.isEmpty()) {
        ValidationResult.Valid
    } else {
        ValidationResult.Invalid(errors)
    }
}

// Use before operation
val validation = validateOfferRequest(request)
if (validation is ValidationResult.Invalid) {
    println("Validation failed: ${validation.errors}")
    return
}

val offer = registry.offerCredential("didcomm", request)
```

---

## Common Error Scenarios

### Scenario 1: Protocol Not Registered

**Problem:**
```kotlin
val registry = CredentialExchangeProtocolRegistry()
val offer = registry.offerCredential("didcomm", request)  // Throws IllegalArgumentException
```

**Solution:**
```kotlin
val registry = CredentialExchangeProtocolRegistry()
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
registry.register(DidCommExchangeProtocol(didCommService))
val offer = registry.offerCredential("didcomm", request)  // Now works
```

---

### Scenario 2: Missing Required Options

**Problem:**
```kotlin
val offer = registry.offerCredential(
    protocolName = "didcomm",
    request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = preview,
        options = emptyMap()  // Missing fromKeyId and toKeyId
    )
)  // Throws IllegalArgumentException
```

**Solution:**
```kotlin
val offer = registry.offerCredential(
    protocolName = "didcomm",
    request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = preview,
        options = mapOf(
            "fromKeyId" to "did:key:issuer#key-1",  // Added
            "toKeyId" to "did:key:holder#key-1"     // Added
        )
    )
)  // Now works
```

---

### Scenario 3: Operation Not Supported

**Problem:**
```kotlin
val proofRequest = registry.requestProof("oidc4vci", request)  // Throws UnsupportedOperationException
```

**Solution:**
```kotlin
// Check supported operations first
val protocol = registry.get("oidc4vci")
if (protocol?.supportedOperations?.contains(ExchangeOperation.REQUEST_PROOF) == true) {
    val proofRequest = registry.requestProof("oidc4vci", request)
} else {
    // Use different protocol
    val proofRequest = registry.requestProof("didcomm", request)
}
```

---

## Error Recovery Strategies

### Strategy 1: Retry with Different Protocol

```kotlin
suspend fun offerCredentialWithRetry(
    protocols: List<String>,
    request: CredentialOfferRequest
): CredentialOfferResponse? {
    for (protocol in protocols) {
        try {
            if (registry.isRegistered(protocol)) {
                return registry.offerCredential(protocol, request)
            }
        } catch (e: Exception) {
            println("Protocol $protocol failed: ${e.message}")
            continue
        }
    }
    return null
}
```

---

### Strategy 2: Register Missing Protocol

```kotlin
suspend fun offerCredentialWithAutoRegister(
    protocolName: String,
    request: CredentialOfferRequest
): CredentialOfferResponse {
    if (!registry.isRegistered(protocolName)) {
        // Auto-register protocol
        when (protocolName) {
            "didcomm" -> {
                val service = DidCommFactory.createInMemoryService(kms, resolveDid)
                registry.register(DidCommExchangeProtocol(service))
            }
            "oidc4vci" -> {
                val service = Oidc4VciService(credentialIssuerUrl, kms)
                registry.register(Oidc4VciExchangeProtocol(service))
            }
            // ... other protocols
        }
    }
    
    return registry.offerCredential(protocolName, request)
}
```

---

## Best Practices

1. **Always check protocol registration:**
   ```kotlin
   if (!registry.isRegistered("didcomm")) {
       // Register or use different protocol
   }
   ```

2. **Check supported operations:**
   ```kotlin
   val protocol = registry.get("oidc4vci")
   if (protocol?.supportedOperations?.contains(ExchangeOperation.OFFER_CREDENTIAL) != true) {
       // Use different protocol
   }
   ```

3. **Validate inputs before operations:**
   ```kotlin
   if (!isValidDid(issuerDid)) {
       return // Handle error
   }
   ```

4. **Use try-catch for all operations:**
   ```kotlin
   try {
       val offer = registry.offerCredential("didcomm", request)
   } catch (e: Exception) {
       // Handle error appropriately
   }
   ```

5. **Provide helpful error messages:**
   ```kotlin
   catch (e: IllegalArgumentException) {
       logger.error("Invalid argument: ${e.message}")
       // Provide user-friendly message
   }
   ```

---

## Related Documentation

- **[Quick Start](./QUICK_START.md)** - Get started quickly (5 minutes)
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Troubleshooting](./TROUBLESHOOTING.md)** - Common issues and solutions
- **[Workflows](./WORKFLOWS.md)** - Step-by-step workflows
- **[Examples](./EXAMPLES.md)** - Code examples with error handling
- **[Best Practices](./BEST_PRACTICES.md)** - Error handling best practices
- **[Glossary](./GLOSSARY.md)** - Terms and concepts

