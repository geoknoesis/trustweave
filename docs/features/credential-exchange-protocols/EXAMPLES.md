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
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import org.trustweave.credential.didcomm.DidCommFactory
import org.trustweave.credential.CredentialService
import org.trustweave.credential.credentialService
import org.trustweave.credential.identifiers.*
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.model.DidDocument
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Setup
    val kms: KeyManagementService = InMemoryKeyManagementService()
    val didResolver: DidResolver = object : DidResolver {
        override suspend fun resolve(did: Did): DidResolutionResult {
            return DidResolutionResult.Success(
                DidDocument(id = did, verificationMethod = emptyList())
            )
        }
    }
    val credentialService: CredentialService = credentialService(didResolver = didResolver)

    val registry = ExchangeProtocolRegistries.default()
    val didCommService = DidCommFactory.createInMemoryService(kms) { didStr ->
        val did = Did(didStr)
        DidDocument(id = did, verificationMethod = emptyList())
    }
    registry.register(DidCommExchangeProtocol(didCommService))

    val exchangeService = ExchangeServices.createExchangeService(
        protocolRegistry = registry,
        credentialService = credentialService,
        didResolver = didResolver
    )

    // Create offer
    val issuerDid = Did("did:key:issuer")
    val holderDid = Did("did:key:holder")

    val offerResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = "didcomm".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = CredentialPreview(
                attributes = listOf(
                    CredentialAttribute("name", "Alice")
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
        else -> throw IllegalStateException("Offer failed: $offerResult")
    }

    println("Offer ID: ${offer.offerId}")
}
```

---

### Example 2: Complete Credential Exchange

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.CredentialService
import org.trustweave.credential.credentialService
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.identifiers.*
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.Clock

fun main() = runBlocking {
    // Setup (same as Example 1)
    val exchangeService = setupExchangeService()

    val issuerDid = Did("did:key:issuer")
    val holderDid = Did("did:key:holder")

    // Step 1: Offer
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
        else -> throw IllegalStateException("Offer failed: $offerResult")
    }

    // Step 2: Request
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
        else -> throw IllegalStateException("Request failed: $requestResult")
    }

    // Step 3: Issue
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
        else -> throw IllegalStateException("Issue failed: $issueResult")
    }

    println("✅ Credential issued: ${issue.credential.id}")
}
```

---

## Complete Workflows

### Example 3: Proof Request and Presentation

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.exchange.request.AttributeRequest
import org.trustweave.credential.exchange.request.AttributeRestriction
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.identifiers.*
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking

fun proofWorkflow() = runBlocking {
    val exchangeService = setupExchangeService()

    val verifierDid = Did("did:key:verifier")
    val proverDid = Did("did:key:prover")

    // Step 1: Request proof
    val proofRequestResult = exchangeService.requestProof(
        ProofExchangeRequest.Request(
            protocolName = "didcomm".requireExchangeProtocolName(),
            verifierDid = verifierDid,
            proverDid = proverDid,
            proofRequest = ProofRequest(
                name = "Age Verification",
                requestedAttributes = mapOf(
                    "name" to AttributeRequest(
                        name = "name",
                        restrictions = listOf(
                            AttributeRestriction(issuerDid = Did("did:key:issuer"))
                        )
                    )
                ),
                options = ExchangeOptions.builder()
                    .addMetadata("requestedPredicates", mapOf(
                        "age_verification" to mapOf(
                            "name" to "age",
                            "pType" to ">=",
                            "pValue" to 18
                        )
                    ))
                    .build()
            ),
            options = ExchangeOptions.builder()
                .addMetadata("fromKeyId", "$verifierDid#key-1")
                .addMetadata("toKeyId", "$proverDid#key-1")
                .build()
        )
    )

    val proofRequest = when (proofRequestResult) {
        is ExchangeResult.Success -> proofRequestResult.value
        else -> throw IllegalStateException("Proof request failed: $proofRequestResult")
    }

    // Step 2: Create presentation (prover side)
    val presentation = createPresentation(proofRequest)

    // Step 3: Present proof
    val presentationResult = exchangeService.presentProof(
        ProofExchangeRequest.Presentation(
            protocolName = "didcomm".requireExchangeProtocolName(),
            proverDid = proverDid,
            verifierDid = verifierDid,
            presentation = presentation,
            requestId = proofRequest.requestId,
            options = ExchangeOptions.builder()
                .addMetadata("fromKeyId", "$proverDid#key-1")
                .addMetadata("toKeyId", "$verifierDid#key-1")
                .build()
        )
    )

    val presentationResponse = when (presentationResult) {
        is ExchangeResult.Success -> presentationResult.value
        else -> throw IllegalStateException("Presentation failed: $presentationResult")
    }

    println("✅ Proof presented: ${presentationResponse.presentationId}")
}
```

---

## Error Handling Examples

### Example 4: Error Handling with Fallback

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.*

suspend fun offerWithErrorHandling(
    exchangeService: ExchangeService,
    issuerDid: Did,
    holderDid: Did,
    preview: CredentialPreview,
    options: ExchangeOptions
): ExchangeResponse.Offer? {
    // Try preferred protocol
    val preferredResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = "didcomm".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = options
        )
    )
    
    when (preferredResult) {
        is ExchangeResult.Success -> return preferredResult.value
        is ExchangeResult.Failure.ProtocolNotSupported -> {
            println("Protocol not registered. Trying fallback...")
            // Try fallback
            val fallbackResult = exchangeService.offer(
                ExchangeRequest.Offer(
                    protocolName = "oidc4vci".requireExchangeProtocolName(),
                    issuerDid = issuerDid,
                    holderDid = holderDid,
                    credentialPreview = preview,
                    options = options
                )
            )
            when (fallbackResult) {
                is ExchangeResult.Success -> return fallbackResult.value
                else -> {
                    println("Fallback failed: $fallbackResult")
                    return null
                }
            }
        }
        is ExchangeResult.Failure.InvalidRequest -> {
            println("Invalid request: ${preferredResult.reason}")
            return null
        }
        is ExchangeResult.Failure.OperationNotSupported -> {
            println("Operation not supported: ${preferredResult.reason}")
            return null
        }
        else -> {
            println("Unexpected error: $preferredResult")
            return null
        }
    }
}
```

---

### Example 5: Validation Before Operation

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.identifiers.*
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking

fun validateAndOffer(
    exchangeService: ExchangeService,
    registry: ExchangeProtocolRegistry,
    issuerDid: Did,
    holderDid: Did,
    preview: CredentialPreview,
    options: ExchangeOptions
): Result<ExchangeResponse.Offer> {
    // Validate DIDs (Did type provides basic validation)
    if (issuerDid.value.isBlank()) {
        return Result.failure(IllegalArgumentException("Invalid issuer DID"))
    }
    if (holderDid.value.isBlank()) {
        return Result.failure(IllegalArgumentException("Invalid holder DID"))
    }

    // Validate preview
    if (preview.attributes.isEmpty()) {
        return Result.failure(IllegalArgumentException("Preview must have attributes"))
    }

    // Validate protocol options
    val protocolName = "didcomm".requireExchangeProtocolName()
    if (!registry.isRegistered(protocolName)) {
        return Result.failure(IllegalStateException("Protocol not registered"))
    }

    // All valid, proceed
    return runBlocking {
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
            is ExchangeResult.Success -> Result.success(result.value)
            else -> Result.failure(IllegalStateException("Offer failed: $result"))
        }
    }
}
```

---

## Protocol Switching Examples

### Example 6: Switch Protocol Based on Context

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.identifiers.*
import org.trustweave.did.identifiers.Did

suspend fun offerWithProtocolSelection(
    exchangeService: ExchangeService,
    issuerDid: Did,
    holderDid: Did,
    preview: CredentialPreview,
    options: ExchangeOptions,
    context: ExchangeContext
): ExchangeResponse.Offer {
    val protocolName = when {
        context.needsEncryption -> "didcomm".requireExchangeProtocolName()
        context.isWebBased -> "oidc4vci".requireExchangeProtocolName()
        context.isBrowser -> "chapi".requireExchangeProtocolName()
        else -> "didcomm".requireExchangeProtocolName()  // Default
    }

    val result = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = protocolName,
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = options
        )
    )

    return when (result) {
        is ExchangeResult.Success -> result.value
        else -> throw IllegalStateException("Offer failed: $result")
    }
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
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import kotlinx.coroutines.runBlocking

fun multipleProtocolsExample() = runBlocking {
    val registry = ExchangeProtocolRegistries.default()

    // Register multiple protocols
    val kms = InMemoryKeyManagementService()
    val didResolver: DidResolver = object : DidResolver {
        override suspend fun resolve(did: Did): DidResolutionResult {
            return DidResolutionResult.Success(
                DidDocument(id = did, verificationMethod = emptyList())
            )
        }
    }
    val credentialService = credentialService(didResolver = didResolver)

    // DIDComm
    val didCommService = DidCommFactory.createInMemoryService(kms) { didStr ->
        DidDocument(id = Did(didStr), verificationMethod = emptyList())
    }
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

    val exchangeService = ExchangeServices.createExchangeService(
        protocolRegistry = registry,
        credentialService = credentialService,
        didResolver = didResolver
    )

    println("Registered protocols: ${registry.getSupportedProtocols()}")
    // Output: [ExchangeProtocolName("didcomm"), ExchangeProtocolName("oidc4vci"), ExchangeProtocolName("chapi")]

    // Use any protocol
    val issuerDid = Did("did:key:issuer")
    val holderDid = Did("did:key:holder")
    val preview = CredentialPreview(attributes = listOf(CredentialAttribute("name", "Alice")))
    val options = ExchangeOptions.builder().build()

    val didCommResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = "didcomm".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = options
        )
    )
    val didCommOffer = when (didCommResult) {
        is ExchangeResult.Success -> didCommResult.value
        else -> throw IllegalStateException("DIDComm offer failed: $didCommResult")
    }

    val oidcResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = "oidc4vci".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = options
        )
    )
    val oidcOffer = when (oidcResult) {
        is ExchangeResult.Success -> oidcResult.value
        else -> throw IllegalStateException("OIDC4VCI offer failed: $oidcResult")
    }

    val chapiResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = "chapi".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = options
        )
    )
    val chapiOffer = when (chapiResult) {
        is ExchangeResult.Success -> chapiResult.value
        else -> throw IllegalStateException("CHAPI offer failed: $chapiResult")
    }
}
```

---

### Example 8: Protocol Fallback Chain

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.identifiers.*
import org.trustweave.did.identifiers.Did

suspend fun offerWithFallbackChain(
    exchangeService: ExchangeService,
    registry: ExchangeProtocolRegistry,
    issuerDid: Did,
    holderDid: Did,
    preview: CredentialPreview,
    options: ExchangeOptions
): ExchangeResponse.Offer {
    val protocols = listOf("didcomm", "oidc4vci", "chapi")

    for (protocolStr in protocols) {
        val protocolName = protocolStr.requireExchangeProtocolName()
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
                    println("Protocol $protocolStr failed: $result")
                    continue
                }
            }
        }
    }

    throw IllegalStateException("All protocols failed")
}
```

---

## Helper Functions

### Setup ExchangeService

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.CredentialService
import org.trustweave.credential.credentialService
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.model.DidDocument
import org.trustweave.did.identifiers.Did

fun setupExchangeService(): ExchangeService {
    val kms: KeyManagementService = InMemoryKeyManagementService()
    val didResolver: DidResolver = object : DidResolver {
        override suspend fun resolve(did: Did): DidResolutionResult {
            return DidResolutionResult.Success(
                DidDocument(id = did, verificationMethod = emptyList())
            )
        }
    }
    val credentialService: CredentialService = credentialService(didResolver = didResolver)

    val registry = ExchangeProtocolRegistries.default()
    val didCommService = DidCommFactory.createInMemoryService(kms) { didStr ->
        DidDocument(id = Did(didStr), verificationMethod = emptyList())
    }
    registry.register(DidCommExchangeProtocol(didCommService))

    return ExchangeServices.createExchangeService(
        protocolRegistry = registry,
        credentialService = credentialService,
        didResolver = didResolver
    )
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

