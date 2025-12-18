# Trust DSL Examples

This document demonstrates the expressive trust DSL with infix operators and policy composition.

## Infix Operators for Trust Relationships

### Adding Trust Anchors

**Before (Traditional):**
```kotlin
trustWeave.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
        description("Trusted university")
    }
}
```

**After (Infix DSL):**
```kotlin
trustWeave.trust {
    addAnchor(universityDid, universityDid trusts "EducationCredential" because {
        description("Trusted university")
    })
    
    // Or for multiple types
    addAnchor(universityDid, universityDid trusts listOf("EducationCredential", "DegreeCredential") because {
        description("Trusted university")
    })
    
    // Or for all types
    addAnchor(caDid, caDid trustsAll because {
        description("Root CA - trusts all credential types")
    })
}
```

### Finding Trust Paths

**Before (Traditional):**
```kotlin
trustWeave.trust {
    val path = findTrustPath(
        from = VerifierIdentity(Did("did:key:verifier")),
        to = IssuerIdentity.from("did:key:issuer", "key-1")
    )
}
```

**After (Infix DSL):**
```kotlin
trustWeave.trust {
    val path = resolve(verifierDid trustsPath issuerDid)
    // OR
    val path = resolve(verifierDid trustsPath issuerDid)
    
    when (path) {
        is TrustPath.Verified -> {
            println("Trusted via path: ${path.anchors.map { it.did }}")
            println("Path length: ${path.length}")
        }
        is TrustPath.NotFound -> {
            println("No trust path found")
        }
    }
}
```

## Policy Composition

### Combining Policies with AND

```kotlin
val policy = TrustPolicy.allowlist(trustedIssuers) and TrustPolicy.blocklist(blockedIssuers)
```

### Combining Policies with OR

```kotlin
val policy = requireAnchor(caDid) or requirePath(maxLength = 3)
```

### Negating Policies

```kotlin
val policy = !TrustPolicy.blocklist(blockedIssuers)
```

### Complex Policy Composition

```kotlin
val policy = (
    TrustPolicy.allowlist(trustedIssuers) 
    and TrustPolicy.blocklist(blockedIssuers)
) or requirePath(maxLength = 2)
```

## Complete Example

```kotlin
val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
    trust { provider(IN_MEMORY) }
}

// Create DIDs
val universityDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
val caDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
val verifierDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()
val issuerDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()

// Add trust anchors using infix DSL
trustWeave.trust {
    addAnchor(universityDid, universityDid trusts "EducationCredential" because {
        description("Trusted university")
    })
    
    addAnchor(caDid, caDid trustsAll because {
        description("Root CA")
    })
    
    // Find trust path
    val path = resolve(verifierDid trustsPath issuerDid)
    when (path) {
        is TrustPath.Verified -> println("Path found: ${path.length} hops")
        is TrustPath.NotFound -> println("No path found")
    }
}

// Use composed policy in verification
val policy = (
    TrustPolicy.allowlist(setOf(universityDid, caDid))
    and !TrustPolicy.blocklist(emptySet())
) or requirePath(maxLength = 3)

val verification = trustWeave.verify {
    credential(credential)
    withTrustPolicy(policy)
}.getOrThrow()
```

