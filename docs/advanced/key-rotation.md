---
title: Key Rotation Strategies
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
import com.trustweave.TrustWeave
import com.trustweave.TrustWeaveDefaults
import kotlinx.coroutines.runBlocking

fun rotateIssuerDid() = runBlocking {
    val config = TrustWeaveDefaults.inMemory()
    val TrustWeave = TrustWeave.create(config)

    val issuerDocument = TrustWeave.dids.create()
    val issuerDid = issuerDocument.id

    val newKey = config.kms.generateKey(
        algorithm = "Ed25519",
        options = mapOf("label" to "issuer-2025q1", "exportable" to false)
    )

    val didMethod = checkNotNull(config.didRegistry.get("key")) {
        "Register the DID method you issued with before rotating keys."
    }

    val updatedDocument = didMethod.updateDid(issuerDid) { document ->
        val existingVm = requireNotNull(document.verificationMethod.firstOrNull()) {
            "The DID document must expose at least one verification method before rotation."
        }

        document.copy(
            verificationMethod = document.verificationMethod + existingVm.copy(
                id = "$issuerDid#${newKey.id}",
                publicKeyMultibase = newKey.publicKeyMultibase
            ),
            assertionMethod = document.assertionMethod + "$issuerDid#${newKey.id}"
        )
    }

    println("Issuer DID rotated: ${updatedDocument.id} now trusts ${newKey.id}")
}
```

**What this does**  
- Reuses the same configuration that bootstrapped `TrustWeave` so the sample can call the KMS and DID registry directly.  
- Generates a labelled Ed25519 key and appends it to the DID’s assertion methods without removing prior verification material.  
- Prints the updated DID so you can verify the new fragment.

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
val credential = TrustWeave.issueCredential(
    issuerDid = issuerDid,
    issuerKeyId = "$issuerDid#${newKey.id}",
    credentialSubject = subjectJson,
    types = listOf("VerifiableCredential", "EmployeeBadge")
).getOrThrow()
```

**What this does**  
- Passes the rotated verification method fragment into `issueCredential`, so proofs reference the new key immediately.  
- Returns `Result<VerifiableCredential>`, letting you integrate error handling with Kotlin’s idiomatic `Result` APIs.

**Result**  
A credential that is signed by the new key but still references the same issuer DID—verifiers will accept it once they consume the republished DID document.

**Design significance**  
The facade’s `Result`-returning APIs make rollout safe: you can stage credentials with the new key, inspect warnings, and only switch traffic once downstream verifiers confirm availability.

Keep issuing with the old key until the updated DID is published and cached by verifiers; then decommission it using `config.kms.deleteKey(oldKeyId)`.

> Need per-credential metadata (audience, schema IDs, previous key references)? Call `CredentialServiceRegistry.issue` directly with `CredentialIssuanceOptions` to supply those hints alongside the new key.

## Checklist

- [ ] Generate new key (`KeyManagementService`).  
- [ ] Update DID document / resolver entry.  
- [ ] Reconfigure issuers, wallets, and anchor clients with new `keyId`.  
- [ ] Communicate the rollout to verifiers (publish in FAQ or change log).  
- [ ] Remove the old key only after outstanding credentials expire or are re-issued.

## See also

- [Key Management](../core-concepts/key-management.md) for the underlying abstractions.  
- [DIDs](../core-concepts/dids.md) for publication guidance.  
- [Verification Policies](verification-policies.md) to enforce that proofs are signed with an expected key set.  
- [Quick Start sample](../../distribution/TrustWeave-examples/src/main/kotlin/com/geoknoesis/TrustWeave/examples/quickstart/QuickStartSample.kt) for runnable issuance code.

