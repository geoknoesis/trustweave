---
title: Key Rotation Strategies
nav_exclude: true
---

# Key Rotation Strategies

Rotating signing keys keeps verifiable credential ecosystems resilient: compromised keys can be retired, and auditors gain a clear timeline of which key was active when. TrustWeave treats key rotation as a collaboration between the KMS, DID registry, and credential services.

## When to rotate

- Scheduled security policy (e.g., every 90 days).
- Incident response if a private key is suspected to be leaked.
- Upgrading to a new signature suite or hardware security module.

## How rotation works in TrustWeave

1. **Mint a replacement key** via `KeyManagementService`.
2. **Update the DID document** published for the issuer (new verification method, adjust `assertionMethod`).
3. **Reconfigure the credential issuer** to use the new `keyId`.
4. **Maintain historic keys** so existing credentials remain verifiable until they expire or are re-issued.

**Goal:** Create a fresh Ed25519 key and append it to an existing DID document while keeping the old key for historical verification.
**Prerequisites:** Build your `TrustWeave` instance from a `TrustWeaveConfig` so you retain direct access to the underlying KMS and DID registry.

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.testkit.services.*
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.*
import org.trustweave.trust.types.getOrThrow

fun rotateIssuerDid() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
    val config = trustWeave.configuration
    val (issuerDid, document) = trustWeave.createDid { }.getOrThrow()

    // 1. Generate a new key with `config.kms.generateKey(...)` (see KMS docs).
    // 2. Obtain the `DidMethod` from `config.didRegistry` and call `updateDid` / method-specific APIs
    //    to publish a document that adds the new verification method (keep old VMs until verifiers migrate).
    // 3. Issue new credentials with `trustWeave.issue { ... signedBy(issuerDid, newKeyFragment) }`.

    println("Issuer DID: ${issuerDid.value}; starting document has ${document.verificationMethod.size} verification method(s)")
}
```

**What this does**
- Shows the supported wiring: build a **`TrustWeave`**, read **`trustWeave.configuration`** for **`kms`** and **`didRegistry`**, and create the issuer DID. The exact **`updateDid`** / publication steps depend on your DID method (`did:key` vs hosted methods).
- After the document lists the new verification method, use **`signedBy(issuerDid, newKeyFragment)`** so new credentials pick up the rotated key.

**Result**
`updatedDocument` contains both the old and the new verification method entries, ensuring existing credentials remain verifiable while future credentials rely on the new key.

**Design significance**
TrustWeave keeps cryptographic operations behind typed services. Holding onto the original `TrustWeaveConfig` is an intentional pattern: it lets advanced workflows (like manual key rotation) operate on the same single source of truth your facade uses internally.

> Tip: use `TrustWeave-testkit` to simulate rotation in tests—`InMemoryKeyManagementService` lets you assert that both old and new keys are available during the transition.

## Publishing the updated DID

- For `did:key`, rotation happens locally (the DID string includes the key); issue new credentials with the new DID.
- For hosted methods (`did:web`, `did:ion`, registry-backed methods) publish the updated DID document to the resolver.
- Anchor the change or log it in your audit system for downstream verifiers.

## Migrating issuers

After rotation update any issuance code to reference the new `keyId`:

**Goal:** Switch issuance to the rotated key while you gradually retire the previous fragment.
**Prerequisites:** The DID document has already been updated and published to whichever resolver your ecosystem relies on.

```kotlin
import org.trustweave.credential.results.getOrThrow

val credential = trustWeave.issue {
    credential {
        type("VerifiableCredential", "EmployeeBadge")
        issuer(issuerDid)
        // … subject / claims from your model …
    }
    signedBy(issuerDid, newKey.id.value) // verification method fragment after rotation
}.getOrThrow()
```

**What this does**
- Passes the rotated verification method fragment into `signedBy`, so proofs reference the new key immediately.
- `issue` returns **`IssuanceResult`**; **`getOrThrow()`** collapses failures to an exception when that matches your rollout strategy.

**Result**
A credential that is signed by the new key but still references the same issuer DID—verifiers will accept it once they consume the republished DID document.

**Design significance**
Prefer **`when (issued)`** on **`IssuanceResult`** in production so you can branch on **`AdapterNotReady`**, validation failures, and adapter errors without exceptions.

Keep issuing with the old key until the updated DID is published and cached by verifiers; then decommission it using `config.kms.deleteKey(oldKeyId)`.

> Need per-credential metadata (audience, schema IDs, previous key references)? Use **`CredentialService.issue(IssuanceRequest(...))`** with **`ProofOptions`** / **`IssuanceRequest` fields**, or extend the **`trustWeave.issue { }`** DSL inputs where supported.

## Checklist

- Generate new key (`KeyManagementService`).
- Update DID document / resolver entry.
- Reconfigure issuers, wallets, and anchor clients with new `keyId`.
- Communicate the rollout to verifiers (publish in FAQ or change log).
- Remove the old key only after outstanding credentials expire or are re-issued.

## See also

- Key Management](../core-concepts/key-management.md) for the underlying abstractions.
- DIDs](../core-concepts/dids.md) for publication guidance.
- Verification Policies](verification-policies.md) to enforce that proofs are signed with an expected key set.
- Quick Start sample](../../distribution/TrustWeave-examples/src/main/kotlin/com/geoknoesis/TrustWeave/examples/quickstart/QuickStartSample.kt) for runnable issuance code.

