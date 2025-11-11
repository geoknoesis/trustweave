# Verification Policies

Verifiers often need more than a boolean “valid/invalid”. VeriCore lets you layer policy checks—expiration, audience, revocation status, anchor validation—on top of signature verification.

## Built-in options

`CredentialVerificationOptions` and `PresentationVerificationOptions` expose toggles for:

- **Expiration** – reject credentials whose `expirationDate` has passed.  
- **Proof purpose** – enforce `assertionMethod`, `authentication`, or custom proof purposes.  
- **Audience/Domain** – match against the values embedded in the VC or presentation.  
- **Status checks** – consult revocation lists when status handlers are registered.

## Custom policy workflow

1. Configure the options object.  
2. Call `VeriCore.verifyCredential` (or presentation variant).  
3. Inspect `CredentialVerificationResult` for additional diagnostics (`valid`, `checks`, `errors`).  
4. Apply domain-specific rules to the result.

```kotlin
import com.geoknoesis.vericore.credential.CredentialVerificationOptions

val options = CredentialVerificationOptions(
    checkExpiration = true,
    checkSchema = true,
    enforceStatus = true,
    expectedAudience = setOf("did:key:verifier-123"),
    requireAnchoring = true
)

val result = vericore.verifyCredential(credential, options)
result.fold(
    onSuccess = { verification ->
        if (!verification.valid) {
            println("Credential failed checks: ${verification.errors}")
            require("ANCHOR" !in verification.errors.keys) { "Anchor missing" }
        } else {
            println("Credential verified with checks: ${verification.checks}")
        }
    },
    onFailure = { error ->
        println("Verification failed: ${error.message}")
    }
)
```

## Extending status and anchor checks

- **Revocation** – register a status list service (e.g., RevocationList2020) and populate `credentialStatus`. The verification result will include `STATUS=true/false`.  
- **Anchoring** – set `requireAnchoring = true` and provide a lookup that maps `AnchorRef` back to stored digests. Combine with [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md).  
- **Custom policies** – wrap the verification call in a separate function that adds business rules (issue date windows, subject DID allowlists, etc.).

## Testing policies

Use `vericore-testkit` to simulate responses (expired credentials, revoked status, missing anchors). This ensures policy regressions surface in CI:

```kotlin
val expiredCredential = testFixture.createCredential {
    expirationDate = Instant.now().minusSeconds(60).toString()
}
val policyResult = vericore.verifyCredential(expiredCredential, options)
assertFalse(policyResult.getOrThrow().valid)
```

## See also

- [Verifiable Credentials](../core-concepts/verifiable-credentials.md) for credential structure.  
- [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) to understand how anchors feed policy checks.  
- [Quick Start sample](../../vericore-examples/src/main/kotlin/com/geoknoesis/vericore/examples/quickstart/QuickStartSample.kt) demonstrates baseline verification.  
- [Key Rotation](key-rotation.md) for maintaining trusted key sets.

