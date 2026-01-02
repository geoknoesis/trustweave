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
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.*

val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder().build()
    )
)

when (offerResult) {
    is ExchangeResult.Success -> {
        val offer = offerResult.value
        println("Offer created: ${offer.offerId}")
    }
    is ExchangeResult.Failure.ProtocolNotSupported -> {
        println("Protocol: ${offerResult.protocolName}")
        println("Available: ${offerResult.availableProtocols}")
    }
    is ExchangeResult.Failure.OperationNotSupported -> {
        println("Operation: ${offerResult.operation}")
        println("Supported: ${offerResult.supportedOperations}")
    }
    is ExchangeResult.Failure.InvalidRequest -> {
        println("Invalid request: ${offerResult.reason}")
        println("Field: ${offerResult.field}")
    }
    is ExchangeResult.Failure.NetworkError -> {
        println("Network error: ${offerResult.reason}")
    }
    else -> {
        println("Exchange error: $offerResult")
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
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder().build()
    )
)

when (offerResult) {
    is ExchangeResult.Failure.ProtocolNotSupported -> {
        println("Protocol: ${offerResult.protocolName}")
        println("Available: ${offerResult.availableProtocols}")
        // Output:
        // Protocol: ExchangeProtocolName("didcomm")
        // Available: []
    }
    else -> {
        // Success or other error
    }
}
```

**Solutions:**

1. **Register the protocol before use:**
   ```kotlin
   import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
   import org.trustweave.credential.exchange.ExchangeServices
   
   val registry = ExchangeProtocolRegistries.default()
   val didCommService = DidCommFactory.createInMemoryService(kms) { didStr ->
       DidDocument(id = Did(didStr), verificationMethod = emptyList())
   }
   registry.register(DidCommExchangeProtocol(didCommService))
   
   val exchangeService = ExchangeServices.createExchangeService(
       protocolRegistry = registry,
       credentialService = credentialService,
       didResolver = didResolver
   )

   // Now safe to use
   val offerResult = exchangeService.offer(
       ExchangeRequest.Offer(
           protocolName = "didcomm".requireExchangeProtocolName(),
           issuerDid = issuerDid,
           holderDid = holderDid,
           credentialPreview = preview,
           options = ExchangeOptions.builder().build()
       )
   )
   ```

2. **Check available protocols:**
   ```kotlin
   val available = registry.getSupportedProtocols()
   val didcommProtocol = "didcomm".requireExchangeProtocolName()
   if (!available.contains(didcommProtocol)) {
       println("Protocol not available. Available: $available")
       // Register protocol or use different protocol
   }
   ```

3. **Use isRegistered() to check:**
   ```kotlin
   val protocolName = "didcomm".requireExchangeProtocolName()
   if (!registry.isRegistered(protocolName)) {
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
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = preview,
        options = ExchangeOptions.Empty // Missing 'fromKeyId' and 'toKeyId'
    )
)

when (offerResult) {
    is ExchangeResult.Failure.InvalidRequest -> {
        println("Invalid request: ${offerResult.reason}")
        println("Field: ${offerResult.field}")
        // Output:
        // Invalid request: Missing required option: fromKeyId
        // Field: options
    }
    else -> {
        // Success or other error
    }
}
```

**Solutions:**
1. **Add the missing option:**
   ```kotlin
   val offerResult = exchangeService.offer(
       ExchangeRequest.Offer(
           protocolName = "didcomm".requireExchangeProtocolName(),
           issuerDid = Did("did:key:issuer"),
           holderDid = Did("did:key:holder"),
           credentialPreview = preview,
           options = ExchangeOptions.builder()
               .addMetadata("fromKeyId", "did:key:issuer#key-1")
               .addMetadata("toKeyId", "did:key:holder#key-1")
               .build()
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
val requestResult = exchangeService.request(
    ExchangeRequest.Request(
        protocolName = "didcomm".requireExchangeProtocolName(),
        holderDid = Did("did:key:holder"),
        issuerDid = Did("did:key:issuer"),
        offerId = OfferId("non-existent-offer-id"),
        options = ExchangeOptions.builder().build()
    )
)

when (requestResult) {
    is ExchangeResult.Failure.MessageNotFound -> {
        println("Offer ID: ${requestResult.messageId}")
        // Output:
        // Offer ID: non-existent-offer-id
    }
    else -> {
        // Success or other error
    }
}
```

**Solutions:**
1. **Use a valid offer ID:**
   ```kotlin
   // First create an offer
   val offerResult = exchangeService.offer(
       ExchangeRequest.Offer(
           protocolName = "didcomm".requireExchangeProtocolName(),
           issuerDid = issuerDid,
           holderDid = holderDid,
           credentialPreview = preview,
           options = ExchangeOptions.builder().build()
       )
   )
   
   val offer = when (offerResult) {
       is ExchangeResult.Success -> offerResult.value
       else -> throw IllegalStateException("Offer failed: $offerResult")
   }

   // Then use the offer ID
   val requestResult = exchangeService.request(
       ExchangeRequest.Request(
           protocolName = "didcomm".requireExchangeProtocolName(),
           holderDid = holderDid,
           issuerDid = issuerDid,
           offerId = offer.offerId, // Use the actual offer ID
           options = ExchangeOptions.builder().build()
       )
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
val issueResult = exchangeService.issue(
    ExchangeRequest.Issue(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credential = credential,
        requestId = RequestId("non-existent-request-id"),
        options = ExchangeOptions.builder().build()
    )
)

when (issueResult) {
    is ExchangeResult.Failure.MessageNotFound -> {
        println("Request ID: ${issueResult.messageId}")
    }
    else -> {
        // Success or other error
    }
}
```

---

### DIDComm-Specific Errors

DIDComm-specific exceptions are located in the `didcomm` plugin module and extend `ExchangeException`:

```kotlin
import org.trustweave.credential.didcomm.exception.DidCommException
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
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder().build()
    )
)

when (offerResult) {
    is ExchangeResult.Failure.NetworkError -> {
        println("Network error: ${offerResult.reason}")
        // DIDComm encryption failures are typically network errors
    }
    is ExchangeResult.Failure.AdapterError -> {
        println("Adapter error: ${offerResult.reason}")
        // Cryptographic errors are typically adapter errors
    }
    else -> {
        // Success or other error
    }
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
import org.trustweave.credential.didcomm.exception.DidCommException

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
import org.trustweave.credential.oidc4vci.exception.Oidc4VciException
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
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "oidc4vci".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder()
            .addMetadata("credentialIssuer", "https://issuer.example.com")
            .build()
    )
)

when (offerResult) {
    is ExchangeResult.Failure.NetworkError -> {
        println("Network error: ${offerResult.reason}")
        // HTTP request failures are network errors
    }
    else -> {
        // Success or other error
    }
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
import org.trustweave.credential.oidc4vci.exception.Oidc4VciException

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
import org.trustweave.credential.oidc4vci.exception.Oidc4VciException

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
import org.trustweave.credential.oidc4vci.exception.Oidc4VciException

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
val proofRequestResult = exchangeService.requestProof(
    ProofExchangeRequest.Request(
        protocolName = "oidc4vci".requireExchangeProtocolName(),
        verifierDid = verifierDid,
        proverDid = proverDid,
        proofRequest = proofRequest,
        options = ExchangeOptions.Empty
    )
)

when (proofRequestResult) {
    is ExchangeResult.Failure.OperationNotSupported -> {
        println("Error code: ${proofRequestResult.code}")
        println("Protocol: ${proofRequestResult.protocolName}")
        println("Operation: ${proofRequestResult.operation}")
        println("Supported: ${proofRequestResult.supportedOperations}")
        // Output:
        // Error code: OPERATION_NOT_SUPPORTED
        // Protocol: ExchangeProtocolName("oidc4vci")
        // Operation: REQUEST_PROOF
        // Supported: [OFFER_CREDENTIAL, REQUEST_CREDENTIAL, ISSUE_CREDENTIAL]
    }
    is ExchangeResult.Success -> {
        // Handle success
    }
    else -> {
        // Handle other errors
    }
}
```

**Solutions:**

1. **Check supported operations:**
   ```kotlin
   val protocolName = "oidc4vci".requireExchangeProtocolName()
   val capabilities = exchangeService.getCapabilities(protocolName)
   if (capabilities != null) {
       println("Supported operations: ${capabilities.supportedOperations}")
       // Output: Supported operations: [OFFER_CREDENTIAL, REQUEST_CREDENTIAL, ISSUE_CREDENTIAL]
   }
   ```

2. **Use a different protocol:**
   ```kotlin
   // OIDC4VCI doesn't support proof requests, use DIDComm instead
   val proofRequestResult = exchangeService.requestProof(
       ProofExchangeRequest.Request(
           protocolName = "didcomm".requireExchangeProtocolName(),
           verifierDid = verifierDid,
           proverDid = proverDid,
           proofRequest = proofRequest,
           options = ExchangeOptions.Empty
       )
   )
   ```

3. **Use a different operation:**
   ```kotlin
   // If you need proof functionality, use DIDComm or CHAPI
   val protocolName = "didcomm".requireExchangeProtocolName()
   if (exchangeService.supports(protocolName)) {
       val proofRequestResult = exchangeService.requestProof(
           ProofExchangeRequest.Request(
               protocolName = protocolName,
               verifierDid = verifierDid,
               proverDid = proverDid,
               proofRequest = proofRequest,
               options = ExchangeOptions.Empty
           )
       )
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
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = preview,
        options = ExchangeOptions.Empty // Missing fromKeyId and toKeyId
    )
)

when (offerResult) {
    is ExchangeResult.Failure.InvalidRequest -> {
        println("Error: ${offerResult.reason}")
    }
    else -> {
        // Success or other error
    }
}
```

**Solution:**
```kotlin
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = preview,
        options = ExchangeOptions.builder()
            .addMetadata("fromKeyId", "did:key:issuer#key-1")  // Required
            .addMetadata("toKeyId", "did:key:holder#key-1")     // Required
            .build()
    )
)

val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}
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
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "oidc4vci".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = preview,
        options = ExchangeOptions.builder()
            .addMetadata("credentialIssuer", "https://issuer.example.com")  // Required
            .build()
    )
)

val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}
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
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "oidc4vci".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder()
            .addMetadata("credentialIssuer", "https://issuer.example.com")
            .build()
    )
)

when (offerResult) {
    is ExchangeResult.Failure.NetworkError -> {
        when {
            offerResult.reason?.contains("404") == true -> {
                println("Credential issuer not found. Check URL.")
            }
            offerResult.reason?.contains("401") == true -> {
                println("Authentication failed. Check credentials.")
            }
            offerResult.reason?.contains("timeout") == true -> {
                println("Request timed out. Retry later.")
            }
            else -> {
                println("HTTP error: ${offerResult.reason}")
            }
        }
    }
    else -> {
        // Success or other error
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
    val offerResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = "chapi".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = ExchangeOptions.Empty
        )
    )
    val offer = when (offerResult) {
        is ExchangeResult.Success -> offerResult.value
        else -> throw IllegalStateException("Offer failed: $offerResult")
    }
} else {
    // Use DIDComm or OIDC4VCI for server-side
    val offerResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = "didcomm".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = ExchangeOptions.builder().build()
        )
    )
    val offer = when (offerResult) {
        is ExchangeResult.Success -> offerResult.value
        else -> throw IllegalStateException("Offer failed: $offerResult")
    }
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
import org.trustweave.credential.identifiers.*

// Check protocol is registered
val protocolName = "didcomm".requireExchangeProtocolName()
if (!registry.isRegistered(protocolName)) {
    println("Protocol not registered")
    return
}

// Check operation is supported
val protocol = registry.get(protocolName)
if (protocol?.supportedOperations?.contains(ExchangeOperation.OFFER_CREDENTIAL) != true) {
    println("Operation not supported")
    return
}

// Now safe to use
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = protocolName,
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder().build()
    )
)

val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}
```

---

### Pattern 2: Result-Based Error Handling

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.*

val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder().build()
    )
)

when (offerResult) {
    is ExchangeResult.Success -> {
        val offer = offerResult.value
        println("Offer created: ${offer.offerId}")
    }
    is ExchangeResult.Failure.ProtocolNotSupported -> {
        println("Protocol not registered. Register it first.")
        registry.register(DidCommExchangeProtocol(didCommService))
        // Retry
        val retryResult = exchangeService.offer(
            ExchangeRequest.Offer(
                protocolName = "didcomm".requireExchangeProtocolName(),
                issuerDid = issuerDid,
                holderDid = holderDid,
                credentialPreview = preview,
                options = ExchangeOptions.builder().build()
            )
        )
        // Handle retry result
    }
    is ExchangeResult.Failure.InvalidRequest -> {
        println("Invalid request: ${offerResult.reason}")
        println("Field: ${offerResult.field}")
    }
    is ExchangeResult.Failure.OperationNotSupported -> {
        println("Operation not supported. Use different protocol or operation.")
        println("Supported operations: ${offerResult.supportedOperations}")
    }
    is ExchangeResult.Failure.NetworkError -> {
        println("Network error: ${offerResult.reason}")
        // DIDComm encryption/decryption failures are typically network errors
    }
    is ExchangeResult.Failure.AdapterError -> {
        println("Adapter error: ${offerResult.reason}")
        // Cryptographic errors are typically adapter errors
    }
    else -> {
        println("Exchange error: $offerResult")
    }
}
```

---

### Pattern 3: Fallback to Alternative Protocol

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.*

suspend fun offerCredentialWithFallback(
    exchangeService: ExchangeService,
    registry: ExchangeProtocolRegistry,
    preferredProtocol: String,
    fallbackProtocols: List<String>,
    issuerDid: Did,
    holderDid: Did,
    credentialPreview: CredentialPreview,
    options: ExchangeOptions
): ExchangeResponse.Offer? {
    // Try preferred protocol first
    val preferredProtocolName = preferredProtocol.requireExchangeProtocolName()
    if (registry.isRegistered(preferredProtocolName)) {
        val result = exchangeService.offer(
            ExchangeRequest.Offer(
                protocolName = preferredProtocolName,
                issuerDid = issuerDid,
                holderDid = holderDid,
                credentialPreview = credentialPreview,
                options = options
            )
        )
        when (result) {
            is ExchangeResult.Success -> return result.value
            else -> println("Preferred protocol failed: $result")
        }
    }

    // Try fallback protocols
    for (protocolStr in fallbackProtocols) {
        val protocolName = protocolStr.requireExchangeProtocolName()
        if (registry.isRegistered(protocolName)) {
            val result = exchangeService.offer(
                ExchangeRequest.Offer(
                    protocolName = protocolName,
                    issuerDid = issuerDid,
                    holderDid = holderDid,
                    credentialPreview = credentialPreview,
                    options = options
                )
            )
            when (result) {
                is ExchangeResult.Success -> return result.value
                else -> println("Fallback protocol $protocolStr failed: $result")
            }
        }
    }

    return null
}

// Usage
val offer = offerCredentialWithFallback(
    exchangeService = exchangeService,
    registry = registry,
    preferredProtocol = "didcomm",
    fallbackProtocols = listOf("oidc4vci", "chapi"),
    issuerDid = issuerDid,
    holderDid = holderDid,
    credentialPreview = preview,
    options = ExchangeOptions.builder().build()
) ?: throw IllegalStateException("All protocols failed")
```

---

### Pattern 4: Validate Before Operation

```kotlin
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.did.identifiers.Did

fun validateOfferRequest(
    issuerDid: Did,
    holderDid: Did,
    credentialPreview: CredentialPreview
): ValidationResult {
    val errors = mutableListOf<String>()

    // Validate DIDs (Did type provides basic validation)
    if (issuerDid.value.isBlank()) {
        errors.add("Invalid issuer DID format")
    }
    if (holderDid.value.isBlank()) {
        errors.add("Invalid holder DID format")
    }

    // Validate preview
    if (credentialPreview.attributes.isEmpty()) {
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
val validation = validateOfferRequest(issuerDid, holderDid, preview)
if (validation is ValidationResult.Invalid) {
    println("Validation failed: ${validation.errors}")
    return
}

val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder().build()
    )
)

val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}
```

---

## Common Error Scenarios

### Scenario 1: Protocol Not Registered

**Problem:**
```kotlin
val registry = ExchangeProtocolRegistries.default()
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder().build()
    )
)  // Returns ExchangeResult.Failure.ProtocolNotSupported
```

**Solution:**
```kotlin
val registry = ExchangeProtocolRegistries.default()
val didCommService = DidCommFactory.createInMemoryService(kms) { didStr ->
    DidDocument(id = Did(didStr), verificationMethod = emptyList())
}
registry.register(DidCommExchangeProtocol(didCommService))

val exchangeService = ExchangeServices.createExchangeService(
    protocolRegistry = registry,
    credentialService = credentialService,
    didResolver = didResolver
)

val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = preview,
        options = ExchangeOptions.builder().build()
    )
)  // Now works

val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}
```

---

### Scenario 2: Missing Required Options

**Problem:**
```kotlin
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = preview,
        options = ExchangeOptions.Empty  // Missing fromKeyId and toKeyId
    )
)  // Returns ExchangeResult.Failure.InvalidRequest
```

**Solution:**
```kotlin
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = preview,
        options = ExchangeOptions.builder()
            .addMetadata("fromKeyId", "did:key:issuer#key-1")  // Added
            .addMetadata("toKeyId", "did:key:holder#key-1")     // Added
            .build()
    )
)  // Now works

val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}
```

---

### Scenario 3: Operation Not Supported

**Problem:**
```kotlin
val proofRequestResult = exchangeService.requestProof(
    ExchangeRequest.ProofRequest(
        protocolName = "oidc4vci".requireExchangeProtocolName(),
        verifierDid = verifierDid,
        proverDid = proverDid,
        // ... other fields
        options = ExchangeOptions.builder().build()
    )
)  // Returns ExchangeResult.Failure.OperationNotSupported
```

**Solution:**
```kotlin
// Check supported operations first
val protocolName = "oidc4vci".requireExchangeProtocolName()
val protocol = registry.get(protocolName)
if (protocol?.supportedOperations?.contains(ExchangeOperation.REQUEST_PROOF) == true) {
    val proofRequestResult = exchangeService.requestProof(
        ExchangeRequest.ProofRequest(
            protocolName = protocolName,
            verifierDid = verifierDid,
            proverDid = proverDid,
            // ... other fields
            options = ExchangeOptions.builder().build()
        )
    )
} else {
    // Use different protocol
    val proofRequestResult = exchangeService.requestProof(
        ExchangeRequest.ProofRequest(
            protocolName = "didcomm".requireExchangeProtocolName(),
            verifierDid = verifierDid,
            proverDid = proverDid,
            // ... other fields
            options = ExchangeOptions.builder().build()
        )
    )
}
```

---

## Error Recovery Utilities

The `ExchangeExceptionRecovery` object provides comprehensive error recovery utilities:

### Automatic Retry with Exponential Backoff

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.*

// Automatically retries on transient errors
suspend fun retryOffer(
    maxRetries: Int = 3,
    exchangeService: ExchangeService,
    issuerDid: Did,
    holderDid: Did,
    preview: CredentialPreview,
    options: ExchangeOptions
): ExchangeResponse.Offer {
    var lastResult: ExchangeResult<ExchangeResponse.Offer>? = null
    repeat(maxRetries) { attempt ->
        val result = exchangeService.offer(
            ExchangeRequest.Offer(
                protocolName = "didcomm".requireExchangeProtocolName(),
                issuerDid = issuerDid,
                holderDid = holderDid,
                credentialPreview = preview,
                options = options
            )
        )
        when (result) {
            is ExchangeResult.Success -> return result.value
            is ExchangeResult.Failure.NetworkError -> {
                lastResult = result
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay((1 shl attempt) * 1000L) // Exponential backoff
                }
            }
            else -> {
                lastResult = result
                break
            }
        }
    }
    throw IllegalStateException("Offer failed after $maxRetries retries: $lastResult")
}
```

### Error Classification

```kotlin
import org.trustweave.credential.exchange.result.ExchangeResult

val result: ExchangeResult<*> = // ... get result

// Check if error is retryable
fun isRetryable(result: ExchangeResult<*>): Boolean {
    return when (result) {
        is ExchangeResult.Failure.NetworkError -> true
        is ExchangeResult.Failure.AdapterError -> {
            // Check if it's a transient adapter error
            result.reason?.contains("timeout") == true ||
            result.reason?.contains("temporary") == true
        }
        else -> false
    }
}

if (isRetryable(result)) {
    // Retry the operation
}

// Check if error is transient
fun isTransient(result: ExchangeResult<*>): Boolean {
    return when (result) {
        is ExchangeResult.Failure.NetworkError -> true
        else -> false
    }
}

if (isTransient(result)) {
    // Error might resolve on its own
}
```

### User-Friendly Error Messages

```kotlin
fun getUserFriendlyMessage(result: ExchangeResult<*>): String {
    return when (result) {
        is ExchangeResult.Success -> "Operation succeeded"
        is ExchangeResult.Failure.ProtocolNotSupported -> 
            "Protocol not supported. Available: ${result.availableProtocols}"
        is ExchangeResult.Failure.InvalidRequest -> 
            "Invalid request: ${result.reason}"
        is ExchangeResult.Failure.NetworkError -> 
            "Network error: ${result.reason}. Please check your connection."
        else -> "Operation failed: $result"
    }
}

val message = getUserFriendlyMessage(result)
println(message) // Displays user-friendly error message
```

### Alternative Protocol Fallback

```kotlin
suspend fun tryAlternativeProtocol(
    result: ExchangeResult<*>,
    availableProtocols: List<String>,
    exchangeService: ExchangeService,
    issuerDid: Did,
    holderDid: Did,
    preview: CredentialPreview,
    options: ExchangeOptions
): ExchangeResponse.Offer? {
    if (result is ExchangeResult.Failure.ProtocolNotSupported) {
        for (protocolStr in availableProtocols) {
            val protocolName = protocolStr.requireExchangeProtocolName()
            val altResult = exchangeService.offer(
                ExchangeRequest.Offer(
                    protocolName = protocolName,
                    issuerDid = issuerDid,
                    holderDid = holderDid,
                    credentialPreview = preview,
                    options = options
                )
            )
            when (altResult) {
                is ExchangeResult.Success -> return altResult.value
                else -> continue
            }
        }
    }
    return null
}
```

---

## Error Recovery Strategies

### Strategy 1: Retry with Different Protocol

```kotlin
suspend fun offerCredentialWithRetry(
    exchangeService: ExchangeService,
    registry: ExchangeProtocolRegistry,
    protocols: List<String>,
    issuerDid: Did,
    holderDid: Did,
    preview: CredentialPreview,
    options: ExchangeOptions
): ExchangeResponse.Offer? {
    for (protocol in protocols) {
        val protocolName = protocol.requireExchangeProtocolName()
        if (registry.isRegistered(protocolName)) {
            val result = exchangeService.offer(
                ExchangeRequest.Offer(
                    protocolName = protocolName,
                    issuerDid = issuerDid,
                    holderDid = holderDid,
                    credentialPreview = preview,
                    options = options
                )
            )
            when (result) {
                is ExchangeResult.Success -> return result.value
                else -> {
                    println("Protocol $protocol failed: $result")
                    continue
                }
            }
        }
    }
    return null
}
```

---

### Strategy 2: Register Missing Protocol

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.*

suspend fun offerCredentialWithAutoRegister(
    exchangeService: ExchangeService,
    registry: ExchangeProtocolRegistry,
    protocolName: String,
    issuerDid: Did,
    holderDid: Did,
    credentialPreview: CredentialPreview,
    options: ExchangeOptions
): ExchangeResponse.Offer {
    val protocolNameObj = protocolName.requireExchangeProtocolName()
    if (!registry.isRegistered(protocolNameObj)) {
        // Auto-register protocol
        when (protocolName) {
            "didcomm" -> {
                val service = DidCommFactory.createInMemoryService(kms) { didStr ->
                    DidDocument(id = Did(didStr), verificationMethod = emptyList())
                }
                registry.register(DidCommExchangeProtocol(service))
            }
            "oidc4vci" -> {
                val service = Oidc4VciService(credentialIssuerUrl, kms)
                registry.register(Oidc4VciExchangeProtocol(service))
            }
            // ... other protocols
        }
    }

    val result = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = protocolNameObj,
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = credentialPreview,
            options = options
        )
    )
    
    return when (result) {
        is ExchangeResult.Success -> result.value
        else -> throw IllegalStateException("Offer failed: $result")
    }
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
   val offerResult = exchangeService.offer(
       ExchangeRequest.Offer(
           protocolName = "didcomm".requireExchangeProtocolName(),
           issuerDid = issuerDid,
           holderDid = holderDid,
           credentialPreview = preview,
           options = ExchangeOptions.Empty
       )
   )
   when (offerResult) {
       is ExchangeResult.Success -> {
           // Use offerResult.value
       }
       else -> {
           // Handle error appropriately
       }
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

