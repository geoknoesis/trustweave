---
title: Credential Exchange Protocols - Troubleshooting
---

# Credential Exchange Protocols - Troubleshooting

Common issues and solutions when working with credential exchange protocols.

## Common Issues

### Issue 1: Protocol Not Registered

**Error:**
```
ExchangeException.ProtocolNotRegistered: Protocol 'didcomm' not registered. Available: []
```

**Error code:** `PROTOCOL_NOT_REGISTERED`

**Symptoms:**
- Calling `offerCredential()` throws `ExchangeException.ProtocolNotRegistered`
- `registry.getAllProtocolNames()` returns empty list

**Solutions:**

1. **Register the protocol:**
   ```kotlin
   val registry = CredentialExchangeProtocolRegistry()
   val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
   registry.register(DidCommExchangeProtocol(didCommService))
   ```

2. **Check if protocol is registered:**
   ```kotlin
   if (!registry.isRegistered("didcomm")) {
       println("Protocol not registered. Registering...")
       registry.register(DidCommExchangeProtocol(didCommService))
   }
   ```

3. **Verify protocol name:**
   ```kotlin
   val available = registry.getAllProtocolNames()
   println("Available protocols: $available")
   // Make sure you're using the correct protocol name
   ```

**Prevention:**
- Always register protocols before use
- Check `registry.isRegistered()` before calling methods
- Use consistent protocol names

---

### Issue 2: Missing Required Options

**Error:**
```
ExchangeException.MissingRequiredOption: Missing required option 'fromKeyId' for protocol 'didcomm'
```

**Error code:** `MISSING_REQUIRED_OPTION`

**Symptoms:**
- DIDComm operations fail with missing option error
- Options map is empty or incomplete

**Solutions:**

1. **Add required options for DIDComm:**
   ```kotlin
   options = mapOf(
       "fromKeyId" to "did:key:issuer#key-1",  // Required
       "toKeyId" to "did:key:holder#key-1"     // Required
   )
   ```

2. **Add required options for OIDC4VCI:**
   ```kotlin
   options = mapOf(
       "credentialIssuer" to "https://issuer.example.com"  // Required
   )
   ```

3. **Check protocol-specific requirements:**
   - See [API Reference](./API_REFERENCE.md) for required options
   - See [Protocol-Specific Options](./API_REFERENCE.md#protocol-specific-options)

**Prevention:**
- Always check protocol documentation for required options
- Validate options before calling methods
- Use helper functions to build options

---

### Issue 3: Operation Not Supported

**Error:**
```
ExchangeException.OperationNotSupported: Protocol 'oidc4vci' does not support operation 'REQUEST_PROOF'. Supported: [OFFER_CREDENTIAL, REQUEST_CREDENTIAL, ISSUE_CREDENTIAL]
```

**Error code:** `OPERATION_NOT_SUPPORTED`

**Symptoms:**
- Calling `requestProof()` throws `ExchangeException.OperationNotSupported`
- Protocol doesn't support the requested operation

**Solutions:**

1. **Check supported operations:**
   ```kotlin
   val protocol = registry.get("oidc4vci")
   if (protocol != null) {
       println("Supported operations: ${protocol.supportedOperations}")
       // Output: [OFFER_CREDENTIAL, REQUEST_CREDENTIAL, ISSUE_CREDENTIAL]
   }
   ```

2. **Use a different protocol:**
   ```kotlin
   // OIDC4VCI doesn't support proof requests, use DIDComm
   val proofRequest = registry.requestProof("didcomm", request)
   ```

3. **Use a different operation:**
   ```kotlin
   // If you need proof functionality, use DIDComm or CHAPI
   if (registry.isRegistered("didcomm")) {
       val proofRequest = registry.requestProof("didcomm", request)
   }
   ```

**Prevention:**
- Check `protocol.supportedOperations` before calling methods
- Use protocol comparison table to choose the right protocol
- Handle `ExchangeException.OperationNotSupported` gracefully

---

### Issue 4: Invalid DID Format

**Error:**
```
IllegalArgumentException: Invalid DID format: invalid-did
```

**Symptoms:**
- DID validation fails
- Operations fail with format error

**Solutions:**

1. **Validate DID format:**
   ```kotlin
   fun isValidDid(did: String): Boolean {
       return did.matches(Regex("^did:[a-z0-9]+:.+$"))
   }

   val issuerDid = "did:key:issuer"
   if (!isValidDid(issuerDid)) {
       println("Invalid DID format")
       return
   }
   ```

2. **Check DID format requirements:**
   - Format: `did:<method>:<identifier>`
   - Method must be lowercase alphanumeric
   - Identifier must not be empty

**Prevention:**
- Always validate DID format before use
- Use DID creation methods instead of manual construction
- Check for typos in DID strings

---

### Issue 5: Key Not Found in KMS

**Error:**
```
IllegalStateException: Key not found: did:key:issuer#key-1
```

**Symptoms:**
- DIDComm operations fail with key not found
- KMS doesn't contain the referenced key

**Solutions:**

1. **Ensure key exists in KMS:**
   ```kotlin
   val keyId = "did:key:issuer#key-1"
   if (!kms.keyExists(keyId)) {
       // Create key or use existing key
       kms.createKey(keyId, algorithm = "Ed25519")
   }
   ```

2. **Use correct key ID:**
   ```kotlin
   // Get key ID from DID document
   val document = resolveDid("did:key:issuer")
   val keyId = document?.verificationMethod?.firstOrNull()?.id
       ?: throw IllegalStateException("No verification method found")
   ```

**Prevention:**
- Always create keys before use
- Use key IDs from DID documents
- Verify key exists in KMS before operations

---

### Issue 6: DID Resolution Failure

**Error:**
```
IllegalStateException: Failed to resolve DID: did:key:issuer
```

**Symptoms:**
- DID resolution returns null
- Operations fail with resolution error

**Solutions:**

1. **Implement proper DID resolution:**
   ```kotlin
   val resolveDid: suspend (String) -> DidDocument? = { did ->
       // Implement real DID resolution
       yourDidResolver.resolve(did)
   }
   ```

2. **Check DID resolver configuration:**
   ```kotlin
   // Ensure DID resolver is properly configured
   val document = resolveDid("did:key:issuer")
   if (document == null) {
       println("DID not resolvable")
       return
   }
   ```

**Prevention:**
- Use proper DID resolver implementation
- Test DID resolution before operations
- Handle resolution failures gracefully

---

### Issue 7: HTTP Request Failure (OIDC4VCI)

**Error:**
```
Exception: HTTP request failed: 404 Not Found
```

**Symptoms:**
- OIDC4VCI operations fail with HTTP errors
- Credential issuer endpoint not reachable

**Solutions:**

1. **Check credential issuer URL:**
   ```kotlin
   val credentialIssuer = "https://issuer.example.com"
   // Verify URL is correct and reachable
   ```

2. **Handle HTTP errors:**
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
       }
   }
   ```

**Prevention:**
- Verify credential issuer URL is correct
- Test connectivity before operations
- Handle HTTP errors gracefully

---

## Debugging Tips

### Tip 1: Enable Logging

```kotlin
// Enable debug logging
val registry = CredentialExchangeProtocolRegistry()
// Add logging interceptor or enable debug mode
```

### Tip 2: Check Protocol State

```kotlin
// Check what protocols are registered
val protocols = registry.getAll()
protocols.forEach { (name, protocol) ->
    println("Protocol: $name")
    println("  Supported operations: ${protocol.supportedOperations}")
}
```

### Tip 3: Validate Inputs

```kotlin
// Validate all inputs before operations
fun validateOfferRequest(request: CredentialOfferRequest): Boolean {
    return isValidDid(request.issuerDid) &&
           isValidDid(request.holderDid) &&
           request.credentialPreview.attributes.isNotEmpty()
}
```

### Tip 4: Use Try-Catch

```kotlin
// Always use try-catch for error handling
try {
    val offer = registry.offerCredential("didcomm", request)
} catch (e: IllegalArgumentException) {
    println("Invalid argument: ${e.message}")
} catch (e: UnsupportedOperationException) {
    println("Unsupported operation: ${e.message}")
} catch (e: Exception) {
    println("Unexpected error: ${e.message}")
    e.printStackTrace()
}
```

---

## Getting Help

If you encounter issues not covered here:

1. **Check Documentation:**
   - [Quick Start](./QUICK_START.md)
   - [API Reference](./API_REFERENCE.md)
   - [Error Handling](./ERROR_HANDLING.md)

2. **Check Examples:**
   - [Complete Examples](./EXAMPLES.md)
   - [Workflows](./WORKFLOWS.md)

3. **File an Issue:**
   - Include error message
   - Include code snippet
   - Include protocol and options used

---

## Related Documentation

- **[Quick Start](./QUICK_START.md)** - Get started quickly (5 minutes)
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Error Handling](./ERROR_HANDLING.md)** - Error handling guide
- **[Workflows](./WORKFLOWS.md)** - Step-by-step workflows
- **[Examples](./EXAMPLES.md)** - Code examples
- **[Best Practices](./BEST_PRACTICES.md)** - Best practices and patterns
- **[Glossary](./GLOSSARY.md)** - Terms and concepts

