# Common Patterns

> **Version:** 1.0.0-SNAPSHOT  
> Learn common usage patterns and best practices for VeriCore.

## Table of Contents

1. [Issuer → Holder → Verifier Workflow](#issuer--holder--verifier-workflow)
2. [Batch Operations](#batch-operations)
3. [Error Recovery with Fallbacks](#error-recovery-with-fallbacks)
4. [Credential Lifecycle Management](#credential-lifecycle-management)
5. [Multi-Chain Anchoring](#multi-chain-anchoring)
6. [Wallet Organization Patterns](#wallet-organization-patterns)

---

## Issuer → Holder → Verifier Workflow

Complete workflow showing all three parties in a credential ecosystem.

```kotlin
package com.example.patterns.workflow

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    // ============================================
    // 1. ISSUER: Create DID and issue credential
    // ============================================
    val issuerDidDoc = vericore.createDid().getOrThrow()
    val issuerDid = issuerDidDoc.id
    val issuerKeyId = issuerDidDoc.verificationMethod.firstOrNull()?.id
        ?: error("No verification method found")
    
    val credential = vericore.issueCredential(
        issuerDid = issuerDid,
        issuerKeyId = issuerKeyId,
        credentialSubject = buildJsonObject {
            put("id", "did:key:holder-123")
            put("name", "Alice")
            put("degree", "Bachelor of Science")
        },
        types = listOf("VerifiableCredential", "DegreeCredential")
    ).getOrThrow()
    
    println("✅ Issuer created credential: ${credential.id}")
    
    // ============================================
    // 2. HOLDER: Store credential in wallet
    // ============================================
    val holderDidDoc = vericore.createDid().getOrThrow()
    val holderDid = holderDidDoc.id
    
    val wallet = vericore.createWallet(holderDid).getOrThrow()
    val credentialId = wallet.store(credential)
    
    println("✅ Holder stored credential: $credentialId")
    
    // ============================================
    // 3. VERIFIER: Verify credential
    // ============================================
    val verification = vericore.verifyCredential(credential).getOrThrow()
    
    if (verification.valid) {
        println("✅ Verifier confirmed credential is valid")
        println("   Proof valid: ${verification.proofValid}")
        println("   Issuer valid: ${verification.issuerValid}")
    } else {
        println("❌ Verifier rejected credential")
        println("   Errors: ${verification.errors}")
    }
}
```

**Key Points:**
- Each party has their own DID
- Issuer signs the credential with their key
- Holder stores the credential in their wallet
- Verifier checks the credential without contacting issuer

---

## Batch Operations

Process multiple DIDs or credentials efficiently using coroutines.

```kotlin
package com.example.patterns.batch

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    // Batch DID resolution
    val dids = listOf(
        "did:key:z6Mk...",
        "did:key:z6Mk...",
        "did:key:z6Mk..."
    )
    
    val results = dids.mapAsync { did ->
        vericore.resolveDid(did)
    }
    
    results.forEachIndexed { index, result ->
        result.fold(
            onSuccess = { resolution ->
                println("✅ DID ${index + 1} resolved: ${resolution.document?.id}")
            },
            onFailure = { error ->
                println("❌ DID ${index + 1} failed: ${error.message}")
            }
        )
    }
    
    // Batch credential creation
    val credentials = (1..10).mapAsync { index ->
        val issuerDid = vericore.createDid().getOrThrow()
        vericore.issueCredential(
            issuerDid = issuerDid.id,
            issuerKeyId = issuerDid.verificationMethod.first().id,
            credentialSubject = buildJsonObject {
                put("id", "did:key:holder-$index")
                put("index", index)
            }
        )
    }
    
    val successful = credentials.filter { it.isSuccess }
    println("Created ${successful.size} credentials out of ${credentials.size}")
}
```

**Key Points:**
- Use `mapAsync` for parallel operations
- Handle each result individually
- Filter successful results for further processing

---

## Error Recovery with Fallbacks

Handle errors gracefully with fallback strategies.

```kotlin
package com.example.patterns.recovery

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    // Pattern: Try multiple DID methods with fallback
    fun createDidWithFallback(methods: List<String>): DidDocument? {
        for (method in methods) {
            val result = vericore.createDid(method)
            result.fold(
                onSuccess = { return it },
                onFailure = { error ->
                    when (error) {
                        is VeriCoreError.DidMethodNotRegistered -> {
                            println("Method '$method' not available, trying next...")
                            // Continue to next method
                        }
                        else -> {
                            println("Unexpected error with method '$method': ${error.message}")
                            // Try next method anyway
                        }
                    }
                }
            )
        }
        return null
    }
    
    val did = createDidWithFallback(listOf("web", "ion", "key"))
        ?: error("All DID methods failed")
    
    println("✅ Created DID: ${did.id}")
    
    // Pattern: Retry with exponential backoff
    suspend fun resolveDidWithRetry(did: String, maxRetries: Int = 3): DidResolutionResult? {
        var lastError: Throwable? = null
        
        for (attempt in 1..maxRetries) {
            val result = vericore.resolveDid(did)
            result.fold(
                onSuccess = { return it },
                onFailure = { error ->
                    lastError = error
                    if (attempt < maxRetries) {
                        val delay = (attempt * attempt * 100).toLong() // Exponential backoff
                        kotlinx.coroutines.delay(delay)
                        println("Retry $attempt/$maxRetries after ${delay}ms...")
                    }
                }
            )
        }
        
        println("Failed after $maxRetries attempts: ${lastError?.message}")
        return null
    }
}
```

**Key Points:**
- Try multiple methods/strategies in order
- Use exponential backoff for retries
- Always have a fallback plan
- Log attempts for debugging

---

## Credential Lifecycle Management

Manage credentials through their entire lifecycle: issuance, storage, presentation, verification, and expiration.

```kotlin
package com.example.patterns.lifecycle

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.credential.PresentationOptions
import com.geoknoesis.vericore.spi.services.WalletCreationOptionsBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    // Create issuer and holder
    val issuerDid = vericore.createDid().getOrThrow()
    val holderDid = vericore.createDid().getOrThrow()
    
    // Issue credential with expiration
    val expirationDate = Instant.now().plus(1, ChronoUnit.YEARS).toString()
    val credential = vericore.issueCredential(
        issuerDid = issuerDid.id,
        issuerKeyId = issuerDid.verificationMethod.first().id,
        credentialSubject = buildJsonObject {
            put("id", holderDid.id)
            put("name", "Alice")
        },
        expirationDate = expirationDate
    ).getOrThrow()
    
    // Store in wallet with lifecycle support
    val wallet = vericore.createWallet(
        holderDid = holderDid.id,
        options = WalletCreationOptionsBuilder().apply {
            enableOrganization = true
            enablePresentation = true
        }.build()
    ).getOrThrow()
    
    val credentialId = wallet.store(credential)
    
    // Organize credential
    wallet.withOrganization { org ->
        val collectionId = org.createCollection("Education", "Academic credentials")
        org.addToCollection(credentialId, collectionId)
        org.tagCredential(credentialId, setOf("degree", "verified"))
    }
    
    // Create presentation when needed
    val presentation = wallet.withPresentation { pres ->
        pres.createPresentation(
            credentialIds = listOf(credentialId),
            holderDid = holderDid.id,
            options = PresentationOptions(
                holderDid = holderDid.id,
                challenge = "job-application-${System.currentTimeMillis()}"
            )
        )
    }
    
    // Verify before using
    val verification = vericore.verifyCredential(credential).getOrThrow()
    if (!verification.valid) {
        println("⚠️ Credential invalid: ${verification.errors}")
        return@runBlocking
    }
    
    // Check expiration
    if (credential.expirationDate != null) {
        val expiration = Instant.parse(credential.expirationDate)
        if (expiration.isBefore(Instant.now())) {
            println("⚠️ Credential expired on ${credential.expirationDate}")
            // Archive expired credential
            wallet.withLifecycle { lifecycle ->
                lifecycle.archive(credentialId)
            }
        }
    }
    
    println("✅ Credential lifecycle managed successfully")
}
```

**Key Points:**
- Always set expiration dates for time-sensitive credentials
- Organize credentials for easy retrieval
- Verify credentials before use
- Archive expired credentials
- Use presentations for selective disclosure

---

## Multi-Chain Anchoring

Anchor the same credential to multiple blockchains for redundancy and interoperability.

```kotlin
package com.example.patterns.multichain

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

fun main() = runBlocking {
    val vericore = VeriCore.create {
        blockchain {
            "algorand:testnet" to InMemoryBlockchainAnchorClient("algorand:testnet")
            "polygon:testnet" to InMemoryBlockchainAnchorClient("polygon:testnet")
        }
    }
    
    // Issue credential
    val issuerDid = vericore.createDid().getOrThrow()
    val credential = vericore.issueCredential(
        issuerDid = issuerDid.id,
        issuerKeyId = issuerDid.verificationMethod.first().id,
        credentialSubject = buildJsonObject {
            put("id", "did:key:holder")
            put("data", "important-data")
        }
    ).getOrThrow()
    
    // Anchor to multiple chains
    val chains = listOf("algorand:testnet", "polygon:testnet")
    val anchorResults = chains.mapNotNull { chainId ->
        vericore.anchor(
            data = credential,
            serializer = VerifiableCredential.serializer(),
            chainId = chainId
        ).fold(
            onSuccess = { anchor ->
                println("✅ Anchored to $chainId: ${anchor.ref.txHash}")
                anchor
            },
            onFailure = { error ->
                println("❌ Failed to anchor to $chainId: ${error.message}")
                null
            }
        )
    }
    
    println("Anchored to ${anchorResults.size} out of ${chains.size} chains")
    
    // Store all anchor references
    val anchorRefs = anchorResults.map { it.ref }
    // In production, store these in a database for later verification
}
```

**Key Points:**
- Anchor to multiple chains for redundancy
- Handle failures gracefully (some chains may be unavailable)
- Store all anchor references for verification
- Use different chains for different use cases

---

## Wallet Organization Patterns

Organize credentials efficiently using collections, tags, and metadata.

```kotlin
package com.example.patterns.organization

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.spi.services.WalletCreationOptionsBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() = runBlocking {
    val vericore = VeriCore.create()
    
    val holderDid = vericore.createDid().getOrThrow().id
    val wallet = vericore.createWallet(
        holderDid = holderDid,
        options = WalletCreationOptionsBuilder().apply {
            enableOrganization = true
        }.build()
    ).getOrThrow()
    
    // Issue multiple credentials
    val issuerDid = vericore.createDid().getOrThrow()
    val credentials = listOf(
        "Bachelor of Science" to "education",
        "Professional License" to "professional",
        "Membership Card" to "membership"
    )
    
    val credentialIds = credentials.map { (name, category) ->
        val credential = vericore.issueCredential(
            issuerDid = issuerDid.id,
            issuerKeyId = issuerDid.verificationMethod.first().id,
            credentialSubject = buildJsonObject {
                put("id", holderDid)
                put("credentialName", name)
            },
            types = listOf("VerifiableCredential", "${category}Credential")
        ).getOrThrow()
        
        val credentialId = wallet.store(credential)
        
        // Organize immediately after storage
        wallet.withOrganization { org ->
            // Create collection by category
            val collectionId = org.createCollection(
                name = category.capitalize(),
                description = "$category credentials"
            )
            org.addToCollection(credentialId, collectionId)
            
            // Add tags
            org.tagCredential(credentialId, setOf(category, "verified", "active"))
            
            // Add metadata
            org.addMetadata(credentialId, mapOf(
                "category" to category,
                "storedAt" to System.currentTimeMillis()
            ))
        }
        
        credentialId
    }
    
    // Query by collection
    wallet.withOrganization { org ->
        val educationCollection = org.listCollections()
            .firstOrNull { it.name == "Education" }
        
        if (educationCollection != null) {
            val educationCreds = org.getCredentialsInCollection(educationCollection.id)
            println("Education credentials: ${educationCreds.size}")
        }
    }
    
    // Query by tag
    wallet.withOrganization { org ->
        val verifiedCreds = org.findByTag("verified")
        println("Verified credentials: ${verifiedCreds.size}")
    }
    
    // Get statistics
    val stats = wallet.getStatistics()
    println("Wallet stats:")
    println("  Total: ${stats.totalCredentials}")
    println("  Collections: ${stats.collectionsCount}")
    println("  Tags: ${stats.tagsCount}")
}
```

**Key Points:**
- Organize credentials immediately after storage
- Use collections for broad categories
- Use tags for flexible querying
- Add metadata for custom properties
- Query by collection, tag, or metadata

---

## Related Documentation

- [Quick Start](quick-start.md) - Getting started guide
- [Wallet API Reference](../api-reference/wallet-api.md) - Complete wallet API
- [Error Handling](../advanced/error-handling.md) - Error handling patterns
- [Troubleshooting](troubleshooting.md) - Common issues and solutions

