---
title: Credential Exchange Protocols - Examples
---

# Credential Exchange Protocols - Examples

Complete code examples for credential exchange protocols.

## Table of Contents

1. [Basic Examples](#basic-examples)
2. [Complete Workflows](#complete-workflows)
3. [Error Handling Examples](#error-handling-examples)
4. [Protocol Switching Examples](#protocol-switching-examples)
5. [Advanced Examples](#advanced-examples)

---

## Basic Examples

### Example 1: Simple Credential Offer

```kotlin
import com.trustweave.credential.exchange.*
import com.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import com.trustweave.credential.didcomm.DidCommFactory
import com.trustweave.kms.KeyManagementService
import com.trustweave.testkit.InMemoryKeyManagementService
import com.trustweave.did.DidDocument
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Setup
    val kms: KeyManagementService = InMemoryKeyManagementService()
    val resolveDid: suspend (String) -> DidDocument? = { did ->
        DidDocument(id = did, verificationMethod = emptyList())
    }
    
    val registry = CredentialExchangeProtocolRegistry()
    val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
    registry.register(DidCommExchangeProtocol(didCommService))
    
    // Create offer
    val offer = registry.offerCredential(
        protocolName = "didcomm",
        request = CredentialOfferRequest(
            issuerDid = "did:key:issuer",
            holderDid = "did:key:holder",
            credentialPreview = CredentialPreview(
                attributes = listOf(
                    CredentialAttribute("name", "Alice")
                )
            ),
            options = mapOf(
                "fromKeyId" to "did:key:issuer#key-1",
                "toKeyId" to "did:key:holder#key-1"
            )
        )
    )
    
    println("Offer ID: ${offer.offerId}")
}
```

---

### Example 2: Complete Credential Exchange

```kotlin
import com.trustweave.credential.exchange.*
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    // Setup (same as Example 1)
    val registry = setupRegistry()
    
    val issuerDid = "did:key:issuer"
    val holderDid = "did:key:holder"
    
    // Step 1: Offer
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
    
    // Step 2: Request
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
    
    // Step 3: Issue
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
    
    println("✅ Credential issued: ${issue.credential.id}")
}
```

---

## Complete Workflows

### Example 3: Proof Request and Presentation

```kotlin
fun proofWorkflow() = runBlocking {
    val registry = setupRegistry()
    
    val verifierDid = "did:key:verifier"
    val proverDid = "did:key:prover"
    
    // Step 1: Request proof
    val proofRequest = registry.requestProof(
        protocolName = "didcomm",
        request = ProofRequestRequest(
            verifierDid = verifierDid,
            proverDid = proverDid,
            name = "Age Verification",
            requestedAttributes = mapOf(
                "name" to RequestedAttribute(
                    name = "name",
                    restrictions = listOf(
                        AttributeRestriction(issuerDid = "did:key:issuer")
                    )
                )
            ),
            requestedPredicates = mapOf(
                "age_verification" to RequestedPredicate(
                    name = "age",
                    pType = ">=",
                    pValue = 18
                )
            ),
            options = mapOf(
                "fromKeyId" to "$verifierDid#key-1",
                "toKeyId" to "$proverDid#key-1"
            )
        )
    )
    
    // Step 2: Create presentation (prover side)
    val presentation = createPresentation(proofRequest)
    
    // Step 3: Present proof
    val presentationResponse = registry.presentProof(
        protocolName = "didcomm",
        request = ProofPresentationRequest(
            proverDid = proverDid,
            verifierDid = verifierDid,
            presentation = presentation,
            requestId = proofRequest.requestId,
            options = mapOf(
                "fromKeyId" to "$proverDid#key-1",
                "toKeyId" to "$verifierDid#key-1"
            )
        )
    )
    
    println("✅ Proof presented: ${presentationResponse.presentationId}")
}
```

---

## Error Handling Examples

### Example 4: Error Handling with Fallback

```kotlin
suspend fun offerWithErrorHandling(
    registry: CredentialExchangeProtocolRegistry,
    request: CredentialOfferRequest
): CredentialOfferResponse? {
    return try {
        // Try preferred protocol
        registry.offerCredential("didcomm", request)
    } catch (e: IllegalArgumentException) {
        when {
            e.message?.contains("not registered") == true -> {
                println("Protocol not registered. Trying fallback...")
                // Try fallback
                try {
                    registry.offerCredential("oidc4vci", request)
                } catch (e2: Exception) {
                    println("Fallback failed: ${e2.message}")
                    null
                }
            }
            e.message?.contains("Missing required option") == true -> {
                println("Missing required option: ${e.message}")
                null
            }
            else -> {
                println("Invalid argument: ${e.message}")
                null
            }
        }
    } catch (e: UnsupportedOperationException) {
        println("Operation not supported: ${e.message}")
        null
    } catch (e: Exception) {
        println("Unexpected error: ${e.message}")
        null
    }
}
```

---

### Example 5: Validation Before Operation

```kotlin
fun validateAndOffer(
    registry: CredentialExchangeProtocolRegistry,
    request: CredentialOfferRequest
): Result<CredentialOfferResponse> {
    // Validate DIDs
    if (!isValidDid(request.issuerDid)) {
        return Result.failure(IllegalArgumentException("Invalid issuer DID"))
    }
    if (!isValidDid(request.holderDid)) {
        return Result.failure(IllegalArgumentException("Invalid holder DID"))
    }
    
    // Validate preview
    if (request.credentialPreview.attributes.isEmpty()) {
        return Result.failure(IllegalArgumentException("Preview must have attributes"))
    }
    
    // Validate protocol options
    if (!registry.isRegistered("didcomm")) {
        return Result.failure(IllegalStateException("Protocol not registered"))
    }
    
    // All valid, proceed
    return runBlocking {
        try {
            Result.success(registry.offerCredential("didcomm", request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun isValidDid(did: String): Boolean {
    return did.matches(Regex("^did:[a-z0-9]+:.+$"))
}
```

---

## Protocol Switching Examples

### Example 6: Switch Protocol Based on Context

```kotlin
suspend fun offerWithProtocolSelection(
    registry: CredentialExchangeProtocolRegistry,
    request: CredentialOfferRequest,
    context: ExchangeContext
): CredentialOfferResponse {
    val protocol = when {
        context.needsEncryption -> "didcomm"
        context.isWebBased -> "oidc4vci"
        context.isBrowser -> "chapi"
        else -> "didcomm"  // Default
    }
    
    return registry.offerCredential(protocol, request)
}

data class ExchangeContext(
    val needsEncryption: Boolean = false,
    val isWebBased: Boolean = false,
    val isBrowser: Boolean = false
)
```

---

## Advanced Examples

### Example 7: Multiple Protocols

```kotlin
fun multipleProtocolsExample() = runBlocking {
    val registry = CredentialExchangeProtocolRegistry()
    
    // Register multiple protocols
    val kms = InMemoryKeyManagementService()
    val resolveDid: suspend (String) -> DidDocument? = { did ->
        DidDocument(id = did, verificationMethod = emptyList())
    }
    
    // DIDComm
    val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
    registry.register(DidCommExchangeProtocol(didCommService))
    
    // OIDC4VCI
    val oidc4vciService = Oidc4VciService(
        credentialIssuerUrl = "https://issuer.example.com",
        kms = kms
    )
    registry.register(Oidc4VciExchangeProtocol(oidc4vciService))
    
    // CHAPI
    val chapiService = ChapiService()
    registry.register(ChapiExchangeProtocol(chapiService))
    
    println("Registered protocols: ${registry.getAllProtocolNames()}")
    // Output: [didcomm, oidc4vci, chapi]
    
    // Use any protocol
    val didCommOffer = registry.offerCredential("didcomm", request)
    val oidcOffer = registry.offerCredential("oidc4vci", request)
    val chapiOffer = registry.offerCredential("chapi", request)
}
```

---

### Example 8: Protocol Fallback Chain

```kotlin
suspend fun offerWithFallbackChain(
    registry: CredentialExchangeProtocolRegistry,
    request: CredentialOfferRequest
): CredentialOfferResponse {
    val protocols = listOf("didcomm", "oidc4vci", "chapi")
    
    for (protocol in protocols) {
        if (registry.isRegistered(protocol)) {
            try {
                return registry.offerCredential(protocol, request)
            } catch (e: Exception) {
                println("Protocol $protocol failed: ${e.message}")
                continue
            }
        }
    }
    
    throw IllegalStateException("All protocols failed")
}
```

---

## Helper Functions

### Setup Registry

```kotlin
fun setupRegistry(): CredentialExchangeProtocolRegistry {
    val kms: KeyManagementService = InMemoryKeyManagementService()
    val resolveDid: suspend (String) -> DidDocument? = { did ->
        DidDocument(id = did, verificationMethod = emptyList())
    }
    
    val registry = CredentialExchangeProtocolRegistry()
    val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
    registry.register(DidCommExchangeProtocol(didCommService))
    
    return registry
}
```

---

## Related Documentation

- **[Quick Start](./QUICK_START.md)** - Get started quickly (5 minutes)
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Workflows](./WORKFLOWS.md)** - Step-by-step workflows
- **[Error Handling](./ERROR_HANDLING.md)** - Error handling guide
- **[Troubleshooting](./TROUBLESHOOTING.md)** - Common issues and solutions
- **[Best Practices](./BEST_PRACTICES.md)** - Best practices and patterns
- **[Glossary](./GLOSSARY.md)** - Terms and concepts

