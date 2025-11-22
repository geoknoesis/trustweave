# Trust Layer Test Templates

This guide explains how to use the comprehensive in-memory test templates for trust layer integration tests.

## Overview

The trust layer test templates provide complete, working examples of common VeriCore workflows using only in-memory components. These templates serve as:

- **Reference implementations** for common use cases
- **Starting points** for creating new integration tests
- **Validation** that workflows work correctly with in-memory components
- **Examples** for adapting to specific configurations (AWS KMS, Ethereum DID, etc.)

## Location

The test templates are located in:
```
core/vericore-trust/src/test/kotlin/com/geoknoesis/vericore/integration/InMemoryTrustLayerIntegrationTest.kt
```

## Key Pattern: Extract Key ID from DID Document

**CRITICAL:** All templates follow this essential pattern:

1. **Create DID** - This generates a key and stores it in the DID document
2. **Extract Key ID** - Get the key ID from the DID document's verification method
3. **Use Extracted Key** - Use that key ID when issuing credentials

This ensures proof verification succeeds because the DID document contains the correct verification method.

```kotlin
// Step 1: Create DID (generates key and stores in DID document)
val issuerDid = trustLayer.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}

// Step 2: Extract key ID from DID document
val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
    ?: throw IllegalStateException("Failed to resolve issuer DID")

val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
    ?: throw IllegalStateException("No verification method in issuer DID")

// Step 3: Use extracted key ID for signing
val credential = trustLayer.issue {
    credential { /* ... */ }
    by(issuerDid = issuerDid, keyId = keyId) // MUST match key in DID document
}
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
@Test
fun `test complete in-memory workflow template`() = runBlocking {
    val kms = InMemoryKeyManagementService()
    
    val trustLayer = trustLayer {
        keys {
            custom(kms)
            signer { data, keyId -> kms.sign(keyId, data) }
        }
        did { method(DidMethods.KEY) {} }
        trust { provider("inMemory") }
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
@Test
fun `test credential revocation workflow template`() = runBlocking {
    val trustLayer = trustLayer {
        // ... setup with revocation provider
        revocation { provider("inMemory") }
    }
    
    // Issue credential with revocation
    val credential = trustLayer.issue {
        credential { /* ... */ }
        by(issuerDid = issuerDid, keyId = keyId)
        withRevocation() // Enable revocation status list
    }
    
    // Revoke credential
    trustLayer.revoke {
        credential(credential.id!!)
        statusList(statusListId)
    }
    
    // Verify revocation
    val result = trustLayer.verify {
        credential(credential)
        checkRevocation()
    }
    assertFalse(result.notRevoked)
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
@Test
fun `test wallet storage workflow template`() = runBlocking {
    // Create wallet
    val wallet = trustLayer.wallet {
        id("holder-wallet-1")
        holder(holderDid)
        inMemory()
        enableOrganization()
        enablePresentation()
    }
    
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
@Test
fun `test verifiable presentation workflow template`() = runBlocking {
    // Store credentials
    credential1.storeIn(wallet)
    credential2.storeIn(wallet)
    
    // Get proof generator from trust layer
    val issuer = trustLayer.dsl().getIssuer()
    val proofGenerator = /* extract from issuer */
    val presentationService = PresentationService(
        proofGenerator = proofGenerator,
        proofRegistry = trustLayer.dsl().getConfig().registries.proofRegistry
    )
    
    // Create presentation
    val presentation = presentation(presentationService) {
        credentials(credential1, credential2)
        holder(holderDid)
        keyId(holderKeyId)
        challenge("challenge-123")
        domain("example.com")
    }
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
@Test
fun `test DID update workflow template`() = runBlocking {
    // Create DID
    val issuerDid = trustLayer.createDid { /* ... */ }
    
    // Generate new key
    val newKey = kms.generateKey("Ed25519")
    
    // Update DID
    trustLayer.updateDid {
        did(issuerDid)
        method(DidMethods.KEY)
        addKey {
            id("$issuerDid#key-2")
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
@Test
fun `test blockchain anchoring workflow template`() = runBlocking {
    val trustLayer = trustLayer {
        // ... setup
        anchor {
            chain("testnet:inMemory") {
                provider("inMemory") // Use in-memory anchor client
            }
        }
        credentials {
            defaultChain("testnet:inMemory")
        }
    }
    
    // Issue credential with anchoring
    val credential = trustLayer.issue {
        credential { /* ... */ }
        by(issuerDid = issuerDid, keyId = keyId)
        anchor("testnet:inMemory")
    }
    
    // Verify anchor
    val result = trustLayer.verify {
        credential(credential)
        verifyAnchor("testnet:inMemory")
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
    // Issue contract as credential
    val contractCredential = trustLayer.issue {
        credential {
            type("SmartContractCredential", "VerifiableCredential")
            // ... contract details
        }
        by(issuerDid = issuerDid, keyId = keyId)
        anchor("testnet:inMemory")
    }
    
    // Verify contract
    val result = trustLayer.verify {
        credential(contractCredential)
        verifyAnchor("testnet:inMemory")
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
@Test
@RequiresPlugin("aws-kms", "ethr-did") // List all required plugins
fun `test with external services template`() = runBlocking {
    // This test will be automatically skipped if AWS credentials 
    // or Ethereum RPC URL are not available
    
    val trustLayer = trustLayer {
        keys { provider("aws-kms") } // Requires AWS credentials
        did { method("ethr") {} } // Requires ETHEREUM_RPC_URL
        trust { provider("inMemory") }
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
@Test
@RequiresPlugin("aws-kms", "ethr-did")
fun `test workflow with AWS KMS and Ethereum DID`() = runBlocking {
    val trustLayer = trustLayer {
        keys { provider("aws-kms") } // AWS KMS instead of in-memory
        did { method("ethr") {} } // Ethereum DID instead of key DID
        trust { provider("inMemory") }
    }
    
    // Same pattern: create DID, extract key ID, issue credential
    val issuerDid = trustLayer.createDid {
        method("ethr")
        algorithm(KeyAlgorithms.ED25519)
    }
    
    // Extract key ID (same pattern)
    val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
        ?: throw IllegalStateException("Failed to resolve issuer DID")
    
    val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method in issuer DID")
    
    // Issue credential (same pattern)
    val credential = trustLayer.issue {
        credential { /* ... */ }
        by(issuerDid = issuerDid, keyId = keyId) // Same pattern!
    }
    
    // Verify (same pattern)
    val result = trustLayer.verify {
        credential(credential)
        checkTrustRegistry()
    }
    
    assertTrue(result.valid)
}
```

## Common Patterns

### Pattern 1: Extract Key ID from DID Document

```kotlin
val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
    ?: throw IllegalStateException("Failed to resolve issuer DID")

val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
    ?: throw IllegalStateException("No verification method in issuer DID")
```

### Pattern 2: Issue Credential with Extracted Key

```kotlin
val credential = trustLayer.issue {
    credential {
        id("https://example.com/credential-1")
        type("TestCredential")
        issuer(issuerDid)
        subject {
            id(holderDid)
            "test" to "value"
        }
        issued(Instant.now())
    }
    by(issuerDid = issuerDid, keyId = keyId) // Use extracted key ID
}
```

### Pattern 3: Verify Credential with All Checks

```kotlin
val result = trustLayer.verify {
    credential(credential)
    checkTrustRegistry()
    checkExpiration()
    checkRevocation()
}

assertTrue(result.proofValid, "Proof should be valid")
assertTrue(result.issuerValid, "Issuer DID should resolve")
assertTrue(result.trustRegistryValid, "Issuer should be trusted")
assertTrue(result.valid, "Credential should be valid")
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
// ❌ WRONG: Generating new key
val newKey = kms.generateKey("Ed25519")
val credential = trustLayer.issue {
    by(issuerDid = issuerDid, keyId = newKey.id) // Key not in DID document!
}

// ✅ CORRECT: Extract key from DID document
val issuerDidDoc = trustLayer.dsl().getConfig().registries.didRegistry.resolve(issuerDid)?.document
val keyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
val credential = trustLayer.issue {
    by(issuerDid = issuerDid, keyId = keyId) // Key matches DID document!
}
```

### Issue: Revocation Check Not Working

**Problem:** `notRevoked` is always `true` even after revocation

**Solution:** Ensure `statusListManager` is configured and passed to verifier:

```kotlin
val trustLayer = trustLayer {
    // ... setup
    revocation { provider("inMemory") } // Must configure revocation provider
}

// VerificationDsl automatically passes statusListManager to verifier
val result = trustLayer.verify {
    credential(credential)
    checkRevocation() // Must enable revocation check
}
```

### Issue: Presentation Creation Fails

**Problem:** `IllegalArgumentException: No proof generator registered`

**Solution:** Create `PresentationService` with proof generator from trust layer:

```kotlin
val issuer = trustLayer.dsl().getIssuer()
val proofGenerator = /* extract from issuer */
val presentationService = PresentationService(
    proofGenerator = proofGenerator,
    proofRegistry = trustLayer.dsl().getConfig().registries.proofRegistry
)

val presentation = presentation(presentationService) {
    // ... presentation configuration
}
```

## Next Steps

- [Test Patterns](test-patterns.md) - Common test patterns
- [Integration Testing](integration-testing.md) - Integration test best practices
- [Plugin Credential Handling](plugin-credential-handling.md) - Handling external service credentials
- [vericore-testkit Module](../../modules/vericore-testkit.md) - Testkit components overview

