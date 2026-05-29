---
title: Web of Trust Scenario
parent: Use Case Scenarios
nav_order: 18
---

# Web of Trust Scenario

This walkthrough explains how TrustWeave supports **trust registries**, **delegation**, **issuance**, and **verification** in a web-of-trust style workflow.

**Runnable reference:** [`WebOfTrustExample.kt`](../../distribution/examples/src/main/kotlin/org/trustweave/examples/trust/WebOfTrustExample.kt) in **`distribution/examples`** — keep that file as the source of truth; the snippets below are abbreviated.

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")
    testImplementation("org.trustweave:testkit:0.6.0")
}
```

## What you will do

1. Build **`TrustWeave`** with in-memory KMS, **`did:key`**, credential defaults, and a **trust registry**.
2. Create DIDs for issuers, holders, and verifiers.
3. Register **trust anchors** (`trustWeave.trust { addAnchor(...) }`).
4. Optionally model **delegation** with **`updateDid { addCapabilityDelegation(...) }`** and verify with **`trustWeave.delegate { from(...); to(...) }`**.
5. **Issue** credentials (`issue { ... }.getOrThrow()` from **`org.trustweave.credential.results`**).
6. **Verify** credentials with **`trustWeave.verify { credential(...); ... }`** and handle **`VerificationResult`** with **`when`** (or use extensions like **`valid`** / **`trustRegistryValid`** from **`org.trustweave.trust.types`**).
7. Explore **trust paths** with **`trustWeave.trust { findTrustPath(VerifierIdentity(...), IssuerIdentity(...)) }`** and list trusted issuers with **`getTrustedIssuers`**.

## 1. Configure `TrustWeave`

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.credential.model.ProofType
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)
            algorithm(KeyAlgorithms.ED25519)
        }
        did {
            method(DidMethods.KEY) {
                algorithm(KeyAlgorithms.ED25519)
            }
        }
        credentials {
            defaultProofType(ProofType.Ed25519Signature2020)
        }
        revocation(IN_MEMORY)
        trust(IN_MEMORY)
    }
    // ...
}
```

## 2. Create DIDs

```kotlin
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.types.getOrThrowDid

val universityDid = trustWeave.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}.getOrThrowDid()
```

In application code, prefer **`getOrThrowDid()`** from **`org.trustweave.trust.types`** for DID creation results.

## 3. Trust anchors

```kotlin
trustWeave.trust {
    addAnchor(universityDid.value) {
        credentialTypes("EducationCredential", "DegreeCredential")
        description("Trusted university")
    }
    val ok = isTrusted(universityDid.value, "EducationCredential")
}
```

## 4. Delegation (optional)

```kotlin
trustWeave.updateDid {
    did(companyDid.value)
    method(DidMethods.KEY)
    addCapabilityDelegation("${hrDeptDid.value}#key-1")
}

val delegation = trustWeave.delegate {
    from(companyDid.value)
    to(hrDeptDid.value)
}
// delegation.valid, delegation.path, delegation.errors
```

## 5. Issue and verify

```kotlin
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import kotlinx.datetime.Clock

val credential = trustWeave.issue {
    credential {
        type("EducationCredential")
        issuer(universityDid)
        subject { id(studentDid); "degree" { "field" to "CS" } }
        issued(Clock.System.now())
    }
    signedBy(universityDid)
}.getOrThrow()

val result = trustWeave.verify {
    credential(credential)
    checkExpiration()
}

when (result) {
    is VerificationResult.Valid -> { /* accepted */ }
    is VerificationResult.Invalid -> { /* inspect result.allErrors */ }
}
```

Trust-registry alignment is reflected on **`VerificationResult`** (e.g. **`UntrustedIssuer`**) and the **`trustRegistryValid`** extension when you import **`org.trustweave.trust.types`**. You can also query the registry explicitly inside **`trustWeave.trust { }`** (see the runnable example).

## 6. Trust paths

```kotlin
import org.trustweave.trust.types.VerifierIdentity
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.TrustPath

trustWeave.trust {
    when (
        val path = findTrustPath(
            VerifierIdentity(verifierDid),
            IssuerIdentity(universityDid),
        )
    ) {
        is TrustPath.Verified -> println(path.fullPath.joinToString { it.value })
        is TrustPath.NotFound -> println(path.reason)
    }
}
```

## Tests and templates

- Integration tests: **`InMemoryTrustWeaveIntegrationTest`**, **`WebOfTrustIntegrationTest`** under **`trust/src/test/kotlin/org/trustweave/integration/`**.
- Contributor templates: **[TrustWeave test templates](../contributing/testing/trustweave-test-templates.md)**.

## See also

- [Trust registry](../core-concepts/trust-registry.md)
- [Delegation](../core-concepts/delegation.md)
- [Proof purpose validation](../core-concepts/proof-purpose-validation.md)
- [DIDs](../core-concepts/dids.md)
- [API patterns — results vs exceptions](../getting-started/api-patterns.md)
