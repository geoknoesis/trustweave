---
title: Configure Trust Policies
nav_order: 7
parent: How-To Guides
keywords:
  - trust
  - policy
  - verification
  - issuer
  - allowlist
  - blocklist
  - trust anchor
---

# Configure Trust Policies

This guide shows you how to configure trust policies for credential verification. Trust policies allow you to control which issuers are trusted when verifying credentials, enabling fine-grained trust management for your applications.

## Prerequisites

Before you begin, ensure you have:

- ✅ TrustWeave dependencies added to your project
- ✅ Basic understanding of credential verification
- ✅ Understanding of trust registries (see [Configure Trust Registry](configure-trust-registry.md))

## Expected Outcome

After completing this guide, you will have:

- ✅ Understood when to use trust policies
- ✅ Configured allowlist, blocklist, and custom trust policies
- ✅ Integrated trust policies into credential verification
- ✅ Learned best practices for trust policy management

## Overview

Trust policies control **which issuers are trusted** during credential verification. By default, TrustWeave only checks cryptographic validity (signature, expiration, revocation) but doesn't check issuer trust. Trust policies add an additional layer of security by explicitly defining which issuers are acceptable.

### Trust Policy Types

TrustWeave supports several trust policy patterns:

1. **No Trust Required** - Accept all issuers (default behavior)
2. **Trust Registry Based** - Require issuer to be in trust registry (direct anchor or trust path)
3. **Allowlist** - Only accept specific issuers
4. **Blocklist** - Reject specific issuers
5. **Custom Policies** - Implement your own trust logic

## Quick Example

Here's a simple example using an allowlist policy:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.credential.trust.TrustPolicy
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.types.VerificationResult
import org.trustweave.testkit.services.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        factories(
            kmsFactory = TestkitKmsFactory(),
            didMethodFactory = TestkitDidMethodFactory()
        )
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }

    // Create allowlist policy
    val trustedIssuers = setOf(
        Did("did:key:university"),
        Did("did:key:government")
    )
    val policy = TrustPolicy.allowlist(trustedIssuers)

    // Verify credential with trust policy
    val result = trustWeave.verify {
        credential(credential)
        withTrustPolicy(policy)
    }

    when (result) {
        is VerificationResult.Valid -> println("✓ Credential valid and issuer trusted")
        is VerificationResult.Invalid -> println("✗ Verification failed: ${result.errors}")
    }
}
```

## Trust Policy Patterns

### Pattern 1: Allowlist (Whitelist)

Only accept credentials from specific trusted issuers:

```kotlin
val trustedIssuers = setOf(
    Did("did:web:university.edu"),
    Did("did:key:z6Mk..."),
    Did("did:web:government.gov")
)

val policy = TrustPolicy.allowlist(trustedIssuers)

val result = trustWeave.verify {
    credential(credential)
    withTrustPolicy(policy)
}
```

**When to use:**
- High-security applications requiring explicit issuer approval
- Compliance requirements (e.g., only accept government-issued credentials)
- Controlled ecosystems with known good issuers

### Pattern 2: Blocklist (Blacklist)

Reject credentials from specific untrusted issuers:

```kotlin
val blockedIssuers = setOf(
    Did("did:web:fraudulent-issuer.com"),
    Did("did:key:z6Mk...")
)

val policy = TrustPolicy.blocklist(blockedIssuers)

val result = trustWeave.verify {
    credential(credential)
    withTrustPolicy(policy)
}
```

**When to use:**
- Open ecosystems where most issuers are acceptable
- Blocking known bad actors
- Gradual trust establishment (blocklist grows over time)

### Pattern 3: Accept All (Default)

No trust checking - accept all cryptographically valid credentials:

```kotlin
val policy = TrustPolicy.acceptAll()

val result = trustWeave.verify {
    credential(credential)
    withTrustPolicy(policy)
}

// Or simply omit the policy (default behavior)
val result = trustWeave.verify {
    credential(credential)
    allowUntrusted() // Explicitly allow untrusted issuers
}
```

**When to use:**
- Development and testing
- Open credential ecosystems
- Applications that handle trust at a higher level

### Pattern 4: Trust Registry Based

Use trust registry to check for direct trust anchors or trust paths:

```kotlin
val trustWeave = TrustWeave.build {
    factories(
        kmsFactory = TestkitKmsFactory(),
        didMethodFactory = TestkitDidMethodFactory()
    )
    keys { provider(IN_MEMORY) }
    did { method(KEY) { algorithm(ED25519) } }
    trust { provider(IN_MEMORY) }
}

// Require issuer to be a direct trust anchor
trustWeave.verify {
    credential(credential)
    requireTrust(trustWeave.getDslContext().getTrustRegistry()!!)
}

// Require trust path (max length 3)
trustWeave.verify {
    credential(credential)
    requireTrustPath(trustWeave.getDslContext().getTrustRegistry()!!, maxLength = 3)
}
```

**When to use:**
- Web-of-trust systems
- Decentralized trust relationships
- Trust established through intermediary anchors

### Pattern 5: Custom Trust Policy

Implement your own trust logic:

```kotlin
class CustomTrustPolicy : TrustPolicy {
    override suspend fun isTrusted(issuer: Did): Boolean {
        // Custom logic here
        return issuer.value.startsWith("did:web:trusted-") ||
               issuer.value.startsWith("did:key:z6Mk")
    }
}

val policy = CustomTrustPolicy()

val result = trustWeave.verify {
    credential(credential)
    withTrustPolicy(policy)
}
```

**When to use:**
- Complex trust rules (e.g., domain-based, pattern matching)
- Integration with external trust systems
- Dynamic trust evaluation based on context

## Using Trust Policies with Verification DSL

The verification DSL provides convenient methods for trust policies:

```kotlin
trustWeave.verify {
    credential(credential)
    
    // Option 1: Use trust registry
    requireTrust(trustRegistry)
    
    // Option 2: Require trust path
    requireTrustPath(trustRegistry, maxLength = 3)
    
    // Option 3: Custom policy
    val policy = TrustPolicy.allowlist(trustedIssuers)
    withTrustPolicy(policy)
    
    // Option 4: Allow untrusted (default)
    allowUntrusted()
}
```

## Trust Policy Best Practices

### 1. Start Permissive, Tighten Over Time

For new applications, start with `TrustPolicy.acceptAll()` and gradually add restrictions:

```kotlin
// Phase 1: Accept all
val policy = TrustPolicy.acceptAll()

// Phase 2: Add blocklist for known bad actors
val policy = TrustPolicy.blocklist(blockedIssuers)

// Phase 3: Move to allowlist as you identify trusted issuers
val policy = TrustPolicy.allowlist(trustedIssuers)
```

### 2. Combine with Trust Registry

Use trust registries for long-term trust relationships, policies for immediate decisions:

```kotlin
// Trust registry for established relationships
trustWeave.trust {
    addAnchor("did:web:university.edu") {
        credentialTypes("EducationCredential")
    }
}

// Policy for quick decisions or additional filtering
val policy = TrustPolicy.blocklist(recentlyRevokedIssuers)

trustWeave.verify {
    credential(credential)
    requireTrust(trustRegistry)  // Must be in registry
    withTrustPolicy(policy)      // AND not in blocklist
}
```

### 3. Make Policies Context-Aware

Different credential types may need different trust policies:

```kotlin
fun getPolicyForCredentialType(type: String): TrustPolicy {
    return when (type) {
        "GovernmentCredential" -> TrustPolicy.allowlist(governmentIssuers)
        "EducationCredential" -> TrustPolicy.allowlist(educationIssuers)
        "SocialCredential" -> TrustPolicy.acceptAll()
        else -> TrustPolicy.blocklist(knownBadActors)
    }
}

val credentialType = credential.type.first().value
val policy = getPolicyForCredentialType(credentialType)

trustWeave.verify {
    credential(credential)
    withTrustPolicy(policy)
}
```

### 4. Handle Policy Failures Gracefully

Trust policy failures should be handled explicitly:

```kotlin
val result = trustWeave.verify {
    credential(credential)
    withTrustPolicy(policy)
}

when (result) {
    is VerificationResult.Valid -> {
        // Credential is valid and issuer is trusted
        processCredential(credential)
    }
    is VerificationResult.Invalid -> {
        val untrustedError = result.errors.find { 
            it.contains("untrusted") || it.contains("not trusted")
        }
        if (untrustedError != null) {
            // Log for security monitoring
            logger.warn("Untrusted issuer attempted verification: ${credential.issuer}")
        }
        // Handle other verification errors
    }
}
```

### 5. Cache Policy Decisions

For performance, consider caching policy decisions:

```kotlin
class CachedTrustPolicy(private val delegate: TrustPolicy) : TrustPolicy {
    private val cache = mutableMapOf<Did, Boolean>()
    
    override suspend fun isTrusted(issuer: Did): Boolean {
        return cache.getOrPut(issuer) {
            delegate.isTrusted(issuer)
        }
    }
}
```

## Integration Examples

### Example 1: API Endpoint with Trust Policy

```kotlin
class CredentialVerificationEndpoint(
    private val trustWeave: TrustWeave,
    private val trustedIssuers: Set<Did>
) {
    suspend fun verifyCredential(
        credentialJson: String
    ): VerificationResponse {
        val credential = Json.decodeFromString<VerifiableCredential>(credentialJson)
        val policy = TrustPolicy.allowlist(trustedIssuers)
        
        val result = trustWeave.verify {
            credential(credential)
            withTrustPolicy(policy)
            checkRevocation(true)
            checkExpiration(true)
        }
        
        return when (result) {
            is VerificationResult.Valid -> VerificationResponse(
                valid = true,
                issuer = credential.issuer.value
            )
            is VerificationResult.Invalid -> VerificationResponse(
                valid = false,
                errors = result.errors
            )
        }
    }
}
```

### Example 2: Multi-Policy Verification

```kotlin
suspend fun verifyWithMultiplePolicies(
    credential: VerifiableCredential,
    allowlist: Set<Did>,
    blocklist: Set<Did>
): VerificationResult {
    // Combine allowlist and blocklist
    val combinedPolicy = object : TrustPolicy {
        override suspend fun isTrusted(issuer: Did): Boolean {
            // First check blocklist
            if (issuer in blocklist) return false
            // Then check allowlist
            return issuer in allowlist
        }
    }
    
    return trustWeave.verify {
        credential(credential)
        withTrustPolicy(combinedPolicy)
    }
}
```

## Related Documentation

- **[Configure Trust Registry](configure-trust-registry.md)** - Learn about trust registries and trust anchors
- **[Verify Credentials](verify-credentials.md)** - Complete guide to credential verification
- **[Trust Concepts](../../core-concepts/trust-registry.md)** - Understanding trust models

## Summary

Trust policies provide fine-grained control over which issuers are trusted during credential verification. Choose the policy pattern that matches your security requirements:

- **Allowlist**: Maximum security, explicit approval required
- **Blocklist**: Open ecosystem with known bad actors blocked
- **Trust Registry**: Web-of-trust relationships
- **Custom**: Complex business rules

Always handle policy failures gracefully and consider caching for performance. Combine trust policies with other verification checks (expiration, revocation, schema validation) for comprehensive credential verification.

