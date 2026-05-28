---
nav_exclude: true
---

# Trust DSL Examples

This document demonstrates the trust registry DSL and **issuer trust** composition during verification. Issuer allowlists/blocklists use `org.trustweave.credential.trust.TrustEvaluator`; infix `and`, `or`, and `not` for composing evaluators live in `org.trustweave.trust.dsl`.

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
        to = IssuerIdentity("did:key:issuer")
    )
}
```

**After (Infix DSL):**
```kotlin
trustWeave.trust {
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

## Issuer trust composition (`TrustEvaluator`)

Import the credential API type and the DSL extensions:

```kotlin
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.trust.dsl.and
import org.trustweave.trust.dsl.or
import org.trustweave.trust.dsl.not
```

### Combining evaluators with AND

```kotlin
val evaluator = TrustEvaluator.allowlist(trustedIssuers) and TrustEvaluator.blocklist(blockedIssuers)
```

### Combining evaluators with OR

Either evaluator can accept the issuer:

```kotlin
val evaluator = TrustEvaluator.allowlist(teamAIssuers) or TrustEvaluator.allowlist(teamBIssuers)
```

### Negating an evaluator

```kotlin
val evaluator = !TrustEvaluator.blocklist(blockedIssuers)
```

### Nested composition

```kotlin
val evaluator = (
    TrustEvaluator.allowlist(trustedIssuers)
        and TrustEvaluator.blocklist(blockedIssuers)
    )
```

## Complete Example

```kotlin
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.dsl.and
import org.trustweave.trust.dsl.not
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.dsl.credential.TrustProviders
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.DidMethods.KEY

val trustWeave = TrustWeave.build {
    keys { provider(KmsProviders.IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
    trust { provider(TrustProviders.IN_MEMORY) }
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
    
    val path = resolve(verifierDid trustsPath issuerDid)
    when (path) {
        is TrustPath.Verified -> println("Path found: ${path.length} hops")
        is TrustPath.NotFound -> println("No path found")
    }
}

// Issuer trust during credential verification (TrustEvaluator + composition)
val trustEvaluator = (
    TrustEvaluator.allowlist(setOf(universityDid, caDid))
        and !TrustEvaluator.blocklist(emptySet())
    )

val verification = trustWeave.verify {
    credential(credential)
    withTrustPolicy(trustEvaluator)
}

when (verification) {
    is VerificationResult.Valid -> println("OK")
    is VerificationResult.Invalid -> println(verification.allErrors.joinToString())
}
```
