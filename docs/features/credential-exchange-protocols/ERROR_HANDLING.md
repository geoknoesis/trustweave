---
title: Credential Exchange Protocols - Error Handling
---

# Credential Exchange Protocols - Error Handling

Complete guide to error handling for credential exchange protocols.

## Overview

All credential exchange operations throw structured exceptions from the `ExchangeException` hierarchy. These exceptions extend `TrustWeaveException` and provide:

- **Structured error codes** for programmatic handling
- **Rich context** with relevant information
- **Type-safe error handling** with sealed classes
- **Consistent error format** across all TrustWeave modules

## Exception Hierarchy

All exchange-related exceptions extend `ExchangeException`, which extends `TrustWeaveException`. Plugin-specific exceptions are located in their respective plugin modules:

```kotlin
import com.trustweave.credential.exchange.exception.ExchangeException
import com.trustweave.credential.didcomm.exception.DidCommException
import com.trustweave.credential.oidc4vci.exception.Oidc4VciException
import com.trustweave.credential.chapi.exception.ChapiException

try {
    val offer = registry.offerCredential("didcomm", request)
} catch (e: ExchangeException) {
    when (e) {
        is ExchangeException.ProtocolNotRegistered -> {
            println("Protocol: ${e.protocolName}")
            println("Available: ${e.availableProtocols}")
        }
        is ExchangeException.OperationNotSupported -> {
            println("Operation: ${e.operation}")
            println("Supported: ${e.supportedOperations}")
        }
        // Plugin-specific exceptions also extend ExchangeException
        is DidCommException.EncryptionFailed -> {
            println("DIDComm encryption failed: ${e.reason}")
        }
        is Oidc4VciException.HttpRequestFailed -> {
            println("OIDC4VCI HTTP request failed: ${e.statusCode}")
        }
        is ChapiException.BrowserNotAvailable -> {
            println("CHAPI requires browser: ${e.reason}")
        }
        // ... handle other exceptions
    }
}
```

### Exception Module Structure

- **Core exceptions** (`ExchangeException`): Located in `credentials/credential-core`
  - Registry errors
  - Request validation errors
  - Resource not found errors
  - Generic/unknown errors

- **Plugin-specific exceptions**: Located in their respective plugin modules
  - `DidCommException`: `credentials/plugins/didcomm`
  - `Oidc4VciException`: `credentials/plugins/oidc4vci`
  - `ChapiException`: `credentials/plugins/chapi`

All plugin exceptions extend `ExchangeException`, ensuring consistent error handling across all protocols.

## Error Types

### Registry Errors

#### ExchangeException.ProtocolNotRegistered

**When it occurs:**
- Calling any registry method with an unregistered protocol name

**Error code:** `PROTOCOL_NOT_REGISTERED`

**Properties:**
- `protocolName: String` - The requested protocol name
- `availableProtocols: List<String>` - List of available protocol names

**Code example:**
```kotlin
try {
    val offer = registry.offerCredential("didcomm", request)
} catch (e: ExchangeException.ProtocolNotRegistered) {
    println("Error code: ${e.code}")
    println("Protocol: ${e.protocolName}")
    println("Available: ${e.availableProtocols}")
    // Output:
    // Error code: PROTOCOL_NOT_REGISTERED
    // Protocol: didcomm
    // Available: []
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

### Request Validation Errors

#### ExchangeException.MissingRequiredOption

**When it occurs:**
- A required option is missing from the request

**Error code:** `MISSING_REQUIRED_OPTION`

**Properties:**
- `optionName: String` - The name of the missing option
- `protocolName: String?` - The protocol name (if applicable)

**Code example:**
```kotlin
try {
    val request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = preview,
        options = emptyMap() // Missing 'fromKeyId' and 'toKeyId'
    )
    val offer = registry.offerCredential("didcomm", request)
} catch (e: ExchangeException.MissingRequiredOption) {
    println("Error code: ${e.code}")
    println("Missing option: ${e.optionName}")
    println("Protocol: ${e.protocolName}")
    // Output:
    // Error code: MISSING_REQUIRED_OPTION
    // Missing option: fromKeyId
    // Protocol: didcomm
}
```

**Solutions:**
1. **Add the missing option:**
   ```kotlin
   val request = CredentialOfferRequest(
       issuerDid = "did:key:issuer",
       holderDid = "did:key:holder",
       credentialPreview = preview,
       options = mapOf(
           "fromKeyId" to "did:key:issuer#key-1",
           "toKeyId" to "did:key:holder#key-1"
       )
   )
   ```

2. **Check protocol requirements:**
   - DIDComm requires: `fromKeyId`, `toKeyId`
   - OIDC4VCI requires: `credentialIssuer`

---

#### ExchangeException.OfferNotFound

**When it occurs:**
- Requesting a credential using a non-existent offer ID

**Error code:** `OFFER_NOT_FOUND`

**Properties:**
- `offerId: String` - The offer ID that was not found

**Code example:**
```kotlin
try {
    val request = CredentialRequestRequest(
        holderDid = "did:key:holder",
        offerId = "non-existent-offer-id"
    )
    val response = registry.requestCredential("didcomm", request)
} catch (e: ExchangeException.OfferNotFound) {
    println("Error code: ${e.code}")
    println("Offer ID: ${e.offerId}")
    // Output:
    // Error code: OFFER_NOT_FOUND
    // Offer ID: non-existent-offer-id
}
```

**Solutions:**
1. **Use a valid offer ID:**
   ```kotlin
   // First create an offer
   val offer = registry.offerCredential("didcomm", offerRequest)

   // Then use the offer ID
   val request = CredentialRequestRequest(
       holderDid = "did:key:holder",
       offerId = offer.offerId // Use the actual offer ID
   )
   ```

2. **Store offer IDs:**
   - Store offer IDs when creating offers
   - Use a database or cache to track offers

---

#### ExchangeException.RequestNotFound

**When it occurs:**
- Issuing a credential using a non-existent request ID

**Error code:** `REQUEST_NOT_FOUND`

**Properties:**
- `requestId: String` - The request ID that was not found

**Code example:**
```kotlin
try {
    val request = CredentialIssueRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credential = credential,
        requestId = "non-existent-request-id"
    )
    val response = registry.issueCredential("didcomm", request)
} catch (e: ExchangeException.RequestNotFound) {
    println("Error code: ${e.code}")
    println("Request ID: ${e.requestId}")
}
```

---

### DIDComm-Specific Errors

DIDComm-specific exceptions are located in the `didcomm` plugin module and extend `ExchangeException`:

```kotlin
import com.trustweave.credential.didcomm.exception.DidCommException
```

#### DidCommException.EncryptionFailed

**When it occurs:**
- DIDComm message encryption fails

**Error code:** `DIDCOMM_ENCRYPTION_FAILED`

**Properties:**
- `reason: String` - The reason encryption failed
- `fromDid: String?` - The sender DID (if available)
- `toDid: String?` - The recipient DID (if available)
- `cause: Throwable?` - The underlying exception

**Code example:**
```kotlin
import com.trustweave.credential.didcomm.exception.DidCommException

try {
    val offer = registry.offerCredential("didcomm", request)
} catch (e: DidCommException.EncryptionFailed) {
    println("Error code: ${e.code}")
    println("Reason: ${e.reason}")
    println("From: ${e.fromDid}")
    println("To: ${e.toDid}")
    println("Cause: ${e.cause?.message}")
}
```

**Common causes:**
- Missing or invalid keys
- Key resolution failure
- Cryptographic operation failure

**Solutions:**
1. **Verify keys exist:**
   ```kotlin
   val fromKey = kms.getPublicKey(fromKeyId)
   val toKey = kms.getPublicKey(toKeyId)
   ```

2. **Check DID resolution:**
   ```kotlin
   val fromDoc = resolveDid(fromDid)
   val toDoc = resolveDid(toDid)
   ```

---

#### DidCommException.DecryptionFailed

**When it occurs:**
- DIDComm message decryption fails

**Error code:** `DIDCOMM_DECRYPTION_FAILED`

**Properties:**
- `reason: String` - The reason decryption failed
- `messageId: String?` - The message ID (if available)
- `cause: Throwable?` - The underlying exception

**Code example:**
```kotlin
import com.trustweave.credential.didcomm.exception.DidCommException

try {
    val message = didCommService.unpack(encryptedMessage)
} catch (e: DidCommException.DecryptionFailed) {
    println("Error code: ${e.code}")
    println("Reason: ${e.reason}")
    println("Message ID: ${e.messageId}")
}
```

---

#### DidCommException.PackingFailed

**When it occurs:**
- DIDComm message packing fails

**Error code:** `DIDCOMM_PACKING_FAILED`

**Properties:**
- `reason: String` - The reason packing failed
- `messageId: String?` - The message ID (if available)
- `cause: Throwable?` - The underlying exception

---

#### DidCommException.UnpackingFailed

**When it occurs:**
- DIDComm message unpacking fails

**Error code:** `DIDCOMM_UNPACKING_FAILED`

**Properties:**
- `reason: String` - The reason unpacking failed
- `messageId: String?` - The message ID (if available)
- `cause: Throwable?` - The underlying exception

---

#### DidCommException.ProtocolError

**When it occurs:**
- DIDComm protocol error occurs

**Error code:** `DIDCOMM_PROTOCOL_ERROR`

**Properties:**
- `reason: String` - The reason for the error
- `field: String?` - The field that caused the error (if applicable)
- `cause: Throwable?` - The underlying exception

---

### OIDC4VCI-Specific Errors

OIDC4VCI-specific exceptions are located in the `oidc4vci` plugin module and extend `ExchangeException`:

```kotlin
import com.trustweave.credential.oidc4vci.exception.Oidc4VciException
```

#### Oidc4VciException.HttpRequestFailed

**When it occurs:**
- OIDC4VCI HTTP request fails

**Error code:** `OIDC4VCI_HTTP_REQUEST_FAILED`

**Properties:**
- `url: String` - The URL that was requested
- `statusCode: Int?` - The HTTP status code (if available)
- `reason: String` - The reason the request failed
- `cause: Throwable?` - The underlying exception

**Code example:**
```kotlin
import com.trustweave.credential.oidc4vci.exception.Oidc4VciException

try {
    val offer = registry.offerCredential("oidc4vci", request)
} catch (e: Oidc4VciException.HttpRequestFailed) {
    println("Error code: ${e.code}")
    println("URL: ${e.url}")
    println("Status: ${e.statusCode}")
    println("Reason: ${e.reason}")
}
```

**Common causes:**
- Network connectivity issues
- Invalid credential issuer URL
- Server errors (5xx)
- Client errors (4xx)

**Solutions:**
1. **Check network connectivity:**
   ```kotlin
   // Verify URL is reachable
   val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
   ```

2. **Verify credential issuer URL:**
   ```kotlin
   val metadata = oidc4vciService.fetchCredentialIssuerMetadata(credentialIssuerUrl)
   ```

---

#### Oidc4VciException.TokenExchangeFailed

**When it occurs:**
- OIDC4VCI token exchange fails

**Error code:** `OIDC4VCI_TOKEN_EXCHANGE_FAILED`

**Properties:**
- `reason: String` - The reason token exchange failed
- `credentialIssuer: String?` - The credential issuer URL (if available)
- `cause: Throwable?` - The underlying exception

**Code example:**
```kotlin
import com.trustweave.credential.oidc4vci.exception.Oidc4VciException

try {
    val token = oidc4vciService.exchangeToken(authorizationCode)
} catch (e: Oidc4VciException.TokenExchangeFailed) {
    println("Error code: ${e.code}")
    println("Reason: ${e.reason}")
    println("Issuer: ${e.credentialIssuer}")
}
```

---

#### Oidc4VciException.MetadataFetchFailed

**When it occurs:**
- OIDC4VCI metadata fetch fails

**Error code:** `OIDC4VCI_METADATA_FETCH_FAILED`

**Properties:**
- `credentialIssuer: String` - The credential issuer URL
- `reason: String` - The reason metadata fetch failed
- `cause: Throwable?` - The underlying exception

**Code example:**
```kotlin
import com.trustweave.credential.oidc4vci.exception.Oidc4VciException

try {
    val metadata = oidc4vciService.fetchCredentialIssuerMetadata(issuerUrl)
} catch (e: Oidc4VciException.MetadataFetchFailed) {
    println("Error code: ${e.code}")
    println("Issuer: ${e.credentialIssuer}")
    println("Reason: ${e.reason}")
}
```

---

#### Oidc4VciException.CredentialRequestFailed

**When it occurs:**
- OIDC4VCI credential request fails

**Error code:** `OIDC4VCI_CREDENTIAL_REQUEST_FAILED`

**Properties:**
- `reason: String` - The reason credential request failed
- `credentialIssuer: String?` - The credential issuer URL (if available)
- `cause: Throwable?` - The underlying exception

**Code example:**
```kotlin
import com.trustweave.credential.oidc4vci.exception.Oidc4VciException

try {
    val credential = oidc4vciService.requestCredential(accessToken, credentialOffer)
} catch (e: Oidc4VciException.CredentialRequestFailed) {
    println("Error code: ${e.code}")
    println("Reason: ${e.reason}")
    println("Issuer: ${e.credentialIssuer}")
}
```

---

#### ExchangeException.OperationNotSupported

**When it occurs:**
- Protocol doesn't support the requested operation

**Error code:** `OPERATION_NOT_SUPPORTED`

**Properties:**
- `protocolName: String` - The protocol name
- `operation: String` - The requested operation
- `supportedOperations: List<String>` - List of supported operations

**Code example:**
```kotlin
try {
    val proofRequest = registry.requestProof("oidc4vci", request)
} catch (e: ExchangeException.OperationNotSupported) {
    println("Error code: ${e.code}")
    println("Protocol: ${e.protocolName}")
    println("Operation: ${e.operation}")
    println("Supported: ${e.supportedOperations}")
    // Output:
    // Error code: OPERATION_NOT_SUPPORTED
    // Protocol: oidc4vci
    // Operation: REQUEST_PROOF
    // Supported: [OFFER_CREDENTIAL, REQUEST_CREDENTIAL, ISSUE_CREDENTIAL]
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
import com.trustweave.credential.exchange.exception.ExchangeException
import com.trustweave.credential.didcomm.exception.DidCommException

try {
    val offer = registry.offerCredential("didcomm", request)
    println("Offer created: ${offer.offerId}")
} catch (e: ExchangeException.ProtocolNotRegistered) {
    println("Protocol not registered. Register it first.")
    registry.register(DidCommExchangeProtocol(didCommService))
    // Retry
    val offer = registry.offerCredential("didcomm", request)
} catch (e: ExchangeException.MissingRequiredOption) {
    println("Missing required option: ${e.optionName}")
    println("Protocol: ${e.protocolName}")
} catch (e: ExchangeException.OperationNotSupported) {
    println("Operation not supported. Use different protocol or operation.")
    println("Supported operations: ${e.supportedOperations}")
} catch (e: DidCommException) {
    when (e) {
        is DidCommException.EncryptionFailed -> {
            println("DIDComm encryption failed: ${e.reason}")
        }
        is DidCommException.DecryptionFailed -> {
            println("DIDComm decryption failed: ${e.reason}")
        }
        // ... handle other DIDComm exceptions
    }
} catch (e: ExchangeException) {
    println("Exchange error: ${e.message}")
    println("Error code: ${e.code}")
} catch (e: Throwable) {
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
val offer = registry.offerCredential("didcomm", request)  // Throws ExchangeException.MissingRequiredOption
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
)  // Throws ExchangeException.MissingRequiredOption
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
val proofRequest = registry.requestProof("oidc4vci", request)  // Throws ExchangeException.OperationNotSupported
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

## Error Recovery Utilities

The `ExchangeExceptionRecovery` object provides comprehensive error recovery utilities:

### Automatic Retry with Exponential Backoff

```kotlin
import com.trustweave.credential.exchange.exception.retryExchangeOperation

// Automatically retries on transient errors
val offer = retryExchangeOperation(maxRetries = 3) {
    registry.offerCredential("didcomm", request)
}
```

### Error Classification

```kotlin
import com.trustweave.credential.exchange.exception.ExchangeExceptionRecovery

val exception: ExchangeException = // ... get exception

// Check if error is retryable
if (ExchangeExceptionRecovery.isRetryable(exception)) {
    // Retry the operation
}

// Check if error is transient
if (ExchangeExceptionRecovery.isTransient(exception)) {
    // Error might resolve on its own
}
```

### User-Friendly Error Messages

```kotlin
val message = ExchangeExceptionRecovery.getUserFriendlyMessage(exception)
println(message) // Displays user-friendly error message
```

### Alternative Protocol Fallback

```kotlin
val result = ExchangeExceptionRecovery.tryAlternativeProtocol(
    exception = exception,
    availableProtocols = listOf("oidc4vci", "chapi")
) { protocol ->
    registry.offerCredential(protocol, request)
}
```

### Companion Object Helpers

```kotlin
import com.trustweave.credential.exchange.exception.ExchangeException

// Use companion object for convenience
if (ExchangeException.isRetryable(exception)) {
    // Retry logic
}

val message = ExchangeException.getUserFriendlyMessage(exception)
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
   catch (e: ExchangeException) {
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

