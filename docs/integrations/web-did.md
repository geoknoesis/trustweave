---
title: Web DID Integration
---

# Web DID Integration

> This guide covers the did:web method integration for TrustWeave. The did:web plugin provides W3C-compliant DID resolution from HTTPS endpoints.

## Overview

The `did/plugins/web` module provides a complete implementation of TrustWeave's `DidMethod` interface using the W3C did:web specification. This integration enables you to:

- Create and resolve DIDs from HTTPS endpoints
- Host DID documents at standard web locations (/.well-known/did.json)
- Support domain-based and path-based did:web identifiers
- Ensure HTTPS security as required by the W3C spec

## Installation

Add the did:web module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:web:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

The did:web provider can be configured via options or automatically discovered via SPI:

```kotlin
import com.trustweave.did.*
import com.trustweave.webdid.*
import com.trustweave.kms.*
import okhttp3.OkHttpClient

// Manual creation
val kms = InMemoryKeyManagementService()
val httpClient = OkHttpClient()
val config = WebDidConfig.default()

val method = WebDidMethod(kms, httpClient, config)
```

### SPI Auto-Discovery

When the module is on the classpath, did:web is automatically available:

```kotlin
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover did:web provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val webProvider = providers.find { it.supportedMethods.contains("web") }

// Create method
val options = DidCreationOptions()
val method = webProvider?.create("web", options)
```

## Usage Examples

### Creating a did:web

```kotlin
val kms = InMemoryKeyManagementService()
val httpClient = OkHttpClient()
val method = WebDidMethod.create(kms, httpClient)

// Create DID for a domain
val options = didCreationOptions {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
    property("domain", "example.com")
    property("hostingUrl", "https://example.com") // Optional: publish immediately
}

val document = method.createDid(options)
println("Created: ${document.id}") // did:web:example.com
```

### Resolving a did:web

```kotlin
import com.trustweave.did.identifiers.Did
import com.trustweave.did.resolver.DidResolutionResult

val did = Did("did:web:example.com")
val result = method.resolveDid(did)

when (result) {
    is DidResolutionResult.Success -> {
        println("Resolved: ${result.document.id}")
        println("Verification methods: ${result.document.verificationMethod.size}")
    }
    is DidResolutionResult.Failure.NotFound -> {
        println("DID not found: ${result.did.value}")
    }
    else -> println("Resolution failed")
}
```

### Creating did:web with Path

```kotlin
// Create DID for did:web:example.com:user:alice
val options = didCreationOptions {
    property("domain", "example.com")
    property("path", "user:alice")
    property("hostingUrl", "https://example.com")
}

val document = method.createDid(options)
// Resolves from: https://example.com/user/alice/.well-known/did.json
```

### Updating a did:web

```kotlin
import com.trustweave.did.identifiers.Did

val did = Did("did:web:example.com")
val document = method.updateDid(did) { currentDoc ->
    currentDoc.copy(
        service = currentDoc.service + Service(
            id = "${currentDoc.id}#didcomm",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://example.com/didcomm"
        )
    )
}
```

### Deactivating a did:web

```kotlin
import com.trustweave.did.identifiers.Did

val did = Did("did:web:example.com")
val deactivated = method.deactivateDid(did)
println("Deactivated: $deactivated")
```

## Configuration Options

### WebDidConfig

```kotlin
val config = WebDidConfig.builder()
    .requireHttps(true)                    // Require HTTPS (default: true)
    .documentPath("/.well-known/did.json") // Document path (default: /.well-known/did.json)
    .timeoutSeconds(30)                    // HTTP timeout (default: 30)
    .followRedirects(true)                 // Follow redirects (default: true)
    .build()
```

### DidCreationOptions Properties

- `domain` (required): Domain name for the DID (e.g., "example.com")
- `path` (optional): Path component for the DID (e.g., "user:alice")
- `hostingUrl` (optional): Base URL where the document will be hosted
- `timeoutSeconds` (optional): HTTP client timeout
- `followRedirects` (optional): Whether to follow redirects

## DID Format

### Domain-based DID

```
did:web:example.com
```

Resolves to: `https://example.com/.well-known/did.json`

### Path-based DID

```
did:web:example.com:user:alice
```

Resolves to: `https://example.com/user/alice/.well-known/did.json`

The path component uses colons (`:`) in the DID and slashes (`/`) in the URL path, per W3C specification.

## HTTPS Requirement

Per W3C did:web specification, all DID documents must be hosted over HTTPS. The implementation validates this by default but can be configured:

```kotlin
val config = WebDidConfig.builder()
    .requireHttps(true)  // Enforce HTTPS (default)
    .build()
```

## Document Hosting

To publish a DID document, you need to:

1. **Create the document** using `createDid()`
2. **Host it** at the appropriate URL path (/.well-known/did.json)
3. **Ensure HTTPS** is enabled
4. **Set proper Content-Type**: `application/json`

### Example: Publishing to AWS S3

```kotlin
// After creating the DID document
val document = method.createDid(options)

// Upload to S3 or your hosting service
val s3Client = AmazonS3Client.builder().build()
val json = Json.encodeToString(documentToJsonElement(document))
s3Client.putObject(
    "my-bucket",
    ".well-known/did.json",
    json
)
```

## Integration with TrustWeave

```kotlin
import com.trustweave.TrustWeave
import com.trustweave.webdid.WebDidMethod
import com.trustweave.webdid.WebDidConfig

val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()

    didMethods {
        + WebDidMethod.create(kms!!, OkHttpClient(), WebDidConfig.default())
    }
}

// Use did:web
val did = TrustWeave.dids.create("web") {
    property("domain", "example.com")
}

val resolved = TrustWeave.dids.resolve(did.id)
```

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `HTTP 404` | Document not found at URL | Ensure document is hosted at correct path |
| `HTTP 403` | Access denied | Check server permissions |
| `Invalid DID format` | DID doesn't match did:web format | Validate DID string format |
| `HTTPS required` | Non-HTTPS URL used | Use HTTPS for all document hosting |
| `Document ID mismatch` | Resolved document ID doesn't match DID | Verify document content |

## Testing

For testing without actual web hosting, the method supports in-memory storage:

```kotlin
val method = WebDidMethod.create(kms, OkHttpClient())

// Create DID (stored in memory)
val document = method.createDid(options)

// Resolve from memory (if not published to web)
val result = method.resolveDid(document.id)
```

## W3C Specification Compliance

This implementation follows the [W3C did:web specification](https://w3c-ccg.github.io/did-method-web/):

- ✅ HTTPS requirement enforcement
- ✅ Standard document path (/.well-known/did.json)
- ✅ Domain and path-based identifiers
- ✅ DID document JSON-LD format
- ✅ Resolution from HTTP endpoints
- ✅ Update and deactivation support

## Best Practices

1. **Always use HTTPS**: Required by spec for security
2. **Standard path**: Use `/.well-known/did.json` unless necessary to change
3. **Cache documents**: Consider caching resolved documents for performance
4. **Content-Type**: Serve documents with `Content-Type: application/json`
5. **CORS headers**: If needed, add CORS headers for web access
6. **Document validation**: Validate document format before publishing

## Troubleshooting

### Document Not Resolving

- Verify the document is hosted at the correct URL
- Check HTTPS certificate is valid
- Verify Content-Type header is `application/json`
- Check server logs for errors

### HTTPS Validation Errors

- Ensure certificate is valid and not expired
- Check that the domain matches the certificate
- Verify redirects preserve HTTPS

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [DID Core Concepts](../core-concepts/dids.md) for DID fundamentals
- Check [Integration Modules](../integrations/README.md) for other DID methods

## References

- [W3C did:web Specification](https://w3c-ccg.github.io/did-method-web/)
- [DID Core Specification](https://www.w3.org/TR/did-core/)
- [TrustWeave Core API](../api-reference/core-api.md)

