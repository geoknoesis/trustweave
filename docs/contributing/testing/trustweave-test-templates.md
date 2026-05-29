---
title: TrustWeave Test Templates
parent: Testing
grand_parent: Contributing to TrustWeave
---

# TrustWeave Test Templates

This guide explains how to use the comprehensive in-memory test templates for TrustWeave integration tests.

## Overview

The TrustWeave test templates provide complete, working examples of common TrustWeave workflows using only in-memory components. These templates serve as:

- **Reference implementations** for common use cases
- **Starting points** for creating new integration tests
- **Validation** that workflows work correctly with in-memory components
- **Examples** for adapting to specific configurations (AWS KMS, Ethereum DID, etc.)

## Location

The test templates are located in:
```
trust/src/test/kotlin/org/trustweave/integration/InMemoryTrustWeaveIntegrationTest.kt
```

## Key Pattern: Extract Key ID from DID Document

**CRITICAL:** All templates follow this essential pattern:

1. **Create DID** - This generates a key and stores it in the DID document
2. **Extract Key ID** - Get the key ID from the DID document's verification method
3. **Use Extracted Key** - Use that key ID when issuing credentials

This ensures proof verification succeeds because the DID document contains the correct verification method.

```kotlin
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId

// Step 1: Create DID (generates key and stores in DID document)

val didResult = trustWeave.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}


val issuerDid = didResult.getOrThrowDid()

// Step 2: Extract key ID from DID document
val issuerDidDoc = when (val res = trustWeave.configuration.didRegistry.resolve(issuerDid.value)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve issuer DID")
}

val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.extractKeyId()
    ?: throw IllegalStateException("No verification method in issuer DID")

// Step 3: Use extracted key ID for signing
val credential = trustWeave.issue {
    credential { /* ... */ }
    signedBy(issuerDid) // Key ID automatically resolved from DID
}.getOrThrow()

// For tests, you can use getOrThrowDid() / getOrThrow():
// val issuerDid = trustWeave.createDid { ... }.getOrThrowDid()
// val credential = trustWeave.issue { ... }.getOrThrow()
```

## Available Templates

### 1. Complete In-Memory Workflow Template

**Test:** `test complete in-memory workflow template`

**Demonstrates:**
- Basic credential issuance and verification
- Trust registry configuration
- Complete verification with all checks

**Use Case:** Starting point for most integration tests

```kotlin
import org.trustweave.trust.dsl.credential.TrustProviders.IN_MEMORY
@Test
fun `test complete in-memory workflow template`() = runBlocking {
    val kms = InMemoryKeyManagementService()

    val trustWeave = TrustWeave.build {
        keys {
            custom(kms)
            signer { data, keyId -> kms.sign(keyId, data) }
        }
        did { method(DidMethods.KEY) {} }
        trust { provider(IN_MEMORY) }
    }

    // Create DIDs, extract key IDs, issue credential, verify
    // ... (see template for full implementation)
}
```

### 2. Credential Revocation Workflow Template

**Test:** `test credential revocation workflow template`

**Demonstrates:**
- Issue credential with revocation support
- Revoke credential
- Verify revocation status

**Use Case:** Testing revocation functionality

```kotlin
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.dsl.credential.RevocationProviders.IN_MEMORY
@Test
fun `test credential revocation workflow template`() = runBlocking {
    val trustWeave = TrustWeave.build {
        // ... setup with revocation provider
        revocation { provider(IN_MEMORY) }
    }

    // Issue credential with revocation
    val issuanceResult = trustWeave.issue {
        credential { /* ... */ }
        signedBy(issuerDid = issuerDid, keyId = keyId)
        withRevocation() // Enable revocation status list
    }
    
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> throw IllegalStateException(
            "Failed to issue credential: ${issuanceResult.allErrors.joinToString()}"
        )
    }
    
    // Or use getOrThrow() in tests:
    // val credential = trustWeave.issue { ... }.getOrThrow()

    // Revoke credential
    trustWeave.revoke {
        credential(credential.id!!)
        statusList(statusListId)
    }

    // Verify revocation
    val result = trustWeave.verify {
        credential(credential)
        checkRevocation()
    }
    assertTrue(result is VerificationResult.Invalid.Revoked)
}
```

### 3. Wallet Storage Workflow Template

**Test:** `test wallet storage workflow template`

**Demonstrates:**
- Create wallet
- Store credentials
- Retrieve credentials
- Query credentials

**Use Case:** Testing wallet operations

```kotlin
import org.trustweave.trust.types.WalletCreationResult

@Test
fun `test wallet storage workflow template`() = runBlocking {
    // Create wallet
    
    val walletResult = trustWeave.wallet {
        id("holder-wallet-1")
        holder(holderDid)
        inMemory()
        enableOrganization()
        enablePresentation()
    }
    
    val wallet = when (walletResult) {
        is WalletCreationResult.Success -> walletResult.wallet
        else -> throw IllegalStateException("Failed to create wallet: ${walletResult.reason}")
    }
    
    // Or use getOrThrow() in tests:
    // val wallet = trustWeave.wallet { ... }.getOrThrow()

    // Store credential
    credential.storeIn(wallet)

    // Retrieve credential
    val retrieved = wallet.get(credential.id!!)

    // Query credentials
    val credentials = wallet.query {
        byType("TestCredential")
        valid()
    }
}
```

### 4. Verifiable Presentation Workflow Template

**Test:** `test verifiable presentation workflow template`

**Demonstrates:**
- Store multiple credentials in wallet
- Create verifiable presentation
- Sign presentation with holder's key

**Use Case:** Testing presentation creation

```kotlin
// Imports: presentationResult, getOrThrow (org.trustweave.trust.types)

@Test
fun `test verifiable presentation workflow template`() = runBlocking {
    credential1.storeIn(wallet)
    credential2.storeIn(wallet)

    val vp = trustWeave.presentationResult {
        credentials(credential1, credential2)
        holder(holderDid.value)
        challenge("challenge-123")
        domain("example.com")
    }.getOrThrow()
}
```

### 5. DID Update Workflow Template

**Test:** `test DID update workflow template`

**Demonstrates:**
- Create DID
- Update DID (add keys, services)
- Issue credential with updated DID

**Use Case:** Testing DID document updates

```kotlin
import org.trustweave.trust.types.DidCreationResult
@Test
fun `test DID update workflow template`() = runBlocking {
    // Create DID
    val didResult = trustWeave.createDid { /* ... */ }
    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure -> {
            val msg = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered ->
                    "method ${didResult.method} not registered; available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> didResult.reason
                is DidCreationResult.Failure.DocumentCreationFailed -> didResult.reason
                is DidCreationResult.Failure.InvalidConfiguration -> didResult.reason
                is DidCreationResult.Failure.Other -> didResult.reason
            }
            throw IllegalStateException("Failed to create DID: $msg")
        }
    }

    // Generate new key
    val newKey = kms.generateKey("Ed25519")

    trustWeave.updateDid {
        did(issuerDid.value)
        method(DidMethods.KEY)
        addKey {
            id("${issuerDid.value}#key-2")
            type("Ed25519VerificationKey2020")
            publicKeyJwk(newKey.publicKeyJwk ?: emptyMap())
        }
    }

    // Issue credential with updated DID
    // ... (see template for full implementation)
}
```

### 6. Blockchain Anchoring Workflow Template

**Test:** `test blockchain anchoring workflow template`

**Demonstrates:**
- Configure blockchain anchor
- Issue credential with anchoring
- Verify anchor

**Use Case:** Testing blockchain anchoring

```kotlin
import org.trustweave.trust.dsl.credential.AnchorProviders.IN_MEMORY
@Test
fun `test blockchain anchoring workflow template`() = runBlocking {
    val trustWeave = TrustWeave.build {
        // ... setup
        anchor {
            chain("testnet:inMemory") {
                provider(IN_MEMORY) // Use in-memory anchor client
            }
        }
        credentials {
            defaultChain("testnet:inMemory")
        }
    }

    // Issue credential. `anchor(...)` is not yet exposed on IssuanceBuilder —
    // configure the anchor at TrustWeave.build { anchor { chain(...) {...} } }
    // and use `trustWeave.blockchains` to write/read anchors explicitly.
    val issuanceResult = trustWeave.issue {
        credential { /* ... */ }
        signedBy(issuerDid = issuerDid, keyId = keyId)
    }
    
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> throw IllegalStateException(
            "Failed to issue credential: ${issuanceResult.allErrors.joinToString()}"
        )
    }

    // Cryptographic verification (anchoring is checked separately via `trustWeave.blockchains` / anchor ref on the credential)
    trustWeave.verify {
        credential(credential)
        checkRevocation()
    }
}
```

### 7. Smart Contract Workflow Template

**Test:** `test smart contract workflow template`

**Demonstrates:**
- Issue contract as verifiable credential
- Anchor contract to blockchain
- Verify contract credential

**Use Case:** Testing smart contract workflows

```kotlin
@Test
fun `test smart contract workflow template`() = runBlocking {
    // Issue contract as credential. Anchoring is not a one-call IssuanceBuilder
    // feature today — anchor explicitly via `trustWeave.blockchains.write(...)`.
    val issuanceResult = trustWeave.issue {
        credential {
            type(CredentialType.Custom("SmartContractCredential"), CredentialType.VerifiableCredential)
            // ... contract details
        }
        signedBy(issuerDid = issuerDid, keyId = keyId)
    }
    
    val contractCredential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> throw IllegalStateException(
            "Failed to issue credential: ${issuanceResult.allErrors.joinToString()}"
        )
    }

    // Verify contract credential (use `trustWeave.blockchains.read` if you need to assert on-chain anchor data)
    trustWeave.verify {
        credential(contractCredential)
        checkRevocation()
    }
}
```

### 8. External Services Template

**Test:** `test with external services template`

**Demonstrates:**
- Using `@RequiresPlugin` annotation
- Automatic test skipping when credentials unavailable
- Testing with real external services

**Use Case:** Testing with AWS KMS, Ethereum DID, etc.

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.ETHR
import org.trustweave.trust.dsl.credential.TrustProviders.IN_MEMORY
@Test
@RequiresPlugin("aws-kms", "ethr-did") // List all required plugins
fun `test with external services template`() = runBlocking {
    // This test will be automatically skipped if AWS credentials
    // or Ethereum RPC URL are not available

    val trustWeave = TrustWeave.build {
        keys { provider("aws-kms") } // Requires AWS credentials
        did { method(ETHR) {} } // Requires ETHEREUM_RPC_URL
        trust { provider(IN_MEMORY) }
    }

    // Test implementation here...
    // If you reach here, all required env vars are available
}
```

## Adapting Templates for Specific Configurations

To adapt a template for a specific configuration (e.g., AWS KMS, Ethereum DID):

1. **Copy the template test**
2. **Replace in-memory components** with your specific providers
3. **Add `@RequiresPlugin` annotation** with required plugins
4. **Follow the same key extraction pattern**

Example: Adapting for AWS KMS and Ethereum DID

```kotlin
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.dsl.credential.DidMethods.ETHR
import org.trustweave.trust.dsl.credential.TrustProviders.IN_MEMORY
@Test
@RequiresPlugin("aws-kms", "ethr-did")
fun `test workflow with AWS KMS and Ethereum DID`() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider("aws-kms") } // AWS KMS instead of in-memory
        did { method(ETHR) {} } // Ethereum DID instead of key DID
        trust { provider(IN_MEMORY) }
    }

    // Same pattern: create DID, extract key ID, issue credential
    val didResult = trustWeave.createDid {
        method(ETHR)
        algorithm(KeyAlgorithms.ED25519)
    }
    
    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        is DidCreationResult.Failure -> {
            val msg = when (didResult) {
                is DidCreationResult.Failure.MethodNotRegistered ->
                    "method ${didResult.method} not registered; available: ${didResult.availableMethods.joinToString()}"
                is DidCreationResult.Failure.KeyGenerationFailed -> didResult.reason
                is DidCreationResult.Failure.DocumentCreationFailed -> didResult.reason
                is DidCreationResult.Failure.InvalidConfiguration -> didResult.reason
                is DidCreationResult.Failure.Other -> didResult.reason
            }
            throw IllegalStateException("Failed to create DID: $msg")
        }
    }

    // Extract key ID (same pattern)
    val issuerDidDoc = when (val res = trustWeave.configuration.didRegistry.resolve(issuerDid.value)) {
        is DidResolutionResult.Success -> res.document
        else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve issuer DID")
    }

    val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.extractKeyId()
        ?: throw IllegalStateException("No verification method in issuer DID")

    // Issue credential (same pattern)
    val issuanceResult = trustWeave.issue {
        credential { /* ... */ }
        signedBy(issuerDid = issuerDid, keyId = keyId) // Same pattern!
    }
    
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> throw IllegalStateException(
            "Failed to issue credential: ${issuanceResult.allErrors.joinToString()}"
        )
    }

    // Verify (same pattern)
    val result = trustWeave.verify {
        credential(credential)
        requireTrust(requireNotNull(trustWeave.configuration.trustRegistry))
    }

    assertTrue(result is VerificationResult.Valid)
}
```

## Common Patterns

### Pattern 1: Extract Key ID from DID Document

```kotlin
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage

// DidMethodRegistry.resolve(String) — pass the DID's raw string form.
val issuerDidDoc = when (val res = trustWeave.configuration.didRegistry.resolve(issuerDid.value)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve issuer DID")
}

val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.extractKeyId()
    ?: throw IllegalStateException("No verification method in issuer DID")
```

### Pattern 2: Issue Credential with Extracted Key

```kotlin
val issuanceResult = trustWeave.issue {
    credential {
        id("https://example.com/credential-1")
        type("TestCredential")
        issuer(issuerDid)
        subject {
            id(holderDid)
            "test" to "value"
        }
        // TrustWeave's DSL uses kotlinx-datetime, not java.time.
        issued(kotlinx.datetime.Clock.System.now())
    }
    signedBy(issuerDid = issuerDid, keyId = keyId) // Use extracted key ID
}

val credential = when (issuanceResult) {
    is IssuanceResult.Success -> issuanceResult.credential
    is IssuanceResult.Failure -> throw IllegalStateException(
        "Failed to issue credential: ${issuanceResult.allErrors.joinToString()}"
    )
}

// In tests, use getOrThrow():
// val credential = trustWeave.issue { ... }.getOrThrow()
```

### Pattern 3: Verify Credential with All Checks

```kotlin
import org.trustweave.credential.results.VerificationResult

val result = trustWeave.verify {
    credential(credential)
    requireTrust(requireNotNull(trustWeave.configuration.trustRegistry))
    checkExpiration()
    checkRevocation()
}

assertTrue(result is VerificationResult.Valid, "Credential should verify")
```

## Best Practices

1. **Always Extract Key ID from DID Document** - Never generate a new key separately
2. **Use In-Memory Components First** - Validate workflow with in-memory components before testing with external services
3. **Add `@RequiresPlugin` for External Services** - Automatically skip tests when credentials unavailable
4. **Follow Template Structure** - Use templates as starting points, maintain consistent patterns
5. **Test All Workflow Steps** - Issue, verify, revoke, update, etc.
6. **Use Descriptive Test Names** - Use backtick names like `` `test workflow description`() ``

## Troubleshooting

### Issue: Proof Verification Fails

**Problem:** `proofValid` is `false` in verification result

**Solution:** Ensure you're extracting the key ID from the DID document, not generating a new key:

```kotlin
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage

// Wrong: generating a new key
val newKey = kms.generateKey("Ed25519")
val issuanceResult1 = trustWeave.issue {
    signedBy(issuerDid = issuerDid, keyId = newKey.id.value) // Key not in DID document!
}
// Wrong key id typically surfaces as IssuanceResult.Failure.AdapterError or InvalidRequest

// Correct: extract key from DID document
val issuerDidDoc = when (val res = trustWeave.configuration.didRegistry.resolve(issuerDid.value)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve issuer DID")
}
val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.extractKeyId()
    ?: throw IllegalStateException("No verification method in issuer DID")

val issuanceResult2 = trustWeave.issue {
    signedBy(issuerDid = issuerDid, keyId = keyId) // Key matches DID document!
}

val credential = when (issuanceResult2) {
    is IssuanceResult.Success -> issuanceResult2.credential
    else -> throw IllegalStateException(
        "Failed to issue credential: ${issuanceResult2.allErrors.joinToString()}"
    )
}
```

### Issue: Revocation Check Not Working

**Problem:** `notRevoked` is always `true` even after revocation

**Solution:** Ensure `statusListManager` is configured and passed to verifier:

```kotlin
import org.trustweave.trust.dsl.credential.RevocationProviders.IN_MEMORY
val trustWeave = TrustWeave.build {
    // ... setup
    revocation { provider(IN_MEMORY) } // Must configure revocation provider
}

// VerificationDsl automatically passes statusListManager to verifier
val result = trustWeave.verify {
    credential(credential)
    checkRevocation() // Must enable revocation check
}
```

### Issue: Presentation creation fails

**Problem:** `PresentationResult.Failure.AdapterNotReady` or proof errors.

**Solution:** Configure **`CredentialService`** on **`TrustWeave.build { ... }`** and use **`trustWeave.presentationResult { ... }`** (see [Create presentations](../../how-to/create-presentations.md)).

## Next Steps

- [Test Patterns](test-patterns.md) - Common test patterns
- [Integration Testing](integration-testing.md) - Integration test best practices
- [Plugin Credential Handling](plugin-credential-handling.md) - Handling external service credentials
- [TrustWeave-testkit Module](../../api-reference/modules/trustweave-testkit.md) - Testkit components overview

