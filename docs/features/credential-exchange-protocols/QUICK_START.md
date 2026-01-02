# Credential Exchange Protocols - Quick Start

Get started with credential exchange protocols in 5 minutes! This guide will walk you through creating your first credential exchange using the protocol abstraction layer.

> **Version:** 1.0.0-SNAPSHOT
> **Kotlin:** 2.2.0+ | **Java:** 21+
> **Prerequisites:** See [Installation](../../getting-started/installation.md)

## Complete Runnable Example

Here's a complete, copy-paste ready example that demonstrates the full credential exchange workflow with proper error handling.

```kotlin
package com.example.credentialexchange.quickstart

import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.response.ExchangeResponse
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import org.trustweave.credential.didcomm.DidCommFactory
import org.trustweave.credential.CredentialService
import org.trustweave.credential.credentialService
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.identifiers.*
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.model.DidDocument
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

fun main() = runBlocking {
    try {
        // Step 1: Setup dependencies
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didResolver: DidResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                // Mock DID resolution - replace with real resolver in production
                return DidResolutionResult.Success(
                    DidDocument(id = did, verificationMethod = emptyList())
                )
            }
        }
        val credentialService: CredentialService = credentialService(didResolver = didResolver)

        // Step 2: Create registry and register protocol
        val registry = ExchangeProtocolRegistries.default()
        val didCommService = DidCommFactory.createInMemoryService(kms) { didStr ->
            val did = Did(didStr)
            DidDocument(id = did, verificationMethod = emptyList())
        }
        registry.register(DidCommExchangeProtocol(didCommService))

        // Step 3: Create ExchangeService
        val exchangeService = ExchangeServices.createExchangeService(
            protocolRegistry = registry,
            credentialService = credentialService,
            didResolver = didResolver
        )

        println("✅ Protocol registered: ${registry.getSupportedProtocols()}")

        // Step 4: Create credential offer
        val issuerDid = Did("did:key:issuer")
        val holderDid = Did("did:key:holder")

        val offerResult = exchangeService.offer(
            ExchangeRequest.Offer(
                protocolName = "didcomm".requireExchangeProtocolName(),
                issuerDid = issuerDid,
                holderDid = holderDid,
                credentialPreview = CredentialPreview(
                    attributes = listOf(
                        CredentialAttribute("name", "Alice"),
                        CredentialAttribute("email", "alice@example.com")
                    )
                ),
                options = ExchangeOptions.builder()
                    .addMetadata("fromKeyId", "$issuerDid#key-1")
                    .addMetadata("toKeyId", "$holderDid#key-1")
                    .build()
            )
        )

        val offer = when (offerResult) {
            is ExchangeResult.Success -> offerResult.value
            is ExchangeResult.Failure.ProtocolNotSupported -> {
                println("❌ Protocol not supported: ${offerResult.protocolName}")
                return@runBlocking
            }
            is ExchangeResult.Failure.OperationNotSupported -> {
                println("❌ Operation not supported: ${offerResult.operation}")
                return@runBlocking
            }
            else -> {
                println("❌ Offer failed: ${offerResult}")
                return@runBlocking
            }
        }

        println("✅ Offer created:")
        println("   Offer ID: ${offer.offerId}")
        println("   Protocol: ${offer.protocolName}")

        // Step 5: Request credential
        val requestResult = exchangeService.request(
            ExchangeRequest.Request(
                protocolName = "didcomm".requireExchangeProtocolName(),
                holderDid = holderDid,
                issuerDid = issuerDid,
                offerId = offer.offerId,
                options = ExchangeOptions.builder()
                    .addMetadata("fromKeyId", "$holderDid#key-1")
                    .addMetadata("toKeyId", "$issuerDid#key-1")
                    .build()
            )
        )

        val request = when (requestResult) {
            is ExchangeResult.Success -> requestResult.value
            is ExchangeResult.Failure.ProtocolNotSupported -> {
                println("❌ Protocol not supported: ${requestResult.protocolName}")
                return@runBlocking
            }
            is ExchangeResult.Failure.OperationNotSupported -> {
                println("❌ Operation not supported: ${requestResult.operation}")
                return@runBlocking
            }
            else -> {
                println("❌ Request failed: ${requestResult}")
                return@runBlocking
            }
        }

        println("✅ Request created:")
        println("   Request ID: ${request.requestId}")
        println("   Protocol: ${request.protocolName}")

        // Step 6: Issue credential
        val credential = VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = holderDid,
                claims = mapOf(
                    "name" to JsonPrimitive("Alice"),
                    "email" to JsonPrimitive("alice@example.com")
                )
            )
        )

        val issueResult = exchangeService.issue(
            ExchangeRequest.Issue(
                protocolName = "didcomm".requireExchangeProtocolName(),
                issuerDid = issuerDid,
                holderDid = holderDid,
                credential = credential,
                requestId = request.requestId,
                options = ExchangeOptions.builder()
                    .addMetadata("fromKeyId", "$issuerDid#key-1")
                    .addMetadata("toKeyId", "$holderDid#key-1")
                    .build()
            )
        )

        val issue = when (issueResult) {
            is ExchangeResult.Success -> issueResult.value
            is ExchangeResult.Failure.ProtocolNotSupported -> {
                println("❌ Protocol not supported: ${issueResult.protocolName}")
                return@runBlocking
            }
            is ExchangeResult.Failure.OperationNotSupported -> {
                println("❌ Operation not supported: ${issueResult.operation}")
                return@runBlocking
            }
            else -> {
                println("❌ Issue failed: ${issueResult}")
                return@runBlocking
            }
        }

        println("✅ Credential issued:")
        println("   Issue ID: ${issue.issueId}")
        println("   Credential ID: ${issue.credential.id}")
        println("   Protocol: ${issue.protocolName}")

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
val registry = ExchangeProtocolRegistries.default()

// Create and register DIDComm protocol
val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
registry.register(DidCommExchangeProtocol(didCommService))

// Verify registration
println("Registered protocols: ${registry.getSupportedProtocols()}")
// Output: Registered protocols: [ExchangeProtocolName("didcomm")]
```

### Step 4: Create Credential Offer

An offer is a message from the issuer to the holder proposing a credential.

```kotlin
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.identifiers.*
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.json.JsonPrimitive

val issuerDid = Did("did:key:issuer")
val holderDid = Did("did:key:holder")

val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credentialPreview = CredentialPreview(
            attributes = listOf(
                CredentialAttribute("name", "Alice"),
                CredentialAttribute("email", "alice@example.com")
            )
        ),
        options = ExchangeOptions.builder()
            .addMetadata("fromKeyId", "$issuerDid#key-1")  // Required for DIDComm
            .addMetadata("toKeyId", "$holderDid#key-1")    // Required for DIDComm
            .build()
    )
)

val offer = when (offerResult) {
    is ExchangeResult.Success -> {
        offerResult.value
    }
    else -> {
        throw IllegalStateException("Offer failed: $offerResult")
    }
}

println("Offer ID: ${offer.offerId}")
```

**What this does:**
- Creates a credential offer using the DIDComm protocol
- Returns an `ExchangeResult` that must be handled
- On success, provides an `offerId` that can be used to reference this offer
- The offer contains a preview of the credential attributes

### Step 5: Request Credential

After receiving an offer, the holder requests the credential.

```kotlin
val requestResult = exchangeService.request(
    ExchangeRequest.Request(
        protocolName = "didcomm".requireExchangeProtocolName(),
        holderDid = holderDid,
        issuerDid = issuerDid,
        offerId = offer.offerId,  // Reference to the offer
        options = ExchangeOptions.builder()
            .addMetadata("fromKeyId", "$holderDid#key-1")
            .addMetadata("toKeyId", "$issuerDid#key-1")
            .build()
    )
)

val request = when (requestResult) {
    is ExchangeResult.Success -> {
        requestResult.value
    }
    else -> {
        throw IllegalStateException("Request failed: $requestResult")
    }
}

println("Request ID: ${request.requestId}")
```

**What this does:**
- Creates a credential request referencing the offer
- Returns an `ExchangeResult` that must be handled
- On success, provides a `requestId` that can be used to reference this request
- The request indicates the holder wants to receive the credential

### Step 6: Issue Credential

After receiving a request, the issuer issues the credential.

```kotlin
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.CredentialType
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive

val credential = VerifiableCredential(
    type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
    issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
    issuanceDate = Clock.System.now(),
    credentialSubject = CredentialSubject(
        id = holderDid,
        claims = mapOf(
            "name" to JsonPrimitive("Alice"),
            "email" to JsonPrimitive("alice@example.com")
        )
    )
)

val issueResult = exchangeService.issue(
    ExchangeRequest.Issue(
        protocolName = "didcomm".requireExchangeProtocolName(),
        issuerDid = issuerDid,
        holderDid = holderDid,
        credential = credential,
        requestId = request.requestId,  // Reference to the request
        options = ExchangeOptions.builder()
            .addMetadata("fromKeyId", "$issuerDid#key-1")
            .addMetadata("toKeyId", "$holderDid#key-1")
            .build()
    )
)

val issue = when (issueResult) {
    is ExchangeResult.Success -> {
        issueResult.value
    }
    else -> {
        throw IllegalStateException("Issue failed: $issueResult")
    }
}

println("Credential ID: ${issue.credential.id}")
```

**What this does:**
- Issues a verifiable credential to the holder
- Returns an `ExchangeResult` that must be handled
- On success, provides the issued credential with proof
- The credential can now be stored, verified, and presented

---

## Protocol-Specific Options

Each protocol requires different options. Here's what you need for each:

### DIDComm Options

```kotlin
import org.trustweave.credential.exchange.options.ExchangeOptions
import kotlinx.serialization.json.JsonPrimitive

val options = ExchangeOptions.builder()
    .addMetadata("fromKeyId", "did:key:issuer#key-1")  // Required: Sender's key ID
    .addMetadata("toKeyId", "did:key:holder#key-1")     // Required: Recipient's key ID
    .addMetadata("encrypt", true)                       // Optional: Encrypt message (default: true)
    .threadId("thread-id")                              // Optional: Thread ID for message threading
    .build()
```

### OIDC4VCI Options

```kotlin
val options = ExchangeOptions.builder()
    .addMetadata("credentialIssuer", "https://issuer.example.com")  // Required: OIDC issuer URL
    .addMetadata("credentialTypes", JsonPrimitive("VerifiableCredential")) // Optional: Credential types
    .addMetadata("redirectUri", "https://holder.example.com/callback") // Optional: Redirect URI
    .build()
```

### CHAPI Options

```kotlin
val options = ExchangeOptions.Empty  // CHAPI typically doesn't require additional options
// Messages are generated for browser use
```

---

## Error Handling

All `ExchangeService` methods return `ExchangeResult` sealed classes. Always handle the result:

```kotlin
val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(...)
)

when (offerResult) {
    is ExchangeResult.Success -> {
        val offer = offerResult.value
        // Handle success
    }
    is ExchangeResult.Failure.ProtocolNotSupported -> {
        println("❌ Protocol not supported: ${offerResult.protocolName}")
        println("   Available: ${offerResult.availableProtocols}")
    }
    is ExchangeResult.Failure.OperationNotSupported -> {
        println("❌ Operation not supported: ${offerResult.operation}")
        println("   Supported: ${offerResult.supportedOperations}")
    }
    is ExchangeResult.Failure.InvalidRequest -> {
        println("❌ Invalid request: ${offerResult.reason}")
        println("   Field: ${offerResult.field}")
    }
    is ExchangeResult.Failure.NetworkError -> {
        println("❌ Network error: ${offerResult.reason}")
    }
    else -> {
        println("❌ Exchange error: $offerResult")
    }
}
```

**Common Error Types:**
- `ExchangeResult.Failure.ProtocolNotSupported`: Protocol not registered
- `ExchangeResult.Failure.OperationNotSupported`: Protocol doesn't support the operation
- `ExchangeResult.Failure.InvalidRequest`: Invalid request field
- `ExchangeResult.Failure.NetworkError`: Network-related errors
- `ExchangeResult.Failure.MessageNotFound`: Message reference not found
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

