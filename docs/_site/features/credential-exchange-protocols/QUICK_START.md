# Credential Exchange Protocols - Quick Start

Get started with credential exchange protocols in 5 minutes! This guide will walk you through creating your first credential exchange using the protocol abstraction layer.

> **Version:** 1.0.0-SNAPSHOT
> **Kotlin:** 2.2.0+ | **Java:** 21+
> **Prerequisites:** See [Installation](../../getting-started/installation.md)

## Complete Runnable Example

Here's a complete, copy-paste ready example that demonstrates the full credential exchange workflow with proper error handling.

```kotlin
package com.example.credentialexchange.quickstart

import com.trustweave.credential.exchange.*
import com.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import com.trustweave.credential.didcomm.DidCommFactory
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.kms.KeyManagementService
import com.trustweave.testkit.InMemoryKeyManagementService
import com.trustweave.did.DidDocument
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    try {
        // Step 1: Setup dependencies
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            // Mock DID resolution - replace with real resolver in production
            DidDocument(id = did, verificationMethod = emptyList())
        }

        // Step 2: Create and register protocol
        val registry = CredentialExchangeProtocolRegistry()
        val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
        registry.register(DidCommExchangeProtocol(didCommService))

        println("✅ Protocol registered: ${registry.getAllProtocolNames()}")

        // Step 3: Create credential offer
        val issuerDid = "did:key:issuer"
        val holderDid = "did:key:holder"

        val offer = registry.offerCredential(
            protocolName = "didcomm",
            request = CredentialOfferRequest(
                issuerDid = issuerDid,
                holderDid = holderDid,
                credentialPreview = CredentialPreview(
                    attributes = listOf(
                        CredentialAttribute("name", "Alice"),
                        CredentialAttribute("email", "alice@example.com")
                    )
                ),
                options = mapOf(
                    "fromKeyId" to "$issuerDid#key-1",
                    "toKeyId" to "$holderDid#key-1"
                )
            )
        )

        println("✅ Offer created:")
        println("   Offer ID: ${offer.offerId}")
        println("   Protocol: ${offer.protocolName}")

        // Step 4: Request credential
        val request = registry.requestCredential(
            protocolName = "didcomm",
            request = CredentialRequestRequest(
                holderDid = holderDid,
                issuerDid = issuerDid,
                offerId = offer.offerId,
                options = mapOf(
                    "fromKeyId" to "$holderDid#key-1",
                    "toKeyId" to "$issuerDid#key-1"
                )
            )
        )

        println("✅ Request created:")
        println("   Request ID: ${request.requestId}")
        println("   Protocol: ${request.protocolName}")

        // Step 5: Issue credential
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject {
                put("id", holderDid)
                put("name", "Alice")
                put("email", "alice@example.com")
            },
            issuanceDate = java.time.Instant.now().toString()
        )

        val issue = registry.issueCredential(
            protocolName = "didcomm",
            request = CredentialIssueRequest(
                issuerDid = issuerDid,
                holderDid = holderDid,
                credential = credential,
                requestId = request.requestId,
                options = mapOf(
                    "fromKeyId" to "$issuerDid#key-1",
                    "toKeyId" to "$holderDid#key-1"
                )
            )
        )

        println("✅ Credential issued:")
        println("   Issue ID: ${issue.issueId}")
        println("   Credential ID: ${issue.credential.id}")
        println("   Protocol: ${issue.protocolName}")

    } catch (e: ExchangeException) {
        when (e) {
            is ExchangeException.ProtocolNotRegistered -> {
                println("❌ Protocol not registered: ${e.protocolName}")
                println("   Available: ${e.availableProtocols}")
            }
            is ExchangeException.OperationNotSupported -> {
                println("❌ Operation not supported: ${e.operation}")
                println("   Supported: ${e.supportedOperations}")
            }
            is ExchangeException.MissingRequiredOption -> {
                println("❌ Missing required option: ${e.optionName}")
            }
            else -> {
                println("❌ Exchange error: ${e.message}")
                println("   Error code: ${e.code}")
            }
        }
        e.printStackTrace()
    } catch (e: Exception) {
        println("❌ Unexpected error: ${e.message}")
        e.printStackTrace()
    }
}
```

**Expected Output:**
```
✅ Protocol registered: [didcomm]
✅ Offer created:
   Offer ID: <offer-id>
   Protocol: didcomm
✅ Request created:
   Request ID: <request-id>
   Protocol: didcomm
✅ Credential issued:
   Issue ID: <issue-id>
   Credential ID: <credential-id>
   Protocol: didcomm
```

**To run this example:**
1. Add dependencies (see [Installation](../../getting-started/installation.md))
2. Copy the code above into `src/main/kotlin/QuickStart.kt`
3. Run with `./gradlew run` or execute in your IDE

---

## Step-by-Step Guide

### Step 1: Add Dependencies

Add the credential exchange dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core credential exchange
    implementation(project(":credentials:credential-core"))

    // DIDComm protocol (optional - choose protocols you need)
    implementation(project(":credentials:plugins:didcomm"))

    // OIDC4VCI protocol (optional)
    // implementation(project(":credentials:plugins:oidc4vci"))

    // CHAPI protocol (optional)
    // implementation(project(":credentials:plugins:chapi"))

    // Test kit for in-memory implementations
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:kms-core"))
}
```

### Step 2: Setup Dependencies

You need two dependencies for credential exchange:

1. **Key Management Service (KMS)**: Manages cryptographic keys
2. **DID Resolver**: Resolves DIDs to DID documents

```kotlin
// For testing/development: Use in-memory implementations
val kms: KeyManagementService = InMemoryKeyManagementService()
val resolveDid: suspend (String) -> DidDocument? = { did ->
    // Mock resolver - replace with real resolver in production
    DidDocument(id = did, verificationMethod = emptyList())
}

// For production: Use real implementations
// val kms = YourKmsImplementation()
// val resolveDid = YourDidResolver()
```

### Step 3: Create Registry and Register Protocol

```kotlin
// Create registry
val registry = CredentialExchangeProtocolRegistry()

// Create and register DIDComm protocol
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
registry.register(DidCommExchangeProtocol(didCommService))

// Verify registration
println("Registered protocols: ${registry.getAllProtocolNames()}")
// Output: Registered protocols: [didcomm]
```

### Step 4: Create Credential Offer

An offer is a message from the issuer to the holder proposing a credential.

```kotlin
val offer = registry.offerCredential(
    protocolName = "didcomm",
    request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = CredentialPreview(
            attributes = listOf(
                CredentialAttribute("name", "Alice"),
                CredentialAttribute("email", "alice@example.com")
            )
        ),
        options = mapOf(
            "fromKeyId" to "did:key:issuer#key-1",  // Required for DIDComm
            "toKeyId" to "did:key:holder#key-1"    // Required for DIDComm
        )
    )
)

println("Offer ID: ${offer.offerId}")
```

**What this does:**
- Creates a credential offer using the DIDComm protocol
- Returns an `offerId` that can be used to reference this offer
- The offer contains a preview of the credential attributes

### Step 5: Request Credential

After receiving an offer, the holder requests the credential.

```kotlin
val request = registry.requestCredential(
    protocolName = "didcomm",
    request = CredentialRequestRequest(
        holderDid = "did:key:holder",
        issuerDid = "did:key:issuer",
        offerId = offer.offerId,  // Reference to the offer
        options = mapOf(
            "fromKeyId" to "did:key:holder#key-1",
            "toKeyId" to "did:key:issuer#key-1"
        )
    )
)

println("Request ID: ${request.requestId}")
```

**What this does:**
- Creates a credential request referencing the offer
- Returns a `requestId` that can be used to reference this request
- The request indicates the holder wants to receive the credential

### Step 6: Issue Credential

After receiving a request, the issuer issues the credential.

```kotlin
val credential = VerifiableCredential(
    type = listOf("VerifiableCredential", "PersonCredential"),
    issuer = "did:key:issuer",
    credentialSubject = buildJsonObject {
        put("id", "did:key:holder")
        put("name", "Alice")
        put("email", "alice@example.com")
    },
    issuanceDate = java.time.Instant.now().toString()
)

val issue = registry.issueCredential(
    protocolName = "didcomm",
    request = CredentialIssueRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credential = credential,
        requestId = request.requestId,  // Reference to the request
        options = mapOf(
            "fromKeyId" to "did:key:issuer#key-1",
            "toKeyId" to "did:key:holder#key-1"
        )
    )
)

println("Credential ID: ${issue.credential.id}")
```

**What this does:**
- Issues a verifiable credential to the holder
- Returns the issued credential with proof
- The credential can now be stored, verified, and presented

---

## Protocol-Specific Options

Each protocol requires different options. Here's what you need for each:

### DIDComm Options

```kotlin
options = mapOf(
    "fromKeyId" to "did:key:issuer#key-1",  // Required: Sender's key ID
    "toKeyId" to "did:key:holder#key-1",    // Required: Recipient's key ID
    "encrypt" to true,                       // Optional: Encrypt message (default: true)
    "thid" to "thread-id"                    // Optional: Thread ID for message threading
)
```

### OIDC4VCI Options

```kotlin
options = mapOf(
    "credentialIssuer" to "https://issuer.example.com",  // Required: OIDC issuer URL
    "credentialTypes" to listOf("VerifiableCredential"), // Optional: Credential types
    "redirectUri" to "https://holder.example.com/callback" // Optional: Redirect URI
)
```

### CHAPI Options

```kotlin
options = mapOf(
    // CHAPI typically doesn't require additional options
    // Messages are generated for browser use
)
```

---

## Error Handling

All registry methods can throw exceptions. Always wrap operations in try-catch:

```kotlin
try {
    val offer = registry.offerCredential("didcomm", request)
} catch (e: IllegalArgumentException) {
    // Protocol not registered or invalid argument
    println("Error: ${e.message}")
} catch (e: UnsupportedOperationException) {
    // Protocol doesn't support this operation
    println("Error: ${e.message}")
} catch (e: Exception) {
    // Other errors
    println("Unexpected error: ${e.message}")
}
```

**Common Errors:**
- `ExchangeException.ProtocolNotRegistered`: Protocol not registered
- `ExchangeException.OperationNotSupported`: Protocol doesn't support the operation
- `ExchangeException.MissingRequiredOption`: Missing required option
- `ExchangeException.InvalidRequest`: Invalid request field
- Plugin-specific exceptions (e.g., `DidCommException`, `Oidc4VciException`, `ChapiException`)
- Protocol-specific errors: See [Error Handling Guide](./ERROR_HANDLING.md)

---

## Next Steps

1. **Learn More**: Read [Core Concepts](../../core-concepts/credential-exchange-protocols.md)
2. **See Examples**: Check [Complete Examples](./EXAMPLES.md)
3. **API Reference**: See [API Reference](./API_REFERENCE.md)
4. **Workflows**: Follow [Workflow Guides](./WORKFLOWS.md)
5. **Troubleshooting**: See [Troubleshooting Guide](./TROUBLESHOOTING.md)

---

## Related Documentation

- **[Overview](./README.md)** - Protocol overview and comparison
- **[Core Concepts](../../core-concepts/credential-exchange-protocols.md)** - Deep dive into protocol abstraction
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Examples](./EXAMPLES.md)** - More code examples
- **[Workflows](./WORKFLOWS.md)** - Step-by-step workflows
- **[Error Handling](./ERROR_HANDLING.md)** - Error handling guide
- **[Troubleshooting](./TROUBLESHOOTING.md)** - Common issues and solutions
- **[Glossary](./GLOSSARY.md)** - Terms and concepts
- **[Best Practices](./BEST_PRACTICES.md)** - Security and performance guidelines
- **[Versioning](./VERSIONING.md)** - Version info and migration guides

