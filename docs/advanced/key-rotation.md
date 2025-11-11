# Key Rotation Strategies

Rotating signing keys keeps verifiable credential ecosystems resilient: compromised keys can be retired, and auditors gain a clear timeline of which key was active when. VeriCore treats key rotation as a collaboration between the KMS, DID registry, and credential services.

## When to rotate

- Scheduled security policy (e.g., every 90 days).  
- Incident response if a private key is suspected to be leaked.  
- Upgrading to a new signature suite or hardware security module.

## How rotation works in VeriCore

1. **Mint a replacement key** via `KeyManagementService`.  
2. **Update the DID document** published for the issuer (new verification method, adjust `assertionMethod`).  
3. **Reconfigure the credential issuer** to use the new `keyId`.  
4. **Maintain historic keys** so existing credentials remain verifiable until they expire or are re-issued.

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.did.didCreationOptions
import kotlinx.coroutines.runBlocking

fun rotateIssuer(vericore: VeriCore, issuerDid: String) = runBlocking {
    val kms = vericore.context.kms

    val newKey = kms.generateKey(
        algorithm = "Ed25519",
        options = mapOf("label" to "issuer-2025q1", "exportable" to false)
    )

    vericore.updateDid(issuerDid) { document ->
        document.copy(
            verificationMethod = document.verificationMethod + document.verificationMethod.first().copy(
                id = "$issuerDid#${newKey.id}",
                publicKeyMultibase = newKey.publicKeyMultibase
            ),
            assertionMethod = document.assertionMethod + "$issuerDid#${newKey.id}"
        )
    }

    println("Issuer DID rotated: ${issuerDid} now trusts ${newKey.id}")
}
```

> Tip: use `vericore-testkit` to simulate rotation in tests—`InMemoryKeyManagementService` lets you assert that both old and new keys are available during the transition.

## Publishing the updated DID

- For `did:key`, rotation happens locally (the DID string includes the key); issue new credentials with the new DID.  
- For hosted methods (`did:web`, `did:ion`, registry-backed methods) publish the updated DID document to the resolver.  
- Anchor the change or log it in your audit system for downstream verifiers.

## Migrating issuers

After rotation update any issuance code to reference the new `keyId`:

```kotlin
val credential = vericore.issueCredential(
    issuerDid = issuerDid,
    issuerKeyId = "$issuerDid#${newKey.id}",
    credentialSubject = subjectJson,
    types = listOf("VerifiableCredential", "EmployeeBadge")
).getOrThrow()
```

Keep issuing with the old key until the updated DID is published and cached by verifiers; then decommission it using `kms.deleteKey(oldKeyId)`.

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
- [Quick Start sample](../../vericore-examples/src/main/kotlin/com/geoknoesis/vericore/examples/quickstart/QuickStartSample.kt) for runnable issuance code.

